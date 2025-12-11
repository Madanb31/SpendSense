package com.example.spendsense

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.spendsense.database.AppDatabase
import com.example.spendsense.database.Category
import com.example.spendsense.database.Transaction
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class AddTransactionBottomSheet : BottomSheetDialogFragment() {

    private lateinit var database: AppDatabase
    private var selectedType = "expense"
    private var selectedCategory: Category? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_add_transaction, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        database = AppDatabase.getDatabase(requireContext())

        val toggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.toggle_transaction_type)
        val btnExpense = view.findViewById<Button>(R.id.btn_expense)
        val btnIncome = view.findViewById<Button>(R.id.btn_income)
        val etAmount = view.findViewById<TextInputEditText>(R.id.et_amount)
        val etDescription = view.findViewById<TextInputEditText>(R.id.et_description)
        val chipGroup = view.findViewById<ChipGroup>(R.id.chip_group_categories)
        val btnSave = view.findViewById<Button>(R.id.btn_save_transaction)

        // Load expense categories by default
        loadCategories(chipGroup, "expense")

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_expense -> {
                        selectedType = "expense"
                        btnExpense.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_light))
                        btnExpense.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                        btnIncome.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                        btnIncome.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
                        loadCategories(chipGroup, "expense")
                    }
                    R.id.btn_income -> {
                        selectedType = "income"
                        btnIncome.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_light))
                        btnIncome.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                        btnExpense.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                        btnExpense.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                        loadCategories(chipGroup, "income")
                    }
                }
            }
        }

        btnSave.setOnClickListener {
            val amountText = etAmount.text.toString()
            val description = etDescription.text.toString()

            if (amountText.isEmpty()) {
                Toast.makeText(context, "Please enter an amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedCategory == null) {
                Toast.makeText(context, "Please select a category", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val amount = amountText.toDoubleOrNull() ?: 0.0
            saveTransaction(amount, description)
        }
    }

    private fun loadCategories(chipGroup: ChipGroup, type: String) {
        lifecycleScope.launch {
            chipGroup.removeAllViews()
            selectedCategory = null // Reset selection when switching type

            database.categoryDao().getCategoriesByType(type).collect { categories ->
                for (category in categories) {
                    val chip = Chip(context)
                    chip.text = "${category.icon} ${category.name}"
                    chip.isCheckable = true
                    chip.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            selectedCategory = category
                        }
                    }
                    chipGroup.addView(chip)
                }
            }
        }
    }

    private fun saveTransaction(amount: Double, description: String) {
        lifecycleScope.launch {
            val prefs = requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE)
            val userId = prefs.getInt("userId", -1)

            if (userId != -1) {
                val transaction = Transaction(
                    userId = userId,
                    amount = amount,
                    categoryId = selectedCategory!!.id,
                    categoryName = selectedCategory!!.name,
                    categoryIcon = selectedCategory!!.icon,
                    type = selectedType,
                    description = description.ifEmpty { selectedCategory!!.name },
                    date = System.currentTimeMillis()
                )
                database.transactionDao().insertTransaction(transaction)
                Toast.makeText(context, "Transaction Saved!", Toast.LENGTH_SHORT).show()
                dismiss()
            } else {
                Toast.makeText(context, "Error: User not logged in", Toast.LENGTH_SHORT).show()
            }
        }
    }
}