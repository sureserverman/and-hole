package org.pihole.android.core.upstream.tls

import org.pihole.android.core.upstream.transport.BidirectionalStream

class PlatformTlsChannelFactory : TlsChannelFactory {
    override fun openClientChannel(host: String, port: Int, transport: BidirectionalStream): TlsChannel {
        return SsLEngineTlsChannel(host = host, port = port, transport = transport)
    }
}
