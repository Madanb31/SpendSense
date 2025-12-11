package com.example.spendsense.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    // Insert or Update (Replace if exists)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: Budget): Long

    // Get all budgets for user (Synchronous)
    @Query("SELECT * FROM budgets WHERE userId = :userId")
    suspend fun getAllBudgets(userId: Int): List<Budget>

    // Get budgets for specific month (Flow/Live)
    @Query("SELECT * FROM budgets WHERE userId = :userId AND month = :month")
    fun getBudgetsForMonth(userId: Int, month: String): Flow<List<Budget>>

    // Get specific category budget
    @Query("SELECT * FROM budgets WHERE userId = :userId AND categoryName = :categoryName LIMIT 1")
    suspend fun getBudgetByCategory(userId: Int, categoryName: String): Budget?

    @Update
    suspend fun updateBudget(budget: Budget)

    @Delete
    suspend fun deleteBudget(budget: Budget)
}