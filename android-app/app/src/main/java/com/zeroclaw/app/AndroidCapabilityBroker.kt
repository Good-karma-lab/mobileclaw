package com.zeroclaw.app

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.provider.Telephony

class AndroidCapabilityBroker(private val context: Context) {
    fun launchApp(packageName: String): Result<String> {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return Result.failure(IllegalArgumentException("Package not launchable: $packageName"))
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return Result.success("launched:$packageName")
    }

    fun listInstalledApps(limit: Int = 50): List<String> {
        return context.packageManager.getInstalledApplications(0)
            .asSequence()
            .map { it.packageName }
            .sorted()
            .take(limit)
            .toList()
    }

    fun readSensorSnapshot(sensorName: String): String {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        return allSensors
            .firstOrNull { it.name.contains(sensorName, ignoreCase = true) }
            ?.let { "sensor:${it.name}" }
            ?: "sensor_not_found:$sensorName"
    }

    fun startSmsIntent(phoneNumber: String, body: String): Result<String> {
        if (!BuildConfig.ENABLE_SMS_CALLS) {
            return Result.failure(IllegalStateException("SMS disabled in this distribution"))
        }
        val uri = Uri.parse("smsto:$phoneNumber")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return Result.success("sms_intent_started")
    }

    fun startCallIntent(phoneNumber: String): Result<String> {
        if (!BuildConfig.ENABLE_SMS_CALLS) {
            return Result.failure(IllegalStateException("Call feature disabled in this distribution"))
        }
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return Result.success("call_intent_started")
    }

    fun defaultSmsAppPackage(): String {
        return Telephony.Sms.getDefaultSmsPackage(context).orEmpty()
    }
}
