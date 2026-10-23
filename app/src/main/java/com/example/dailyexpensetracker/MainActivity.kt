package com.example.dailyexpensetracker

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

data class ExpenseTransaction(
    val id: Long,
    val amount: Double,
    val category: String,
    val note: String,
    val timestamp: Long
)

private val expenseCategories = listOf(
    "Food", "Travel", "Shopping", "Bills",
    "Medical", "Education", "Other"
)

private fun loadTransactions(
    prefs: SharedPreferences
): List<ExpenseTransaction> {
    val result = mutableListOf<ExpenseTransaction>()

    try {
        val json = JSONArray(prefs.getString("transactions", "[]") ?: "[]")

        for (i in 0 until json.length()) {
            val item = json.getJSONObject(i)
            result.add(
                ExpenseTransaction(
                    id = item.optLong("id"),
                    amount = item.optDouble("amount"),
                    category = item.optString("category", "Other"),
                    note = item.optString("note", ""),
                    timestamp = item.optLong("timestamp")
                )
            )
        }
    } catch (_: Exception) {
        // Invalid saved data will not crash the app.
    }

    return result.sortedByDescending { it.timestamp }
}

private fun saveTransactions(
    prefs: SharedPreferences,
    transactions: List<ExpenseTransaction>
) {
    val json = JSONArray()

    transactions.forEach { transaction ->
        json.put(
            JSONObject().apply {
                put("id", transaction.id)
                put("amount", transaction.amount)
                put("category", transaction.category)
                put("note", transaction.note)
                put("timestamp", transaction.timestamp)
            }
        )
    }

    prefs.edit().putString("transactions", json.toString()).apply()
}

private fun money(amount: Double): String =
    "₹" + String.format(Locale.US, "%,.2f", amount)

private fun dateTime(timestamp: Long): String =
    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        .format(Date(timestamp))

private fun dateKey(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(
            "expense_tracker",
            Context.MODE_PRIVATE
        )

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ExpenseApp(prefs)
                }
            }
        }
    }
}

