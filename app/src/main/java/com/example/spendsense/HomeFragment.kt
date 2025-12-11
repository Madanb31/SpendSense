package com.example.spendsense

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.spendsense.database.AppDatabase
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.example.spendsense.database.Transaction

class HomeFragment : Fragment() {

    private lateinit var database: AppDatabase
    private lateinit var adapter: TransactionAdapter
    private var userId: Int = -1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Init Database & User
        database = AppDatabase.getDatabase(requireContext())
        val prefs = requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE)
        userId = prefs.getInt("userId", -1)

        // Setup RecyclerView
        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_recent_transactions)
        val emptyState = view.findViewById<TextView>(R.id.tv_empty_state)
        adapter = TransactionAdapter()
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)

        // FAB Click
        val fab = view.findViewById<FloatingActionButton>(R.id.fab_add_transaction)
        fab.setOnClickListener {
            val addTransactionSheet = AddTransactionBottomSheet()
            addTransactionSheet.show(parentFragmentManager, "AddTransactionBottomSheet")
        }

        // Settings Click
        val settingsIcon = view.findViewById<TextView>(R.id.btn_settings)
        settingsIcon.setOnClickListener {
            val intent = Intent(requireContext(), SettingsActivity::class.java)
            startActivity(intent)
        }

        // Load Data
        loadDashboardData(view, emptyState)

        return view
    }

    private fun loadDashboardData(view: View, emptyState: TextView) {
        if (userId == -1) return

        lifecycleScope.launch {
            // We only need ONE observer to get all data and update everything
            database.transactionDao().getAllTransactions(userId).collect { allTransactions ->

                // 1. Calculate Totals
                val income = allTransactions.filter { it.type == "income" }.sumOf { it.amount }
                val expense = allTransactions.filter { it.type == "expense" }.sumOf { it.amount }

                // 2. Update UI (Balance, Stats, etc.)
                updateUI(view, income, expense, allTransactions)

                // 3. Update RecyclerView (Show only top 5 recent)
                if (allTransactions.isEmpty()) {
                    emptyState.visibility = View.VISIBLE
                    adapter.setData(emptyList())
                } else {
                    emptyState.visibility = View.GONE
                    // Take top 5 for the "Recent" list
                    adapter.setData(allTransactions.take(5))
                }
            }
        }
    }

    private fun updateUI(view: View, income: Double, expense: Double, transactions: List<Transaction>) {
        val tvBalance = view.findViewById<TextView>(R.id.tv_balance)
        val tvIncome = view.findViewById<TextView>(R.id.tv_income)
        val tvExpense = view.findViewById<TextView>(R.id.tv_expense)

        // Find Quick Stats Views (you need to add IDs to XML if missing!)
        // Let's assume IDs: tv_stats_count, tv_stats_top_category, tv_stats_top_amount
        // NOTE: Check your fragment_home.xml to ensure these IDs exist on the "-" textviews!
        val tvStatsCount = view.findViewById<TextView>(R.id.tv_stats_count)
        val tvStatsTopAmount = view.findViewById<TextView>(R.id.tv_stats_top_amount)
        val tvStatsTopIcon = view.findViewById<TextView>(R.id.tv_stats_top_icon)

        // 1. Balance & Totals
        val balance = income - expense
        tvIncome.text = "₹${String.format("%.0f", income)}"
        tvExpense.text = "₹${String.format("%.0f", expense)}"
        tvBalance.text = "₹${String.format("%.2f", balance)}"

        // 2. Transaction Count
        tvStatsCount.text = "${transactions.size}"

        // 3. Top Spending Category
        val expenses = transactions.filter { it.type == "expense" }
        if (expenses.isNotEmpty()) {
            val topCategory = expenses.groupBy { it.categoryName }
                .maxByOrNull { entry -> entry.value.sumOf { it.amount } }

            if (topCategory != null) {
                val amount = topCategory.value.sumOf { it.amount }
                val icon = topCategory.value.first().categoryIcon

                tvStatsTopAmount.text = "₹${String.format("%.0f", amount)}"
                tvStatsTopIcon.text = icon
            }
        } else {
            tvStatsTopAmount.text = "₹0"
            tvStatsTopIcon.text = "⭐"
        }
    }
}