package com.guappa.app.tools.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class TwitterPostTool : Tool {
    override val name = "twitter_post"
    override val description = "Post to Twitter/X via deep link. Opens the compose screen with pre-filled text"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "text": {
                    "type": "string",
                    "description": "Tweet text content"
                },
                "url": {
                    "type": "string",
                    "description": "Optional URL to attach to the tweet"
                }
            },
            "required": ["text"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val text = params.optString("text", "")
        if (text.isEmpty()) {
            return ToolResult.Error("Tweet text is required.", "INVALID_PARAMS")
        }

        val url = params.optString("url", "")
        val fullText = if (url.isNotEmpty()) "$text $url" else text

        return try {
            // Try Twitter/X app deep link first
            val twitterIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, fullText)
                setPackage("com.twitter.android")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val resolved = context.packageManager.resolveActivity(twitterIntent, 0)
            if (resolved != null) {
                context.startActivity(twitterIntent)
                ToolResult.Success("Opened Twitter app with compose screen.")
            } else {
                // Fall back to web intent
                val encodedText = Uri.encode(fullText)
                val webUri = Uri.parse("https://twitter.com/intent/tweet?text=$encodedText")
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                ToolResult.Success("Opened Twitter web intent for composing tweet.")
            }
        } catch (e: Exception) {
            ToolResult.Error("Failed to post to Twitter: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
