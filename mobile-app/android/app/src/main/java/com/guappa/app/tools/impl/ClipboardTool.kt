package com.guappa.app.tools.impl

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class ClipboardTool : Tool {
    override val name = "clipboard"
    override val description = "Get or set the device clipboard content"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "action": {
                    "type": "string",
                    "enum": ["get", "set"],
                    "description": "Action: 'get' to read clipboard, 'set' to write to clipboard"
                },
                "text": {
                    "type": "string",
                    "description": "Text to copy to clipboard. Required for 'set' action."
                },
                "label": {
                    "type": "string",
                    "description": "Optional label for the clipboard data. Default 'Guappa'."
                }
            },
            "required": ["action"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val action = params.optString("action", "")
        if (action.isEmpty()) {
            return ToolResult.Error("Action is required.", "INVALID_PARAMS")
        }

        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return ToolResult.Error("Clipboard service not available.", "EXECUTION_ERROR")

        return try {
            when (action) {
                "get" -> getClipboard(clipboardManager)
                "set" -> setClipboard(params, clipboardManager)
                else -> ToolResult.Error("Invalid action: $action. Use 'get' or 'set'.", "INVALID_PARAMS")
            }
        } catch (e: Exception) {
            ToolResult.Error("Clipboard operation failed: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun getClipboard(clipboardManager: ClipboardManager): ToolResult {
        if (!clipboardManager.hasPrimaryClip()) {
            return ToolResult.Success(
                content = "Clipboard is empty.",
                data = JSONObject().apply {
                    put("has_content", false)
                }
            )
        }

        val clip = clipboardManager.primaryClip
        if (clip == null || clip.itemCount == 0) {
            return ToolResult.Success(
                content = "Clipboard is empty.",
                data = JSONObject().apply {
                    put("has_content", false)
                }
            )
        }

        val text = clip.getItemAt(0).text?.toString() ?: ""
        val label = clip.description?.label?.toString() ?: ""

        val data = JSONObject().apply {
            put("has_content", true)
            put("text", text)
            put("label", label)
            put("item_count", clip.itemCount)
        }

        return if (text.isNotEmpty()) {
            ToolResult.Success(
                content = "Clipboard content: $text",
                data = data
            )
        } else {
            ToolResult.Success(
                content = "Clipboard has non-text content.",
                data = data
            )
        }
    }

    private fun setClipboard(params: JSONObject, clipboardManager: ClipboardManager): ToolResult {
        val text = params.optString("text", "")
        if (text.isEmpty()) {
            return ToolResult.Error("Text is required for set action.", "INVALID_PARAMS")
        }

        val label = params.optString("label", "Guappa")
        val clip = ClipData.newPlainText(label, text)
        clipboardManager.setPrimaryClip(clip)

        return ToolResult.Success(
            content = "Copied ${text.length} characters to clipboard."
        )
    }
}
