package com.example.spendsense

import android.content.Context
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

    // This tracks if user has manually set a limit
    private var manualMonthlyLimit = 0.0

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
        val prefs = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        userId = prefs.getInt("userId", -1)

        // Load saved manual limit (0 if never set)
        manualMonthlyLimit = prefs.getFloat("monthly_budget_$userId", 0f).toDouble()

        // Init Views
        tvTotalBudget = view.findViewById(R.id.tv_total_budget)
        tvTotalSpent = view.findViewById(R.id.tv_total_spent)
        tvTotalRemaining = view.findViewById(R.id.tv_total_remaining)
        pbTotal = view.findViewById(R.id.pb_total)
        tvTotalPercent = view.findViewById(R.id.tv_total_percent)
        categoryContainer = view.findViewById(R.id.ll_category_container)

        // 1. FAB -> Create Category Budget
        val fab = view.findViewById<FloatingActionButton>(R.id.fab_add_budget)
        fab.setOnClickListener {
            val editBudgetSheet = EditBudgetBottomSheet()
            editBudgetSheet.show(parentFragmentManager, "EditBudgetBottomSheet")
        }

        // 2. Pencil Icon -> Edit Monthly Budget
        val editMonthlyBtn = view.findViewById<TextView>(R.id.btn_edit_monthly_budget)
        editMonthlyBtn.setOnClickListener {
            val sheet = EditMonthlyBudgetBottomSheet { newLimit ->
                saveMonthlyBudget(newLimit)
            }
            sheet.show(parentFragmentManager, "EditMonthlyBudget")
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        loadBudgetData()
    }

    private fun saveMonthlyBudget(limit: Double) {
        manualMonthlyLimit = limit
        val prefs = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        prefs.edit().putFloat("monthly_budget_$userId", limit.toFloat()).apply()
        loadBudgetData() // Refresh UI immediately
        Toast.makeText(context, "Monthly Budget Updated!", Toast.LENGTH_SHORT).show()
    }

    private fun loadBudgetData() {
        if (userId == -1) return

        lifecycleScope.launch(Dispatchers.IO) {
            // Get Category Budgets
            customBudgets = database.budgetDao().getAllBudgets(userId)

            // Get Transactions to calculate totals
            database.transactionDao().getAllTransactions(userId).collect { transactions ->
                calculateBudgets(transactions)
            }
        }
    }

    private suspend fun calculateBudgets(transactions: List<Transaction>) {
        withContext(Dispatchers.Default) {
            // 1. Determine Monthly Budget Limit
            val totalIncome = transactions.filter { it.type == "income" }.sumOf { it.amount }

            val activeBudgetLimit = if (manualMonthlyLimit > 0) {
                manualMonthlyLimit // Use user's manual setting
            } else {
                if (totalIncome > 0) totalIncome * 0.60 else 0.0 // Default 60% rule
            }

            // 2. Calculate Total Expenses
            val expenses = transactions.filter { it.type == "expense" }
            val totalSpent = expenses.sumOf { it.amount }
            val remaining = activeBudgetLimit - totalSpent
            val percentUsed = if (activeBudgetLimit > 0) (totalSpent / activeBudgetLimit * 100).toInt() else 0

            // 3. Category Breakdown
            val categorySpending = expenses.groupBy { it.categoryName }
                .mapValues { entry ->
                    val amt = entry.value.sumOf { it.amount }
                    val icon = entry.value.firstOrNull()?.categoryIcon ?: "🏷️"
                    Pair(amt, icon)
                }

            // 4. Switch to Main Thread
            withContext(Dispatchers.Main) {
                if (isAdded) {
                    updateSummaryUI(activeBudgetLimit, totalSpent, remaining, percentUsed)
                    updateCategoryList(categorySpending)
                }
            }
        }
    }

    private fun updateSummaryUI(budgetLimit: Double, totalSpent: Double, remaining: Double, percentUsed: Int) {
        val symbol = CurrencyHelper.getSymbol(requireContext())
        tvTotalBudget.text = "$symbol${String.format("%.0f", budgetLimit)}"
        tvTotalSpent.text = "$symbol${String.format("%.0f", totalSpent)}"

        // FIX: No negative remaining
        if (remaining < 0) {
            tvTotalRemaining.text = "Over Budget!"
        } else {
            tvTotalRemaining.text = "$symbol${String.format("%.0f", remaining)}"
        }

        pbTotal.progress = percentUsed.coerceIn(0, 100)
        tvTotalPercent.text = "$percentUsed% of budget used"
    }

    private fun updateCategoryList(spendingMap: Map<String, Pair<Double, String>>) {
        categoryContainer.removeAllViews()
        val symbol = CurrencyHelper.getSymbol(requireContext())

        // LOGIC CHANGE: Only iterate through CUSTOM BUDGETS
        // We ignore categories that have spending but NO budget set.

        if (customBudgets.isEmpty()) {
            val emptyView = TextView(context)
            emptyView.text = "No category budgets set.\nTap + to add one."
            emptyView.textAlignment = View.TEXT_ALIGNMENT_CENTER
            emptyView.setPadding(0, 32, 0, 0)
            emptyView.setTextColor(ContextCompat.getColor(requireContext(), R.color.bottom_nav_unselected))
            categoryContainer.addView(emptyView)
            return
        }

        for (budget in customBudgets) {
            val categoryName = budget.categoryName
            val limit = budget.limit

            // Get actual spending for this budgeted category (or 0 if none)
            val spendingData = spendingMap[categoryName]
            val amount = spendingData?.first ?: 0.0

            // Icon: From spending map OR lookup manually
            val icon = spendingData?.second ?: getIconForCategory(categoryName)

            val remaining = limit - amount
            val progress = if (limit > 0) (amount / limit * 100).toInt() else 0

            val itemView = LayoutInflater.from(context).inflate(R.layout.item_budget_category, categoryContainer, false)

            val tvName = itemView.findViewById<TextView>(R.id.tv_cat_name)
            val tvSpent = itemView.findViewById<TextView>(R.id.tv_cat_spent)
            val tvLeft = itemView.findViewById<TextView>(R.id.tv_cat_left)
            val pbCat = itemView.findViewById<ProgressBar>(R.id.pb_cat)
            val tvIcon = itemView.findViewById<TextView>(R.id.tv_cat_icon)

            tvName.text = categoryName
            tvIcon.text = icon
            tvSpent.text = "$symbol${String.format("%.0f", amount)} of $symbol${String.format("%.0f", limit)}"

            if (remaining < 0) {
                tvLeft.text = "$symbol${String.format("%.0f", Math.abs(remaining))} over!"
                tvLeft.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                pbCat.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
            } else {
                tvLeft.text = "$symbol${String.format("%.0f", remaining)} left"
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