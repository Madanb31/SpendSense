package com.example.spendsense

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.spendsense.database.AppDatabase
import com.example.spendsense.database.Category
import com.example.spendsense.database.Transaction
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class TransactionsFragment : Fragment() {

    private lateinit var database: AppDatabase
    private lateinit var adapter: TransactionAdapter
    private var allTransactions = listOf<Transaction>()
    private var currentSearchText = ""
    private var currentCategoryFilter = "All"
    private var userId: Int = -1

    private lateinit var chipsContainer: LinearLayout
    private val chipViews = mutableListOf<TextView>() // To keep track of all chips

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_transactions, container, false)

        // Init DB
        database = AppDatabase.getDatabase(requireContext())
        val prefs = requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE)
        userId = prefs.getInt("userId", -1)

        // Init Views
        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_transactions)
        val emptyState = view.findViewById<TextView>(R.id.tv_empty_state)
        val searchInput = view.findViewById<EditText>(R.id.et_search)
        chipsContainer = view.findViewById(R.id.ll_category_chips)

        // Setup RecyclerView
        adapter = TransactionAdapter()
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Load Data
        loadTransactions(emptyState)
        loadCategoryChips(emptyState)

        // Search Listener
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                currentSearchText = s.toString()
                applyFilters(emptyState)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // FAB
        view.findViewById<FloatingActionButton>(R.id.fab_add_transaction).setOnClickListener {
            val addTransactionSheet = AddTransactionBottomSheet()
            addTransactionSheet.show(parentFragmentManager, "AddTransactionBottomSheet")
        }

        return view
    }

    private fun loadTransactions(emptyState: TextView) {
        if (userId == -1) return

        lifecycleScope.launch {
            database.transactionDao().getAllTransactions(userId).collect { transactions ->
                allTransactions = transactions
                applyFilters(emptyState)
            }
        }
    }

    private fun loadCategoryChips(emptyState: TextView) {
        lifecycleScope.launch {
            // Get all expense categories from DB
            database.categoryDao().getCategoriesByType("expense").collect { categories ->
                setupChips(categories, emptyState)
            }
        }
    }

    private fun setupChips(categories: List<Category>, emptyState: TextView) {
        chipsContainer.removeAllViews()
        chipViews.clear()

        // 1. Add "All" Chip
        addChip("All", "All", true, emptyState)

        // 2. Add "Income" Chip
        addChip("Income", "Income", false, emptyState)

        // 3. Add Expense Category Chips (Dynamic)
        for (cat in categories) {
            val label = "${cat.icon} ${cat.name}"
            addChip(label, cat.name, false, emptyState)
        }
    }

    private fun addChip(label: String, filterValue: String, isSelected: Boolean, emptyState: TextView) {
        val chip = TextView(context)
        chip.text = label
        chip.textSize = 14f
        chip.setPadding(50, 20, 50, 20) // Padding: Left, Top, Right, Bottom

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 24, 0) // Margin End
        chip.layoutParams = params

        // Tag stores the actual filter value (e.g. "Food") vs Label (e.g. "🍔 Food")
        chip.tag = filterValue

        // Set visual style
        updateChipVisuals(chip, isSelected)

        // Click Listener
        chip.setOnClickListener {
            // Deselect all others
            for (view in chipViews) {
                updateChipVisuals(view, false)
            }
            // Select this one
            updateChipVisuals(chip, true)

            currentCategoryFilter = filterValue
            applyFilters(emptyState)
        }

        chipsContainer.addView(chip)
        chipViews.add(chip)
    }

    private fun updateChipVisuals(chip: TextView, isSelected: Boolean) {
        if (isSelected) {
            chip.setBackgroundResource(R.drawable.chip_selected)
            chip.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        } else {
            chip.setBackgroundResource(R.drawable.chip_unselected)
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.bottom_nav_unselected))
        }
    }

    private fun applyFilters(emptyState: TextView) {
        var filteredList = allTransactions

        // 1. Filter by Category Chip
        if (currentCategoryFilter != "All") {
            if (currentCategoryFilter == "Income") {
                // Show all income types
                filteredList = filteredList.filter { it.type == "income" }
            } else {
                // Show specific category
                filteredList = filteredList.filter { it.categoryName.equals(currentCategoryFilter, ignoreCase = true) }
            }
        }

        // 2. Filter by Search Text
        if (currentSearchText.isNotEmpty()) {
            filteredList = filteredList.filter {
                it.description.contains(currentSearchText, ignoreCase = true) ||
                        it.categoryName.contains(currentSearchText, ignoreCase = true)
            }
        }

        // 3. Update UI
        if (filteredList.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            adapter.setData(emptyList())
        } else {
            emptyState.visibility = View.GONE
            adapter.setData(filteredList)
        }
    }
}