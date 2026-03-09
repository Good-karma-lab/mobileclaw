package com.guappa.app.tools.impl

import android.content.Context
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class GenerateImageTool : Tool {
    override val name = "generate_image"
    override val description = "Generate an image from a text description. Returns instructions for the orchestrator to route to an image-generation capable provider (DALL-E, Imagen, Stable Diffusion)."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "prompt": {
                    "type": "string",
                    "description": "A detailed description of the image to generate"
                },
                "size": {
                    "type": "string",
                    "description": "Image size: '256x256', '512x512', or '1024x1024' (default: '512x512')"
                },
                "style": {
                    "type": "string",
                    "description": "Image style: 'natural', 'vivid', 'artistic' (default: 'natural')"
                },
                "count": {
                    "type": "integer",
                    "description": "Number of images to generate (default: 1, max: 4)"
                }
            },
            "required": ["prompt"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val prompt = params.optString("prompt", "")
        if (prompt.isEmpty()) {
            return ToolResult.Error("Image prompt is required.", "INVALID_PARAMS")
        }

        val size = params.optString("size", "512x512")
        val style = params.optString("style", "natural")
        val count = params.optInt("count", 1).coerceIn(1, 4)

        val validSizes = setOf("256x256", "512x512", "1024x1024")
        if (size !in validSizes) {
            return ToolResult.Error(
                "Invalid size '$size'. Must be one of: ${validSizes.joinToString()}",
                "INVALID_PARAMS"
            )
        }

        val data = JSONObject().apply {
            put("type", "image_generation_delegation")
            put("prompt", prompt)
            put("size", size)
            put("style", style)
            put("count", count)
        }

        return ToolResult.Success(
            content = "Image generation requested: \"$prompt\" (size=$size, style=$style, count=$count). " +
                    "The orchestrator should route this to an IMAGE_GENERATION capable provider.",
            data = data
        )
    }
}
