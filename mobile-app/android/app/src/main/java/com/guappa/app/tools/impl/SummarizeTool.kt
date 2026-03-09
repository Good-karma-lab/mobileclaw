package com.guappa.app.tools.impl

import android.content.Context
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class SummarizeTool : Tool {
    override val name = "summarize"
    override val description = "Summarize a long text into a concise summary. Returns instructions for the orchestrator to route to the LLM for summarization."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "text": {
                    "type": "string",
                    "description": "The text to summarize"
                },
                "max_length": {
                    "type": "integer",
                    "description": "Target maximum length of the summary in words (default: 100)"
                },
                "style": {
                    "type": "string",
                    "description": "Summary style: 'brief', 'detailed', 'bullet_points', 'key_facts' (default: 'brief')"
                },
                "language": {
                    "type": "string",
                    "description": "Language for the summary output (default: same as input)"
                }
            },
            "required": ["text"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val text = params.optString("text", "")
        if (text.isEmpty()) {
            return ToolResult.Error("Text to summarize is required.", "INVALID_PARAMS")
        }

        val maxLength = params.optInt("max_length", 100)
        val style = params.optString("style", "brief")
        val language = params.optString("language", "")

        val styleInstruction = when (style) {
            "detailed" -> "Provide a detailed summary"
            "bullet_points" -> "Summarize as a bullet-point list"
            "key_facts" -> "Extract the key facts and present them concisely"
            else -> "Provide a brief summary"
        }

        val lengthInstruction = "Keep the summary under $maxLength words."
        val langInstruction = if (language.isNotEmpty()) " Write the summary in $language." else ""

        val prompt = "$styleInstruction of the following text. $lengthInstruction$langInstruction\n\n$text"

        val data = JSONObject().apply {
            put("type", "llm_delegation")
            put("prompt", prompt)
            put("input_length", text.length)
            put("style", style)
            put("max_length", maxLength)
            if (language.isNotEmpty()) put("language", language)
        }

        return ToolResult.Success(
            content = prompt,
            data = data
        )
    }
}
