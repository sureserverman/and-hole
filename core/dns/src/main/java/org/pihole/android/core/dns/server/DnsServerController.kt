package org.pihole.android.core.dns.server

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.pihole.android.core.dns.codec.DnsCodec
import org.pihole.android.core.dns.codec.DnsConstants
import org.pihole.android.core.dns.upstream.DnsUpstream
import org.pihole.android.core.filter.rules.CompiledMatcher
import org.pihole.android.core.filter.rules.FilterDecision
import org.pihole.android.core.filter.rules.RuleKind
import java.net.InetAddress

class DnsServerController(
    private val scope: CoroutineScope,
    private val listenAddress: InetAddress = InetAddress.getByName("127.0.0.1"),
    private val matcher: CompiledMatcher? = null,
    private val upstream: DnsUpstream? = null,
    private val localRecords: List<StaticDnsRr> = emptyList(),
    private val onQueryLog: (suspend (DnsQueryLogEvent) -> Unit)? = null,
) {
    private val bindLock = Any()

    private var udp: UdpDnsServer? = null
    private var tcp: TcpDnsServer? = null
    private val cache = SimpleDnsCache()

    var port: Int = 53_535
        private set

    /**
     * Binds UDP and TCP on [listenAddress]:[port] before returning, so clients (VPN forwarders,
     * sing-box) do not race a "listener running" state against an unbound socket.
     */
    suspend fun start(port: Int) {
        lateinit var udpBound: CompletableDeferred<Unit>
        lateinit var tcpBound: CompletableDeferred<Unit>
        lateinit var udpSrv: UdpDnsServer
        lateinit var tcpSrv: TcpDnsServer
        val handler: suspend (ByteArray) -> ByteArray? = { packet -> handleQuery(packet) }
        synchronized(bindLock) {
            stop()
            this.port = port
            udpSrv = UdpDnsServer(scope, listenAddress, port, handler)
            tcpSrv = TcpDnsServer(scope, listenAddress, port, handler)
            udpBound = udpSrv.start()
            tcpBound = tcpSrv.start()
            udp = udpSrv
            tcp = tcpSrv
        }
        try {
            withTimeout(15_000L) {
                udpBound.await()
                tcpBound.await()
            }
        } catch (e: TimeoutCancellationException) {
            stop()
            throw IllegalStateException("DNS listener bind timed out after 15s", e)
        } catch (e: Throwable) {
            stop()
            throw e
        }
    }

    fun stop() {
        synchronized(bindLock) {
            udp?.stop()
            tcp?.stop()
            udp = null
            tcp = null
            // Brief yield so the OS releases loopback UDP/TCP sockets before the next bind
            // (instrumented suites restart the listener rapidly; EADDRINUSE otherwise).
            try {
                Thread.sleep(150L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private suspend fun handleQuery(packet: ByteArray): ByteArray? {
        val startedAt = System.currentTimeMillis()
        var responseCode = 0
        var decisionStr = "pass"
        var matchedRuleId: Long? = null
        var matchedSourceId: Long? = null
        val (queryId, questions) = DnsCodec.parseQuestions(packet)
        if (questions.size != 1) return null
        val q = questions.first()
        val normalized = q.qname.trim().lowercase()

        if (normalized == "test.pi-hole.local." && q.qtype == DnsConstants.QTYPE_A) {
            val answer = DnsCodec.buildARecordAnswer(
                q.qname,
                60,
                DnsCodec.ipv4Bytes(192, 0, 2, 1),
            )
            decisionStr = "allowed"
            val resp = DnsCodec.buildResponseQuery(queryId, q, answer)
            emitQueryLogIfNeeded(
                startedAt,
                q.qname,
                q.qtype,
                decisionStr,
                matchedRuleId,
                matchedSourceId,
                responseCode,
                resp,
                fromCache = false,
            )
            return resp
        }

        StaticDnsResponder.buildAnswerPayloadIfHit(q.qname, q.qtype, localRecords)?.let { payload ->
            decisionStr = "allowed"
            val resp = DnsCodec.buildResponseQuery(queryId, q, payload)
            emitQueryLogIfNeeded(
                startedAt,
                q.qname,
                q.qtype,
                decisionStr,
                matchedRuleId,
                matchedSourceId,
                responseCode,
                resp,
                fromCache = false,
            )
            return resp
        }

        val m = matcher
        if (m != null) {
            val matchDecision = m.match(q.qname)
            when (matchDecision) {
                is FilterDecision.Block -> {
                    decisionStr = "blocked"
                    when (matchDecision.reason) {
                        RuleKind.SUBSCRIBED_DENY ->
                            matchedSourceId = MATCHED_SUBSCRIBED_LIST_SENTINEL
                        RuleKind.EXACT_DENY,
                        RuleKind.REGEX_DENY,
                        ->
                            matchedRuleId = MATCHED_CUSTOM_RULE_SENTINEL
                        else -> Unit
                    }
                    when (q.qtype) {
                        DnsConstants.QTYPE_A -> {
                            val answer = DnsCodec.buildARecordAnswer(
                                q.qname,
                                60,
                                DnsCodec.nullIpv4(),
                            )
                            val resp = DnsCodec.buildResponseQuery(queryId, q, answer)
                            emitQueryLogIfNeeded(
                                startedAt,
                                q.qname,
                                q.qtype,
                                decisionStr,
                                matchedRuleId,
                                matchedSourceId,
                                responseCode,
                                resp,
                                fromCache = false,
                            )
                            return resp
                        }
                        DnsConstants.QTYPE_AAAA -> {
                            val answer = DnsCodec.buildAaaaRecordAnswer(
                                q.qname,
                                60,
                                DnsCodec.nullIpv6(),
                            )
                            val resp = DnsCodec.buildResponseQuery(queryId, q, answer)
                            emitQueryLogIfNeeded(
                                startedAt,
                                q.qname,
                                q.qtype,
                                decisionStr,
                                matchedRuleId,
                                matchedSourceId,
                                responseCode,
                                resp,
                                fromCache = false,
                            )
                            return resp
                        }
                        else -> return null
                    }
                }
                FilterDecision.Allow,
                FilterDecision.Pass,
                -> { /* continue */ }
            }
            if (matchDecision is FilterDecision.Allow) decisionStr = "allowed"
        }

        val u = upstream
        if (u != null) {
            cache.get(q.qname, q.qtype, startedAt)?.let { cached ->
                if (DnsCodec.isValidResponseForQuery(queryId, cached)) {
                    responseCode = readRcode(cached)
                    emitQueryLogIfNeeded(
                        startedAt,
                        q.qname,
                        q.qtype,
                        decisionStr,
                        matchedRuleId,
                        matchedSourceId,
                        responseCode,
                        cached,
                        fromCache = true,
                    )
                    return cached
                }
            }

            val resp = withContext(Dispatchers.IO) { u.forward(packet) }
            if (resp == null || !DnsCodec.isValidResponseForQuery(queryId, resp)) {
                Log.w(
                    "PiholeDns",
                    "upstream: null or id mismatch for qname=${q.qname} qtype=${q.qtype} (see Diagnostics last upstream error + logcat PiholeDns)",
                )
            }
            if (resp != null && DnsCodec.isValidResponseForQuery(queryId, resp)) {
                responseCode = readRcode(resp)
                if (m != null && responseContainsBlockedCnameTarget(resp, m)) {
                    decisionStr = "blocked"
                    matchedSourceId = matchedSourceId ?: MATCHED_SUBSCRIBED_LIST_SENTINEL
                    val blockResp =
                        when (q.qtype) {
                            DnsConstants.QTYPE_A -> {
                                val answer = DnsCodec.buildARecordAnswer(q.qname, 60, DnsCodec.nullIpv4())
                                DnsCodec.buildResponseQuery(queryId, q, answer)
                            }
                            DnsConstants.QTYPE_AAAA -> {
                                val answer = DnsCodec.buildAaaaRecordAnswer(q.qname, 60, DnsCodec.nullIpv6())
                                DnsCodec.buildResponseQuery(queryId, q, answer)
                            }
                            else -> null
                        }
                    if (blockResp != null) {
                        emitQueryLogIfNeeded(
                            startedAt,
                            q.qname,
                            q.qtype,
                            decisionStr,
                            matchedRuleId,
                            matchedSourceId,
                            responseCode,
                            blockResp,
                            fromCache = false,
                        )
                        return blockResp
                    }
                }
                cache.putIfNoError(q.qname, q.qtype, resp, startedAt)
                emitQueryLogIfNeeded(
                    startedAt,
                    q.qname,
                    q.qtype,
                    decisionStr,
                    matchedRuleId,
                    matchedSourceId,
                    responseCode,
                    resp,
                    fromCache = false,
                )
                return resp
            }
            val servfail = try {
                DnsCodec.buildServFailFromQuery(packet)
            } catch (_: IllegalArgumentException) {
                null
            }
            if (servfail != null) {
                responseCode = DnsConstants.RCODE_SERVFAIL
                emitQueryLogIfNeeded(
                    startedAt,
                    q.qname,
                    q.qtype,
                    decisionStr,
                    matchedRuleId,
                    matchedSourceId,
                    responseCode,
                    servfail,
                    fromCache = false,
                )
            }
            return servfail
        }

        emitQueryLogIfNeeded(
            startedAt,
            q.qname,
            q.qtype,
            decisionStr,
            matchedRuleId,
            matchedSourceId,
            responseCode,
            null,
            fromCache = false,
        )
        return null
    }

    private fun responseContainsBlockedCnameTarget(resp: ByteArray, m: CompiledMatcher): Boolean {
        var blocked = false
        DnsCodec.forEachCnameTargetInAnswers(resp) { target ->
            if (m.match(target) is FilterDecision.Block) blocked = true
        }
        return blocked
    }

    private fun readRcode(response: ByteArray): Int {
        if (response.size < 4) return 0
        val flags = ((response[2].toInt() and 0xFF) shl 8) or (response[3].toInt() and 0xFF)
        return flags and 0xF
    }

    private suspend fun emitQueryLogIfNeeded(
        startedAtEpochMs: Long,
        qname: String,
        qtype: Int,
        decision: String,
        matchedRuleId: Long?,
        matchedSourceId: Long?,
        responseCode: Int,
        response: ByteArray?,
        fromCache: Boolean,
    ) {
        val cb = onQueryLog ?: return
        val now = System.currentTimeMillis()
        val latency = (now - startedAtEpochMs).coerceAtLeast(0L)
        cb(
            DnsQueryLogEvent(
                timestampEpochMs = startedAtEpochMs,
                qname = qname,
                qtype = qtype,
                decision = decision,
                matchedRuleId = matchedRuleId,
                matchedSourceId = matchedSourceId,
                responseCode = responseCode,
                latencyMs = latency,
                answeredFromCache = fromCache,
            ),
        )
    }

    companion object {
        /**
         * Sentinel attribution values used when the matcher can classify source class but has no DB IDs.
         */
        const val MATCHED_SUBSCRIBED_LIST_SENTINEL: Long = -1L
        const val MATCHED_CUSTOM_RULE_SENTINEL: Long = -2L
    }
}
