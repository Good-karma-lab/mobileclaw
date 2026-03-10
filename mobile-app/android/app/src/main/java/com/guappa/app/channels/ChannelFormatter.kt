package com.guappa.app.channels

/**
 * Adapts message formatting per channel requirements.
 * Each channel has different Markdown support and message length limits.
 */
object ChannelFormatter {

    /**
     * Format a message for a specific channel.
     */
    fun format(channelId: String, text: String): String {
        return when (channelId) {
            "telegram" -> formatTelegram(text)
            "discord" -> formatDiscord(text)
            "slack" -> formatSlack(text)
            "sms" -> formatSms(text)
            "email" -> text // Full Markdown ok
            else -> text
        }
    }

    /**
     * Telegram: Supports MarkdownV2 with specific escaping rules.
     * Max message length: 4096 characters.
     */
    private fun formatTelegram(text: String): String {
        val truncated = if (text.length > 4000) text.take(4000) + "…" else text
        return truncated
    }

    /**
     * Discord: Supports standard Markdown.
     * Max message length: 2000 characters.
     */
    private fun formatDiscord(text: String): String {
        val truncated = if (text.length > 1950) text.take(1950) + "…" else text
        return truncated
    }

    /**
     * Slack: Uses mrkdwn format (slightly different from Markdown).
     * Bold: *text*, Italic: _text_, Code: `text`, Link: <url|text>
     */
    private fun formatSlack(text: String): String {
        return text
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "*$1*")        // **bold** → *bold*
            .replace(Regex("\\[(.+?)\\]\\((.+?)\\)"), "<$2|$1>") // [text](url) → <url|text>
            .let { if (it.length > 3900) it.take(3900) + "…" else it }
    }

    /**
     * SMS: Plain text only, max 1600 characters (10 segments).
     */
    private fun formatSms(text: String): String {
        return text
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")     // Remove bold
            .replace(Regex("\\*(.+?)\\*"), "$1")             // Remove italic
            .replace(Regex("`(.+?)`"), "$1")                  // Remove code
            .replace(Regex("\\[(.+?)\\]\\((.+?)\\)"), "$1: $2") // Links as "text: url"
            .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "") // Remove headers
            .let { if (it.length > 1500) it.take(1500) + "…" else it }
    }
}
