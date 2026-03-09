package com.guappa.app.tools.impl

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

class SensorTool : Tool {
    override val name = "sensor"
    override val description = "Read device sensor data: accelerometer, gyroscope, proximity, light, or step counter"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "sensor_type": {
                    "type": "string",
                    "description": "Type of sensor to read: accelerometer, gyroscope, proximity, light, step_counter, magnetic_field, pressure, gravity, rotation",
                    "enum": ["accelerometer", "gyroscope", "proximity", "light", "step_counter", "magnetic_field", "pressure", "gravity", "rotation"]
                },
                "duration_ms": {
                    "type": "integer",
                    "description": "Duration to collect sensor data in milliseconds (default: 1000, max: 10000)"
                }
            },
            "required": ["sensor_type"]
        }
    """.trimIndent())

    private val sensorTypeMap = mapOf(
        "accelerometer" to Sensor.TYPE_ACCELEROMETER,
        "gyroscope" to Sensor.TYPE_GYROSCOPE,
        "proximity" to Sensor.TYPE_PROXIMITY,
        "light" to Sensor.TYPE_LIGHT,
        "step_counter" to Sensor.TYPE_STEP_COUNTER,
        "magnetic_field" to Sensor.TYPE_MAGNETIC_FIELD,
        "pressure" to Sensor.TYPE_PRESSURE,
        "gravity" to Sensor.TYPE_GRAVITY,
        "rotation" to Sensor.TYPE_ROTATION_VECTOR
    )

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val sensorTypeName = params.optString("sensor_type", "")
        if (sensorTypeName.isEmpty()) {
            return ToolResult.Error("sensor_type is required.", "INVALID_PARAMS")
        }

        val sensorTypeId = sensorTypeMap[sensorTypeName]
            ?: return ToolResult.Error(
                "Unknown sensor_type: $sensorTypeName. Valid types: ${sensorTypeMap.keys.joinToString()}",
                "INVALID_PARAMS"
            )

        val durationMs = params.optInt("duration_ms", 1000).coerceIn(100, 10000)

        return try {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = sensorManager.getDefaultSensor(sensorTypeId)
                ?: return ToolResult.Error(
                    "Sensor '$sensorTypeName' is not available on this device.",
                    "SENSOR_UNAVAILABLE"
                )

            val readings = mutableListOf<FloatArray>()
            val timestamps = mutableListOf<Long>()

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    synchronized(readings) {
                        readings.add(event.values.clone())
                        timestamps.add(event.timestamp)
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)

            // Collect for the requested duration, then unregister
            delay(durationMs.toLong())
            sensorManager.unregisterListener(listener)

            if (readings.isEmpty()) {
                return ToolResult.Error(
                    "No sensor data received from '$sensorTypeName' within ${durationMs}ms.",
                    "NO_DATA"
                )
            }

            // Compute statistics
            val numValues = readings.first().size
            val avgValues = FloatArray(numValues)
            val minValues = FloatArray(numValues) { Float.MAX_VALUE }
            val maxValues = FloatArray(numValues) { Float.MIN_VALUE }

            for (reading in readings) {
                for (i in 0 until numValues.coerceAtMost(reading.size)) {
                    avgValues[i] += reading[i]
                    if (reading[i] < minValues[i]) minValues[i] = reading[i]
                    if (reading[i] > maxValues[i]) maxValues[i] = reading[i]
                }
            }
            for (i in avgValues.indices) {
                avgValues[i] /= readings.size
            }

            val axisLabels = getAxisLabels(sensorTypeName, numValues)

            val data = JSONObject().apply {
                put("sensor_type", sensorTypeName)
                put("sample_count", readings.size)
                put("duration_ms", durationMs)
                put("unit", getSensorUnit(sensorTypeName))

                val avg = JSONObject()
                val min = JSONObject()
                val max = JSONObject()
                for (i in 0 until numValues) {
                    avg.put(axisLabels[i], String.format("%.4f", avgValues[i]).toDouble())
                    min.put(axisLabels[i], String.format("%.4f", minValues[i]).toDouble())
                    max.put(axisLabels[i], String.format("%.4f", maxValues[i]).toDouble())
                }
                put("average", avg)
                put("min", min)
                put("max", max)

                // Include last few raw readings (up to 10)
                val rawArray = JSONArray()
                val lastReadings = readings.takeLast(10)
                for (reading in lastReadings) {
                    val entry = JSONObject()
                    for (i in 0 until numValues.coerceAtMost(reading.size)) {
                        entry.put(axisLabels[i], String.format("%.4f", reading[i]).toDouble())
                    }
                    rawArray.put(entry)
                }
                put("last_readings", rawArray)
            }

            val summary = buildString {
                appendLine("Sensor: $sensorTypeName (${readings.size} samples over ${durationMs}ms)")
                appendLine("Unit: ${getSensorUnit(sensorTypeName)}")
                appendLine("Average: ${axisLabels.indices.joinToString { "${axisLabels[it]}=${String.format("%.4f", avgValues[it])}" }}")
                appendLine("Min: ${axisLabels.indices.joinToString { "${axisLabels[it]}=${String.format("%.4f", minValues[it])}" }}")
                append("Max: ${axisLabels.indices.joinToString { "${axisLabels[it]}=${String.format("%.4f", maxValues[it])}" }}")
            }

            ToolResult.Success(content = summary, data = data)
        } catch (e: Exception) {
            ToolResult.Error("Failed to read sensor: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun getAxisLabels(sensorType: String, numValues: Int): List<String> {
        return when (sensorType) {
            "accelerometer", "gyroscope", "magnetic_field", "gravity" -> listOf("x", "y", "z")
            "rotation" -> if (numValues >= 5) listOf("x", "y", "z", "cos", "accuracy") else listOf("x", "y", "z")
            "proximity" -> listOf("distance")
            "light" -> listOf("lux")
            "step_counter" -> listOf("steps")
            "pressure" -> listOf("hPa")
            else -> (0 until numValues).map { "v$it" }
        }
    }

    private fun getSensorUnit(sensorType: String): String {
        return when (sensorType) {
            "accelerometer", "gravity" -> "m/s^2"
            "gyroscope" -> "rad/s"
            "proximity" -> "cm"
            "light" -> "lux"
            "step_counter" -> "steps"
            "magnetic_field" -> "uT"
            "pressure" -> "hPa"
            "rotation" -> "unitless"
            else -> "unknown"
        }
    }
}
