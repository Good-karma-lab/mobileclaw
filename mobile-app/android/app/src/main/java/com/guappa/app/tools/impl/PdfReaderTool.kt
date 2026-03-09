package com.guappa.app.tools.impl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Extract text from a PDF file.
 * Uses PdfRenderer to render pages to bitmaps, then delegates to
 * OCR (ML Kit or vision provider) for text extraction.
 * Also attempts basic text extraction from the PDF binary for text-based PDFs.
 */
class PdfReaderTool : Tool {
    override val name = "pdf_reader"
    override val description =
        "Extract text from a PDF file. Supports page range selection. " +
        "Uses on-device rendering and text extraction."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "file_path": {
                    "type": "string",
                    "description": "Absolute path to the PDF file"
                },
                "pages": {
                    "type": "string",
                    "description": "Page range to extract (e.g. '1-5', '3', '1,3,5'). Default: all pages."
                }
            },
            "required": ["file_path"]
        }
    """.trimIndent())

    companion object {
        private const val MAX_PAGES = 50
        private const val RENDER_DPI = 150
    }

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val filePath = params.optString("file_path", "")
        if (filePath.isEmpty()) {
            return ToolResult.Error("file_path is required.", "INVALID_PARAMS")
        }

        val file = File(filePath)
        if (!file.exists()) {
            return ToolResult.Error("PDF file not found: $filePath", "FILE_NOT_FOUND")
        }
        if (!file.canRead()) {
            return ToolResult.Error("Cannot read PDF file: $filePath", "PERMISSION_DENIED")
        }

        val pagesParam = params.optString("pages", "")

        return try {
            withContext(Dispatchers.IO) {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val totalPages = renderer.pageCount

                val pageIndices = parsePageRange(pagesParam, totalPages)
                if (pageIndices.isEmpty()) {
                    renderer.close()
                    pfd.close()
                    return@withContext ToolResult.Error(
                        "No valid pages in range. PDF has $totalPages page(s).",
                        "INVALID_PARAMS"
                    )
                }

                // First attempt: try to extract raw text from PDF stream
                val rawText = extractRawPdfText(file)
                if (rawText != null && rawText.isNotBlank()) {
                    renderer.close()
                    pfd.close()
                    return@withContext buildTextResult(rawText, filePath, totalPages, pageIndices)
                }

                // Fallback: render pages to images and delegate to vision/OCR
                val pageImages = mutableListOf<String>()
                val pagesArray = JSONArray()

                for (pageIndex in pageIndices) {
                    val page = renderer.openPage(pageIndex)
                    val scale = RENDER_DPI / 72f
                    val width = (page.width * scale).toInt()
                    val height = (page.height * scale).toInt()

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    // Save rendered page
                    val cacheDir = File(context.cacheDir, "pdf_pages")
                    cacheDir.mkdirs()
                    val pageFile = File(cacheDir, "page_${pageIndex + 1}_${System.currentTimeMillis()}.png")
                    FileOutputStream(pageFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                    }
                    bitmap.recycle()

                    pageImages.add(pageFile.absolutePath)
                    pagesArray.put(JSONObject().apply {
                        put("page_number", pageIndex + 1)
                        put("image_path", pageFile.absolutePath)
                        put("width", width)
                        put("height", height)
                    })
                }

                renderer.close()
                pfd.close()

                val data = JSONObject().apply {
                    put("type", "vision_delegation")
                    put("file_path", filePath)
                    put("total_pages", totalPages)
                    put("rendered_pages", pagesArray)
                    put("page_count", pageIndices.size)
                    put("question", "Extract all text from these PDF page images. " +
                            "Maintain the reading order and paragraph structure.")
                    put("method", "pdf_render_ocr")
                }

                ToolResult.Success(
                    content = "PDF rendered to ${pageIndices.size} page image(s) for text extraction. " +
                            "Total pages: $totalPages. " +
                            "The orchestrator should route page images to a VISION capability provider for OCR.",
                    data = data,
                    attachments = pageImages
                )
            }
        } catch (e: SecurityException) {
            ToolResult.Error("Permission denied reading PDF: ${e.message}", "PERMISSION_DENIED")
        } catch (e: Exception) {
            ToolResult.Error("Failed to read PDF: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun buildTextResult(
        rawText: String,
        filePath: String,
        totalPages: Int,
        pageIndices: List<Int>
    ): ToolResult {
        val truncated = rawText.length > 50000
        val content = if (truncated) {
            rawText.take(50000) + "\n...[truncated at 50000 chars]"
        } else {
            rawText
        }

        val data = JSONObject().apply {
            put("file_path", filePath)
            put("total_pages", totalPages)
            put("extracted_pages", pageIndices.size)
            put("char_count", rawText.length)
            put("truncated", truncated)
            put("method", "raw_text_extraction")
        }

        return ToolResult.Success(content = content, data = data)
    }

    /**
     * Attempt basic text extraction from PDF binary.
     * Looks for text streams between BT/ET markers in the PDF.
     * This works for simple text-based PDFs but not scanned documents.
     */
    private fun extractRawPdfText(file: File): String? {
        return try {
            val bytes = file.readBytes()
            val content = String(bytes, Charsets.ISO_8859_1)
            val textBuilder = StringBuilder()

            // Look for text objects (BT ... ET blocks)
            var pos = 0
            while (pos < content.length) {
                val btIndex = content.indexOf("BT", pos)
                if (btIndex < 0) break
                val etIndex = content.indexOf("ET", btIndex)
                if (etIndex < 0) break

                val textBlock = content.substring(btIndex + 2, etIndex)

                // Extract text from Tj and TJ operators
                val tjPattern = Regex("\\(([^)]+)\\)\\s*Tj")
                for (match in tjPattern.findAll(textBlock)) {
                    val text = decodePdfString(match.groupValues[1])
                    if (text.isNotBlank()) {
                        textBuilder.append(text)
                    }
                }

                // Handle TJ arrays
                val tjArrayPattern = Regex("\\[([^\\]]+)\\]\\s*TJ")
                for (match in tjArrayPattern.findAll(textBlock)) {
                    val arrayContent = match.groupValues[1]
                    val stringPattern = Regex("\\(([^)]+)\\)")
                    for (strMatch in stringPattern.findAll(arrayContent)) {
                        val text = decodePdfString(strMatch.groupValues[1])
                        if (text.isNotBlank()) {
                            textBuilder.append(text)
                        }
                    }
                }

                // Add newline between text blocks
                if (textBuilder.isNotEmpty() && !textBuilder.endsWith('\n')) {
                    textBuilder.append('\n')
                }

                pos = etIndex + 2
            }

            val result = textBuilder.toString().trim()
            if (result.length > 20) result else null
        } catch (e: Exception) {
            null
        }
    }

    private fun decodePdfString(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    '(' -> { sb.append('('); i += 2 }
                    ')' -> { sb.append(')'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    else -> { sb.append(s[i + 1]); i += 2 }
                }
            } else {
                sb.append(s[i])
                i++
            }
        }
        return sb.toString()
    }

    /**
     * Parse page range string into list of 0-based page indices.
     * Supports: "1-5", "3", "1,3,5-8", "" (all pages).
     */
    private fun parsePageRange(pages: String, totalPages: Int): List<Int> {
        if (pages.isEmpty()) {
            return (0 until totalPages.coerceAtMost(MAX_PAGES)).toList()
        }

        val indices = mutableSetOf<Int>()
        val parts = pages.split(",")

        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                val range = trimmed.split("-")
                if (range.size == 2) {
                    val start = (range[0].trim().toIntOrNull() ?: continue) - 1
                    val end = (range[1].trim().toIntOrNull() ?: continue) - 1
                    for (i in start..end) {
                        if (i in 0 until totalPages) {
                            indices.add(i)
                        }
                    }
                }
            } else {
                val pageNum = (trimmed.toIntOrNull() ?: continue) - 1
                if (pageNum in 0 until totalPages) {
                    indices.add(pageNum)
                }
            }
        }

        return indices.sorted().take(MAX_PAGES)
    }
}
