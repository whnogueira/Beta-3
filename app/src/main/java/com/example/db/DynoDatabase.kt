package com.example.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DynoRunEntity::class, VehicleEntity::class],
    version = 2,
    exportSchema = false
)
abstract class DynoDatabase : RoomDatabase() {
    abstract fun dynoRunDao(): DynoRunDao
    abstract fun vehicleDao(): VehicleDao

    companion object {
        @Volatile
        private var INSTANCE: DynoDatabase? = null

        fun getDatabase(context: Context): DynoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DynoDatabase::class.java,
                    "dyno_mobile_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
