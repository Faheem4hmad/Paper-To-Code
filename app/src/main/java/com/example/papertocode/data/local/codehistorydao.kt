package com.example.papertocode.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CodeHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCode(history: CodeHistoryEntity)

    @Query("SELECT * FROM code_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<CodeHistoryEntity>>

    @Query("DELETE FROM code_history")
    suspend fun clearAllHistory()
}