package com.example.spendsense.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: Int,
    val categoryId: Int,
    val categoryName: String,
    val limit: Double,         // Budget limit for the month
    val month: String,         // Format: "2024-11" (year-month)
    val createdAt: Long = System.currentTimeMillis()
)