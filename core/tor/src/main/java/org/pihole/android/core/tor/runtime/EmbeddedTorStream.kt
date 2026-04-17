package org.pihole.android.core.tor.runtime

import java.io.Closeable

interface EmbeddedTorStream : Closeable {
    fun writeFully(bytes: ByteArray)

    fun readFully(length: Int): ByteArray
}
