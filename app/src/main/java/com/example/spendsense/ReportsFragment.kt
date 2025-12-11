package com.example.spendsense

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.spendsense.database.AppDatabase
import com.example.spendsense.database.Transaction
import com.example.spendsense.utils.CurrencyHelper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportsFragment : Fragment() {

    private lateinit var database: AppDatabase
    private var userId: Int = -1

    // Data holders for export
    private var totalIncome = 0.0
    private var totalExpense = 0.0
    private var categoryData = listOf<Pair<String, Double>>()
    private var topDaysData = listOf<Pair<String, Double>>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_reports, container, false)

        database = AppDatabase.getDatabase(requireContext())
        val prefs = requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE)
        userId = prefs.getInt("userId", -1)

        // Load Data
        loadReportsData(view)

        // Buttons
        view.findViewById<Button>(R.id.btn_share_report).setOnClickListener { shareReport() }
        view.findViewById<FloatingActionButton>(R.id.fab_export_pdf).setOnClickListener { exportToPdf() }

        return view
    }

    private fun loadReportsData(view: View) {
        if (userId == -1) return

        val tvMonthYear = view.findViewById<TextView>(R.id.tv_month_year)
        val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        tvMonthYear.text = dateFormat.format(Date())

        lifecycleScope.launch {
            database.transactionDao().getAllTransactions(userId).collect { transactions ->
                calculateReports(view, transactions)
            }
        }
    }

    private fun calculateReports(view: View, transactions: List<Transaction>) {

        // In calculateReports()
        val symbol = CurrencyHelper.getSymbol(requireContext())
// ... replace all "₹" with "$symbol" ...

        // 1. Totals
        totalIncome = transactions.filter { it.type == "income" }.sumOf { it.amount }
        totalExpense = transactions.filter { it.type == "expense" }.sumOf { it.amount }
        val savings = totalIncome - totalExpense

        view.findViewById<TextView>(R.id.tv_income_val).text = "₹${String.format("%.0f", totalIncome)}"
        view.findViewById<TextView>(R.id.tv_expense_val).text = "₹${String.format("%.0f", totalExpense)}"
        view.findViewById<TextView>(R.id.tv_savings_val).text = "₹${String.format("%.0f", savings)}"

        // 2. Chart Bar
        view.findViewById<TextView>(R.id.tv_chart_income).text = "₹${String.format("%.0f", totalIncome)}"
        view.findViewById<TextView>(R.id.tv_chart_expense).text = "₹${String.format("%.0f", totalExpense)}"

        val expenseBar = view.findViewById<View>(R.id.view_expense_bar)
        val expenseSpace = view.findViewById<Space>(R.id.view_expense_space)

        if (totalIncome > 0) {
            val expenseWeight = (totalExpense / totalIncome).toFloat()
            val paramsBar = expenseBar.layoutParams as LinearLayout.LayoutParams
            paramsBar.weight = expenseWeight.coerceAtMost(1.0f)
            expenseBar.layoutParams = paramsBar

            val paramsSpace = expenseSpace.layoutParams as LinearLayout.LayoutParams
            paramsSpace.weight = (1.0f - expenseWeight).coerceAtLeast(0.0f)
            expenseSpace.layoutParams = paramsSpace
        }

        // 3. Categories
        val expenses = transactions.filter { it.type == "expense" }
        categoryData = expenses.groupBy { it.categoryName }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }

        val categoryContainer = view.findViewById<LinearLayout>(R.id.ll_reports_categories)
        categoryContainer.removeAllViews()

        for ((name, amount) in categoryData) {
            val percent = if (totalExpense > 0) (amount / totalExpense * 100).toInt() else 0
            // Just create simple text views for now to avoid layout inflation complexity inside loop
            val row = TextView(context)
            row.text = "$name: ₹${String.format("%.0f", amount)} ($percent%)"
            row.textSize = 14f
            row.setPadding(0, 8, 0, 8)
            categoryContainer.addView(row)
        }

        // 4. Top Days
        val dayFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
        topDaysData = expenses.groupBy { dayFormat.format(Date(it.date)) }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
            .take(3)

        val daysContainer = view.findViewById<LinearLayout>(R.id.ll_top_days)
        daysContainer.removeAllViews()

        for ((date, amount) in topDaysData) {
            val row = TextView(context)
            row.text = "$date: ₹${String.format("%.0f", amount)}"
            row.textSize = 14f
            row.setPadding(0, 8, 0, 8)
            daysContainer.addView(row)
        }
    }

    private fun shareReport() {
        val reportText = """
            📊 SpendSense Report
            Income: ₹$totalIncome
            Expense: ₹$totalExpense
            Savings: ₹${totalIncome - totalExpense}
            
            Top Categories:
            ${categoryData.take(3).joinToString("\n") { "${it.first}: ₹${it.second}" }}
        """.trimIndent()

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "SpendSense Report")
            putExtra(Intent.EXTRA_TEXT, reportText)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Report via"))
    }

    private fun exportToPdf() {
        try {
            val fileName = "SpendSense_Report_${System.currentTimeMillis()}.pdf"
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            val pdfWriter = PdfWriter(file)
            val pdfDocument = PdfDocument(pdfWriter)
            val document = Document(pdfDocument)

            document.add(Paragraph("SpendSense Report").setFontSize(24f).setBold())
            document.add(Paragraph("\nFinancial Summary").setFontSize(18f).setBold())
            document.add(Paragraph("Income: ₹$totalIncome"))
            document.add(Paragraph("Expense: ₹$totalExpense"))
            document.add(Paragraph("Savings: ₹${totalIncome - totalExpense}"))

            document.add(Paragraph("\nSpending by Category").setFontSize(18f).setBold())
            val table = Table(2)
            table.addCell("Category")
            table.addCell("Amount")
            for ((name, amount) in categoryData) {
                table.addCell(name)
                table.addCell("₹$amount")
            }
            document.add(table)

            document.close()
            Toast.makeText(context, "PDF Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()

            // Open PDF logic here (same as before)

        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}