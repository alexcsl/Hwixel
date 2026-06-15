package edu.bluejack25_2.hwixel.data.source.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProjectEntity::class, TaskEntity::class, UserEntity::class],
    version = 3,
    exportSchema = true
)
@TypeConverters(ListStringConverter::class)
abstract class HwixelDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun taskDao(): TaskDao
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var instance: HwixelDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN completedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): HwixelDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HwixelDatabase::class.java,
                    "hwixel.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
