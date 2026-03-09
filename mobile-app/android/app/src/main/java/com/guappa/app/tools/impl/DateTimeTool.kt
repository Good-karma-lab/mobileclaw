package com.guappa.app.tools.impl

import android.content.Context
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class DateTimeTool : Tool {
    override val name = "date_time"
    override val description = "Get current date/time info or perform date arithmetic (add/subtract days, hours, etc.) and timezone conversion"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "action": {
                    "type": "string",
                    "description": "Action to perform: now, add, subtract, convert_timezone (default: now)",
                    "enum": ["now", "add", "subtract", "convert_timezone"]
                },
                "amount": {
                    "type": "integer",
                    "description": "Amount to add or subtract (for add/subtract actions)"
                },
                "unit": {
                    "type": "string",
                    "description": "Unit for add/subtract: years, months, weeks, days, hours, minutes, seconds",
                    "enum": ["years", "months", "weeks", "days", "hours", "minutes", "seconds"]
                },
                "timezone": {
                    "type": "string",
                    "description": "Target timezone (e.g. America/New_York, Europe/London, Asia/Tokyo). Used for now and convert_timezone actions."
                },
                "date_string": {
                    "type": "string",
                    "description": "Input date in ISO 8601 format (yyyy-MM-ddTHH:mm:ss) for add/subtract/convert_timezone. Defaults to current time if not provided."
                }
            },
            "required": []
        }
    """.trimIndent())

    private val isoFormat = "yyyy-MM-dd'T'HH:mm:ss"
    private val displayFormat = "EEEE, MMMM d, yyyy 'at' HH:mm:ss z"

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val action = params.optString("action", "now")

        return try {
            when (action) {
                "now" -> handleNow(params)
                "add" -> handleArithmetic(params, add = true)
                "subtract" -> handleArithmetic(params, add = false)
                "convert_timezone" -> handleConvertTimezone(params)
                else -> ToolResult.Error("Unknown action: $action. Use now, add, subtract, or convert_timezone.", "INVALID_PARAMS")
            }
        } catch (e: Exception) {
            ToolResult.Error("Date/time operation failed: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun handleNow(params: JSONObject): ToolResult {
        val timezoneStr = params.optString("timezone", "")
        val tz = if (timezoneStr.isNotEmpty()) {
            val zone = TimeZone.getTimeZone(timezoneStr)
            // TimeZone.getTimeZone returns GMT for invalid IDs
            if (zone.id == "GMT" && timezoneStr != "GMT" && timezoneStr != "UTC") {
                return ToolResult.Error("Unknown timezone: $timezoneStr", "INVALID_PARAMS")
            }
            zone
        } else {
            TimeZone.getDefault()
        }

        val cal = Calendar.getInstance(tz)

        val isoFormatter = SimpleDateFormat(isoFormat, Locale.US).apply { timeZone = tz }
        val displayFormatter = SimpleDateFormat(displayFormat, Locale.US).apply { timeZone = tz }
        val dateOnlyFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }
        val timeOnlyFormatter = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = tz }

        val data = JSONObject().apply {
            put("iso", isoFormatter.format(cal.time))
            put("display", displayFormatter.format(cal.time))
            put("date", dateOnlyFormatter.format(cal.time))
            put("time", timeOnlyFormatter.format(cal.time))
            put("timezone", tz.id)
            put("timezone_display_name", tz.getDisplayName(tz.inDaylightTime(cal.time), TimeZone.LONG, Locale.US))
            put("utc_offset_hours", tz.getOffset(cal.timeInMillis) / 3600000.0)
            put("day_of_week", SimpleDateFormat("EEEE", Locale.US).apply { timeZone = tz }.format(cal.time))
            put("day_of_week_number", cal.get(Calendar.DAY_OF_WEEK))
            put("week_number", cal.get(Calendar.WEEK_OF_YEAR))
            put("day_of_year", cal.get(Calendar.DAY_OF_YEAR))
            put("year", cal.get(Calendar.YEAR))
            put("month", cal.get(Calendar.MONTH) + 1)
            put("day", cal.get(Calendar.DAY_OF_MONTH))
            put("hour", cal.get(Calendar.HOUR_OF_DAY))
            put("minute", cal.get(Calendar.MINUTE))
            put("second", cal.get(Calendar.SECOND))
            put("timestamp_ms", cal.timeInMillis)
            put("is_dst", tz.inDaylightTime(cal.time))
            put("is_leap_year", isLeapYear(cal.get(Calendar.YEAR)))
        }

        val content = buildString {
            appendLine(displayFormatter.format(cal.time))
            appendLine("Date: ${dateOnlyFormatter.format(cal.time)}")
            appendLine("Time: ${timeOnlyFormatter.format(cal.time)}")
            appendLine("Timezone: ${tz.id} (UTC${formatOffset(tz.getOffset(cal.timeInMillis))})")
            appendLine("Day of week: ${SimpleDateFormat("EEEE", Locale.US).apply { timeZone = tz }.format(cal.time)}")
            appendLine("Week number: ${cal.get(Calendar.WEEK_OF_YEAR)}")
            append("Day of year: ${cal.get(Calendar.DAY_OF_YEAR)}")
        }

        return ToolResult.Success(content = content, data = data)
    }

    private fun handleArithmetic(params: JSONObject, add: Boolean): ToolResult {
        val amount = params.optInt("amount", 0)
        if (amount == 0) {
            return ToolResult.Error("amount is required and must be non-zero for ${if (add) "add" else "subtract"}.", "INVALID_PARAMS")
        }

        val unit = params.optString("unit", "")
        if (unit.isEmpty()) {
            return ToolResult.Error("unit is required for ${if (add) "add" else "subtract"}.", "INVALID_PARAMS")
        }

        val calendarField = when (unit) {
            "years" -> Calendar.YEAR
            "months" -> Calendar.MONTH
            "weeks" -> Calendar.WEEK_OF_YEAR
            "days" -> Calendar.DAY_OF_YEAR
            "hours" -> Calendar.HOUR_OF_DAY
            "minutes" -> Calendar.MINUTE
            "seconds" -> Calendar.SECOND
            else -> return ToolResult.Error("Invalid unit: $unit", "INVALID_PARAMS")
        }

        val timezoneStr = params.optString("timezone", "")
        val tz = if (timezoneStr.isNotEmpty()) TimeZone.getTimeZone(timezoneStr) else TimeZone.getDefault()

        val cal = Calendar.getInstance(tz)

        // Parse optional input date
        val dateString = params.optString("date_string", "")
        if (dateString.isNotEmpty()) {
            val parser = SimpleDateFormat(isoFormat, Locale.US).apply { timeZone = tz }
            val parsed = parser.parse(dateString)
                ?: return ToolResult.Error("Invalid date_string format. Use: yyyy-MM-ddTHH:mm:ss", "INVALID_PARAMS")
            cal.time = parsed
        }

        val originalTime = cal.time
        val direction = if (add) amount else -amount
        cal.add(calendarField, direction)

        val isoFormatter = SimpleDateFormat(isoFormat, Locale.US).apply { timeZone = tz }
        val displayFormatter = SimpleDateFormat(displayFormat, Locale.US).apply { timeZone = tz }

        val op = if (add) "+" else "-"

        val data = JSONObject().apply {
            put("original", isoFormatter.format(originalTime))
            put("result", isoFormatter.format(cal.time))
            put("result_display", displayFormatter.format(cal.time))
            put("operation", if (add) "add" else "subtract")
            put("amount", amount)
            put("unit", unit)
            put("timezone", tz.id)
            put("day_of_week", SimpleDateFormat("EEEE", Locale.US).apply { timeZone = tz }.format(cal.time))
            put("week_number", cal.get(Calendar.WEEK_OF_YEAR))
            put("timestamp_ms", cal.timeInMillis)
        }

        val content = buildString {
            appendLine("${isoFormatter.format(originalTime)} $op $amount $unit =")
            appendLine(displayFormatter.format(cal.time))
            append("(${isoFormatter.format(cal.time)})")
        }

        return ToolResult.Success(content = content, data = data)
    }

    private fun handleConvertTimezone(params: JSONObject): ToolResult {
        val targetTimezoneStr = params.optString("timezone", "")
        if (targetTimezoneStr.isEmpty()) {
            return ToolResult.Error("timezone is required for convert_timezone.", "INVALID_PARAMS")
        }

        val targetTz = TimeZone.getTimeZone(targetTimezoneStr)
        if (targetTz.id == "GMT" && targetTimezoneStr != "GMT" && targetTimezoneStr != "UTC") {
            return ToolResult.Error("Unknown timezone: $targetTimezoneStr", "INVALID_PARAMS")
        }

        val sourceTz = TimeZone.getDefault()

        val cal = Calendar.getInstance(sourceTz)

        // Parse optional input date
        val dateString = params.optString("date_string", "")
        if (dateString.isNotEmpty()) {
            val parser = SimpleDateFormat(isoFormat, Locale.US).apply { timeZone = sourceTz }
            val parsed = parser.parse(dateString)
                ?: return ToolResult.Error("Invalid date_string format. Use: yyyy-MM-ddTHH:mm:ss", "INVALID_PARAMS")
            cal.time = parsed
        }

        val sourceFormatter = SimpleDateFormat(displayFormat, Locale.US).apply { timeZone = sourceTz }
        val targetFormatter = SimpleDateFormat(displayFormat, Locale.US).apply { timeZone = targetTz }
        val sourceIso = SimpleDateFormat(isoFormat, Locale.US).apply { timeZone = sourceTz }
        val targetIso = SimpleDateFormat(isoFormat, Locale.US).apply { timeZone = targetTz }

        val data = JSONObject().apply {
            put("source_timezone", sourceTz.id)
            put("target_timezone", targetTz.id)
            put("source_time", sourceIso.format(cal.time))
            put("target_time", targetIso.format(cal.time))
            put("source_display", sourceFormatter.format(cal.time))
            put("target_display", targetFormatter.format(cal.time))
            put("source_utc_offset_hours", sourceTz.getOffset(cal.timeInMillis) / 3600000.0)
            put("target_utc_offset_hours", targetTz.getOffset(cal.timeInMillis) / 3600000.0)
            put("hour_difference", (targetTz.getOffset(cal.timeInMillis) - sourceTz.getOffset(cal.timeInMillis)) / 3600000.0)
        }

        val content = buildString {
            appendLine("Source (${sourceTz.id}): ${sourceFormatter.format(cal.time)}")
            appendLine("Target (${targetTz.id}): ${targetFormatter.format(cal.time)}")
            val diffHours = (targetTz.getOffset(cal.timeInMillis) - sourceTz.getOffset(cal.timeInMillis)) / 3600000.0
            val sign = if (diffHours >= 0) "+" else ""
            append("Difference: $sign${diffHours}h")
        }

        return ToolResult.Success(content = content, data = data)
    }

    private fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }

    private fun formatOffset(offsetMs: Int): String {
        val hours = offsetMs / 3600000
        val minutes = Math.abs(offsetMs % 3600000) / 60000
        val sign = if (hours >= 0) "+" else ""
        return if (minutes > 0) "$sign$hours:${String.format("%02d", minutes)}" else "$sign$hours"
    }
}
