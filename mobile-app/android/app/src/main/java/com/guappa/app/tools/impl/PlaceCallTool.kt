package com.guappa.app.tools.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class PlaceCallTool : Tool {
    override val name = "place_call"
    override val description = "Place a phone call to a given number"
    override val requiredPermissions = listOf("CALL_PHONE")
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "phone_number": {
                    "type": "string",
                    "description": "The phone number to call"
                }
            },
            "required": ["phone_number"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val phoneNumber = params.optString("phone_number", "")
        if (phoneNumber.isEmpty()) {
            return ToolResult.Error("Phone number is required.", "INVALID_PARAMS")
        }

        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${Uri.encode(phoneNumber)}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.Success("Placing call to $phoneNumber")
        } catch (e: Exception) {
            ToolResult.Error("Failed to place call: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
