package com.guappa.app

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloaderModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "ModelDownloader"

    private fun modelsDir(): File {
        val dir = File(reactApplicationContext.filesDir, ".zeroclaw/models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    @ReactMethod
    fun getModelPath(modelName: String, promise: Promise) {
        try {
            val file = File(modelsDir(), modelName)
            promise.resolve(file.absolutePath)
        } catch (e: Exception) {
            promise.reject("MODEL_PATH_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun isModelDownloaded(modelName: String, promise: Promise) {
        try {
            val file = File(modelsDir(), modelName)
            promise.resolve(file.exists() && file.length() > 0)
        } catch (e: Exception) {
            promise.reject("MODEL_CHECK_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun downloadModel(urlStr: String, destPath: String, promise: Promise) {
        Thread {
            try {
                val url = URL(urlStr)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                connection.connect()

                if (connection.responseCode != 200) {
                    promise.reject("DOWNLOAD_ERROR", "HTTP ${connection.responseCode}")
                    return@Thread
                }

                val totalBytes = connection.contentLengthLong
                val destFile = File(destPath)
                destFile.parentFile?.mkdirs()

                val input = connection.inputStream
                val output = FileOutputStream(destFile)
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead

                    if (totalBytes > 0) {
                        val progress = (totalRead.toDouble() / totalBytes * 100).toInt()
                        reactApplicationContext
                            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                            .emit("ModelDownloadProgress", progress)
                    }
                }

                output.close()
                input.close()
                connection.disconnect()
                promise.resolve(destPath)
            } catch (e: Exception) {
                promise.reject("DOWNLOAD_ERROR", e.message, e)
            }
        }.start()
    }
}
