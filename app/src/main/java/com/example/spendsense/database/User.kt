package com.example.spendsense.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val email: String,
    val phone: String,
    val password: String,
    val loginMethod: String, // "phone" or "email"
    val createdAt: Long = System.currentTimeMillis()
)