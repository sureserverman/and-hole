package org.pihole.android.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import org.pihole.android.data.db.dao.AdlistSourceDao
import org.pihole.android.data.db.dao.CompiledSnapshotDao
import org.pihole.android.data.db.dao.CustomRuleDao
import org.pihole.android.data.db.dao.LocalDnsRecordDao
import org.pihole.android.data.db.dao.QueryLogDao
import org.pihole.android.data.db.dao.UpstreamResolverDao
import org.pihole.android.data.db.entity.AdlistSourceEntity
import org.pihole.android.data.db.entity.CompiledSnapshotEntity
import org.pihole.android.data.db.entity.CustomRuleEntity
import org.pihole.android.data.db.entity.LocalDnsRecordEntity
import org.pihole.android.data.db.entity.QueryLogEntity
import org.pihole.android.data.db.entity.RefreshRunEntity
import org.pihole.android.data.db.entity.UpstreamResolverEntity

@Database(
    entities = [
        AdlistSourceEntity::class,
        CustomRuleEntity::class,
        QueryLogEntity::class,
        CompiledSnapshotEntity::class,
        RefreshRunEntity::class,
        LocalDnsRecordEntity::class,
        UpstreamResolverEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun adlistSourceDao(): AdlistSourceDao
    abstract fun customRuleDao(): CustomRuleDao
    abstract fun queryLogDao(): QueryLogDao
    abstract fun compiledSnapshotDao(): CompiledSnapshotDao

    abstract fun localDnsRecordDao(): LocalDnsRecordDao
    abstract fun upstreamResolverDao(): UpstreamResolverDao
}
