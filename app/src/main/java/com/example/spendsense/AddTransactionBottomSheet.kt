package com.example.spendsense

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
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

    // Edit Mode Variables
    private var transactionId: Int = 0
    private var isEditMode = false
    private var originalDate: Long = System.currentTimeMillis()

    // Singleton to pass data easily
    companion object {
        fun newInstance(transaction: Transaction? = null): AddTransactionBottomSheet {
            val fragment = AddTransactionBottomSheet()
            if (transaction != null) {
                val args = Bundle()
                args.putInt("id", transaction.id)
                args.putDouble("amount", transaction.amount)
                args.putString("desc", transaction.description)
                args.putString("type", transaction.type)
                args.putString("catName", transaction.categoryName)
                args.putInt("catId", transaction.categoryId)
                args.putString("catIcon", transaction.categoryIcon)
                args.putLong("date", transaction.date)
                fragment.arguments = args
            }
            return fragment
        }
    }

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

        // Check if we are Editing
        if (arguments != null && requireArguments().containsKey("id")) {
            isEditMode = true
            val args = requireArguments()
            transactionId = args.getInt("id")
            originalDate = args.getLong("date")

            val amount = args.getDouble("amount")
            val desc = args.getString("desc")
            val type = args.getString("type") ?: "expense"
            val catName = args.getString("catName")
            val catIcon = args.getString("catIcon") ?: "🏷️"
            val catId = args.getInt("catId")

            // Pre-fill fields
            etAmount.setText(amount.toString())
            etDescription.setText(desc)
            btnSave.text = "Update Transaction"

            // Set Toggle
            if (type == "income") {
                toggleGroup.check(R.id.btn_income)
                selectedType = "income"
            } else {
                toggleGroup.check(R.id.btn_expense)
                selectedType = "expense"
            }

            // Pre-set selected category object for logic
            selectedCategory = Category(id = catId, name = catName ?: "", icon = catIcon, color = "", type = type)
        }

        // Color update helper
        fun updateToggleColors(isExpense: Boolean) {
            val red = ContextCompat.getColor(requireContext(), android.R.color.holo_red_light)
            val green = ContextCompat.getColor(requireContext(), android.R.color.holo_green_light)
            val white = ContextCompat.getColor(requireContext(), android.R.color.white)

            if (isExpense) {
                btnExpense.setBackgroundColor(red)
                btnExpense.setTextColor(white)
                btnIncome.setBackgroundColor(white)
                btnIncome.setTextColor(green)
            } else {
                btnExpense.setBackgroundColor(white)
                btnExpense.setTextColor(red)
                btnIncome.setBackgroundColor(green)
                btnIncome.setTextColor(white)
            }
        }

        // Initial Color
        updateToggleColors(selectedType == "expense")

        // Load Categories
        loadCategories(chipGroup, selectedType)

        // Toggle Listener
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btn_expense) {
                    selectedType = "expense"
                    updateToggleColors(true)
                } else {
                    selectedType = "income"
                    updateToggleColors(false)
                }
                loadCategories(chipGroup, selectedType)
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
            database.categoryDao().getCategoriesByType(type).collect { categories ->
                for (category in categories) {
                    val chip = Chip(context)
                    chip.text = "${category.icon} ${category.name}"
                    chip.isCheckable = true

                    // If Editing, check the chip that matches
                    if (isEditMode && selectedCategory != null && category.name == selectedCategory!!.name) {
                        chip.isChecked = true
                        selectedCategory = category // Update ref to real DB object
                    }

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
                    id = if (isEditMode) transactionId else 0,
                    userId = userId,
                    amount = amount,
                    categoryId = selectedCategory!!.id,
                    categoryName = selectedCategory!!.name,
                    categoryIcon = selectedCategory!!.icon,
                    type = selectedType,
                    description = description.ifEmpty { selectedCategory!!.name },
                    date = originalDate
                )

                if (isEditMode) {
                    database.transactionDao().updateTransaction(transaction)
                    Toast.makeText(context, "Transaction Updated", Toast.LENGTH_SHORT).show()
                } else {
                    database.transactionDao().insertTransaction(transaction)
                    Toast.makeText(context, "Transaction Saved", Toast.LENGTH_SHORT).show()
                }
                dismiss()
            }
        }
    }
}