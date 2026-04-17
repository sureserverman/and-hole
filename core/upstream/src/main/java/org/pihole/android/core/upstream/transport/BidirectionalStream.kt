package org.pihole.android.core.upstream.transport

import java.io.Closeable

interface BidirectionalStream : Closeable {
    fun writeFully(bytes: ByteArray)

    fun readFully(length: Int): ByteArray
}
