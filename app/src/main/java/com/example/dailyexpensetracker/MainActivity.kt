// ===== IMPORTS (MainActivity.kt ke top mein daalo, duplicate hata dena) =====
import android.content.SharedPreferences
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ===== DATA CLASS (agar already file mein hai to dobara mat daalo) =====
data class SavedAppNotification(
    val id: String,
    val app: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val category: String
)

// ===== DATE HELPER (agar already file mein hai to dobara mat daalo) =====
fun dateLabel(timestamp: Long): String {
    return try {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            .format(Date(timestamp))
    } catch (e: Exception) {
        ""
    }
}

// ===== MAIN SCREEN =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(prefs: SharedPreferences) {
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var refreshCount by remember { mutableIntStateOf(0) }

    val notifications = remember(refreshCount) {
        val result = mutableListOf<SavedAppNotification>()
        try {
            val array = JSONArray(
                prefs.getString("saved_notifications", "[]") ?: "[]"
            )
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                result.add(
                    SavedAppNotification(
                        id = item.optString("id"),
                        app = item.optString("app", item.optString("packageName", "Unknown app")),
                        title = item.optString("title"),
                        text = item.optString("text"),
                        timestamp = item.optLong("timestamp"),
                        category = item.optString("category", "Other")
                    )
                )
            }
        } catch (e: Exception) {
            // ignore bad data
        }
        result.sortedByDescending { it.timestamp }
    }

    val filtered = notifications.filter { item ->
        val matchesFilter = when (filter) {
            "Truecaller" -> item.app.contains("truecaller", ignoreCase = true) ||
                item.id.contains("com.truecaller", ignoreCase = true)
            "Bank/UPI" -> item.category == "Bank/UPI"
            "Other" -> item.category == "Other"
            else -> true
        }
        val searchable = "${item.app} ${item.title} ${item.text} ${item.id}"
        matchesFilter && searchable.contains(search, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Notifications", style = MaterialTheme.typography.headlineSmall)
        Text("Saved notifications: ${notifications.size}")

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Search notifications") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("All", "Truecaller", "Bank/UPI", "Other").forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = { Text(option) }
                )
            }
        }

        Button(
            onClick = { refreshCount++ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Refresh")
        }

        Text("Showing ${filtered.size} notifications")
        Spacer(Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            Text(
                "No saved notifications yet. Check Notification Access, then tap Refresh after a new notification arrives.",
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            filtered.forEach { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(item.app, style = MaterialTheme.typography.titleMedium)
                        if (item.title.isNotBlank()) Text(item.title)
                        if (item.text.isNotBlank()) Text(item.text)
                        Text(
                            dateLabel(item.timestamp),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            item.category,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}
