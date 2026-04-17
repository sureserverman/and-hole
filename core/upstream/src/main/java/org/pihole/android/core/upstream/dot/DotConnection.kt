package org.pihole.android.core.upstream.dot

import java.io.Closeable

interface DotConnection : Closeable {
    fun writeLengthPrefixed(message: ByteArray)
    fun readLengthPrefixed(): ByteArray
}
