package com.guappa.app.tools.impl

import android.content.Context
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class ImageAnalyzeTool : Tool {
    override val name = "analyze_image"
    override val description = "Analyze an image using a vision-capable AI model. Returns instructions for the orchestrator to route to a vision provider."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "image_path": {
                    "type": "string",
                    "description": "Path to the image file on the device"
                },
                "image_url": {
                    "type": "string",
                    "description": "URL of the image to analyze"
                },
                "question": {
                    "type": "string",
                    "description": "Question about the image or analysis instruction (default: 'Describe this image')"
                }
            },
            "required": []
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val imagePath = params.optString("image_path", "")
        val imageUrl = params.optString("image_url", "")
        val question = params.optString("question", "Describe this image")

        if (imagePath.isEmpty() && imageUrl.isEmpty()) {
            return ToolResult.Error(
                "Either 'image_path' or 'image_url' is required.",
                "INVALID_PARAMS"
            )
        }

        val data = JSONObject().apply {
            put("type", "vision_delegation")
            if (imagePath.isNotEmpty()) put("image_path", imagePath)
            if (imageUrl.isNotEmpty()) put("image_url", imageUrl)
            put("question", question)
        }

        val source = if (imagePath.isNotEmpty()) "file: $imagePath" else "url: $imageUrl"
        return ToolResult.Success(
            content = "Image analysis requested ($source). Question: $question. " +
                    "This requires a vision-capable provider. The orchestrator should route this to a VISION capability provider.",
            data = data
        )
    }
}
