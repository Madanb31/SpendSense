package com.example.spendsense

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.spendsense.database.AppDatabase
import com.example.spendsense.database.Budget
import com.example.spendsense.database.Transaction
import com.example.spendsense.utils.CurrencyHelper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BudgetFragment : Fragment() {

    private lateinit var database: AppDatabase
    private var userId: Int = -1

    private var customBudgets = listOf<Budget>()

    // UI Views
    private lateinit var tvTotalBudget: TextView
    private lateinit var tvTotalSpent: TextView
    private lateinit var tvTotalRemaining: TextView
    private lateinit var pbTotal: ProgressBar
    private lateinit var tvTotalPercent: TextView
    private lateinit var categoryContainer: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_budget, container, false)

        database = AppDatabase.getDatabase(requireContext())
        val prefs = requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE)
        userId = prefs.getInt("userId", -1)

        // Init Views
        tvTotalBudget = view.findViewById(R.id.tv_total_budget)
        tvTotalSpent = view.findViewById(R.id.tv_total_spent)
        tvTotalRemaining = view.findViewById(R.id.tv_total_remaining)
        pbTotal = view.findViewById(R.id.pb_total)
        tvTotalPercent = view.findViewById(R.id.tv_total_percent)
        categoryContainer = view.findViewById(R.id.ll_category_container)

        // FAB
        val fab = view.findViewById<FloatingActionButton>(R.id.fab_add_budget)
        fab.setOnClickListener {
            val editBudgetSheet = EditBudgetBottomSheet()
            editBudgetSheet.show(parentFragmentManager, "EditBudgetBottomSheet")
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        loadBudgetData()
    }

    private fun loadBudgetData() {
        if (userId == -1) return

        lifecycleScope.launch(Dispatchers.IO) { // Run on Background Thread
            // 1. Get Custom Budgets
            customBudgets = database.budgetDao().getAllBudgets(userId)

            // 2. Then watch Transactions
            database.transactionDao().getAllTransactions(userId).collect { transactions ->
                calculateBudgets(transactions)
            }
        }
    }

    private suspend fun calculateBudgets(transactions: List<Transaction>) {
        // Run calculations on Default dispatcher (CPU intensive)
        withContext(Dispatchers.Default) {
            // 1. Calculate Income & Budget Limit
            val totalIncome = transactions.filter { it.type == "income" }.sumOf { it.amount }
            // FIX: Default 0 if no income
            val budgetLimit = if (totalIncome > 0) totalIncome * 0.60 else 0.0

            // 2. Calculate Total Expenses
            val expenses = transactions.filter { it.type == "expense" }
            val totalSpent = expenses.sumOf { it.amount }
            val remaining = budgetLimit - totalSpent
            val percentUsed = if (budgetLimit > 0) (totalSpent / budgetLimit * 100).toInt() else 0

            // 3. Group by Category (Name -> (Amount, Icon))
            val categorySpending = expenses.groupBy { it.categoryName }
                .mapValues { entry ->
                    val amount = entry.value.sumOf { it.amount }
                    val icon = entry.value.firstOrNull()?.categoryIcon ?: "🏷️"
                    Pair(amount, icon)
                }
                .toList()
                .sortedByDescending { it.second.first }

            // Switch to Main Thread for UI Updates
            withContext(Dispatchers.Main) {
                if (isAdded) {
                    updateSummaryUI(budgetLimit, totalSpent, remaining, percentUsed)
                    updateCategoryList(categorySpending, budgetLimit)
                }
            }
        }
    }

    private fun updateSummaryUI(budgetLimit: Double, totalSpent: Double, remaining: Double, percentUsed: Int) {
        // FIX: Use CurrencyHelper
        val symbol = CurrencyHelper.getSymbol(requireContext())

        tvTotalBudget.text = "$symbol${String.format("%.0f", budgetLimit)}"
        tvTotalSpent.text = "$symbol${String.format("%.0f", totalSpent)}"
        tvTotalRemaining.text = "$symbol${String.format("%.0f", remaining)}"
        pbTotal.progress = percentUsed.coerceIn(0, 100)
        tvTotalPercent.text = "$percentUsed% of budget used"
    }

    private fun updateCategoryList(categorySpending: List<Pair<String, Pair<Double, String>>>, totalBudget: Double) {
        categoryContainer.removeAllViews()
        val symbol = CurrencyHelper.getSymbol(requireContext())

        // FIX: Combine Spending Keys + Custom Budget Keys
        val spendingMap = categorySpending.toMap() // Map<CategoryName, Pair<Amount, Icon>>
        val allCategories = (spendingMap.keys + customBudgets.map { it.categoryName }).toSet()

        if (allCategories.isEmpty()) return

        for (categoryName in allCategories) {
            // Get spending data (or 0 if no spending)
            val spendingData = spendingMap[categoryName]
            val amount = spendingData?.first ?: 0.0

            // Get Icon: From transaction OR lookup if no transaction
            var iconText = spendingData?.second
            if (iconText == null) {
                iconText = getIconForCategory(categoryName)
            }

            // Check Custom Budget
            val customBudget = customBudgets.find { it.categoryName == categoryName }
            val categoryLimit = customBudget?.limit ?: (totalBudget * 0.20)

            val categoryRemaining = categoryLimit - amount
            val progress = if (categoryLimit > 0) (amount / categoryLimit * 100).toInt() else 0

            // Inflate layout
            val itemView = LayoutInflater.from(context).inflate(R.layout.item_budget_category, categoryContainer, false)

            val tvName = itemView.findViewById<TextView>(R.id.tv_cat_name)
            val tvSpent = itemView.findViewById<TextView>(R.id.tv_cat_spent)
            val tvLeft = itemView.findViewById<TextView>(R.id.tv_cat_left)
            val pbCat = itemView.findViewById<ProgressBar>(R.id.pb_cat)
            val tvIcon = itemView.findViewById<TextView>(R.id.tv_cat_icon)

            tvName.text = categoryName
            tvIcon.text = iconText
            tvSpent.text = "$symbol${String.format("%.0f", amount)} of $symbol${String.format("%.0f", categoryLimit)}"

            if (categoryRemaining < 0) {
                tvLeft.text = "$symbol${String.format("%.0f", Math.abs(categoryRemaining))} over!"
                tvLeft.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                pbCat.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
            } else {
                tvLeft.text = "$symbol${String.format("%.0f", categoryRemaining)} left"
                tvLeft.setTextColor(ContextCompat.getColor(requireContext(), R.color.bottom_nav_selected))

                if (progress > 80) {
                    pbCat.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light))
                } else {
                    pbCat.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.bottom_nav_selected))
                }
            }

            pbCat.progress = progress.coerceIn(0, 100)
            categoryContainer.addView(itemView)
        }
    }

    private fun getIconForCategory(name: String): String {
        return when (name) {
            "Food" -> "🍔"
            "Transport" -> "🚗"
            "Shopping" -> "🛍️"
            "Bills" -> "📄"
            "Entertainment" -> "🎬"
            "Health" -> "💊"
            "Education" -> "📚"
            else -> "💸"
        }
    }
}