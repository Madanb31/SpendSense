package com.example.spendsense.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [User::class, Category::class, Transaction::class, Budget::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "spendsense_database"
                )
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        // Pre-populate default categories
        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateDatabase(database.categoryDao())
                    }
                }
            }
        }

        suspend fun populateDatabase(categoryDao: CategoryDao) {
            // Default expense categories
            categoryDao.insertCategory(Category(name = "Food", icon = "🍔", color = "#FFC107", type = "expense"))
            categoryDao.insertCategory(Category(name = "Transport", icon = "🚗", color = "#2196F3", type = "expense"))
            categoryDao.insertCategory(Category(name = "Shopping", icon = "🛍️", color = "#9C27B0", type = "expense"))
            categoryDao.insertCategory(Category(name = "Bills", icon = "📄", color = "#FF5722", type = "expense"))
            categoryDao.insertCategory(Category(name = "Entertainment", icon = "🎬", color = "#E91E63", type = "expense"))
            categoryDao.insertCategory(Category(name = "Health", icon = "💊", color = "#F44336", type = "expense"))
            categoryDao.insertCategory(Category(name = "Education", icon = "📚", color = "#3F51B5", type = "expense"))
            categoryDao.insertCategory(Category(name = "Other", icon = "💸", color = "#607D8B", type = "expense"))

            // Default income categories
            categoryDao.insertCategory(Category(name = "Salary", icon = "💼", color = "#4CAF50", type = "income"))
            categoryDao.insertCategory(Category(name = "Business", icon = "💰", color = "#8BC34A", type = "income"))
            categoryDao.insertCategory(Category(name = "Investment", icon = "📈", color = "#00BCD4", type = "income"))
            categoryDao.insertCategory(Category(name = "Other Income", icon = "💵", color = "#009688", type = "income"))
        }
    }
}