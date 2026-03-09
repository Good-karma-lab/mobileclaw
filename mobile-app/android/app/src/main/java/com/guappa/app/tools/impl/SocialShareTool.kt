package com.guappa.app.tools.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject
import java.io.File

class SocialShareTool : Tool {
    override val name = "social_share"
    override val description = "Universal social share via Android share chooser. Supports text and optional image attachment"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "text": {
                    "type": "string",
                    "description": "Text content to share"
                },
                "subject": {
                    "type": "string",
                    "description": "Optional subject line (used by email-like apps)"
                },
                "image_path": {
                    "type": "string",
                    "description": "Optional absolute path to an image file to attach"
                },
                "mime_type": {
                    "type": "string",
                    "description": "MIME type for the share (e.g. text/plain, image/jpeg). Defaults to text/plain or image/* if image is attached"
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
        val imagePath = params.optString("image_path", "")

        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_TEXT, text)

                if (subject.isNotEmpty()) {
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                }

                if (imagePath.isNotEmpty()) {
                    val file = File(imagePath)
                    if (!file.exists()) {
                        return ToolResult.Error("Image file not found: $imagePath", "FILE_NOT_FOUND")
                    }
                    val imageUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val mimeType = params.optString("mime_type", "image/*")
                    type = mimeType
                } else {
                    val mimeType = params.optString("mime_type", "text/plain")
                    type = mimeType
                }
            }

            val chooser = Intent.createChooser(intent, "Share via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)

            val summary = buildString {
                append("Share dialog opened with ${text.length} chars of text")
                if (imagePath.isNotEmpty()) append(" and image attachment")
            }
            ToolResult.Success(summary)
        } catch (e: Exception) {
            ToolResult.Error("Failed to share: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
