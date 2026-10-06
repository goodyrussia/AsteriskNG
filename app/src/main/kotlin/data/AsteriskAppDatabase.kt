// Copyright 2026, AsteriskNG contributors
// SPDX-License-Identifier: GPL-3.0

package data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal const val AsteriskDatabaseName = "asteriskng.db"

@Database(
    entities = [
        ProxyServerEntity::class,
        ProxyAppListSelectedAppEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
internal abstract class AsteriskAppDatabase : RoomDatabase() {
    abstract fun appStateDao(): AppStateDao
}

internal val Migration1To2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE subscription_groups ADD COLUMN hwid TEXT NOT NULL DEFAULT ''",
        )
        db.execSQL(
            "ALTER TABLE subscription_groups ADD COLUMN ageSecretKey TEXT NOT NULL DEFAULT ''",
        )
    }
}

internal val Migration2To3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "DROP TABLE IF EXISTS `routing_rules`",
        )
    }
}

internal val Migration3To4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `proxy_servers_new` (`id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `serverJson` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO `proxy_servers_new` (`id`, `position`, `serverJson`) SELECT `id`, `position`, `serverJson` FROM `proxy_servers`")
        db.execSQL("DROP TABLE `proxy_servers`")
        db.execSQL("ALTER TABLE `proxy_servers_new` RENAME TO `proxy_servers`")
        db.execSQL("DROP TABLE IF EXISTS `subscription_groups`")
    }
}
