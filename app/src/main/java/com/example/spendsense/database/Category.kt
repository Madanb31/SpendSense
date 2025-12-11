package com.example.spendsense.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val icon: String, // Emoji like "🍔", "🚗", etc.
    val color: String, // Hex color like "#FF5722"
    val type: String  // "expense" or "income"
)