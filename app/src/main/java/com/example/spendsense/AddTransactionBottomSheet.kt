package com.example.spendsense

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.spendsense.database.AppDatabase
import com.example.spendsense.database.Category
import com.example.spendsense.database.Transaction
import com.example.spendsense.network.RetrofitClient
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
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
    private var selectedDate: Long = System.currentTimeMillis()

    private var transactionId: Int = 0
    private var isEditMode = false

    // Force the Transparent Theme
    override fun getTheme(): Int {
        return R.style.BottomSheetDialogTheme
    }
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
            selectedDate = args.getLong("date")
        }

        val btnExpense = view.findViewById<Button>(R.id.btn_expense)
        val btnIncome = view.findViewById<Button>(R.id.btn_income)
        val etAmount = view.findViewById<TextInputEditText>(R.id.et_amount)
        val etDate = view.findViewById<TextInputEditText>(R.id.et_date)
        val etDescription = view.findViewById<TextInputEditText>(R.id.et_description)
        val chipGroup = view.findViewById<ChipGroup>(R.id.chip_group_categories)
        val btnSave = view.findViewById<Button>(R.id.btn_save_transaction)
        val spinnerCurrency = view.findViewById<Spinner>(R.id.spinner_currency)
        val btnConvert = view.findViewById<ImageButton>(R.id.btn_convert)

        // 1. Setup Spinner
        val currencies = arrayOf("INR", "USD", "EUR", "GBP", "JPY", "AUD", "CAD", "SGD", "AED", "CNY")
        val adapter = ArrayAdapter(requireContext(), R.layout.spinner_item, currencies)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCurrency.adapter = adapter

        // 2. Handle Convert
        btnConvert.setOnClickListener {
            val amountText = etAmount.text.toString()
            if (amountText.isNotEmpty()) {
                val amount = amountText.toDouble()
                val selectedCurrency = spinnerCurrency.selectedItem.toString()
                if (selectedCurrency != "INR") {
                    convertCurrency(amount, selectedCurrency, "INR", etAmount)
                } else {
                    Toast.makeText(context, "Already in INR", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Enter amount first", Toast.LENGTH_SHORT).show()
            }
        }

        // Set Date Field Text
        val dateFormat = SimpleDateFormat("EEE, MMM dd yyyy", Locale.getDefault())
        etDate.setText(dateFormat.format(Date(selectedDate)))

        // Date Picker Logic
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

            // Set initial selected category object
            selectedCategory = Category(
                id = args.getInt("catId"),
                name = args.getString("catName") ?: "",
                icon = args.getString("catIcon") ?: "🏷️",
                color = "",
                type = type
            )
        }

        // Initial UI State
        updateToggleUI(btnExpense, btnIncome, selectedType == "expense")
        loadCategories(chipGroup, selectedType)

        // --- NEW TOGGLE LOGIC (Simple Buttons) ---
        btnExpense.setOnClickListener {
            selectedType = "expense"
            updateToggleUI(btnExpense, btnIncome, true)
            loadCategories(chipGroup, "expense")
        }

        btnIncome.setOnClickListener {
            selectedType = "income"
            updateToggleUI(btnExpense, btnIncome, false)
            loadCategories(chipGroup, "income")
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
        val white = ContextCompat.getColor(requireContext(), android.R.color.white)

        // Determine unselected text color based on UI Mode
        val nightModeFlags = requireContext().resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isNightMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES

        val unselectedTextColor = if (isNightMode) {
            Color.LTGRAY // Light Gray for Night Mode (visible on dark)
        } else {
            Color.DKGRAY // Dark Gray for Day Mode (visible on light)
        }

        if (isExpense) {
            // EXPENSE SELECTED
            btnExpense.setBackgroundResource(R.drawable.toggle_btn_selected_red)
            btnExpense.setTextColor(white)

            // INCOME UNSELECTED
            btnIncome.setBackgroundResource(R.drawable.toggle_btn_unselected)
            btnIncome.setTextColor(unselectedTextColor)
        } else {
            // EXPENSE UNSELECTED
            btnExpense.setBackgroundResource(R.drawable.toggle_btn_unselected)
            btnExpense.setTextColor(unselectedTextColor)

            // INCOME SELECTED
            btnIncome.setBackgroundResource(R.drawable.toggle_btn_selected_green)
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

    private fun convertCurrency(amount: Double, from: String, to: String, etAmount: TextInputEditText) {
        Toast.makeText(context, "Converting...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.getRates(from, to, amount)
                val convertedAmount = response.rates[to]
                if (convertedAmount != null) {
                    etAmount.setText(String.format("%.2f", convertedAmount))
                } else {
                    Toast.makeText(context, "Conversion failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Network Error", Toast.LENGTH_SHORT).show()
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
                    date = selectedDate
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