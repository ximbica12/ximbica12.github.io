package com.github.libretube.db

import androidx.room.DeleteColumn
import androidx.room.Room
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.libretube.LibreTubeApp
import com.github.libretube.helpers.ProfileManager

object DatabaseHolder {
    private const val DATABASE_NAME = "LibreTubeDatabase"

    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE 'localPlaylist' ADD COLUMN 'description' TEXT DEFAULT NULL")
        }
    }

    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE 'playlistBookmark' ADD COLUMN 'videos' INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE 'subscriptionGroups' ADD COLUMN 'index' INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE 'downloadItem' ADD COLUMN 'language' TEXT DEFAULT NULL")
        }
    }

    private val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE 'watchHistoryItem' ADD COLUMN 'isShort' INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    private val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE 'downloadChapters' (" +
                    "id INTEGER PRIMARY KEY NOT NULL, " +
                    "videoId TEXT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "start INTEGER NOT NULL, " +
                    "thumbnailUrl TEXT NOT NULL" +
                    ")")
        }
    }

    private val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE 'downloadSponsorBlockSegment' (" +
                    "uuid TEXT PRIMARY KEY NOT NULL, " +
                    "videoId TEXT NOT NULL, " +
                    "actionType TEXT NOT NULL, " +
                    "category TEXT NOT NULL, " +
                    "description TEXT, " +
                    "locked INTEGER NOT NULL, " +
                    "startTime REAL NOT NULL, " +
                    "endTime REAL NOT NULL, " +
                    "videoDuration REAL NOT NULL, " +
                    "votes INTEGER NOT NULL, " +
                    "CONSTRAINT parentDownload FOREIGN KEY (videoId) REFERENCES download (videoId) ON DELETE CASCADE" +
                    ")")
        }
    }

    private val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE 'download' ADD COLUMN 'uploaderUrl' TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE 'download' ADD COLUMN 'views' INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE 'download' ADD COLUMN 'likes' INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE 'download' ADD COLUMN 'dislikes' INTEGER NOT NULL DEFAULT -1")
        }
    }

    @DeleteColumn(tableName = "downloadItem", columnName = "url")
    class MIGRATION_23_24 : AutoMigrationSpec

    @Volatile
    private var database: AppDatabase? = null
    private var openedProfileId: String? = null

    val Database: AppDatabase
        @Synchronized get() {
            val profileId = ProfileManager.getActiveProfileId()
            if (database == null || openedProfileId != profileId) {
                database?.close()
                database = Room.databaseBuilder(
                    LibreTubeApp.instance,
                    AppDatabase::class.java,
                    databaseName(profileId)
                )
                    .addMigrations(
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16,
                        MIGRATION_17_18,
                        MIGRATION_21_22,
                        MIGRATION_22_23
                    )
                    .build()
                openedProfileId = profileId
            }
            return database!!
        }

    @Synchronized
    fun switchProfile(profileId: String) {
        ProfileManager.setActiveProfile(profileId)
        database?.close()
        database = null
        openedProfileId = null
    }

    private fun databaseName(profileId: String): String {
        if (profileId == ProfileManager.DEFAULT_PROFILE_ID) return DATABASE_NAME
        val safe = profileId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return "${DATABASE_NAME}_profile_$safe"
    }
}
