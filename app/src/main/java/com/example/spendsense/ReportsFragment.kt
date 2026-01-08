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
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
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

    override fun onResume() {
        super.onResume()
        // Reload data in case currency changed or new transactions added
        view?.let { loadReportsData(it) }
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
        // 1. Totals
        totalIncome = transactions.filter { it.type == "income" }.sumOf { it.amount }
        totalExpense = transactions.filter { it.type == "expense" }.sumOf { it.amount }
        val savings = totalIncome - totalExpense

        // FIX: Use CurrencyHelper.format()
        view.findViewById<TextView>(R.id.tv_income_val).text = CurrencyHelper.format(requireContext(), totalIncome)
        view.findViewById<TextView>(R.id.tv_expense_val).text = CurrencyHelper.format(requireContext(), totalExpense)
        view.findViewById<TextView>(R.id.tv_savings_val).text = CurrencyHelper.format(requireContext(), savings)

        // 2. Chart Bar Logic
        view.findViewById<TextView>(R.id.tv_chart_income).text = CurrencyHelper.format(requireContext(), totalIncome)
        view.findViewById<TextView>(R.id.tv_chart_expense).text = CurrencyHelper.format(requireContext(), totalExpense)

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
        } else {
            val paramsBar = expenseBar.layoutParams as LinearLayout.LayoutParams
            paramsBar.weight = if (totalExpense > 0) 1.0f else 0.0f
            expenseBar.layoutParams = paramsBar
        }

        // 3. Calculate Category Data
        val expenses = transactions.filter { it.type == "expense" }
        categoryData = expenses.groupBy { it.categoryName }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }

        // Populate Category List
        val categoryContainer = view.findViewById<LinearLayout>(R.id.ll_reports_categories)
        categoryContainer.removeAllViews()

        if (categoryData.isEmpty()) {
            val emptyText = TextView(context)
            emptyText.text = "No expenses recorded yet"
            emptyText.setPadding(0, 16, 0, 16)
            categoryContainer.addView(emptyText)
        } else {
            for ((name, amount) in categoryData) {
                val percent = if (totalExpense > 0) (amount / totalExpense * 100).toInt() else 0
                val row = TextView(context)

                // FIX: Use CurrencyHelper.format()
                row.text = "$name: ${CurrencyHelper.format(requireContext(), amount)} ($percent%)"

                row.textSize = 14f
                row.setPadding(0, 8, 0, 8)
                row.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_primary))
                categoryContainer.addView(row)
            }
        }

        // 4. Calculate Top Days Data
        val dayFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
        topDaysData = expenses.groupBy { dayFormat.format(Date(it.date)) }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
            .take(3)

        // Populate Top Days List
        val daysContainer = view.findViewById<LinearLayout>(R.id.ll_top_days)
        daysContainer.removeAllViews()

        if (topDaysData.isEmpty()) {
            val emptyText = TextView(context)
            emptyText.text = "No activity yet"
            emptyText.setPadding(0, 16, 0, 16)
            daysContainer.addView(emptyText)
        } else {
            for ((date, amount) in topDaysData) {
                val row = TextView(context)

                // FIX: Use CurrencyHelper.format()
                row.text = "$date: ${CurrencyHelper.format(requireContext(), amount)}"

                row.textSize = 14f
                row.setPadding(0, 8, 0, 8)
                row.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_primary))
                daysContainer.addView(row)
            }
        }

        // 5. Financial Insights Logic
        val calendar = java.util.Calendar.getInstance()
        val currentDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val dailyAvg = if (currentDay > 0) totalExpense / currentDay else 0.0

        view.findViewById<TextView>(R.id.tv_insight_avg).text = "${CurrencyHelper.format(requireContext(), dailyAvg)} / day"

        val maxTransaction = expenses.maxByOrNull { it.amount }
        if (maxTransaction != null) {
            view.findViewById<TextView>(R.id.tv_insight_max).text =
                "${CurrencyHelper.format(requireContext(), maxTransaction.amount)} (${maxTransaction.categoryName})"
        } else {
            view.findViewById<TextView>(R.id.tv_insight_max).text = "None"
        }

        view.findViewById<TextView>(R.id.tv_insight_count).text = "${transactions.size} Records"
    }

    private fun shareReport() {
        val context = requireContext()
        val formattedIncome = CurrencyHelper.format(context, totalIncome)
        val formattedExpense = CurrencyHelper.format(context, totalExpense)
        val formattedSavings = CurrencyHelper.format(context, totalIncome - totalExpense)

        val reportText = """
            📊 SpendSense Report
            Income: $formattedIncome
            Expense: $formattedExpense
            Savings: $formattedSavings
            
            Top Categories:
            ${categoryData.take(3).joinToString("\n") { "${it.first}: ${CurrencyHelper.format(context, it.second)}" }}
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

            // FIX: Use Helper
            val context = requireContext()
            document.add(Paragraph("Income: ${CurrencyHelper.format(context, totalIncome)}"))
            document.add(Paragraph("Expense: ${CurrencyHelper.format(context, totalExpense)}"))
            document.add(Paragraph("Savings: ${CurrencyHelper.format(context, totalIncome - totalExpense)}"))

            document.add(Paragraph("\nSpending by Category").setFontSize(18f).setBold())
            val table = Table(2)
            table.addCell(Paragraph("Category").setBold())
            table.addCell(Paragraph("Amount").setBold())
            for ((name, amount) in categoryData) {
                table.addCell(name)
                table.addCell(CurrencyHelper.format(context, amount))
            }
            document.add(table)

            document.add(Paragraph("\nGenerated by SpendSense").setFontSize(10f).setItalic())

            document.close()
            Toast.makeText(context, "PDF Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()

            try {
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.provider",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(uri, "application/pdf")
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                startActivity(Intent.createChooser(intent, "Open Report"))
            } catch (e: Exception) {
                // Ignore
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error creating PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}