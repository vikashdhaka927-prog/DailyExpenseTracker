package com.example.dailyexpensetracker

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

private val categories = listOf(
    "Food", "Travel", "Shopping", "Bills",
    "Medical", "Education", "Recharge", "Other"
)

private fun money(value: Double): String =
    "₹" + String.format(Locale.US, "%,.2f", value)

private fun dayKey(time: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(time))

private fun monthKey(time: Long): String =
    SimpleDateFormat("yyyy-MM", Locale.US).format(Date(time))

private fun dateLabel(time: Long): String =
    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        .format(Date(time))

private fun loadTransactions(
    prefs: SharedPreferences
): List<ExpenseTransaction> {
    val list = mutableListOf<ExpenseTransaction>()
    try {
        val array = JSONArray(prefs.getString("transactions", "[]") ?: "[]")
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                ExpenseTransaction(
                    id = obj.optLong("id"),
                    amount = obj.optDouble("amount"),
                    category = obj.optString("category", "Other"),
                    note = obj.optString("note", ""),
                    timestamp = obj.optLong("timestamp")
                )
            )
        }
    } catch (_: Exception) {
    }
    return list.sortedByDescending { it.timestamp }
}

private fun saveTransactions(
    prefs: SharedPreferences,
    transactions: List<ExpenseTransaction>
) {
    val array = JSONArray()
    transactions.forEach { item ->
        array.put(
            JSONObject().apply {
                put("id", item.id)
                put("amount", item.amount)
                put("category", item.category)
                put("note", item.note)
                put("timestamp", item.timestamp)
            }
        )
    }
    prefs.edit().putString("transactions", array.toString()).apply()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("expense_tracker", Context.MODE_PRIVATE)

        setContent {
            MaterialTheme {
                ExpenseApp(prefs)
            }
        }
    }
}

