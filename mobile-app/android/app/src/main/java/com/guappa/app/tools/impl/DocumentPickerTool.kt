package com.guappa.app.tools.impl

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.OpenableColumns
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

/**
 * Open the Android Storage Access Framework (SAF) document picker.
 * Fires ACTION_OPEN_DOCUMENT to let the user select a file,
 * and returns the selected file's URI and metadata.
 *
 * Note: This tool fires an intent and returns immediately.
 * The actual file selection result must be handled by the Activity's
 * onActivityResult callback. The tool returns instructions for the
 * orchestrator to await the result.
 */
class DocumentPickerTool : Tool {
    override val name = "document_picker"
    override val description =
        "Open the system document picker to let the user select a file. " +
        "Supports filtering by MIME type. Returns the selected file URI and metadata."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "mime_type": {
                    "type": "string",
                    "description": "MIME type filter (e.g. 'application/pdf', 'image/*', '*/*'). Default: '*/*'."
                },
                "multiple": {
                    "type": "boolean",
                    "description": "Allow selecting multiple files. Default: false."
                }
            },
            "required": []
        }
    """.trimIndent())

    companion object {
        const val REQUEST_CODE_PICK_DOCUMENT = 9501
    }

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val mimeType = params.optString("mime_type", "*/*").ifEmpty { "*/*" }
        val multiple = params.optBoolean("multiple", false)

        val activity = getActivity(context)
        if (activity == null) {
            return ToolResult.Error(
                "Cannot open document picker: no active Activity available. " +
                "The document picker requires a foreground Activity.",
                "EXECUTION_ERROR"
            )
        }

        return try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mimeType
                if (multiple) {
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            activity.startActivityForResult(intent, REQUEST_CODE_PICK_DOCUMENT)

            val data = JSONObject().apply {
                put("type", "pending_user_interaction")
                put("interaction", "document_picker")
                put("mime_type", mimeType)
                put("multiple", multiple)
                put("request_code", REQUEST_CODE_PICK_DOCUMENT)
            }

            ToolResult.Success(
                content = "Document picker opened. Waiting for user to select a file " +
                        "(MIME filter: $mimeType). The result will be delivered via " +
                        "onActivityResult with request code $REQUEST_CODE_PICK_DOCUMENT.",
                data = data
            )
        } catch (e: Exception) {
            ToolResult.Error(
                "Failed to open document picker: ${e.message}",
                "EXECUTION_ERROR"
            )
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
