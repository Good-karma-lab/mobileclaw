package com.guappa.app.tools.impl

import android.content.Context
import android.provider.ContactsContract
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONArray
import org.json.JSONObject

class GetContactsTool : Tool {
    override val name = "get_contacts"
    override val description = "Search and retrieve contacts from the device"
    override val requiredPermissions = listOf("READ_CONTACTS")
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "Search query to filter contacts by name"
                },
                "limit": {
                    "type": "integer",
                    "description": "Maximum number of contacts to return (default: 20, max: 100)"
                }
            },
            "required": []
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val query = params.optString("query", "")
        val limit = params.optInt("limit", 20).coerceIn(1, 100)

        return try {
            val selection = if (query.isNotEmpty()) {
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
            } else null
            val selectionArgs = if (query.isNotEmpty()) {
                arrayOf("%$query%")
            } else null

            val cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.HAS_PHONE_NUMBER
                ),
                selection,
                selectionArgs,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
            )

            val contacts = JSONArray()
            cursor?.use {
                var count = 0
                while (it.moveToNext() && count < limit) {
                    val contactId = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                    val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))
                    val hasPhone = it.getInt(it.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER))

                    val contact = JSONObject()
                    contact.put("id", contactId)
                    contact.put("name", name ?: "Unknown")

                    if (hasPhone > 0) {
                        val phoneCursor = context.contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(contactId),
                            null
                        )
                        val phones = JSONArray()
                        phoneCursor?.use { pc ->
                            while (pc.moveToNext()) {
                                val number = pc.getString(
                                    pc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                )
                                phones.put(number)
                            }
                        }
                        contact.put("phone_numbers", phones)
                    }

                    contacts.put(contact)
                    count++
                }
            }

            val data = JSONObject()
            data.put("contacts", contacts)
            data.put("count", contacts.length())

            ToolResult.Success(
                content = "Found ${contacts.length()} contact(s)",
                data = data
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to read contacts: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
