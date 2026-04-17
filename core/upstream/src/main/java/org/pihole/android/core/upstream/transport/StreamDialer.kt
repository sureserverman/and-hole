package org.pihole.android.core.upstream.transport

fun interface StreamDialer {
    fun connect(host: String, port: Int): BidirectionalStream
}
