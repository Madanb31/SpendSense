package com.example.spendsense.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: Int,           // Links to User
    val amount: Double,
    val categoryId: Int,       // Links to Category
    val categoryName: String,  // For quick display
    val categoryIcon: String,  // For quick display
    val type: String,          // "expense" or "income"
    val description: String,
    val date: Long,            // Timestamp
    val createdAt: Long = System.currentTimeMillis()
)