package org.pihole.android.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    val MIGRATION_3_4: Migration =
        object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_query_log_timestamp ON query_log(timestamp)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_query_log_decision ON query_log(decision)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_query_log_qname_timestamp ON query_log(qname, timestamp)",
                )
            }
        }

    val MIGRATION_4_5: Migration =
        object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS upstream_resolvers (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        label TEXT NOT NULL,
                        host TEXT NOT NULL,
                        port INTEGER NOT NULL,
                        tlsServerName TEXT,
                        enabled INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO upstream_resolvers(label, host, port, tlsServerName, enabled, sortOrder)
                    VALUES
                    ('Cloudflare Tor Onion', 'dns4torpnlfs2ifuz2s2yf3fc7rdmsbhm6rw75euj35pac6ap25zgqad.onion', 853, NULL, 1, 0),
                    ('Cloudflare Tor Hostname', 'tor.cloudflare-dns.com', 853, NULL, 1, 1)
                    """.trimIndent(),
                )
            }
        }
}
