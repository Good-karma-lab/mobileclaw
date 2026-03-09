package com.guappa.app.tools.impl

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject
import java.util.Locale

class LocationTool : Tool {
    override val name = "get_location"
    override val description = "Get the current GPS location of the device including latitude, longitude, and address"
    override val requiredPermissions = listOf("ACCESS_FINE_LOCATION")
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "include_address": {
                    "type": "boolean",
                    "description": "Reverse-geocode the coordinates to a human-readable address (default: true)"
                },
                "provider": {
                    "type": "string",
                    "description": "Location provider: 'gps', 'network', or 'best' (default: 'best')"
                }
            },
            "required": []
        }
    """.trimIndent())

    @SuppressLint("MissingPermission")
    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val includeAddress = params.optBoolean("include_address", true)
        val providerPref = params.optString("provider", "best")

        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            val provider = when (providerPref) {
                "gps" -> LocationManager.GPS_PROVIDER
                "network" -> LocationManager.NETWORK_PROVIDER
                else -> {
                    // Pick best available provider
                    when {
                        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                            LocationManager.GPS_PROVIDER
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                            LocationManager.NETWORK_PROVIDER
                        else -> return ToolResult.Error(
                            "No location provider is enabled. Please enable GPS or network location.",
                            "PROVIDER_UNAVAILABLE"
                        )
                    }
                }
            }

            if (!locationManager.isProviderEnabled(provider)) {
                return ToolResult.Error(
                    "Location provider '$provider' is not enabled.",
                    "PROVIDER_UNAVAILABLE"
                )
            }

            @Suppress("DEPRECATION")
            val location: Location? = locationManager.getLastKnownLocation(provider)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)

            if (location == null) {
                return ToolResult.Error(
                    "Could not determine current location. Try moving to an open area with GPS signal.",
                    "LOCATION_UNAVAILABLE"
                )
            }

            val data = JSONObject().apply {
                put("latitude", location.latitude)
                put("longitude", location.longitude)
                put("altitude_meters", location.altitude)
                put("accuracy_meters", location.accuracy)
                put("speed_mps", location.speed)
                put("bearing", location.bearing)
                put("provider", location.provider ?: provider)
                put("timestamp_ms", location.time)
            }

            var addressText = ""
            if (includeAddress) {
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(
                        location.latitude, location.longitude, 1
                    )
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        addressText = buildString {
                            for (i in 0..addr.maxAddressLineIndex) {
                                if (i > 0) append(", ")
                                append(addr.getAddressLine(i))
                            }
                        }
                        data.put("address", addressText)
                        data.put("locality", addr.locality ?: "")
                        data.put("country", addr.countryName ?: "")
                        data.put("country_code", addr.countryCode ?: "")
                        data.put("postal_code", addr.postalCode ?: "")
                    }
                } catch (_: Exception) {
                    // Geocoding failed, continue without address
                }
            }

            val content = buildString {
                append("Location: ${location.latitude}, ${location.longitude}")
                append(" (accuracy: ${location.accuracy.toInt()}m)")
                if (addressText.isNotEmpty()) {
                    append("\nAddress: $addressText")
                }
            }

            ToolResult.Success(content = content, data = data)
        } catch (e: SecurityException) {
            ToolResult.Error("Location permission not granted.", "PERMISSION_DENIED")
        } catch (e: Exception) {
            ToolResult.Error("Failed to get location: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
