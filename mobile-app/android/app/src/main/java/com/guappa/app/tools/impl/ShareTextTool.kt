package com.guappa.app.tools.impl

import android.content.Context
import android.content.Intent
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class ShareTextTool : Tool {
    override val name = "share_text"
    override val description = "Share text content via the Android share dialog"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "text": {
                    "type": "string",
                    "description": "The text content to share"
                },
                "subject": {
                    "type": "string",
                    "description": "Optional subject line (used by email apps)"
                }
            },
            "required": ["text"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val text = params.optString("text", "")
        if (text.isEmpty()) {
            return ToolResult.Error("Text content is required.", "INVALID_PARAMS")
        }

        val subject = params.optString("subject", "")

        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                if (subject.isNotEmpty()) {
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Share via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            ToolResult.Success("Share dialog opened with ${text.length} chars of text")
        } catch (e: Exception) {
            ToolResult.Error("Failed to share text: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
