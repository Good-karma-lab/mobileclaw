package com.guappa.app.tools.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject
import java.io.File

class InstagramShareTool : Tool {
    override val name = "instagram_share"
    override val description = "Share an image to Instagram via ACTION_SEND Intent"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "image_path": {
                    "type": "string",
                    "description": "Absolute path to the image file to share"
                },
                "caption": {
                    "type": "string",
                    "description": "Optional caption text (copied to clipboard since Instagram does not support caption via Intent)"
                }
            },
            "required": ["image_path"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val imagePath = params.optString("image_path", "")
        if (imagePath.isEmpty()) {
            return ToolResult.Error("Image path is required.", "INVALID_PARAMS")
        }

        val file = File(imagePath)
        if (!file.exists()) {
            return ToolResult.Error("Image file not found: $imagePath", "FILE_NOT_FOUND")
        }

        return try {
            val imageUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                setPackage("com.instagram.android")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val resolved = context.packageManager.resolveActivity(intent, 0)
            if (resolved == null) {
                return ToolResult.Error(
                    "Instagram app is not installed.",
                    "APP_NOT_FOUND"
                )
            }

            // Copy caption to clipboard if provided, since Instagram doesn't accept it via Intent
            val caption = params.optString("caption", "")
            if (caption.isNotEmpty()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("caption", caption)
                clipboard.setPrimaryClip(clip)
            }

            context.startActivity(intent)
            val msg = if (caption.isNotEmpty()) {
                "Opened Instagram share with image. Caption copied to clipboard."
            } else {
                "Opened Instagram share with image."
            }
            ToolResult.Success(msg)
        } catch (e: Exception) {
            ToolResult.Error("Failed to share to Instagram: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
