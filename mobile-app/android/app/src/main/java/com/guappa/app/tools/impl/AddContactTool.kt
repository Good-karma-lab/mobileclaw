package com.guappa.app.tools.impl

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class AddContactTool : Tool {
    override val name = "add_contact"
    override val description = "Add a new contact to the device address book"
    override val requiredPermissions = listOf("WRITE_CONTACTS")
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string",
                    "description": "Full name of the contact"
                },
                "phone": {
                    "type": "string",
                    "description": "Phone number"
                },
                "email": {
                    "type": "string",
                    "description": "Email address (optional)"
                },
                "company": {
                    "type": "string",
                    "description": "Company or organization (optional)"
                },
                "notes": {
                    "type": "string",
                    "description": "Notes about the contact (optional)"
                }
            },
            "required": ["name", "phone"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val name = params.optString("name", "")
        val phone = params.optString("phone", "")

        if (name.isEmpty()) {
            return ToolResult.Error("Contact name is required.", "INVALID_PARAMS")
        }
        if (phone.isEmpty()) {
            return ToolResult.Error("Phone number is required.", "INVALID_PARAMS")
        }

        val email = params.optString("email", "")
        val company = params.optString("company", "")
        val notes = params.optString("notes", "")

        return try {
            val ops = ArrayList<ContentProviderOperation>()

            // Insert raw contact
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )

            // Display name
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build()
            )

            // Phone number
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                    .withValue(
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                    )
                    .build()
            )

            // Email (optional)
            if (email.isNotEmpty()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.Email.DATA, email)
                        .withValue(
                            ContactsContract.CommonDataKinds.Email.TYPE,
                            ContactsContract.CommonDataKinds.Email.TYPE_WORK
                        )
                        .build()
                )
            }

            // Company (optional)
            if (company.isNotEmpty()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, company)
                        .build()
                )
            }

            // Notes (optional)
            if (notes.isNotEmpty()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.Note.NOTE, notes)
                        .build()
                )
            }

            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)

            val data = JSONObject().apply {
                put("name", name)
                put("phone", phone)
                if (email.isNotEmpty()) put("email", email)
                if (company.isNotEmpty()) put("company", company)
            }

            ToolResult.Success(
                content = "Contact '$name' added with phone $phone",
                data = data
            )
        } catch (e: Exception) {
            ToolResult.Error("Failed to add contact: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
