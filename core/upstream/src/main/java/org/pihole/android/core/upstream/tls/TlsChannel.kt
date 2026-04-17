package org.pihole.android.core.upstream.tls

import java.io.Closeable

interface TlsChannel : Closeable {
    fun writeFully(bytes: ByteArray)

    fun readFully(length: Int): ByteArray
}
