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
