package com.example.spendsense

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.spendsense.database.AppDatabase
import com.example.spendsense.database.Budget
import com.example.spendsense.database.Category // <-- CRITICAL IMPORT
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EditBudgetBottomSheet : BottomSheetDialogFragment() {

    private lateinit var database: AppDatabase
    private var selectedCategory: Category? = null // <-- CRITICAL TYPE

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_edit_budget, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        database = AppDatabase.getDatabase(requireContext())

        val chipGroup = view.findViewById<ChipGroup>(R.id.chip_group_budget_categories)
        val etLimit = view.findViewById<TextInputEditText>(R.id.et_budget_limit)
        val btnSave = view.findViewById<Button>(R.id.btn_save_budget)

        loadCategories(chipGroup)

        btnSave.setOnClickListener {
            val limitText = etLimit.text.toString()

            if (selectedCategory == null) {
                Toast.makeText(context, "Select a category", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (limitText.isEmpty()) {
                Toast.makeText(context, "Enter a limit", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveBudget(limitText.toDouble())
        }
    }

    private fun loadCategories(chipGroup: ChipGroup) {
        lifecycleScope.launch {
            database.categoryDao().getCategoriesByType("expense").collect { categories ->
                chipGroup.removeAllViews()
                for (category in categories) {
                    val chip = Chip(context)
                    chip.text = "${category.icon} ${category.name}"
                    chip.isCheckable = true
                    chip.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) selectedCategory = category
                    }
                    chipGroup.addView(chip)
                }
            }
        }
    }

    private fun saveBudget(limit: Double) {
        lifecycleScope.launch {
            val prefs = requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE)
            val userId = prefs.getInt("userId", -1)

            if (userId != -1 && selectedCategory != null) { // Null check
                val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

                // 1. Check if budget already exists
                val existingBudget = database.budgetDao().getBudgetByCategory(userId, selectedCategory!!.name)

                val budget = if (existingBudget != null) {
                    // Update existing
                    existingBudget.copy(limit = limit, month = currentMonth)
                } else {
                    // Create new
                    Budget(
                        userId = userId,
                        categoryId = selectedCategory!!.id,
                        categoryName = selectedCategory!!.name,
                        limit = limit,
                        month = currentMonth
                    )
                }

                database.budgetDao().insertBudget(budget)
                Toast.makeText(context, "Budget Set: ₹$limit for ${selectedCategory!!.name}", Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
    }
}