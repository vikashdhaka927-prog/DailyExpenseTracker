package com.example.dailyexpensetracker

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

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

            val records = JSONArray(
                prefs.getString("saved_notifications", "[]") ?: "[]"
            )

            val id = "$packageName|${sbn.key}|${sbn.postTime}"

            for (i in 0 until records.length()) {
                if (records.optJSONObject(i)?.optString("id") == id) {
                    return
                }
            }

            val appName = try {
                val info = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(info).toString()
            } catch (_: Exception) {
                packageName
            }

            val searchable = "$packageName $appName $title $text"
                .lowercase()

            val keywords = listOf(
                "upi", "credited", "debited", "debit", "credit",
                "transaction", "txn", "payment", "paid", "received",
                "bank", "account", "a/c", "rs.", "inr", "₹",
                "gpay", "google pay", "phonepe", "paytm",
                "bhim", "imps", "neft", "rtgs", "withdrawal",
                "spent", "purchase", "refund", "balance"
            )

            val category = if (
                keywords.any { searchable.contains(it) }
            ) "Bank/UPI" else "Other"

            records.put(JSONObject().apply {
                put("id", id)
                put("app", appName)
                put("packageName", packageName)
                put("title", title)
                put("text", text)
                put("timestamp", sbn.postTime)
                put("category", category)
            })

            val trimmed = JSONArray()
            val start = (records.length() - 2000).coerceAtLeast(0)

            for (i in start until records.length()) {
                trimmed.put(records.getJSONObject(i))
            }

            prefs.edit()
                .putString("saved_notifications", trimmed.toString())
                .apply()

        } catch (_: Exception) {
            // Ek notification process na ho to app crash na kare.
        }
    }
}
