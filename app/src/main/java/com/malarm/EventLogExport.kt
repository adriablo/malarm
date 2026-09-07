package com.malarm

/** CSV export of the event log (RFC 4180 quoting). Saved via the system file
 * picker, so arbitrarily large logs never touch the binder transaction limit
 * that the old ACTION_SEND share would hit. */
object EventLogExport {

    fun fileName(nowMillis: Long = System.currentTimeMillis()): String {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss", java.util.Locale.US)
            .format(java.util.Date(nowMillis))
        return "malarm-log-$stamp.csv"
    }

    fun toCsv(events: List<AlarmEvent>): String = buildString {
        appendLine("timestamp,type,alarmId,label,details")
        for (event in events) {
            appendLine(
                listOf(
                    AlarmFormatter.timestamp(event.timestamp),
                    event.type.name,
                    event.alarmId?.toString() ?: "",
                    event.label ?: "",
                    event.details ?: "",
                ).joinToString(",") { escape(it) },
            )
        }
    }

    private fun escape(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}
