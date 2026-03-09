package com.guappa.app.tools.impl

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class CalendarTool : Tool {
    override val name = "calendar"
    override val description = "Read, create, update, or delete calendar events via the device calendar"
    override val requiredPermissions = listOf("READ_CALENDAR", "WRITE_CALENDAR")
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "action": {
                    "type": "string",
                    "description": "Action to perform: read, create, update, delete",
                    "enum": ["read", "create", "update", "delete"]
                },
                "event_id": {
                    "type": "integer",
                    "description": "Event ID (required for update/delete)"
                },
                "title": {
                    "type": "string",
                    "description": "Event title (required for create)"
                },
                "start_time": {
                    "type": "string",
                    "description": "Start time in ISO 8601 format, e.g. 2026-03-10T14:00:00"
                },
                "end_time": {
                    "type": "string",
                    "description": "End time in ISO 8601 format, e.g. 2026-03-10T15:00:00"
                },
                "description": {
                    "type": "string",
                    "description": "Event description/notes"
                },
                "location": {
                    "type": "string",
                    "description": "Event location"
                },
                "days_ahead": {
                    "type": "integer",
                    "description": "For read: number of days ahead to query (default: 7)"
                }
            },
            "required": ["action"]
        }
    """.trimIndent())

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val action = params.optString("action", "")
        if (action.isEmpty()) {
            return ToolResult.Error("Action is required.", "INVALID_PARAMS")
        }

        return when (action) {
            "read" -> readEvents(params, context)
            "create" -> createEvent(params, context)
            "update" -> updateEvent(params, context)
            "delete" -> deleteEvent(params, context)
            else -> ToolResult.Error("Unknown action: $action. Use read, create, update, or delete.", "INVALID_PARAMS")
        }
    }

    private suspend fun readEvents(params: JSONObject, context: Context): ToolResult {
        val daysAhead = params.optInt("days_ahead", 7).coerceIn(1, 90)

        return try {
            val now = Calendar.getInstance()
            val startMillis = now.timeInMillis
            now.add(Calendar.DAY_OF_YEAR, daysAhead)
            val endMillis = now.timeInMillis

            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.ALL_DAY
            )

            val selection = "(${CalendarContract.Events.DTSTART} >= ?) AND (${CalendarContract.Events.DTSTART} <= ?)"
            val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())

            val events = JSONArray()
            withContext(Dispatchers.IO) {
                context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${CalendarContract.Events.DTSTART} ASC"
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val event = JSONObject()
                        event.put("id", cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)))
                        event.put("title", cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)) ?: "")
                        val dtStart = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART))
                        val dtEnd = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND))
                        event.put("start_time", dateFormat.format(dtStart))
                        event.put("end_time", if (dtEnd > 0) dateFormat.format(dtEnd) else "")
                        event.put("description", cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)) ?: "")
                        event.put("location", cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)) ?: "")
                        event.put("all_day", cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)) == 1)
                        events.put(event)
                    }
                }
            }

            val data = JSONObject().apply {
                put("events", events)
                put("count", events.length())
                put("days_ahead", daysAhead)
            }

            ToolResult.Success(
                content = "Found ${events.length()} event(s) in the next $daysAhead day(s)",
                data = data
            )
        } catch (e: SecurityException) {
            ToolResult.Error("Calendar permission not granted.", "PERMISSION_DENIED")
        } catch (e: Exception) {
            ToolResult.Error("Failed to read calendar: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private suspend fun createEvent(params: JSONObject, context: Context): ToolResult {
        val title = params.optString("title", "")
        if (title.isEmpty()) {
            return ToolResult.Error("Title is required for creating an event.", "INVALID_PARAMS")
        }

        val startTimeStr = params.optString("start_time", "")
        if (startTimeStr.isEmpty()) {
            return ToolResult.Error("start_time is required for creating an event.", "INVALID_PARAMS")
        }

        val startMillis = try {
            dateFormat.parse(startTimeStr)?.time
                ?: return ToolResult.Error("Invalid start_time format. Use ISO 8601: yyyy-MM-ddTHH:mm:ss", "INVALID_PARAMS")
        } catch (e: Exception) {
            return ToolResult.Error("Invalid start_time format. Use ISO 8601: yyyy-MM-ddTHH:mm:ss", "INVALID_PARAMS")
        }

        val endTimeStr = params.optString("end_time", "")
        val endMillis = if (endTimeStr.isNotEmpty()) {
            try {
                dateFormat.parse(endTimeStr)?.time ?: (startMillis + 3600000)
            } catch (e: Exception) {
                startMillis + 3600000 // Default to 1 hour
            }
        } else {
            startMillis + 3600000 // Default to 1 hour
        }

        val description = params.optString("description", "")
        val location = params.optString("location", "")

        return try {
            // Get the first available calendar ID
            val calendarId = withContext(Dispatchers.IO) {
                getDefaultCalendarId(context)
            } ?: return ToolResult.Error("No calendar account found on this device.", "NO_CALENDAR")

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                if (description.isNotEmpty()) put(CalendarContract.Events.DESCRIPTION, description)
                if (location.isNotEmpty()) put(CalendarContract.Events.EVENT_LOCATION, location)
            }

            val uri = withContext(Dispatchers.IO) {
                context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            } ?: return ToolResult.Error("Failed to insert calendar event.", "EXECUTION_ERROR")

            val eventId = ContentUris.parseId(uri)
            val data = JSONObject().apply {
                put("event_id", eventId)
                put("title", title)
                put("start_time", startTimeStr)
                put("end_time", if (endTimeStr.isNotEmpty()) endTimeStr else dateFormat.format(endMillis))
            }

            ToolResult.Success(
                content = "Created event '$title' (ID: $eventId) starting at $startTimeStr",
                data = data
            )
        } catch (e: SecurityException) {
            ToolResult.Error("Calendar permission not granted.", "PERMISSION_DENIED")
        } catch (e: Exception) {
            ToolResult.Error("Failed to create event: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private suspend fun updateEvent(params: JSONObject, context: Context): ToolResult {
        val eventId = params.optLong("event_id", -1)
        if (eventId < 0) {
            return ToolResult.Error("event_id is required for update.", "INVALID_PARAMS")
        }

        val values = ContentValues()
        var fieldsUpdated = 0

        params.optString("title", "").takeIf { it.isNotEmpty() }?.let {
            values.put(CalendarContract.Events.TITLE, it)
            fieldsUpdated++
        }
        params.optString("start_time", "").takeIf { it.isNotEmpty() }?.let { str ->
            try {
                dateFormat.parse(str)?.time?.let { millis ->
                    values.put(CalendarContract.Events.DTSTART, millis)
                    fieldsUpdated++
                }
            } catch (_: Exception) {}
        }
        params.optString("end_time", "").takeIf { it.isNotEmpty() }?.let { str ->
            try {
                dateFormat.parse(str)?.time?.let { millis ->
                    values.put(CalendarContract.Events.DTEND, millis)
                    fieldsUpdated++
                }
            } catch (_: Exception) {}
        }
        params.optString("description", "").takeIf { it.isNotEmpty() }?.let {
            values.put(CalendarContract.Events.DESCRIPTION, it)
            fieldsUpdated++
        }
        params.optString("location", "").takeIf { it.isNotEmpty() }?.let {
            values.put(CalendarContract.Events.EVENT_LOCATION, it)
            fieldsUpdated++
        }

        if (fieldsUpdated == 0) {
            return ToolResult.Error("No fields to update. Provide title, start_time, end_time, description, or location.", "INVALID_PARAMS")
        }

        return try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val rows = withContext(Dispatchers.IO) {
                context.contentResolver.update(uri, values, null, null)
            }

            if (rows > 0) {
                ToolResult.Success("Updated event ID $eventId ($fieldsUpdated field(s) changed)")
            } else {
                ToolResult.Error("Event ID $eventId not found.", "EVENT_NOT_FOUND")
            }
        } catch (e: SecurityException) {
            ToolResult.Error("Calendar permission not granted.", "PERMISSION_DENIED")
        } catch (e: Exception) {
            ToolResult.Error("Failed to update event: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private suspend fun deleteEvent(params: JSONObject, context: Context): ToolResult {
        val eventId = params.optLong("event_id", -1)
        if (eventId < 0) {
            return ToolResult.Error("event_id is required for delete.", "INVALID_PARAMS")
        }

        return try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val rows = withContext(Dispatchers.IO) {
                context.contentResolver.delete(uri, null, null)
            }

            if (rows > 0) {
                ToolResult.Success("Deleted event ID $eventId")
            } else {
                ToolResult.Error("Event ID $eventId not found.", "EVENT_NOT_FOUND")
            }
        } catch (e: SecurityException) {
            ToolResult.Error("Calendar permission not granted.", "PERMISSION_DENIED")
        } catch (e: Exception) {
            ToolResult.Error("Failed to delete event: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun getDefaultCalendarId(context: Context): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY
        )

        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.VISIBLE} = 1",
            null,
            "${CalendarContract.Calendars.IS_PRIMARY} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
            }
        }
        return null
    }
}
