package org.pihole.android.core.upstream.tls

import org.pihole.android.core.upstream.transport.BidirectionalStream

fun interface TlsChannelFactory {
    fun openClientChannel(host: String, port: Int, transport: BidirectionalStream): TlsChannel
}
