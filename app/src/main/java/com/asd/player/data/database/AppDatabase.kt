package com.asd.player.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SongEntity::class, PlaylistEntity::class, PlaylistEntryEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "asd.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        /** v2: arriva Google Drive, ogni brano ricorda da quale fonte viene. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN source TEXT NOT NULL DEFAULT 'LOCAL'")
            }
        }

        /** v3: volume uniforme, ogni brano ricorda la sua loudness misurata. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN loudnessLufs REAL")
                db.execSQL("ALTER TABLE songs ADD COLUMN peakDb REAL")
            }
        }
    }
}
