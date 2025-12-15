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
    private var monthlyBudgetLimit = 0.0

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

        // Load saved monthly limit
        monthlyBudgetLimit = prefs.getFloat("monthly_budget_$userId", 0f).toDouble()

        tvTotalBudget = view.findViewById(R.id.tv_total_budget)
        tvTotalSpent = view.findViewById(R.id.tv_total_spent)
        tvTotalRemaining = view.findViewById(R.id.tv_total_remaining)
        pbTotal = view.findViewById(R.id.pb_total)
        tvTotalPercent = view.findViewById(R.id.tv_total_percent)
        categoryContainer = view.findViewById(R.id.ll_category_container)

        // FAB - Add Category Budget
        val fab = view.findViewById<FloatingActionButton>(R.id.fab_add_budget)
        fab.setOnClickListener {
            val editBudgetSheet = EditBudgetBottomSheet()
            editBudgetSheet.show(parentFragmentManager, "EditBudgetBottomSheet")
        }

        // Pencil Icon - Edit Monthly Budget
        val editMonthlyBtn = view.findViewById<TextView>(R.id.btn_edit_monthly_budget)
        editMonthlyBtn.setOnClickListener {
            val sheet = EditMonthlyBudgetBottomSheet { newLimit ->
                saveMonthlyBudget(newLimit)
            }
            sheet.show(parentFragmentManager, "EditMonthlyBudget")
        }

        // Listen for updates from BottomSheet
        parentFragmentManager.setFragmentResultListener("budget_updated", viewLifecycleOwner) { _, _ ->
            loadBudgetData() // Refresh immediately!
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
        loadBudgetData()
        Toast.makeText(context, "Monthly Budget Updated!", Toast.LENGTH_SHORT).show()
    }

    private var manualMonthlyLimit = 0.0

    private fun loadBudgetData() {
        if (userId == -1) return

        lifecycleScope.launch(Dispatchers.IO) {
            customBudgets = database.budgetDao().getAllBudgets(userId)
            database.transactionDao().getAllTransactions(userId).collect { transactions ->
                calculateBudgets(transactions)
            }
        }
    }

    private suspend fun calculateBudgets(transactions: List<Transaction>) {
        withContext(Dispatchers.Default) {
            // 1. Calculate Total Expenses
            val expenses = transactions.filter { it.type == "expense" }
            val totalSpent = expenses.sumOf { it.amount }

            // Monthly Limit Logic
            val totalIncome = transactions.filter { it.type == "income" }.sumOf { it.amount }
            val activeBudgetLimit = if (manualMonthlyLimit > 0) {
                manualMonthlyLimit
            } else {
                if (totalIncome > 0) totalIncome * 0.60 else 0.0
            }

            val remaining = activeBudgetLimit - totalSpent
            val percentUsed = if (activeBudgetLimit > 0) (totalSpent / activeBudgetLimit * 100).toInt() else 0

            // 2. Calculate Category Spending
            val categorySpendingMap = expenses.groupBy { it.categoryName }
                .mapValues { entry ->
                    val amt = entry.value.sumOf { it.amount }
                    val icon = entry.value.firstOrNull()?.categoryIcon ?: "🏷️"
                    Pair(amt, icon)
                }

            withContext(Dispatchers.Main) {
                if (isAdded) {
                    updateSummaryUI(activeBudgetLimit, totalSpent, remaining, percentUsed)
                    updateCategoryList(categorySpendingMap)
                }
            }
        }
    }

    private fun updateSummaryUI(budgetLimit: Double, totalSpent: Double, remaining: Double, percentUsed: Int) {
        val symbol = CurrencyHelper.getSymbol(requireContext())
        tvTotalBudget.text = "$symbol${String.format("%.0f", budgetLimit)}"
        tvTotalSpent.text = "$symbol${String.format("%.0f", totalSpent)}"

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

            val spendingData = spendingMap[categoryName]
            val amount = spendingData?.first ?: 0.0
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

            // Long Press to Edit/Delete
            itemView.setOnLongClickListener {
                showBudgetActionDialog(categoryName, limit)
                true
            }

            categoryContainer.addView(itemView)
        }
    }

    private fun showBudgetActionDialog(categoryName: String, currentLimit: Double) {
        val options = arrayOf("Edit Limit", "Delete Budget")
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("$categoryName Budget")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditBudgetDialog(categoryName, currentLimit)
                    1 -> showDeleteBudgetConfirmation(categoryName)
                }
            }
            .show()
    }

    private fun showEditBudgetDialog(categoryName: String, currentLimit: Double) {
        val editSheet = EditBudgetBottomSheet.newInstance(categoryName, currentLimit)
        editSheet.show(parentFragmentManager, "EditBudget")
    }

    private fun showDeleteBudgetConfirmation(categoryName: String) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Budget?")
            .setMessage("Are you sure you want to delete the budget for $categoryName?")
            .setPositiveButton("Delete") { _, _ ->
                deleteBudget(categoryName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteBudget(categoryName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val budget = database.budgetDao().getBudgetByCategory(userId, categoryName)
            if (budget != null) {
                database.budgetDao().deleteBudget(budget)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Budget Deleted", Toast.LENGTH_SHORT).show()
                    loadBudgetData()
                }
            }
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
            else -> "🏷️"
        }
    }
}