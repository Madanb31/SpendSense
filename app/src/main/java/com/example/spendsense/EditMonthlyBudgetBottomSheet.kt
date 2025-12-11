package com.example.spendsense

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText

class EditMonthlyBudgetBottomSheet(private val onSave: (Double) -> Unit) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_edit_monthly_budget, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etAmount = view.findViewById<TextInputEditText>(R.id.et_monthly_limit)
        val btnSave = view.findViewById<Button>(R.id.btn_save_monthly_budget)

        btnSave.setOnClickListener {
            val amountText = etAmount.text.toString()
            if (amountText.isNotEmpty()) {
                val amount = amountText.toDoubleOrNull() ?: 0.0
                onSave(amount)
                dismiss()
            } else {
                Toast.makeText(context, "Enter an amount", Toast.LENGTH_SHORT).show()
            }
        }
    }
}