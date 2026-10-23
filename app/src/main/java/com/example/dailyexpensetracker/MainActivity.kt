package com.example.dailyexpensetracker

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("expense_tracker", Context.MODE_PRIVATE)

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
fun ExpenseApp(prefs: android.content.SharedPreferences) {
    var monthlyIncome by remember {
        mutableStateOf(prefs.getString("income", "") ?: "")
    }
    var fixedExpenses by remember {
        mutableStateOf(prefs.getString("fixed", "") ?: "")
    }
    var savingPercent by remember {
        mutableStateOf(prefs.getString("saving", "20") ?: "20")
    }
    var todayExpense by remember {
        mutableDoubleStateOf(0.0)
    }
    var expenseInput by remember { mutableStateOf("") }
    var setupComplete by remember {
        mutableStateOf(prefs.getBoolean("setup_done", false))
    }
    var message by remember { mutableStateOf("") }

    val income = monthlyIncome.toDoubleOrNull() ?: 0.0
    val fixed = fixedExpenses.toDoubleOrNull() ?: 0.0
    val saving = (savingPercent.toDoubleOrNull() ?: 0.0)
        .coerceIn(0.0, 100.0)

    val available = max(0.0, income - fixed - income * saving / 100.0)
    val dailyLimit = available / 30.0

    val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val savedDate = prefs.getString("expense_date", "") ?: ""

    LaunchedEffect(todayKey) {
        if (savedDate == todayKey) {
            todayExpense = prefs.getFloat("today_expense", 0f).toDouble()
        } else {
            todayExpense = 0.0
            prefs.edit()
                .putString("expense_date", todayKey)
                .putFloat("today_expense", 0f)
                .apply()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Daily Expense Tracker",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(8.dp))

        Text("Apne kharch par rakhein nazar!")

        Spacer(Modifier.height(24.dp))

        if (!setupComplete) {
            Text(
                "Apna budget set karein",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = monthlyIncome,
                onValueChange = { monthlyIncome = it },
                label = { Text("Monthly income (₹)") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = fixedExpenses,
                onValueChange = { fixedExpenses = it },
                label = { Text("Monthly fixed expenses (₹)") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = savingPercent,
                onValueChange = { savingPercent = it },
                label = { Text("Saving target (%)") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    val parsedIncome = monthlyIncome.toDoubleOrNull()
                    val parsedFixed = fixedExpenses.toDoubleOrNull()
                    val parsedSaving = savingPercent.toDoubleOrNull()

                    when {
                        parsedIncome == null || parsedIncome <= 0 ->
                            message = "Sahi monthly income bharein."

                        parsedFixed == null || parsedFixed < 0 ->
                            message = "Fixed expenses sahi bharein."

                        parsedFixed > parsedIncome ->
                            message = "Fixed expenses income se zyada hain."

                        parsedSaving == null ||
                            parsedSaving !in 0.0..100.0 ->
                            message = "Saving target 0 se 100% ke beech bharein."

                        else -> {
                            prefs.edit()
                                .putString("income", monthlyIncome)
                                .putString("fixed", fixedExpenses)
                                .putString("saving", savingPercent)
                                .putBoolean("setup_done", true)
                                .apply()

                            setupComplete = true
                            message = "Setup save ho gaya!"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Setup aur Dashboard kholein")
            }

            if (message.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(message)
            }
        } else {
            Text(
                "Aapka Dashboard",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(Modifier.height(20.dp))

            InfoCard("Monthly Income", income)
            InfoCard("Fixed Expenses", fixed)
            InfoCard("Saving Target", income * saving / 100.0)
            InfoCard("Available Monthly Budget", available)
            InfoCard("Aaj ki kharch limit", dailyLimit)
            InfoCard("Aaj ka kharch", todayExpense)
            InfoCard("Aaj bacha budget", dailyLimit - todayExpense)

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = expenseInput,
                onValueChange = { expenseInput = it },
                label = { Text("Naya kharch (₹)") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    val amount = expenseInput.toDoubleOrNull()
                    if (amount == null || amount <= 0) {
                        message = "Kharch ki sahi rakam bharein."
                    } else {
                        todayExpense += amount
                        prefs.edit()
                            .putString("expense_date", todayKey)
                            .putFloat(
                                "today_expense",
                                todayExpense.toFloat()
                            )
                            .apply()
                        expenseInput = ""
                        message = "Kharch save ho gaya."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Kharch Save Karein")
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    monthlyIncome =
                        prefs.getString("income", "") ?: ""
                    fixedExpenses =
                        prefs.getString("fixed", "") ?: ""
                    savingPercent =
                        prefs.getString("saving", "20") ?: "20"
                    setupComplete = false
                    message = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Budget Setup Badlein")
            }

            if (message.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(message)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Note: Daily limit abhi 30 din ke hisaab se calculate hoti hai.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun InfoCard(title: String, amount: Double) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, modifier = Modifier.weight(1f))
            Text("₹${"%.2f".format(Locale.US, amount)}")
        }
    }
}
