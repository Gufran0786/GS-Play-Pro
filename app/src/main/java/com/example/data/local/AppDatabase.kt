package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_items ORDER BY isPinned DESC, createdAt DESC")
    fun getAllVaultItems(): Flow<List<VaultItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultItem(item: VaultItemEntity): Long

    @Update
    suspend fun updateVaultItem(item: VaultItemEntity)

    @Delete
    suspend fun deleteVaultItem(item: VaultItemEntity)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteVaultItemById(id: Long)
}

@Dao
interface StreamHistoryDao {
    @Query("SELECT * FROM stream_history ORDER BY lastPlayedAt DESC")
    fun getAllStreamHistory(): Flow<List<StreamHistoryEntity>>

    @Query("SELECT * FROM stream_history WHERE isBookmarked = 1 ORDER BY lastPlayedAt DESC")
    fun getBookmarkedStreams(): Flow<List<StreamHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateStream(item: StreamHistoryEntity): Long

    @Query("UPDATE stream_history SET isBookmarked = :bookmarked WHERE id = :id")
    suspend fun setBookmark(id: Long, bookmarked: Boolean)

    @Delete
    suspend fun deleteStream(item: StreamHistoryEntity)

    @Query("DELETE FROM stream_history")
    suspend fun clearHistory()
}

@Database(
    entities = [VaultItemEntity::class, StreamHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
    abstract fun streamHistoryDao(): StreamHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gs_play_pro_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
