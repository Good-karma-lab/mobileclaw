package com.guappa.app.tools.impl

import android.content.Context
import android.net.Uri
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONArray
import org.json.JSONObject

class ReadSmsTool : Tool {
    override val name = "read_sms"
    override val description = "Read SMS messages from the device inbox"
    override val requiredPermissions = listOf("READ_SMS")
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "limit": {
                    "type": "integer",
                    "description": "Maximum number of messages to read (default: 10, max: 50)"
                },
                "from": {
                    "type": "string",
                    "description": "Filter by sender phone number"
                }
            },
            "required": []
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val limit = params.optInt("limit", 10).coerceIn(1, 50)
        val fromFilter = params.optString("from", "")

        return try {
            val uri = Uri.parse("content://sms/inbox")
            val selection = if (fromFilter.isNotEmpty()) "address = ?" else null
            val selectionArgs = if (fromFilter.isNotEmpty()) arrayOf(fromFilter) else null

            val cursor = context.contentResolver.query(
                uri,
                arrayOf("_id", "address", "body", "date", "read"),
                selection,
                selectionArgs,
                "date DESC"
            )

            val messages = JSONArray()
            cursor?.use {
                var count = 0
                while (it.moveToNext() && count < limit) {
                    val msg = JSONObject()
                    msg.put("id", it.getString(it.getColumnIndexOrThrow("_id")))
                    msg.put("from", it.getString(it.getColumnIndexOrThrow("address")))
                    msg.put("body", it.getString(it.getColumnIndexOrThrow("body")))
                    msg.put("date", it.getLong(it.getColumnIndexOrThrow("date")))
                    msg.put("read", it.getInt(it.getColumnIndexOrThrow("read")) == 1)
                    messages.put(msg)
                    count++
                }
            }

            val data = JSONObject()
            data.put("messages", messages)
            data.put("count", messages.length())

            ToolResult.Success(
                content = "Found ${messages.length()} SMS message(s)",
                data = data
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to read SMS: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
