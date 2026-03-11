package com.guappa.app.memory

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        TaskEntity::class,
        MemoryFactEntity::class,
        EpisodeEntity::class,
        EmbeddingEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class GuappaDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun taskDao(): TaskDao
    abstract fun memoryFactDao(): MemoryFactDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun embeddingDao(): EmbeddingDao

    companion object {
        private const val TAG = "GuappaDatabase"
        private const val DB_NAME = "guappa_memory.db"

        @Volatile
        private var INSTANCE: GuappaDatabase? = null

        fun getInstance(context: Context): GuappaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): GuappaDatabase {
            return try {
                Room.databaseBuilder(
                    context.applicationContext,
                    GuappaDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to build database, deleting and retrying", e)
                context.deleteDatabase(DB_NAME)
                Room.databaseBuilder(
                    context.applicationContext,
                    GuappaDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
            }
        }
    }
}
