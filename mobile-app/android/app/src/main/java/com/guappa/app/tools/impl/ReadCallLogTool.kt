package com.guappa.app.tools.impl

import android.content.Context
import android.provider.CallLog
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReadCallLogTool : Tool {
    override val name = "read_call_log"
    override val description = "Read recent call history from the device including incoming, outgoing, and missed calls"
    override val requiredPermissions = listOf("READ_CALL_LOG")
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "limit": {
                    "type": "integer",
                    "description": "Maximum number of call records to return (default: 20, max: 100)"
                },
                "type": {
                    "type": "string",
                    "description": "Filter by call type: 'incoming', 'outgoing', 'missed', or 'all' (default: 'all')"
                },
                "number": {
                    "type": "string",
                    "description": "Filter by phone number (optional)"
                }
            },
            "required": []
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val limit = params.optInt("limit", 20).coerceIn(1, 100)
        val typeFilter = params.optString("type", "all")
        val numberFilter = params.optString("number", "")

        return try {
            val selection = buildString {
                val conditions = mutableListOf<String>()

                when (typeFilter) {
                    "incoming" -> conditions.add("${CallLog.Calls.TYPE} = ${CallLog.Calls.INCOMING_TYPE}")
                    "outgoing" -> conditions.add("${CallLog.Calls.TYPE} = ${CallLog.Calls.OUTGOING_TYPE}")
                    "missed" -> conditions.add("${CallLog.Calls.TYPE} = ${CallLog.Calls.MISSED_TYPE}")
                }

                if (numberFilter.isNotEmpty()) {
                    conditions.add("${CallLog.Calls.NUMBER} LIKE ?")
                }

                append(conditions.joinToString(" AND "))
            }.ifEmpty { null }

            val selectionArgs = if (numberFilter.isNotEmpty()) {
                arrayOf("%$numberFilter%")
            } else null

            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
                ),
                selection,
                selectionArgs,
                "${CallLog.Calls.DATE} DESC"
            )

            val calls = JSONArray()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            cursor?.use {
                var count = 0
                while (it.moveToNext() && count < limit) {
                    val callType = it.getInt(it.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                    val callTypeStr = when (callType) {
                        CallLog.Calls.INCOMING_TYPE -> "incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                        CallLog.Calls.MISSED_TYPE -> "missed"
                        CallLog.Calls.REJECTED_TYPE -> "rejected"
                        CallLog.Calls.BLOCKED_TYPE -> "blocked"
                        else -> "unknown"
                    }

                    val dateMs = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DATE))
                    val duration = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DURATION))

                    val call = JSONObject().apply {
                        put("id", it.getString(it.getColumnIndexOrThrow(CallLog.Calls._ID)))
                        put("number", it.getString(it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)) ?: "")
                        put("name", it.getString(it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)) ?: "")
                        put("type", callTypeStr)
                        put("date", dateFormat.format(Date(dateMs)))
                        put("date_ms", dateMs)
                        put("duration_seconds", duration)
                    }
                    calls.put(call)
                    count++
                }
            }

            val data = JSONObject().apply {
                put("calls", calls)
                put("count", calls.length())
            }

            val summary = buildString {
                append("Found ${calls.length()} call record(s)")
                if (typeFilter != "all") append(" (type: $typeFilter)")
                if (numberFilter.isNotEmpty()) append(" for number $numberFilter")
            }

            ToolResult.Success(content = summary, data = data)
        } catch (e: Exception) {
            ToolResult.Error("Failed to read call log: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
