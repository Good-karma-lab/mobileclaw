package com.guappa.app.tools.impl

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Scan barcodes from an image file.
 * Uses ML Kit barcode scanning when available, falls back to
 * vision provider delegation for barcode reading.
 */
class BarcodeScanTool : Tool {
    override val name = "barcode_scan"
    override val description =
        "Scan barcodes from an image file. Supports QR codes, UPC, EAN, Code 128, " +
        "and other common barcode formats. Returns barcode value and format."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "image_path": {
                    "type": "string",
                    "description": "Absolute path to the image containing a barcode"
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

        return try {
            withContext(Dispatchers.IO) {
                // Try ML Kit barcode scanning via reflection
                val mlKitResult = tryMlKitBarcodeScan(context, imagePath)
                if (mlKitResult != null) {
                    return@withContext mlKitResult
                }

                // Fallback: delegate to vision provider
                visionDelegation(imagePath, options.outWidth, options.outHeight)
            }
        } catch (e: Exception) {
            ToolResult.Error("Barcode scanning failed: ${e.message}", "EXECUTION_ERROR")
        }
    }

    /**
     * Attempt barcode scanning using ML Kit via reflection to avoid hard dependency.
     */
    private fun tryMlKitBarcodeScan(context: Context, imagePath: String): ToolResult? {
        return try {
            val barcodeScanningClass = Class.forName("com.google.mlkit.vision.barcode.BarcodeScanning")
            val inputImageClass = Class.forName("com.google.mlkit.vision.common.InputImage")

            val fromFilePath = inputImageClass.getMethod(
                "fromFilePath",
                Context::class.java,
                Uri::class.java
            )
            val uri = Uri.fromFile(File(imagePath))
            val inputImage = fromFilePath.invoke(null, context, uri)

            val getClient = barcodeScanningClass.getMethod("getClient")
            val scanner = getClient.invoke(null)

            val processMethod = scanner!!::class.java.getMethod("process", inputImageClass)
            val task = processMethod.invoke(scanner, inputImage)

            val tasksClass = Class.forName("com.google.android.gms.tasks.Tasks")
            val awaitMethod = tasksClass.getMethod("await", Class.forName("com.google.android.gms.tasks.Task"))
            @Suppress("UNCHECKED_CAST")
            val barcodeList = awaitMethod.invoke(null, task) as? List<*>

            if (barcodeList.isNullOrEmpty()) {
                val data = JSONObject().apply {
                    put("image_path", imagePath)
                    put("method", "ml_kit")
                    put("barcode_count", 0)
                }
                return ToolResult.Success(
                    content = "No barcodes found in image.",
                    data = data
                )
            }

            val barcodesArray = JSONArray()
            val summary = StringBuilder()
            summary.appendLine("Found ${barcodeList.size} barcode(s):")
            summary.appendLine()

            for ((index, barcode) in barcodeList.withIndex()) {
                if (barcode == null) continue

                val getRawValue = barcode::class.java.getMethod("getRawValue")
                val getFormat = barcode::class.java.getMethod("getFormat")
                val getValueType = barcode::class.java.getMethod("getValueType")

                val rawValue = getRawValue.invoke(barcode) as? String ?: ""
                val format = getFormat.invoke(barcode) as? Int ?: -1
                val valueType = getValueType.invoke(barcode) as? Int ?: -1

                val formatName = getBarcodeFormatName(format)
                val typeName = getBarcodeTypeName(valueType)

                val barcodeObj = JSONObject().apply {
                    put("value", rawValue)
                    put("format", formatName)
                    put("format_id", format)
                    put("value_type", typeName)
                    put("value_type_id", valueType)
                }
                barcodesArray.put(barcodeObj)

                summary.appendLine("${index + 1}. Value: $rawValue")
                summary.appendLine("   Format: $formatName")
                summary.appendLine("   Type: $typeName")
                summary.appendLine()
            }

            val data = JSONObject().apply {
                put("image_path", imagePath)
                put("method", "ml_kit")
                put("barcode_count", barcodeList.size)
                put("barcodes", barcodesArray)
            }

            ToolResult.Success(
                content = summary.toString().trim(),
                data = data
            )
        } catch (e: ClassNotFoundException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun visionDelegation(imagePath: String, width: Int, height: Int): ToolResult {
        val data = JSONObject().apply {
            put("type", "vision_delegation")
            put("image_path", imagePath)
            put("question", "Scan and decode any barcodes or QR codes visible in this image. " +
                    "For each barcode found, return the encoded data/value and the barcode format type.")
            put("image_width", width)
            put("image_height", height)
            put("method", "vision_provider")
        }

        return ToolResult.Success(
            content = "ML Kit barcode scanning not available. " +
                    "Delegating to vision provider for barcode detection in: $imagePath",
            data = data
        )
    }

    private fun getBarcodeFormatName(format: Int): String {
        return when (format) {
            0 -> "UNKNOWN"
            1 -> "CODE_128"
            2 -> "CODE_39"
            4 -> "CODE_93"
            8 -> "CODABAR"
            16 -> "DATA_MATRIX"
            32 -> "EAN_13"
            64 -> "EAN_8"
            128 -> "ITF"
            256 -> "QR_CODE"
            512 -> "UPC_A"
            1024 -> "UPC_E"
            2048 -> "PDF417"
            4096 -> "AZTEC"
            else -> "FORMAT_$format"
        }
    }

    private fun getBarcodeTypeName(valueType: Int): String {
        return when (valueType) {
            0 -> "UNKNOWN"
            1 -> "CONTACT_INFO"
            2 -> "EMAIL"
            3 -> "ISBN"
            4 -> "PHONE"
            5 -> "PRODUCT"
            6 -> "SMS"
            7 -> "TEXT"
            8 -> "URL"
            9 -> "WIFI"
            10 -> "GEO"
            11 -> "CALENDAR_EVENT"
            12 -> "DRIVER_LICENSE"
            else -> "TYPE_$valueType"
        }
    }
}
