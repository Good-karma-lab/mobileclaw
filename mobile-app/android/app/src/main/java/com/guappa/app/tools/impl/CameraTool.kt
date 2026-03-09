package com.guappa.app.tools.impl

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraTool : Tool {
    override val name = "camera"
    override val description = "Take a photo using the device camera. Saves to app cache directory and returns the file path."
    override val requiredPermissions = listOf("CAMERA")
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "facing": {
                    "type": "string",
                    "description": "Camera facing direction: front or back (default: back)",
                    "enum": ["front", "back"]
                },
                "flash": {
                    "type": "string",
                    "description": "Flash mode: on, off, or auto (default: auto)",
                    "enum": ["on", "off", "auto"]
                }
            },
            "required": []
        }
    """.trimIndent())

    @Suppress("MissingPermission")
    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val facing = params.optString("facing", "back")
        val flash = params.optString("flash", "auto")

        val lensFacing = when (facing) {
            "front" -> CameraCharacteristics.LENS_FACING_FRONT
            else -> CameraCharacteristics.LENS_FACING_BACK
        }

        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == lensFacing
            } ?: return ToolResult.Error(
                "No $facing camera found on this device.",
                "CAMERA_NOT_FOUND"
            )

            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val streamConfigMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return ToolResult.Error("Failed to get camera stream configuration.", "CAMERA_ERROR")

            val jpegSizes = streamConfigMap.getOutputSizes(ImageFormat.JPEG)
            val bestSize = jpegSizes?.maxByOrNull { it.width * it.height }
                ?: return ToolResult.Error("No supported JPEG output sizes.", "CAMERA_ERROR")

            val handlerThread = HandlerThread("CameraCapture").apply { start() }
            val handler = Handler(handlerThread.looper)

            try {
                val filePath = withContext(Dispatchers.IO) {
                    capturePhoto(context, cameraManager, cameraId, bestSize.width, bestSize.height, flash, handler)
                }

                val file = File(filePath)
                val data = JSONObject().apply {
                    put("file_path", filePath)
                    put("file_size_bytes", file.length())
                    put("file_size_kb", String.format("%.1f", file.length() / 1024.0))
                    put("width", bestSize.width)
                    put("height", bestSize.height)
                    put("facing", facing)
                    put("flash", flash)
                }

                ToolResult.Success(
                    content = "Photo captured: $filePath (${String.format("%.1f", file.length() / 1024.0)} KB, ${bestSize.width}x${bestSize.height})",
                    data = data,
                    attachments = listOf(filePath)
                )
            } finally {
                handlerThread.quitSafely()
            }
        } catch (e: SecurityException) {
            ToolResult.Error("Camera permission not granted.", "PERMISSION_DENIED")
        } catch (e: Exception) {
            ToolResult.Error("Failed to capture photo: ${e.message}", "EXECUTION_ERROR")
        }
    }

    @Suppress("MissingPermission")
    private suspend fun capturePhoto(
        context: Context,
        cameraManager: CameraManager,
        cameraId: String,
        width: Int,
        height: Int,
        flash: String,
        handler: Handler
    ): String {
        val imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1)

        val camera = suspendCancellableCoroutine<CameraDevice> { cont ->
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    cont.resume(device)
                }
                override fun onDisconnected(device: CameraDevice) {
                    device.close()
                    cont.resumeWithException(RuntimeException("Camera disconnected"))
                }
                override fun onError(device: CameraDevice, error: Int) {
                    device.close()
                    cont.resumeWithException(RuntimeException("Camera error: $error"))
                }
            }, handler)
        }

        try {
            val session = suspendCancellableCoroutine<CameraCaptureSession> { cont ->
                camera.createCaptureSession(
                    listOf(imageReader.surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            cont.resume(session)
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            cont.resumeWithException(RuntimeException("Camera session configuration failed"))
                        }
                    },
                    handler
                )
            }

            val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(imageReader.surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                when (flash) {
                    "on" -> {
                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                        set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
                    }
                    "off" -> {
                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                    }
                    else -> {
                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                    }
                }
                set(CaptureRequest.JPEG_QUALITY, 90.toByte())
            }

            val imageBytes = withTimeoutOrNull(10000L) {
                suspendCancellableCoroutine<ByteArray> { cont ->
                    imageReader.setOnImageAvailableListener({ reader ->
                        val image = reader.acquireLatestImage()
                        if (image != null) {
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            image.close()
                            cont.resume(bytes)
                        }
                    }, handler)
                    session.capture(captureBuilder.build(), null, handler)
                }
            } ?: throw RuntimeException("Camera capture timed out after 10 seconds")

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val photoFile = File(context.cacheDir, "photo_$timestamp.jpg")
            photoFile.writeBytes(imageBytes)

            session.close()
            return photoFile.absolutePath
        } finally {
            camera.close()
            imageReader.close()
        }
    }
}
