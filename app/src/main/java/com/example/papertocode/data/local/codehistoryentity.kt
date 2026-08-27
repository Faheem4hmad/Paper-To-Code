package com.example.papertocode.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "code_history")
data class CodeHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val language: String,
    val code: String,
    val formattedDateTime: String,
    val timestamp: Long = System.currentTimeMillis()
)