package com.guappa.app.tools.impl

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.view.View
import android.app.Activity
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenshotTool : Tool {
    override val name = "take_screenshot"
    override val description = "Capture a screenshot of the current app screen and save it to the device"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "filename": {
                    "type": "string",
                    "description": "Optional filename for the screenshot. Auto-generated if not provided."
                },
                "format": {
                    "type": "string",
                    "enum": ["png", "jpeg"],
                    "description": "Image format. Default 'png'."
                },
                "quality": {
                    "type": "integer",
                    "description": "Compression quality 1-100 (only for jpeg). Default 90."
                }
            },
            "required": []
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val format = params.optString("format", "png")
        val quality = params.optInt("quality", 90)

        if (format != "png" && format != "jpeg") {
            return ToolResult.Error("Format must be 'png' or 'jpeg'.", "INVALID_PARAMS")
        }
        if (quality < 1 || quality > 100) {
            return ToolResult.Error("Quality must be 1-100.", "INVALID_PARAMS")
        }

        return try {
            // Attempt to capture the app's own view hierarchy (no special permissions needed)
            val activity = getActivity(context)
            if (activity == null) {
                return ToolResult.Error(
                    "Cannot capture screenshot: no active Activity available. " +
                    "Full-screen capture requires MediaProjection which needs user approval via system UI.",
                    "EXECUTION_ERROR"
                )
            }

            val rootView = activity.window.decorView.rootView
            rootView.isDrawingCacheEnabled = true
            rootView.buildDrawingCache(true)
            val bitmap = Bitmap.createBitmap(rootView.drawingCache)
            rootView.isDrawingCacheEnabled = false

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val defaultName = "screenshot_$timestamp"
            val filename = params.optString("filename", defaultName).ifEmpty { defaultName }
            val extension = if (format == "jpeg") "jpg" else "png"
            val fullFilename = "$filename.$extension"

            val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ?: return ToolResult.Error("Cannot access pictures directory.", "EXECUTION_ERROR")

            if (!picturesDir.exists()) picturesDir.mkdirs()
            val file = File(picturesDir, fullFilename)

            val compressFormat = if (format == "jpeg") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
            FileOutputStream(file).use { out ->
                bitmap.compress(compressFormat, quality, out)
            }
            bitmap.recycle()

            val data = JSONObject().apply {
                put("file_path", file.absolutePath)
                put("width", rootView.width)
                put("height", rootView.height)
                put("format", format)
                put("size_bytes", file.length())
            }

            ToolResult.Success(
                content = "Screenshot saved: ${file.absolutePath} (${rootView.width}x${rootView.height}, ${file.length() / 1024}KB)",
                data = data,
                attachments = listOf(file.absolutePath)
            )
        } catch (e: Exception) {
            ToolResult.Error("Screenshot failed: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun getActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
