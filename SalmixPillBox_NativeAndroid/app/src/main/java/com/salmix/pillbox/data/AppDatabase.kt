package com.salmix.pillbox.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Medicine::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicineDao(): MedicineDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medicines ADD COLUMN customTime TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE medicines ADD COLUMN frequencyType TEXT NOT NULL DEFAULT 'DAILY'")
                db.execSQL("ALTER TABLE medicines ADD COLUMN daysOfWeek TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE medicines ADD COLUMN expiryDate TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE medicines ADD COLUMN dosageUnit TEXT NOT NULL DEFAULT 'عدد'")
                db.execSQL("ALTER TABLE medicines ADD COLUMN stockDecrementedToday INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medicines ADD COLUMN takenSlotsToday TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE medicines ADD COLUMN lastTakenDate TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE medicines ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE medicines ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "salmix_pillbox_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
