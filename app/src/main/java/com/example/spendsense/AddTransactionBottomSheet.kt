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
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddTransactionBottomSheet : BottomSheetDialogFragment() {

    private lateinit var database: AppDatabase
    private var selectedType = "expense"
    private var selectedCategory: Category? = null

    // Date Variable (Default to Now)
    private var selectedDate: Long = System.currentTimeMillis()

    // Edit Mode Variables
    private var transactionId: Int = 0
    private var isEditMode = false

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_add_transaction, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        database = AppDatabase.getDatabase(requireContext())

        // Check arguments for Edit Mode
        if (arguments != null && requireArguments().containsKey("id")) {
            isEditMode = true
            val args = requireArguments()
            transactionId = args.getInt("id")
            // Use the date from the existing transaction
            selectedDate = args.getLong("date")

            // Reconstruct temp transaction object for logic
            // (Only basic fields needed for UI setup)
        }

        val toggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.toggle_transaction_type)
        val btnExpense = view.findViewById<Button>(R.id.btn_expense)
        val btnIncome = view.findViewById<Button>(R.id.btn_income)
        val etAmount = view.findViewById<TextInputEditText>(R.id.et_amount)
        val etDate = view.findViewById<TextInputEditText>(R.id.et_date) // NEW
        val etDescription = view.findViewById<TextInputEditText>(R.id.et_description)
        val chipGroup = view.findViewById<ChipGroup>(R.id.chip_group_categories)
        val btnSave = view.findViewById<Button>(R.id.btn_save_transaction)

        // Set Date Field Text
        val dateFormat = SimpleDateFormat("EEE, MMM dd yyyy", Locale.getDefault())
        etDate.setText(dateFormat.format(Date(selectedDate)))

        // --- DATE PICKER LOGIC ---
        etDate.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Date")
                .setSelection(selectedDate)
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                selectedDate = selection
                etDate.setText(dateFormat.format(Date(selectedDate)))
            }

            datePicker.show(parentFragmentManager, "DATE_PICKER")
        }

        // Pre-fill data if Editing
        if (isEditMode) {
            val args = requireArguments()
            btnSave.text = "Update Transaction"
            etAmount.setText(args.getDouble("amount").toString())
            etDescription.setText(args.getString("desc"))

            val type = args.getString("type") ?: "expense"
            selectedType = type

            if (type == "income") toggleGroup.check(R.id.btn_income)
            else toggleGroup.check(R.id.btn_expense)

            // Re-create category object for selection logic
            selectedCategory = Category(
                id = args.getInt("catId"),
                name = args.getString("catName") ?: "",
                icon = args.getString("catIcon") ?: "🏷️",
                color = "",
                type = type
            )
        }

        updateToggleUI(btnExpense, btnIncome, selectedType == "expense")
        loadCategories(chipGroup, selectedType)

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btn_expense) {
                    selectedType = "expense"
                    updateToggleUI(btnExpense, btnIncome, true)
                } else {
                    selectedType = "income"
                    updateToggleUI(btnExpense, btnIncome, false)
                }
                loadCategories(chipGroup, selectedType)
            }
        }

        btnSave.setOnClickListener {
            val amountText = etAmount.text.toString()
            val description = etDescription.text.toString()

            if (amountText.isEmpty() || selectedCategory == null) {
                Toast.makeText(context, "Fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveTransaction(amountText.toDouble(), description)
        }
    }

    private fun updateToggleUI(btnExpense: Button, btnIncome: Button, isExpense: Boolean) {
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

    private fun loadCategories(chipGroup: ChipGroup, type: String) {
        lifecycleScope.launch {
            chipGroup.removeAllViews()
            database.categoryDao().getCategoriesByType(type).collect { categories ->
                for (category in categories) {
                    val chip = Chip(context)
                    chip.text = "${category.icon} ${category.name}"
                    chip.isCheckable = true

                    if (isEditMode && selectedCategory != null && category.name == selectedCategory!!.name) {
                        chip.isChecked = true
                        selectedCategory = category
                    }

                    chip.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) selectedCategory = category
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
                    date = selectedDate // USE THE SELECTED DATE
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