@Composable
fun ExpenseApp(prefs: SharedPreferences) {
    var incomeText by remember {
        mutableStateOf(prefs.getString("income", "") ?: "")
    }
    var fixedText by remember {
        mutableStateOf(prefs.getString("fixed", "") ?: "")
    }
    var savingText by remember {
        mutableStateOf(prefs.getString("saving", "20") ?: "20")
    }
    var setupDone by remember {
        mutableStateOf(prefs.getBoolean("setup_done", false))
    }
    var selectedTab by remember { mutableStateOf("Dashboard") }

    val transactions = remember {
        androidx.compose.runtime.mutableStateListOf<ExpenseTransaction>()
            .apply { addAll(loadTransactions(prefs)) }
    }

    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Food") }
    var message by remember { mutableStateOf("") }

    val income = incomeText.toDoubleOrNull() ?: 0.0
    val fixed = fixedText.toDoubleOrNull() ?: 0.0
    val savingPercent = (savingText.toDoubleOrNull() ?: 0.0)
        .coerceIn(0.0, 100.0)
    val savingAmount = income * savingPercent / 100.0
    val availableBudget = max(0.0, income - fixed - savingAmount)
    val dailyLimit = availableBudget / 30.0

    val now = System.currentTimeMillis()
    val todayExpense = transactions
        .filter { dayKey(it.timestamp) == dayKey(now) }
        .sumOf { it.amount }
    val monthlyExpense = transactions
        .filter { monthKey(it.timestamp) == monthKey(now) }
        .sumOf { it.amount }

    fun saveBudget(): String {
        val parsedIncome = incomeText.toDoubleOrNull()
        val parsedFixed = fixedText.toDoubleOrNull()
        val parsedSaving = savingText.toDoubleOrNull()

        return when {
            parsedIncome == null || !parsedIncome.isFinite() || parsedIncome <= 0 ->
                "Valid monthly income bharein."
            parsedFixed == null || !parsedFixed.isFinite() || parsedFixed < 0 ->
                "Fixed expenses sahi bharein."
            parsedFixed > parsedIncome ->
                "Fixed expenses income se zyada hain."
            parsedSaving == null || !parsedSaving.isFinite() ||
                parsedSaving !in 0.0..100.0 ->
                "Saving target 0 se 100% ke beech hona chahiye."
            else -> {
                prefs.edit()
                    .putString("income", incomeText)
                    .putString("fixed", fixedText)
                    .putString("saving", savingText)
                    .putBoolean("setup_done", true)
                    .apply()
                setupDone = true
                "Budget save ho gaya."
            }
        }
    }

    fun addExpense() {
        val amount = amountText.toDoubleOrNull()
        if (amount == null || !amount.isFinite() || amount <= 0) {
            message = "Kharch ki sahi rakam bharein."
            return
        }

        val transaction = ExpenseTransaction(
            id = System.currentTimeMillis(),
            amount = amount,
            category = selectedCategory,
            note = noteText.trim(),
            timestamp = System.currentTimeMillis()
        )

        transactions.add(0, transaction)
        saveTransactions(prefs, transactions)
        amountText = ""
        noteText = ""
        message = "Transaction save ho gaya."
    }

    if (!setupDone) {
        SetupScreen(
            income = incomeText,
            onIncomeChange = { incomeText = it },
            fixed = fixedText,
            onFixedChange = { fixedText = it },
            saving = savingText,
            onSavingChange = { savingText = it },
            message = message,
            onSave = { message = saveBudget() }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    listOf("Dashboard", "Transactions", "Settings").forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = {
                                selectedTab = tab
                                message = ""
                            },
                            icon = {
                                Text(
                                    when (tab) {
                                        "Dashboard" -> "⌂"
                                        "Transactions" -> "₹"
                                        else -> "⚙"
                                    },
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            label = { Text(tab) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                when (selectedTab) {
                    "Dashboard" -> {
                        Text(
                            "Daily Expense Tracker",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            SimpleDateFormat(
                                "EEEE, dd MMMM yyyy",
                                Locale.getDefault()
                            ).format(Date()),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(Modifier.height(18.dp))

                        SummaryCard("Available Monthly Budget", money(availableBudget))
                        SummaryCard("Today's Spending Limit", money(dailyLimit))
                        SummaryCard("Today's Expense", money(todayExpense))
                        SummaryCard(
                            "Today's Remaining Limit",
                            money(dailyLimit - todayExpense)
                        )
                        SummaryCard("This Month's Expense", money(monthlyExpense))

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = {
                                selectedTab = "Transactions"
                                message = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("+ Add New Expense")
                        }

                        Spacer(Modifier.height(18.dp))
                        Text(
                            "Recent Transactions",
                            style = MaterialTheme.typography.titleLarge
                        )

                        val recent = transactions.take(5)
                        if (recent.isEmpty()) {
                            Text(
                                "Abhi koi transaction nahi hai.",
                                modifier = Modifier.padding(vertical = 14.dp)
                            )
                        } else {
                            recent.forEach { item ->
                                TransactionCard(
                                    transaction = item,
                                    onDelete = {
                                        transactions.remove(item)
                                        saveTransactions(prefs, transactions)
                                        message = "Transaction delete ho gaya."
                                    }
                                )
                            }
                        }
                    }

                    "Transactions" -> {
                        Text(
                            "Transactions",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text("Apne daily expenses record karein.")

                        Spacer(Modifier.height(16.dp))

                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it },
                            label = { Text("Amount (₹)") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(Modifier.height(10.dp))
                        Text("Category", style = MaterialTheme.typography.titleMedium)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categories.forEach { category ->
                                FilterChip(
                                    selected = selectedCategory == category,
                                    onClick = { selectedCategory = category },
                                    label = { Text(category) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = noteText,
                            onValueChange = { noteText = it },
                            label = { Text("Note (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(Modifier.height(10.dp))

                        Button(
                            onClick = { addExpense() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Expense")
                        }

                        if (message.isNotBlank()) {
                            Text(
                                message,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        Spacer(Modifier.height(18.dp))
                        Text(
                            "All Transactions (${transactions.size})",
                            style = MaterialTheme.typography.titleLarge
                        )

                        if (transactions.isEmpty()) {
                            Text(
                                "Koi record nahi hai. Upar pehla kharch add karein.",
                                modifier = Modifier.padding(vertical = 14.dp)
                            )
                        } else {
                            transactions.forEach { item ->
                                TransactionCard(
                                    transaction = item,
                                    onDelete = {
                                        transactions.remove(item)
                                        saveTransactions(prefs, transactions)
                                        message = "Transaction delete ho gaya."
                                    }
                                )
                            }
                        }
                    }

                    "Settings" -> {
                        Text(
                            "Settings",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text("Apni income aur budget settings badlein.")

                        Spacer(Modifier.height(18.dp))

                        OutlinedTextField(
                            value = incomeText,
                            onValueChange = { incomeText = it },
                            label = { Text("Monthly Income (₹)") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = fixedText,
                            onValueChange = { fixedText = it },
                            label = { Text("Fixed Monthly Expenses (₹)") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = savingText,
                            onValueChange = { savingText = it },
                            label = { Text("Saving Target (%)") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(Modifier.height(14.dp))

                        Button(
                            onClick = { message = saveBudget() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Budget Settings")
                        }

                        if (message.isNotBlank()) {
                            Text(
                                message,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        Spacer(Modifier.height(18.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(18.dp))

                        Text(
                            "Expense Tracker",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("Version 2 • Local offline storage")
                        Text("SMS automatic tracking abhi available nahi hai.")

                        Spacer(Modifier.height(18.dp))

                        OutlinedButton(
                            onClick = {
                                setupDone = false
                                message = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Setup Screen")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SetupScreen(
    income: String,
    onIncomeChange: (String) -> Unit,
    fixed: String,
    onFixedChange: (String) -> Unit,
    saving: String,
    onSavingChange: (String) -> Unit,
    message: String,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Daily Expense Tracker",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(8.dp))
        Text("Let's set up your budget")

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = income,
            onValueChange = onIncomeChange,
            label = { Text("Monthly Income (₹)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = fixed,
            onValueChange = onFixedChange,
            label = { Text("Fixed Monthly Expenses (₹)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = saving,
            onValueChange = onSavingChange,
            label = { Text("Saving Target (%)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Setup & Continue")
        }

        if (message.isNotBlank()) {
            Text(message, modifier = Modifier.padding(top = 12.dp))
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
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
                        dateLabel(transaction.timestamp),
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
