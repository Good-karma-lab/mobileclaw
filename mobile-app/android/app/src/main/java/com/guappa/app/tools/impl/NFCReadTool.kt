package com.guappa.app.tools.impl

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class NFCReadTool : Tool {
    override val name = "nfc_read"
    override val description = "Read NFC tag content. The device must be held near an NFC tag. Returns tag content as text."
    override val requiredPermissions = listOf("NFC")
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "timeout_ms": {
                    "type": "integer",
                    "description": "Timeout in milliseconds to wait for an NFC tag (default: 10000, max: 30000)"
                }
            },
            "required": []
        }
    """.trimIndent())

    override fun isAvailable(context: Context): Boolean {
        val nfcManager = context.getSystemService(Context.NFC_SERVICE) as? NfcManager
        val adapter = nfcManager?.defaultAdapter
        return adapter != null && adapter.isEnabled
    }

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val timeoutMs = params.optInt("timeout_ms", 10000).coerceIn(1000, 30000)

        return try {
            val nfcManager = context.getSystemService(Context.NFC_SERVICE) as? NfcManager
            val adapter = nfcManager?.defaultAdapter

            if (adapter == null) {
                return ToolResult.Error("NFC is not available on this device.", "NFC_UNAVAILABLE")
            }

            if (!adapter.isEnabled) {
                return ToolResult.Error(
                    "NFC is disabled. Please enable NFC in device settings.",
                    "NFC_DISABLED"
                )
            }

            // NFC tag reading in Android typically requires a foreground Activity with
            // enableForegroundDispatch or enableReaderMode. Since tools execute from a
            // background service context, we check for a cached/pending tag from the
            // NFC dispatch system.
            //
            // The NFC foreground dispatch delivers tags via Activity.onNewIntent().
            // Here we provide a helper that reads from a tag if one has been dispatched
            // to the app's NFC tag holder.

            val tag = NfcTagHolder.consumeTag(timeoutMs.toLong())
                ?: return ToolResult.Error(
                    "No NFC tag detected within ${timeoutMs}ms. Hold the device near an NFC tag and try again.",
                    "TAG_TIMEOUT"
                )

            readTag(tag)
        } catch (e: Exception) {
            ToolResult.Error("Failed to read NFC tag: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private suspend fun readTag(tag: Tag): ToolResult = withContext(Dispatchers.IO) {
        try {
            val tagId = tag.id?.joinToString(":") { String.format("%02X", it) } ?: "unknown"
            val techList = tag.techList?.toList() ?: emptyList()

            val data = JSONObject().apply {
                put("tag_id", tagId)
                put("technologies", JSONArray(techList))
            }

            // Try NDEF first (most common)
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                try {
                    ndef.connect()
                    val ndefMessage = ndef.ndefMessage ?: ndef.cachedNdefMessage

                    if (ndefMessage != null) {
                        val records = JSONArray()
                        val contentParts = mutableListOf<String>()

                        for (record in ndefMessage.records) {
                            val recordJson = JSONObject()
                            val tnf = record.tnf
                            val type = String(record.type)
                            val payload = record.payload

                            recordJson.put("tnf", tnf)
                            recordJson.put("type", type)

                            // Parse common NDEF record types
                            val text = when {
                                tnf == android.nfc.NdefRecord.TNF_WELL_KNOWN && type == "T" -> {
                                    // Text record
                                    parseTextRecord(payload)
                                }
                                tnf == android.nfc.NdefRecord.TNF_WELL_KNOWN && type == "U" -> {
                                    // URI record
                                    parseUriRecord(payload)
                                }
                                tnf == android.nfc.NdefRecord.TNF_MIME_MEDIA -> {
                                    String(payload, Charsets.UTF_8)
                                }
                                else -> {
                                    payload.joinToString(":") { String.format("%02X", it) }
                                }
                            }

                            recordJson.put("content", text)
                            recordJson.put("payload_size", payload.size)
                            records.put(recordJson)
                            contentParts.add(text)
                        }

                        data.put("ndef_records", records)
                        data.put("record_count", records.length())
                        data.put("ndef_type", ndef.type)
                        data.put("max_size", ndef.maxSize)
                        data.put("is_writable", ndef.isWritable)

                        val content = buildString {
                            appendLine("NFC Tag ID: $tagId")
                            appendLine("Type: ${ndef.type}")
                            appendLine("Records: ${records.length()}")
                            for ((i, part) in contentParts.withIndex()) {
                                appendLine("Record ${i + 1}: $part")
                            }
                        }

                        return@withContext ToolResult.Success(content = content.trim(), data = data)
                    }
                } finally {
                    try { ndef.close() } catch (_: Exception) {}
                }
            }

            // Try MifareUltralight
            val mifareUl = MifareUltralight.get(tag)
            if (mifareUl != null) {
                try {
                    mifareUl.connect()
                    val pages = mutableListOf<String>()
                    // Read first 16 pages (4 bytes each)
                    for (pageOffset in 0 until 16 step 4) {
                        try {
                            val pageData = mifareUl.readPages(pageOffset)
                            pages.add(pageData.joinToString(":") { String.format("%02X", it) })
                        } catch (_: Exception) {
                            break
                        }
                    }
                    data.put("mifare_ultralight_pages", JSONArray(pages))
                } finally {
                    try { mifareUl.close() } catch (_: Exception) {}
                }
            }

            // Return basic tag info if no NDEF data
            val content = buildString {
                appendLine("NFC Tag ID: $tagId")
                appendLine("Technologies: ${techList.joinToString { it.substringAfterLast('.') }}")
                append("No NDEF data found on this tag.")
            }

            ToolResult.Success(content = content, data = data)
        } catch (e: Exception) {
            ToolResult.Error("Failed to read NFC tag data: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun parseTextRecord(payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        val languageCodeLength = payload[0].toInt() and 0x3F
        val textStart = 1 + languageCodeLength
        return if (textStart < payload.size) {
            String(payload, textStart, payload.size - textStart, Charsets.UTF_8)
        } else ""
    }

    private fun parseUriRecord(payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        val prefixByte = payload[0].toInt() and 0xFF
        val uriPrefix = URI_PREFIXES.getOrElse(prefixByte) { "" }
        val rest = if (payload.size > 1) String(payload, 1, payload.size - 1, Charsets.UTF_8) else ""
        return uriPrefix + rest
    }

    companion object {
        private val URI_PREFIXES = mapOf(
            0x00 to "",
            0x01 to "http://www.",
            0x02 to "https://www.",
            0x03 to "http://",
            0x04 to "https://",
            0x05 to "tel:",
            0x06 to "mailto:",
            0x07 to "ftp://anonymous:anonymous@",
            0x08 to "ftp://ftp.",
            0x09 to "ftps://",
            0x0A to "sftp://",
            0x0B to "smb://",
            0x0C to "nfs://",
            0x0D to "ftp://",
            0x0E to "dav://",
            0x0F to "news:",
            0x10 to "telnet://",
            0x11 to "imap:",
            0x12 to "rtsp://",
            0x13 to "urn:",
            0x14 to "pop:",
            0x15 to "sip:",
            0x16 to "sips:",
            0x17 to "tftp:",
            0x18 to "btspp://",
            0x19 to "btl2cap://",
            0x1A to "btgoep://",
            0x1B to "tcpobex://",
            0x1C to "irdaobex://",
            0x1D to "file://",
            0x1E to "urn:epc:id:",
            0x1F to "urn:epc:tag:",
            0x20 to "urn:epc:pat:",
            0x21 to "urn:epc:raw:",
            0x22 to "urn:epc:",
            0x23 to "urn:nfc:"
        )
    }
}

/**
 * Singleton holder for NFC tags dispatched via Activity's foreground dispatch system.
 * The hosting Activity should call [offerTag] when a tag is received via onNewIntent().
 */
object NfcTagHolder {
    private val tagChannel = java.util.concurrent.LinkedBlockingQueue<Tag>(1)

    /** Called by the Activity when an NFC tag is received. */
    fun offerTag(tag: Tag) {
        tagChannel.clear()
        tagChannel.offer(tag)
    }

    /** Waits up to [timeoutMs] for a tag. Returns null if none arrives. */
    fun consumeTag(timeoutMs: Long): Tag? {
        return tagChannel.poll(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    }
}
