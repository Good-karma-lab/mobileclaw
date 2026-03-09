package com.guappa.app.tools.impl

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioRecordTool : Tool {
    override val name = "audio_record"
    override val description = "Record ambient audio using the device microphone. Returns the file path of the recording."
    override val requiredPermissions = listOf("RECORD_AUDIO")
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "duration_seconds": {
                    "type": "integer",
                    "description": "Recording duration in seconds (1-60, default: 10)"
                },
                "format": {
                    "type": "string",
                    "description": "Output format: m4a or 3gp (default: m4a)",
                    "enum": ["m4a", "3gp"]
                }
            },
            "required": []
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val durationSeconds = params.optInt("duration_seconds", 10).coerceIn(1, 60)
        val format = params.optString("format", "m4a")

        val extension = when (format) {
            "3gp" -> "3gp"
            else -> "m4a"
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outputFile = File(context.cacheDir, "recording_$timestamp.$extension")

        return try {
            val recorder = withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    MediaRecorder()
                }

                rec.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    when (format) {
                        "3gp" -> {
                            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                        }
                        else -> {
                            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                            setAudioEncodingBitRate(128000)
                            setAudioSamplingRate(44100)
                        }
                    }
                    setMaxDuration(durationSeconds * 1000)
                    setOutputFile(outputFile.absolutePath)
                    prepare()
                    start()
                }

                rec
            }

            // Wait for the recording duration
            delay(durationSeconds * 1000L)

            withContext(Dispatchers.IO) {
                try {
                    recorder.stop()
                } catch (_: RuntimeException) {
                    // stop() can throw if no data was recorded
                } finally {
                    recorder.release()
                }
            }

            if (!outputFile.exists() || outputFile.length() == 0L) {
                return ToolResult.Error("Recording failed: output file is empty.", "RECORDING_FAILED")
            }

            val fileSizeKb = outputFile.length() / 1024.0

            val data = JSONObject().apply {
                put("file_path", outputFile.absolutePath)
                put("file_size_bytes", outputFile.length())
                put("file_size_kb", String.format("%.1f", fileSizeKb))
                put("duration_seconds", durationSeconds)
                put("format", extension)
            }

            ToolResult.Success(
                content = "Recorded ${durationSeconds}s of audio: ${outputFile.absolutePath} (${String.format("%.1f", fileSizeKb)} KB)",
                data = data,
                attachments = listOf(outputFile.absolutePath)
            )
        } catch (e: SecurityException) {
            ToolResult.Error("Microphone permission not granted.", "PERMISSION_DENIED")
        } catch (e: Exception) {
            // Clean up partial file on error
            if (outputFile.exists()) outputFile.delete()
            ToolResult.Error("Failed to record audio: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
