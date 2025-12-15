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
import com.example.spendsense.database.Category
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
    private var selectedCategory: Category? = null

    // Edit Mode
    private var isEditMode = false
    private var editCategoryName: String? = null
    private var editLimit: Double = 0.0

    companion object {
        fun newInstance(categoryName: String, limit: Double): EditBudgetBottomSheet {
            val fragment = EditBudgetBottomSheet()
            val args = Bundle()
            args.putString("catName", categoryName)
            args.putDouble("limit", limit)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_edit_budget, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        database = AppDatabase.getDatabase(requireContext())

        // Check Arguments
        if (arguments != null) {
            isEditMode = true
            editCategoryName = requireArguments().getString("catName")
            editLimit = requireArguments().getDouble("limit")
        }

        val chipGroup = view.findViewById<ChipGroup>(R.id.chip_group_budget_categories)
        val etLimit = view.findViewById<TextInputEditText>(R.id.et_budget_limit)
        val btnSave = view.findViewById<Button>(R.id.btn_save_budget)

        // Pre-fill Limit
        if (isEditMode) {
            etLimit.setText(String.format("%.0f", editLimit))
            btnSave.text = "Update Budget"
        }

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

                    // Pre-select if editing
                    if (isEditMode && category.name == editCategoryName) {
                        chip.isChecked = true
                        selectedCategory = category
                        // Disable other chips in edit mode (optional, prevents changing category of an existing budget)
                        chipGroup.isEnabled = false
                    }

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

            if (userId != -1 && selectedCategory != null) {
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
                Toast.makeText(context, "Budget Saved for ${selectedCategory!!.name}", Toast.LENGTH_SHORT).show()
                parentFragmentManager.setFragmentResult("budget_updated", Bundle())
                dismiss()
            }
        }
    }
}