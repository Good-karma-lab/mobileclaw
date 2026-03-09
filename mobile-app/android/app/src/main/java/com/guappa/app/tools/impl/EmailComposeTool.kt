package com.guappa.app.tools.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class EmailComposeTool : Tool {
    override val name = "compose_email"
    override val description = "Compose an email using the device email client via ACTION_SENDTO with mailto: URI"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "to": {
                    "type": "string",
                    "description": "Recipient email address(es), comma-separated for multiple"
                },
                "cc": {
                    "type": "string",
                    "description": "CC email address(es), comma-separated"
                },
                "bcc": {
                    "type": "string",
                    "description": "BCC email address(es), comma-separated"
                },
                "subject": {
                    "type": "string",
                    "description": "Email subject line"
                },
                "body": {
                    "type": "string",
                    "description": "Email body text"
                }
            },
            "required": ["to"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val to = params.optString("to", "")
        if (to.isEmpty()) {
            return ToolResult.Error("Recipient email address is required.", "INVALID_PARAMS")
        }

        return try {
            val uriBuilder = StringBuilder("mailto:${Uri.encode(to)}")
            val queryParts = mutableListOf<String>()

            val cc = params.optString("cc", "")
            if (cc.isNotEmpty()) {
                queryParts.add("cc=${Uri.encode(cc)}")
            }

            val bcc = params.optString("bcc", "")
            if (bcc.isNotEmpty()) {
                queryParts.add("bcc=${Uri.encode(bcc)}")
            }

            val subject = params.optString("subject", "")
            if (subject.isNotEmpty()) {
                queryParts.add("subject=${Uri.encode(subject)}")
            }

            val body = params.optString("body", "")
            if (body.isNotEmpty()) {
                queryParts.add("body=${Uri.encode(body)}")
            }

            if (queryParts.isNotEmpty()) {
                uriBuilder.append("?${queryParts.joinToString("&")}")
            }

            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(uriBuilder.toString())).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.Success("Opened email composer to: $to")
        } catch (e: Exception) {
            ToolResult.Error("Failed to compose email: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
