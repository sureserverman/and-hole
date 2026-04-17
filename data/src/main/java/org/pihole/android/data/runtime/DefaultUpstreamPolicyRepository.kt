package org.pihole.android.data.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.pihole.android.data.db.AppDatabase
import org.pihole.android.data.db.entity.UpstreamResolverEntity

class DefaultUpstreamPolicyRepository(
    private val db: AppDatabase,
) : UpstreamPolicyRepository {

    override val resolvers: Flow<List<UpstreamResolver>> =
        db.upstreamResolverDao().observeAllOrdered().map { rows -> rows.map { it.toModel() } }

    override suspend fun listNow(): List<UpstreamResolver> = db.upstreamResolverDao().getAllOrdered().map { it.toModel() }

    override suspend fun addResolver(
        label: String,
        host: String,
        port: Int,
        tlsServerName: String?,
        enabled: Boolean,
    ): Long {
        val nextSortOrder = db.upstreamResolverDao().getAllOrdered().maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
        return db.upstreamResolverDao().insert(
            UpstreamResolverEntity(
                label = label,
                host = host,
                port = port,
                tlsServerName = tlsServerName?.takeIf { it.isNotBlank() },
                enabled = enabled,
                sortOrder = nextSortOrder,
            ),
        )
    }

    override suspend fun updateResolver(
        id: Long,
        label: String,
        host: String,
        port: Int,
        tlsServerName: String?,
        enabled: Boolean,
    ) {
        val current = db.upstreamResolverDao().getById(id) ?: return
        db.upstreamResolverDao().update(
            current.copy(
                label = label,
                host = host,
                port = port,
                tlsServerName = tlsServerName?.takeIf { it.isNotBlank() },
                enabled = enabled,
            ),
        )
    }

    override suspend fun deleteResolver(id: Long) {
        db.upstreamResolverDao().deleteById(id)
        normalizeSortOrder()
    }

    override suspend fun setResolverEnabled(id: Long, enabled: Boolean) {
        val current = db.upstreamResolverDao().getById(id) ?: return
        db.upstreamResolverDao().update(current.copy(enabled = enabled))
    }

    override suspend fun moveResolverUp(id: Long) {
        val rows = db.upstreamResolverDao().getAllOrdered()
        val idx = rows.indexOfFirst { it.id == id }
        if (idx <= 0) return
        swapSort(rows[idx - 1], rows[idx])
    }

    override suspend fun moveResolverDown(id: Long) {
        val rows = db.upstreamResolverDao().getAllOrdered()
        val idx = rows.indexOfFirst { it.id == id }
        if (idx == -1 || idx >= rows.lastIndex) return
        swapSort(rows[idx], rows[idx + 1])
    }

    private suspend fun swapSort(a: UpstreamResolverEntity, b: UpstreamResolverEntity) {
        db.upstreamResolverDao().update(a.copy(sortOrder = b.sortOrder))
        db.upstreamResolverDao().update(b.copy(sortOrder = a.sortOrder))
    }

    private suspend fun normalizeSortOrder() {
        db.upstreamResolverDao().getAllOrdered().forEachIndexed { index, row ->
            if (row.sortOrder != index) {
                db.upstreamResolverDao().update(row.copy(sortOrder = index))
            }
        }
    }
}

private fun UpstreamResolverEntity.toModel(): UpstreamResolver =
    UpstreamResolver(
        id = id,
        label = label,
        host = host,
        port = port,
        tlsServerName = tlsServerName,
        enabled = enabled,
        sortOrder = sortOrder,
    )

