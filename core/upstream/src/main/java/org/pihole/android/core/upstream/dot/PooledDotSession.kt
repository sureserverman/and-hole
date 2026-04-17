package org.pihole.android.core.upstream.dot

import java.io.Closeable

internal class PooledDotSession(
    private val connector: () -> DotConnection,
) : Closeable {
    private var connection: DotConnection? = null

    fun ensureConnected() {
        if (connection == null) {
            connection = connector()
        }
    }

    fun exchange(message: ByteArray): ByteArray {
        val hadExistingConnection = connection != null
        return try {
            exchangeOn(connection ?: connector().also { connection = it }, message)
        } catch (firstFailure: Exception) {
            val shouldRetry = hadExistingConnection
            resetFailedConnection()
            if (!shouldRetry) {
                throw firstFailure
            }
            try {
                exchangeOn(connector().also { connection = it }, message)
            } catch (retryFailure: Exception) {
                resetFailedConnection()
                retryFailure.addSuppressed(firstFailure)
                throw retryFailure
            }
        }
    }

    override fun close() {
        closeQuietly(connection)
        connection = null
    }

    private fun closeQuietly(connection: DotConnection?) {
        runCatching { connection?.close() }
    }

    private fun exchangeOn(connection: DotConnection, message: ByteArray): ByteArray {
        connection.writeLengthPrefixed(message)
        return connection.readLengthPrefixed()
    }

    private fun resetFailedConnection() {
        closeQuietly(connection)
        connection = null
    }
}
