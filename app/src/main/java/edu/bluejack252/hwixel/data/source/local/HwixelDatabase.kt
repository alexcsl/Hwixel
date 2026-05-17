package edu.bluejack252.hwixel.data.source.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ProjectEntity::class, TaskEntity::class, UserEntity::class],
    version = 2,
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

        fun getInstance(context: Context): HwixelDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HwixelDatabase::class.java,
                    "hwixel.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
