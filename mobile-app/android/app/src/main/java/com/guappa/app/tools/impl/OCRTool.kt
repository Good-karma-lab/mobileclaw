package com.guappa.app.tools.impl

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Extract text from an image using the device's available OCR capabilities.
 *
 * Strategy:
 * 1. Attempts ML Kit Text Recognition if available on the device.
 * 2. Falls back to returning a vision-delegation payload so the orchestrator
 *    can route the image to a vision-capable LLM via ProviderRouter.
 */
class OCRTool : Tool {
    override val name = "ocr"
    override val description =
        "Extract text from an image file using on-device text recognition (ML Kit) " +
        "or by delegating to a vision-capable AI provider."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "image_path": {
                    "type": "string",
                    "description": "Absolute path to the image file on the device"
                }
            },
            "required": ["image_path"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val imagePath = params.optString("image_path", "")
        if (imagePath.isEmpty()) {
            return ToolResult.Error("image_path is required.", "INVALID_PARAMS")
        }

        val file = File(imagePath)
        if (!file.exists()) {
            return ToolResult.Error("Image file not found: $imagePath", "FILE_NOT_FOUND")
        }
        if (!file.canRead()) {
            return ToolResult.Error("Cannot read image file: $imagePath", "PERMISSION_DENIED")
        }

        // Validate it's a readable image
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imagePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return ToolResult.Error(
                "File does not appear to be a valid image: $imagePath",
                "INVALID_FORMAT"
            )
        }

        // Try ML Kit Text Recognition
        return try {
            val mlKitResult = tryMlKitOcr(context, imagePath)
            if (mlKitResult != null) {
                mlKitResult
            } else {
                // Fall back to vision provider delegation
                visionDelegation(imagePath, options.outWidth, options.outHeight)
            }
        } catch (e: Exception) {
            // ML Kit not available or failed; delegate to vision provider
            visionDelegation(imagePath, options.outWidth, options.outHeight)
        }
    }

    /**
     * Attempts on-device OCR using ML Kit Text Recognition via reflection
     * to avoid a hard compile-time dependency.
     */
    private suspend fun tryMlKitOcr(context: Context, imagePath: String): ToolResult? {
        return withContext(Dispatchers.IO) {
            try {
                // Attempt to load ML Kit classes via reflection
                val textRecognitionClass = Class.forName("com.google.mlkit.vision.text.TextRecognition")
                val latinClass = Class.forName("com.google.mlkit.vision.text.latin.TextRecognizerOptions")
                val inputImageClass = Class.forName("com.google.mlkit.vision.common.InputImage")

                // InputImage.fromFilePath(context, uri)
                val fromFilePath = inputImageClass.getMethod(
                    "fromFilePath",
                    Context::class.java,
                    Uri::class.java
                )
                val uri = Uri.fromFile(File(imagePath))
                val inputImage = fromFilePath.invoke(null, context, uri)

                // TextRecognizerOptions.DEFAULT_OPTIONS
                val defaultOptions = latinClass.getField("DEFAULT_OPTIONS").get(null)

                // TextRecognition.getClient(options)
                val getClientMethod = textRecognitionClass.getMethod("getClient", defaultOptions!!::class.java)
                val recognizer = getClientMethod.invoke(null, defaultOptions)

                // recognizer.process(inputImage) returns Task<Text>
                val processMethod = recognizer!!::class.java.getMethod("process", inputImageClass)
                val task = processMethod.invoke(recognizer, inputImage)

                // Await the Task using Tasks.await()
                val tasksClass = Class.forName("com.google.android.gms.tasks.Tasks")
                val awaitMethod = tasksClass.getMethod("await", Class.forName("com.google.android.gms.tasks.Task"))
                val textResult = awaitMethod.invoke(null, task)

                // text.getText()
                val getTextMethod = textResult!!::class.java.getMethod("getText")
                val extractedText = getTextMethod.invoke(textResult) as? String ?: ""

                // text.getTextBlocks()
                val getBlocksMethod = textResult::class.java.getMethod("getTextBlocks")
                @Suppress("UNCHECKED_CAST")
                val blocks = getBlocksMethod.invoke(textResult) as? List<*> ?: emptyList<Any>()

                if (extractedText.isEmpty()) {
                    val data = JSONObject().apply {
                        put("image_path", imagePath)
                        put("method", "ml_kit")
                        put("text_found", false)
                    }
                    return@withContext ToolResult.Success(
                        content = "No text found in image.",
                        data = data
                    )
                }

                val data = JSONObject().apply {
                    put("image_path", imagePath)
                    put("method", "ml_kit")
                    put("text_found", true)
                    put("block_count", blocks.size)
                    put("char_count", extractedText.length)
                }

                ToolResult.Success(
                    content = extractedText,
                    data = data
                )
            } catch (e: ClassNotFoundException) {
                // ML Kit not available
                null
            } catch (e: Exception) {
                // ML Kit failed
                null
            }
        }
    }

    private fun visionDelegation(imagePath: String, width: Int, height: Int): ToolResult {
        val data = JSONObject().apply {
            put("type", "vision_delegation")
            put("image_path", imagePath)
            put("question", "Extract all text visible in this image. Return the text exactly as it appears, preserving layout where possible.")
            put("image_width", width)
            put("image_height", height)
            put("method", "vision_provider")
        }

        return ToolResult.Success(
            content = "OCR requested for image: $imagePath (${width}x${height}). " +
                    "ML Kit not available; delegating to vision-capable provider. " +
                    "The orchestrator should route this to a VISION capability provider.",
            data = data
        )
    }
}
