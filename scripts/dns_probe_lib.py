"""
Shared DNS probe helpers for And-hole Android loopback (TCP per RFC 1035 length prefix, UDP raw datagram).
"""
from __future__ import annotations

import random
import socket
import struct
import subprocess
from typing import Literal, Optional

QTYPE_A = 1
QCLASS_IN = 1

Protocol = Literal["tcp", "udp"]


def encode_name(fqdn: str) -> bytes:
    fqdn = fqdn.strip().lower().rstrip(".")
    if not fqdn:
        return b"\x00"
    out = bytearray()
    for part in fqdn.split("."):
        b = part.encode("ascii", errors="strict")
        if len(b) > 63:
            raise ValueError(f"label too long: {part!r}")
        out.append(len(b))
        out.extend(b)
    out.append(0)
    return bytes(out)


def normalize_fqdn(name: str) -> str:
    n = name.strip().lower()
    return n if n.endswith(".") else n + "."


def build_query(packet_id: int, fqdn: str) -> bytes:
    qname = encode_name(fqdn + ".") if not fqdn.endswith(".") else encode_name(fqdn)
    body = bytearray()
    body.extend(struct.pack("!HHHHHH", packet_id, 0, 1, 0, 0, 0))
    body.extend(qname)
    body.extend(struct.pack("!HH", QTYPE_A, QCLASS_IN))
    return bytes(body)


def random_query_id() -> int:
    return random.randint(1, 0xFFFE)


def _recv_exact(sock: socket.socket, n: int, deadline_timeout: float) -> bytes:
    sock.settimeout(deadline_timeout)
    chunks: list[bytes] = []
    remaining = n
    while remaining > 0:
        chunk = sock.recv(remaining)
        if not chunk:
            break
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)


def tcp_dns_exchange(host: str, port: int, query: bytes, timeout: float) -> Optional[bytes]:
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(timeout)
    try:
        sock.connect((host, port))
        sock.sendall(struct.pack("!H", len(query)) + query)
        hdr = _recv_exact(sock, 2, timeout)
        if len(hdr) < 2:
            return None
        msg_len = struct.unpack("!H", hdr)[0]
        if msg_len == 0 or msg_len > 4096:
            return None
        return _recv_exact(sock, msg_len, timeout)
    except (OSError, socket.timeout, struct.error):
        return None
    finally:
        try:
            sock.close()
        except OSError:
            pass


def udp_dns_exchange(host: str, port: int, query: bytes, timeout: float) -> Optional[bytes]:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.settimeout(timeout)
    try:
        sock.sendto(query, (host, port))
        data, _ = sock.recvfrom(4096)
        return data
    except (OSError, socket.timeout):
        return None
    finally:
        try:
            sock.close()
        except OSError:
            pass


def dns_exchange(protocol: Protocol, host: str, port: int, query: bytes, timeout: float) -> Optional[bytes]:
    if protocol == "tcp":
        return tcp_dns_exchange(host, port, query, timeout)
    return udp_dns_exchange(host, port, query, timeout)


def a_rdata_all_zero(resp: bytes) -> bool:
    if len(resp) < 12:
        return False
    ancount = struct.unpack("!H", resp[6:8])[0]
    if ancount < 1:
        return False
    return len(resp) >= 4 and resp[-4:] == b"\x00\x00\x00\x00"


def a_rdata_192_0_2_1(resp: bytes) -> bool:
    return len(resp) >= 4 and resp[-4:] == bytes([192, 0, 2, 1])


def adb_forward_add(
    protocol: Protocol,
    local_port: int,
    remote_port: int,
    adb: str,
    serial: Optional[str],
) -> None:
    cmd = [adb]
    if serial:
        cmd.extend(["-s", serial])
    cmd.extend(["forward", f"{protocol}:{local_port}", f"{protocol}:{remote_port}"])
    subprocess.run(cmd, check=True, capture_output=True, text=True)


def adb_forward_remove(protocol: Protocol, local_port: int, adb: str, serial: Optional[str]) -> None:
    cmd = [adb]
    if serial:
        cmd.extend(["-s", serial])
    cmd.extend(["forward", "--remove", f"{protocol}:{local_port}"])
    subprocess.run(cmd, capture_output=True, text=True)


def adb_supports_udp_forward(adb: str, serial: Optional[str]) -> bool:
    """Some distro adb builds omit udp: forwarding; probe a throwaway mapping."""
    lp, rp = 27998, 27999
    try:
        adb_forward_add("udp", lp, rp, adb, serial)
        adb_forward_remove("udp", lp, adb, serial)
        return True
    except subprocess.CalledProcessError:
        try:
            adb_forward_remove("udp", lp, adb, serial)
        except Exception:
            pass
        return False


def dns_rcode(resp: Optional[bytes]) -> Optional[int]:
    if resp is None or len(resp) < 4:
        return None
    flags = struct.unpack("!H", resp[2:4])[0]
    return flags & 0xF


def evaluate_expect(
    expect: str,
    name: str,
    resp: Optional[bytes],
    protocol: Protocol,
    *,
    strict_pass: bool = False,
) -> tuple[int, str]:
    """Return (exit_code, one_line_message)."""
    transport = "TCP" if protocol == "tcp" else "UDP"
    if expect == "blocked":
        if resp is None or len(resp) < 12:
            return (
                1,
                f"FAIL: expected blocked (0.0.0.0 A), got no/short response ({len(resp or b'')}) [{transport}]",
            )
        if not a_rdata_all_zero(resp):
            last = resp[-4:].hex() if len(resp) >= 4 else ""
            return (1, f"FAIL: expected A RDATA 0.0.0.0 (last4={last}) [{transport}]")
        return (0, f"OK: blocked {name!r} ({len(resp)} bytes) [{transport}]")

    if expect == "test-host":
        if resp is None or len(resp) < 12:
            return (1, f"FAIL: expected test-host 192.0.2.1, got no/short response [{transport}]")
        if not a_rdata_192_0_2_1(resp):
            last = resp[-4:].hex() if len(resp) >= 4 else ""
            return (1, f"FAIL: expected A 192.0.2.1, last4={last} [{transport}]")
        return (0, f"OK: test-host {name!r} -> 192.0.2.1 ({len(resp)} bytes) [{transport}]")

    # pass: not sinkholed to NULL A (0.0.0.0); silence or any real/SERVFAIL answer OK (resolver may answer)
    if resp is None or len(resp) == 0:
        if strict_pass:
            return (
                1,
                f"FAIL: strict-pass requires DNS payload for {name!r} (no {transport} reply) [{transport}]",
            )
        return (0, f"OK: pass {name!r} (no {transport} DNS payload)")
    if strict_pass:
        rc = dns_rcode(resp)
        if rc is not None and rc != 0:
            return (
                1,
                f"FAIL: strict-pass expected NOERROR (rcode=0) for {name!r}, got rcode={rc} ({len(resp)} bytes) [{transport}]",
            )
    if a_rdata_all_zero(resp):
        return (
            1,
            f"FAIL: pass expected not NULL-blocked, got 0.0.0.0 A ({len(resp)} bytes) [{transport}]",
        )
    if strict_pass:
        ancount = struct.unpack("!H", resp[6:8])[0] if len(resp) >= 8 else 0
        if ancount < 1:
            return (
                1,
                f"FAIL: strict-pass expected ANCOUNT>=1 for {name!r}, got {ancount} [{transport}]",
            )
    return (0, f"OK: pass {name!r} ({len(resp)} bytes) [{transport}]")