@Composable
fun ExpenseApp(prefs: SharedPreferences) {
    var monthlyIncome by remember {
        mutableStateOf(prefs.getString("income", "") ?: "")
    }
    var fixedExpenses by remember {
        mutableStateOf(prefs.getString("fixed", "") ?: "")
    }
    var savingPercent by remember {
        mutableStateOf(prefs.getString("saving", "20") ?: "20")
    }
    var setupComplete by remember {
        mutableStateOf(prefs.getBoolean("setup_done", false))
    }

    val transactions = remember {
        androidx.compose.runtime.mutableStateListOf<ExpenseTransaction>()
            .apply { addAll(loadTransactions(prefs)) }
    }

    var expenseInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Food") }
    var message by remember { mutableStateOf("") }

    val income = monthlyIncome.toDoubleOrNull() ?: 0.0
    val fixed = fixedExpenses.toDoubleOrNull() ?: 0.0
    val saving = (savingPercent.toDoubleOrNull() ?: 0.0)
        .coerceIn(0.0, 100.0)

    val available = max(0.0, income - fixed - income * saving / 100.0)
    val dailyLimit = available / 30.0

    val today = dateKey(System.currentTimeMillis())
    val todayExpense = transactions
        .filter { dateKey(it.timestamp) == today }
        .sumOf { it.amount }

    val month = SimpleDateFormat("yyyy-MM", Locale.US)
        .format(Date())

    val monthlyExpense = transactions
        .filter {
            SimpleDateFormat("yyyy-MM", Locale.US)
                .format(Date(it.timestamp)) == month
        }
        .sumOf { it.amount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Daily Expense Tracker",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(8.dp))
        Text("Apne kharch par rakhein nazar!")
        Spacer(Modifier.height(20.dp))

        if (!setupComplete) {
            Text(
                "Apna Budget Set Karein",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = monthlyIncome,
                onValueChange = { monthlyIncome = it },
                label = { Text("Monthly Income (₹)") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = fixedExpenses,
                onValueChange = { fixedExpenses = it },
                label = { Text("Monthly Fixed Expenses (₹)") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = savingPercent,
                onValueChange = { savingPercent = it },
                label = { Text("Saving Target (%)") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val inc = monthlyIncome.toDoubleOrNull()
                    val exp = fixedExpenses.toDoubleOrNull()
                    val save = savingPercent.toDoubleOrNull()

                    message = when {
                        inc == null || inc <= 0 ->
                            "Sahi monthly income bharein."
                        exp == null || exp < 0 ->
                            "Fixed expenses sahi bharein."
                        exp > inc ->
                            "Fixed expenses income se zyada hain."
                        save == null || save !in 0.0..100.0 ->
                            "Saving target 0 se 100 ke beech bharein."
                        else -> {
                            prefs.edit()
                                .putString("income", monthlyIncome)
                                .putString("fixed", fixedExpenses)
                                .putString("saving", savingPercent)
                                .putBoolean("setup_done", true)
                                .apply()
                            setupComplete = true
                            "Setup save ho gaya!"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Setup")
            }

            if (message.isNotBlank()) {
                Text(message, modifier = Modifier.padding(top = 8.dp))
            }
        } else {
            Text(
                "Dashboard",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(Modifier.height(12.dp))

            SummaryCard("Monthly Income", money(income))
            SummaryCard("Fixed Expenses", money(fixed))
            SummaryCard("Saving Target", money(income * saving / 100.0))
            SummaryCard("Available Budget", money(available))
            SummaryCard("Daily Spending Limit", money(dailyLimit))
            SummaryCard("Today's Expense", money(todayExpense))
            SummaryCard("Today's Remaining Limit", money(dailyLimit - todayExpense))
            SummaryCard("This Month's Expense", money(monthlyExpense))

            Spacer(Modifier.height(20.dp))

            HorizontalDivider()

            Spacer(Modifier.height(12.dp))

            Text(
                "Naya Kharch Add Karein",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = expenseInput,
                onValueChange = { expenseInput = it },
                label = { Text("Amount (₹)") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            Text("Category", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                expenseCategories.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) }
                    )
                }
            }

            OutlinedTextField(
                value = noteInput,
                onValueChange = { noteInput = it },
                label = { Text("Note (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    val amount = expenseInput.toDoubleOrNull()

                    if (amount == null || !amount.isFinite() || amount <= 0) {
                        message = "Kharch ki sahi rakam bharein."
                    } else {
                        val transaction = ExpenseTransaction(
                            id = System.currentTimeMillis(),
                            amount = amount,
                            category = selectedCategory,
                            note = noteInput.trim(),
                            timestamp = System.currentTimeMillis()
                        )

                        transactions.add(0, transaction)
                        saveTransactions(prefs, transactions)

                        expenseInput = ""
                        noteInput = ""
                        message = "Kharch record mein save ho gaya."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Kharch Save Karein")
            }

            if (message.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(message)
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Text(
                "Transaction History",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(Modifier.height(8.dp))

            if (transactions.isEmpty()) {
                Text("Abhi koi kharch record nahi hai.")
            } else {
                transactions.forEach { transaction ->
                    TransactionCard(
                        transaction = transaction,
                        onDelete = {
                            transactions.remove(transaction)
                            saveTransactions(prefs, transactions)
                            message = "Transaction delete ho gaya."
                        }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            OutlinedButton(
                onClick = {
                    setupComplete = false
                    message = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Budget Setup Badlein")
            }
        }
    }
}

@Composable
fun SummaryCard(title: String, value: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun TransactionCard(
    transaction: ExpenseTransaction,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        transaction.category,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (transaction.note.isNotBlank()) {
                        Text(transaction.note)
                    }
                    Text(
                        dateTime(transaction.timestamp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    money(transaction.amount),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            TextButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Delete")
            }
        }
    }
}
