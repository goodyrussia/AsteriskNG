// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal const val AsteriskDatabaseName = "asteriskng.db"

@Database(
    entities = [
        SubscriptionGroupEntity::class,
        ProxyServerEntity::class,
        ProxyAppListSelectedAppEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
internal abstract class AsteriskAppDatabase : RoomDatabase() {
    abstract fun appStateDao(): AppStateDao
}

internal val Migration1To2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `routing_rules`")
    }
}
