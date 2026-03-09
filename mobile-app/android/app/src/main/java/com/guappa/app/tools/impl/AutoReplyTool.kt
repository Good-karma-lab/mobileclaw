package com.guappa.app.tools.impl

import android.content.Context
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONArray
import org.json.JSONObject

class AutoReplyTool : Tool {
    override val name = "auto_reply"
    override val description = "Set up, list, or remove auto-reply rules for incoming SMS messages. " +
        "When enabled, the agent will automatically respond to matching messages."
    override val requiredPermissions = listOf("READ_SMS", "SEND_SMS")
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "action": {
                    "type": "string",
                    "description": "Action to perform: 'enable', 'disable', 'list', or 'remove'"
                },
                "rule_id": {
                    "type": "string",
                    "description": "Unique identifier for the auto-reply rule (required for enable/remove)"
                },
                "reply_message": {
                    "type": "string",
                    "description": "The auto-reply message to send (required for enable)"
                },
                "match_sender": {
                    "type": "string",
                    "description": "Only auto-reply to messages from this phone number (optional, all senders if omitted)"
                },
                "match_keyword": {
                    "type": "string",
                    "description": "Only auto-reply to messages containing this keyword (optional)"
                },
                "max_replies": {
                    "type": "integer",
                    "description": "Maximum number of auto-replies to send per sender per hour (default: 1)"
                }
            },
            "required": ["action"]
        }
    """.trimIndent())

    companion object {
        private const val PREFS_NAME = "guappa_auto_reply"
    }

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val action = params.optString("action", "")

        return when (action) {
            "enable" -> enableRule(params, context)
            "disable" -> disableAllRules(context)
            "list" -> listRules(context)
            "remove" -> removeRule(params, context)
            else -> ToolResult.Error(
                "Invalid action: '$action'. Use 'enable', 'disable', 'list', or 'remove'.",
                "INVALID_PARAMS"
            )
        }
    }

    private fun enableRule(params: JSONObject, context: Context): ToolResult {
        val ruleId = params.optString("rule_id", "")
        if (ruleId.isEmpty()) {
            return ToolResult.Error("rule_id is required for enable action.", "INVALID_PARAMS")
        }

        val replyMessage = params.optString("reply_message", "")
        if (replyMessage.isEmpty()) {
            return ToolResult.Error("reply_message is required for enable action.", "INVALID_PARAMS")
        }

        val matchSender = params.optString("match_sender", "")
        val matchKeyword = params.optString("match_keyword", "")
        val maxReplies = params.optInt("max_replies", 1).coerceIn(1, 10)

        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            val rule = JSONObject().apply {
                put("rule_id", ruleId)
                put("reply_message", replyMessage)
                put("match_sender", matchSender)
                put("match_keyword", matchKeyword)
                put("max_replies_per_hour", maxReplies)
                put("enabled", true)
                put("created_at", System.currentTimeMillis())
            }

            prefs.edit().putString("rule_$ruleId", rule.toString()).apply()

            val filterDesc = buildString {
                if (matchSender.isNotEmpty()) append("from $matchSender ")
                if (matchKeyword.isNotEmpty()) append("containing '$matchKeyword' ")
                if (isEmpty()) append("all messages ")
            }.trim()

            ToolResult.Success(
                content = "Auto-reply rule '$ruleId' enabled for $filterDesc. " +
                    "Reply: \"$replyMessage\" (max $maxReplies/hour)",
                data = rule
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to enable auto-reply: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun disableAllRules(context: Context): ToolResult {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()

            var disabledCount = 0
            prefs.all.forEach { (key, value) ->
                if (key.startsWith("rule_") && value is String) {
                    try {
                        val rule = JSONObject(value)
                        rule.put("enabled", false)
                        editor.putString(key, rule.toString())
                        disabledCount++
                    } catch (_: Exception) { }
                }
            }
            editor.apply()

            ToolResult.Success("Disabled $disabledCount auto-reply rule(s)")
        } catch (e: Exception) {
            ToolResult.Error("Failed to disable auto-reply rules: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun listRules(context: Context): ToolResult {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val rules = JSONArray()

            prefs.all.forEach { (key, value) ->
                if (key.startsWith("rule_") && value is String) {
                    try {
                        rules.put(JSONObject(value))
                    } catch (_: Exception) { }
                }
            }

            val data = JSONObject().apply {
                put("rules", rules)
                put("count", rules.length())
            }

            ToolResult.Success(
                content = "Found ${rules.length()} auto-reply rule(s)",
                data = data
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to list auto-reply rules: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun removeRule(params: JSONObject, context: Context): ToolResult {
        val ruleId = params.optString("rule_id", "")
        if (ruleId.isEmpty()) {
            return ToolResult.Error("rule_id is required for remove action.", "INVALID_PARAMS")
        }

        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = "rule_$ruleId"

            if (!prefs.contains(key)) {
                return ToolResult.Error("Auto-reply rule '$ruleId' not found.", "NOT_FOUND")
            }

            prefs.edit().remove(key).apply()
            ToolResult.Success("Auto-reply rule '$ruleId' removed")
        } catch (e: Exception) {
            ToolResult.Error("Failed to remove auto-reply rule: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
