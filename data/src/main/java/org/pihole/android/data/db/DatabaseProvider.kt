package org.pihole.android.data.db

import android.content.Context
import androidx.room.Room

object DatabaseProvider {

    @Volatile
    private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "pihole_android.db",
            )
                .addMigrations(Migrations.MIGRATION_3_4)
                .addMigrations(Migrations.MIGRATION_4_5)
                .build()
                .also { instance = it }
        }
    }
}
