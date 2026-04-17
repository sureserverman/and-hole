package org.pihole.android.core.tor.runtime

import java.io.File

internal object ArtiJni {
    init {
        System.loadLibrary("andhole_arti_bridge")
    }

    external fun nativeInit(dataDir: String)

    external fun nativeStart()

    /**
     * 0 = stopped, 1 = starting, 2 = ready, 3 = failed
     */
    external fun nativeBootstrapState(): Int

    external fun nativeBootstrapProgress(): Int

    external fun nativeBootstrapSummary(): String

    external fun nativeLastError(): String

    external fun nativeOpenStream(host: String, port: Int): Long

    external fun nativeWrite(streamId: Long, bytes: ByteArray)

    external fun nativeRead(streamId: Long, length: Int): ByteArray

    external fun nativeCloseStream(streamId: Long)

    external fun nativeShutdown()

    fun initOnce(dataDir: File) {
        dataDir.mkdirs()
        nativeInit(dataDir.absolutePath)
    }
}

