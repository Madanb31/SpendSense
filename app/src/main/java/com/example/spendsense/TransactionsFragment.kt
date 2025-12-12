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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.spendsense.database.AppDatabase
import com.example.spendsense.database.Category
import com.example.spendsense.database.Transaction
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransactionsFragment : Fragment() {

    private lateinit var database: AppDatabase
    private lateinit var adapter: TransactionAdapter
    private var allTransactions = listOf<Transaction>()
    private var currentSearchText = ""
    private var currentCategoryFilter = "All"
    private var userId: Int = -1

    private lateinit var chipsContainer: LinearLayout
    private val chipViews = mutableListOf<TextView>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_transactions, container, false)

        database = AppDatabase.getDatabase(requireContext())
        val prefs = requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE)
        userId = prefs.getInt("userId", -1)

        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_transactions)
        val emptyState = view.findViewById<TextView>(R.id.tv_empty_state)
        val searchInput = view.findViewById<EditText>(R.id.et_search)
        chipsContainer = view.findViewById(R.id.ll_category_chips)

        // Setup Adapter with Long Click
        adapter = TransactionAdapter { transaction ->
            showEditDeleteDialog(transaction)
        }

        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)

        loadTransactions(emptyState)
        loadCategoryChips(emptyState)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                currentSearchText = s.toString()
                applyFilters(emptyState)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        view.findViewById<FloatingActionButton>(R.id.fab_add_transaction).setOnClickListener {
            val addTransactionSheet = AddTransactionBottomSheet.newInstance(null)
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
            database.categoryDao().getCategoriesByType("expense").collect { categories ->
                setupChips(categories, emptyState)
            }
        }
    }

    private fun setupChips(categories: List<Category>, emptyState: TextView) {
        chipsContainer.removeAllViews()
        chipViews.clear()
        addChip("All", "All", true, emptyState)
        addChip("Income", "Income", false, emptyState)
        for (cat in categories) {
            addChip("${cat.icon} ${cat.name}", cat.name, false, emptyState)
        }
    }

    private fun addChip(label: String, filterValue: String, isSelected: Boolean, emptyState: TextView) {
        val chip = TextView(context)
        chip.text = label
        chip.textSize = 14f
        chip.setPadding(50, 20, 50, 20)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 24, 0)
        chip.layoutParams = params
        chip.tag = filterValue

        updateChipVisuals(chip, isSelected)

        chip.setOnClickListener {
            for (view in chipViews) updateChipVisuals(view, false)
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
        if (currentCategoryFilter != "All") {
            if (currentCategoryFilter == "Income") {
                filteredList = filteredList.filter { it.type == "income" }
            } else {
                filteredList = filteredList.filter { it.categoryName.equals(currentCategoryFilter, ignoreCase = true) }
            }
        }
        if (currentSearchText.isNotEmpty()) {
            filteredList = filteredList.filter {
                it.description.contains(currentSearchText, ignoreCase = true) ||
                        it.categoryName.contains(currentSearchText, ignoreCase = true)
            }
        }
        if (filteredList.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            adapter.setData(emptyList())
        } else {
            emptyState.visibility = View.GONE
            adapter.setData(filteredList)
        }
    }

    // --- Edit/Delete Logic ---
    private fun showEditDeleteDialog(transaction: Transaction) {
        val options = arrayOf("Edit", "Delete")
        AlertDialog.Builder(requireContext())
            .setTitle("Transaction Action")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val editSheet = AddTransactionBottomSheet.newInstance(transaction)
                        editSheet.show(parentFragmentManager, "EditTransaction")
                    }
                    1 -> showDeleteConfirmation(transaction)
                }
            }
            .show()
    }

    private fun showDeleteConfirmation(transaction: Transaction) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Transaction?")
            .setMessage("Are you sure you want to delete this?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    database.transactionDao().deleteTransaction(transaction)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}