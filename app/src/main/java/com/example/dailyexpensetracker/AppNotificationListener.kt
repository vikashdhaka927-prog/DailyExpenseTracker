package com.example.dailyexpensetracker

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class AppNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        try {
            val extras = sbn.notification.extras ?: return

            val title = extras
                .getCharSequence(Notification.EXTRA_TITLE)
                ?.toString()?.trim().orEmpty()

            val text = (
                extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                    ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                )?.toString()?.trim().orEmpty()

            if (title.isBlank() && text.isBlank()) return

            val packageName = sbn.packageName
            val prefs = getSharedPreferences(
                "expense_tracker",
                MODE_PRIVATE
            )

            val notificationId = "$packageName|${sbn.key}|${sbn.postTime}"

            // Save all notifications
            val notifications = JSONArray(
                prefs.getString("saved_notifications", "[]") ?: "[]"
            )

            for (i in 0 until notifications.length()) {
                if (notifications.optJSONObject(i)
                        ?.optString("id") == notificationId) {
                    return
                }
            }

            val appName = try {
                val info = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(info).toString()
            } catch (_: Exception) {
                packageName
            }

            val fullText = "$title $text"
            val lower = fullText.lowercase(Locale.ROOT)

            val category = if (
                listOf(
                    "upi", "bank", "credited", "debited", "payment",
                    "transaction", "phonepe", "paytm", "gpay"
                ).any { lower.contains(it) }
            ) "Bank/UPI" else "Other"

            notifications.put(JSONObject().apply {
                put("id", notificationId)
                put("app", appName)
                put("packageName", packageName)
                put("title", title)
                put("text", text)
                put("timestamp", sbn.postTime)
                put("category", category)
            })

            // Keep the latest 2000 notifications
            val savedNotifications = JSONArray()
            val start = (notifications.length() - 2000).coerceAtLeast(0)

            for (i in start until notifications.length()) {
                savedNotifications.put(notifications.getJSONObject(i))
            }

            prefs.edit()
                .putString("saved_notifications", savedNotifications.toString())
                .apply()

            // Auto-add only clear debit notifications
            val creditWords = listOf(
                "credited", "credit received", "money received",
                "received from", "refund received"
            )

            val debitWords = listOf(
                "debited", "debit", "spent", "paid",
                "purchase", "withdrawn", "sent to"
            )

            val isCredit = creditWords.any { lower.contains(it) }
            val isDebit = debitWords.any { lower.contains(it) }

            if (isCredit || !isDebit) return

            // Extract amount only when currency is clearly mentioned
            val amountRegex = Regex(
                """(?:₹|rs\.?|inr)\s*([0-9,]+(?:\.[0-9]{1,2})?)""",
                RegexOption.IGNORE_CASE
            )

            val amountText = amountRegex.find(fullText)
                ?.groupValues?.get(1)
                ?: return

            val amount = amountText.replace(",", "").toDoubleOrNull()
                ?: return

            if (!amount.isFinite() || amount <= 0) return

            // Prevent the same notification being added twice
            val transactions = JSONArray(
                prefs.getString("transactions", "[]") ?: "[]"
            )

            for (i in 0 until transactions.length()) {
                if (transactions.optJSONObject(i)
                        ?.optString("notificationId") == notificationId) {
                    return
                }
            }

            transactions.put(JSONObject().apply {
                put("id", sbn.postTime)
                put("amount", amount)
                put("category", "Other")
                put("note", "$appName: $fullText")
                put("timestamp", sbn.postTime)
                put("notificationId", notificationId)
            })

            prefs.edit()
                .putString("transactions", transactions.toString())
                .apply()

        } catch (_: Exception) {
            // Ignore malformed notifications safely.
        }
    }
}
