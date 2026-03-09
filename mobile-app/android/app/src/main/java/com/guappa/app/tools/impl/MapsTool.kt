package com.guappa.app.tools.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class MapsTool : Tool {
    override val name = "maps"
    override val description = "Open maps for navigation, search, or directions using geo: Intent and Google Maps navigation URI"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "action": {
                    "type": "string",
                    "description": "Action to perform: navigate, search, or directions",
                    "enum": ["navigate", "search", "directions"]
                },
                "destination": {
                    "type": "string",
                    "description": "Destination address or place name"
                },
                "origin": {
                    "type": "string",
                    "description": "Origin address (for directions action; defaults to current location)"
                },
                "mode": {
                    "type": "string",
                    "description": "Travel mode: driving, walking, bicycling, or transit",
                    "enum": ["driving", "walking", "bicycling", "transit"]
                }
            },
            "required": ["action", "destination"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val action = params.optString("action", "")
        val destination = params.optString("destination", "")

        if (action.isEmpty()) {
            return ToolResult.Error("Action is required (navigate, search, or directions).", "INVALID_PARAMS")
        }
        if (destination.isEmpty()) {
            return ToolResult.Error("Destination is required.", "INVALID_PARAMS")
        }

        val mode = params.optString("mode", "driving")
        val modeCode = when (mode) {
            "driving" -> "d"
            "walking" -> "w"
            "bicycling" -> "b"
            "transit" -> "r"
            else -> "d"
        }

        return try {
            val uri = when (action) {
                "navigate" -> {
                    val encoded = Uri.encode(destination)
                    Uri.parse("google.navigation:q=$encoded&mode=$modeCode")
                }
                "search" -> {
                    val encoded = Uri.encode(destination)
                    Uri.parse("geo:0,0?q=$encoded")
                }
                "directions" -> {
                    val origin = params.optString("origin", "")
                    val encodedDest = Uri.encode(destination)
                    if (origin.isNotEmpty()) {
                        val encodedOrigin = Uri.encode(origin)
                        Uri.parse("https://www.google.com/maps/dir/?api=1&origin=$encodedOrigin&destination=$encodedDest&travelmode=$mode")
                    } else {
                        Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$encodedDest&travelmode=$mode")
                    }
                }
                else -> return ToolResult.Error(
                    "Invalid action: $action. Use navigate, search, or directions.",
                    "INVALID_PARAMS"
                )
            }

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.Success("Opened maps with action '$action' to: $destination")
        } catch (e: Exception) {
            ToolResult.Error("Failed to open maps: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
