package com.example.spendsense.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert
    suspend fun insertTransaction(transaction: Transaction): Long

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY date DESC")
    fun getAllTransactions(userId: Int): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY date DESC LIMIT :limit")
    fun getRecentTransactions(userId: Int, limit: Int): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE userId = :userId AND type = :type ORDER BY date DESC")
    fun getTransactionsByType(userId: Int, type: String): Flow<List<Transaction>>

    @Query("SELECT SUM(amount) FROM transactions WHERE userId = :userId AND type = 'income'")
    fun getTotalIncome(userId: Int): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE userId = :userId AND type = 'expense'")
    fun getTotalExpense(userId: Int): Flow<Double?>

    @Query("SELECT * FROM transactions WHERE id = :transactionId")
    suspend fun getTransactionById(transactionId: Int): Transaction?

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE userId = :userId")
    suspend fun deleteAllTransactions(userId: Int)

    // Search transactions by description
    @Query("SELECT * FROM transactions WHERE userId = :userId AND description LIKE '%' || :query || '%' ORDER BY date DESC")
    fun searchTransactions(userId: Int, query: String): Flow<List<Transaction>>
}