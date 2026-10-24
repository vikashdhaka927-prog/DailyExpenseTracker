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
        } catch (_: Exception) {
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

