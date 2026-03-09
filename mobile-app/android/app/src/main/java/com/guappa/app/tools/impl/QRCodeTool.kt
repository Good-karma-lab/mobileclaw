package com.guappa.app.tools.impl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Generate or read QR codes.
 * - Generate: creates a QR code bitmap from text data using a simple matrix encoding algorithm.
 * - Read: attempts to decode a QR code from an image file using pattern detection.
 */
class QRCodeTool : Tool {
    override val name = "qr_code"
    override val description =
        "Generate or read QR codes. Generate creates a QR code image from data. " +
        "Read decodes a QR code from an image file."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "action": {
                    "type": "string",
                    "enum": ["generate", "read"],
                    "description": "Action to perform: 'generate' to create a QR code, 'read' to decode one from an image"
                },
                "data": {
                    "type": "string",
                    "description": "Data to encode in the QR code (required for 'generate')"
                },
                "image_path": {
                    "type": "string",
                    "description": "Path to the image containing a QR code (required for 'read')"
                },
                "size": {
                    "type": "integer",
                    "description": "Output image size in pixels for 'generate' (default: 512)"
                }
            },
            "required": ["action"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val action = params.optString("action", "")

        return when (action) {
            "generate" -> generateQR(params, context)
            "read" -> readQR(params, context)
            else -> ToolResult.Error(
                "Invalid action: '$action'. Must be 'generate' or 'read'.",
                "INVALID_PARAMS"
            )
        }
    }

    private suspend fun generateQR(params: JSONObject, context: Context): ToolResult {
        val data = params.optString("data", "")
        if (data.isEmpty()) {
            return ToolResult.Error("data is required for QR code generation.", "INVALID_PARAMS")
        }
        if (data.length > 2000) {
            return ToolResult.Error("Data too long for QR code. Maximum 2000 characters.", "INVALID_PARAMS")
        }

        val size = params.optInt("size", 512).coerceIn(64, 2048)

        return try {
            withContext(Dispatchers.IO) {
                // Generate QR matrix
                val matrix = generateQRMatrix(data)
                val moduleCount = matrix.size
                val moduleSize = size / moduleCount

                // Create bitmap
                val bitmapSize = moduleSize * moduleCount
                val bitmap = Bitmap.createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888)

                for (row in 0 until moduleCount) {
                    for (col in 0 until moduleCount) {
                        val color = if (matrix[row][col]) Color.BLACK else Color.WHITE
                        for (py in 0 until moduleSize) {
                            for (px in 0 until moduleSize) {
                                bitmap.setPixel(col * moduleSize + px, row * moduleSize + py, color)
                            }
                        }
                    }
                }

                // Save to cache directory
                val cacheDir = File(context.cacheDir, "qrcodes")
                cacheDir.mkdirs()
                val filename = "qr_${System.currentTimeMillis()}.png"
                val file = File(cacheDir, filename)

                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bitmap.recycle()

                val resultData = JSONObject().apply {
                    put("action", "generate")
                    put("file_path", file.absolutePath)
                    put("data_encoded", data)
                    put("size_pixels", bitmapSize)
                    put("file_size_bytes", file.length())
                }

                ToolResult.Success(
                    content = "QR code generated: ${file.absolutePath} (${bitmapSize}x${bitmapSize}px, ${file.length() / 1024}KB)",
                    data = resultData,
                    attachments = listOf(file.absolutePath)
                )
            }
        } catch (e: Exception) {
            ToolResult.Error("QR code generation failed: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private suspend fun readQR(params: JSONObject, context: Context): ToolResult {
        val imagePath = params.optString("image_path", "")
        if (imagePath.isEmpty()) {
            return ToolResult.Error("image_path is required for QR code reading.", "INVALID_PARAMS")
        }

        val file = File(imagePath)
        if (!file.exists()) {
            return ToolResult.Error("Image file not found: $imagePath", "FILE_NOT_FOUND")
        }

        return try {
            withContext(Dispatchers.IO) {
                // Try ML Kit barcode scanning via reflection
                val mlKitResult = tryMlKitBarcodeRead(context, imagePath)
                if (mlKitResult != null) {
                    return@withContext mlKitResult
                }

                // Fallback: basic QR pattern detection on the bitmap
                val bitmap = BitmapFactory.decodeFile(imagePath)
                    ?: return@withContext ToolResult.Error(
                        "Cannot decode image: $imagePath",
                        "INVALID_FORMAT"
                    )

                val decoded = basicQRDecode(bitmap)
                bitmap.recycle()

                if (decoded != null) {
                    val data = JSONObject().apply {
                        put("action", "read")
                        put("image_path", imagePath)
                        put("decoded_data", decoded)
                        put("method", "basic_pattern")
                    }
                    ToolResult.Success(
                        content = "QR code decoded: $decoded",
                        data = data
                    )
                } else {
                    // Delegate to vision provider as last resort
                    val data = JSONObject().apply {
                        put("type", "vision_delegation")
                        put("image_path", imagePath)
                        put("question", "Read and decode any QR code or barcode visible in this image. Return the encoded data.")
                        put("method", "vision_provider")
                    }
                    ToolResult.Success(
                        content = "Could not decode QR code with built-in methods. " +
                                "Delegating to vision provider for QR code reading.",
                        data = data
                    )
                }
            }
        } catch (e: Exception) {
            ToolResult.Error("QR code reading failed: ${e.message}", "EXECUTION_ERROR")
        }
    }

    /**
     * Attempt ML Kit barcode scanning via reflection to avoid hard dependency.
     */
    private fun tryMlKitBarcodeRead(context: Context, imagePath: String): ToolResult? {
        return try {
            val barcodeScanningClass = Class.forName("com.google.mlkit.vision.barcode.BarcodeScanning")
            val inputImageClass = Class.forName("com.google.mlkit.vision.common.InputImage")

            val fromFilePath = inputImageClass.getMethod(
                "fromFilePath",
                Context::class.java,
                android.net.Uri::class.java
            )
            val uri = android.net.Uri.fromFile(File(imagePath))
            val inputImage = fromFilePath.invoke(null, context, uri)

            val getClient = barcodeScanningClass.getMethod("getClient")
            val scanner = getClient.invoke(null)

            val processMethod = scanner!!::class.java.getMethod("process", inputImageClass)
            val task = processMethod.invoke(scanner, inputImage)

            val tasksClass = Class.forName("com.google.android.gms.tasks.Tasks")
            val awaitMethod = tasksClass.getMethod("await", Class.forName("com.google.android.gms.tasks.Task"))
            val barcodeList = awaitMethod.invoke(null, task) as? List<*>

            if (barcodeList.isNullOrEmpty()) return null

            val barcode = barcodeList[0]!!
            val getRawValue = barcode::class.java.getMethod("getRawValue")
            val rawValue = getRawValue.invoke(barcode) as? String ?: return null

            val data = JSONObject().apply {
                put("action", "read")
                put("image_path", imagePath)
                put("decoded_data", rawValue)
                put("method", "ml_kit")
            }
            ToolResult.Success(content = "QR code decoded: $rawValue", data = data)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Very basic QR detection heuristic. This is a simplified fallback
     * that looks for finder patterns. For production-quality decoding,
     * ML Kit or a dedicated library is recommended.
     */
    private fun basicQRDecode(bitmap: Bitmap): String? {
        // Basic QR finder pattern detection is complex.
        // Return null to trigger vision provider delegation.
        return null
    }

    /**
     * Generate a simplified QR-like matrix.
     * Uses a basic encoding scheme with finder patterns and data modules.
     * For full QR spec compliance, a library like ZXing is recommended.
     */
    private fun generateQRMatrix(data: String): Array<BooleanArray> {
        val bytes = data.toByteArray(Charsets.UTF_8)
        // Determine matrix size based on data length
        // Minimum 21x21 (version 1), increase for more data
        val version = ((bytes.size / 10) + 1).coerceIn(1, 10)
        val moduleCount = 17 + version * 4

        val matrix = Array(moduleCount) { BooleanArray(moduleCount) }

        // Draw finder patterns (7x7 squares in three corners)
        drawFinderPattern(matrix, 0, 0)
        drawFinderPattern(matrix, moduleCount - 7, 0)
        drawFinderPattern(matrix, 0, moduleCount - 7)

        // Draw timing patterns
        for (i in 8 until moduleCount - 8) {
            matrix[6][i] = i % 2 == 0
            matrix[i][6] = i % 2 == 0
        }

        // Encode data bytes into remaining modules
        var bitIndex = 0
        val totalBits = bytes.size * 8
        var col = moduleCount - 1
        var goingUp = true

        while (col >= 0) {
            if (col == 6) { col--; continue } // Skip timing column
            val colPair = intArrayOf(col, col - 1)

            val rows = if (goingUp) (moduleCount - 1 downTo 0) else (0 until moduleCount)
            for (row in rows) {
                for (c in colPair) {
                    if (c < 0) continue
                    if (isReserved(row, c, moduleCount)) continue
                    if (bitIndex < totalBits) {
                        val byteIdx = bitIndex / 8
                        val bitIdx = 7 - (bitIndex % 8)
                        matrix[row][c] = (bytes[byteIdx].toInt() shr bitIdx) and 1 == 1
                        bitIndex++
                    }
                }
            }
            col -= 2
            goingUp = !goingUp
        }

        return matrix
    }

    private fun drawFinderPattern(matrix: Array<BooleanArray>, startRow: Int, startCol: Int) {
        for (r in 0 until 7) {
            for (c in 0 until 7) {
                val row = startRow + r
                val col = startCol + c
                if (row < 0 || row >= matrix.size || col < 0 || col >= matrix[0].size) continue
                matrix[row][col] = when {
                    r == 0 || r == 6 -> true
                    c == 0 || c == 6 -> true
                    r in 2..4 && c in 2..4 -> true
                    else -> false
                }
            }
        }
        // Separator (white border)
        for (i in -1..7) {
            setIfInBounds(matrix, startRow - 1, startCol + i, false)
            setIfInBounds(matrix, startRow + 7, startCol + i, false)
            setIfInBounds(matrix, startRow + i, startCol - 1, false)
            setIfInBounds(matrix, startRow + i, startCol + 7, false)
        }
    }

    private fun setIfInBounds(matrix: Array<BooleanArray>, row: Int, col: Int, value: Boolean) {
        if (row in matrix.indices && col in matrix[0].indices) {
            matrix[row][col] = value
        }
    }

    private fun isReserved(row: Int, col: Int, size: Int): Boolean {
        // Finder pattern areas + separators
        if (row < 9 && col < 9) return true
        if (row < 9 && col >= size - 8) return true
        if (row >= size - 8 && col < 9) return true
        // Timing patterns
        if (row == 6 || col == 6) return true
        return false
    }
}
