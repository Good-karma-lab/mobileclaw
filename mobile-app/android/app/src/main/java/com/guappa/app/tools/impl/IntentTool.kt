package com.guappa.app.tools.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class IntentTool : Tool {
    override val name = "fire_intent"
    override val description = "Fire an arbitrary Android Intent with configurable action, data URI, MIME type, package, extras, and category"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "action": {
                    "type": "string",
                    "description": "The Intent action (e.g. android.intent.action.VIEW)"
                },
                "data": {
                    "type": "string",
                    "description": "Data URI for the Intent"
                },
                "type": {
                    "type": "string",
                    "description": "MIME type (e.g. image/jpeg)"
                },
                "package_name": {
                    "type": "string",
                    "description": "Target package name to restrict the Intent to a specific app"
                },
                "extras": {
                    "type": "object",
                    "description": "Key-value map of extras to attach to the Intent"
                },
                "category": {
                    "type": "string",
                    "description": "Intent category (e.g. android.intent.category.BROWSABLE)"
                }
            },
            "required": ["action"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val action = params.optString("action", "")
        if (action.isEmpty()) {
            return ToolResult.Error("Intent action is required.", "INVALID_PARAMS")
        }

        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                val dataUri = params.optString("data", "")
                val mimeType = params.optString("type", "")

                if (dataUri.isNotEmpty() && mimeType.isNotEmpty()) {
                    setDataAndType(Uri.parse(dataUri), mimeType)
                } else if (dataUri.isNotEmpty()) {
                    data = Uri.parse(dataUri)
                } else if (mimeType.isNotEmpty()) {
                    type = mimeType
                }

                val packageName = params.optString("package_name", "")
                if (packageName.isNotEmpty()) {
                    setPackage(packageName)
                }

                val category = params.optString("category", "")
                if (category.isNotEmpty()) {
                    addCategory(category)
                }

                val extras = params.optJSONObject("extras")
                if (extras != null) {
                    val keys = extras.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = extras.get(key)
                        when (value) {
                            is String -> putExtra(key, value)
                            is Int -> putExtra(key, value)
                            is Long -> putExtra(key, value)
                            is Double -> putExtra(key, value)
                            is Boolean -> putExtra(key, value)
                            else -> putExtra(key, value.toString())
                        }
                    }
                }
            }
            context.startActivity(intent)
            ToolResult.Success("Fired Intent with action: $action")
        } catch (e: Exception) {
            ToolResult.Error("Failed to fire Intent: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
