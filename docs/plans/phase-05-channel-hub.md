# Phase 5: Channel Hub — Messenger Integrations

**Date**: 2026-03-06
**Status**: Proposal
**Depends On**: Phase 1 (Foundation), Phase 4 (Proactive Agent)
**Blocks**: Phase 8 (Documentation)

---

## 1. Objective

Enable Guappa to communicate through multiple messenger channels simultaneously: Telegram, Discord, Slack, WhatsApp, Signal, Matrix, Email, and SMS — in addition to the in-app chat.

---

## 2. Research Checklist

- [ ] Telegram Bot API (latest) — long polling, webhooks, inline keyboards, file upload
- [ ] Discord Bot API — gateway WebSocket, slash commands, message intents
- [ ] Slack Bot API — Events API, Socket Mode, Block Kit formatting
- [ ] WhatsApp Business API — Cloud API, message templates, 24h window
- [ ] Signal — signald / signal-cli integration, E2E encryption
- [ ] Matrix — Client-Server API, E2EE (libolm/vodozemac), Synapse homeserver
- [ ] Email — IMAP (idle push), SMTP, JavaMail / Jakarta Mail
- [ ] Android SMS — SmsManager, BroadcastReceiver for incoming

---

## 3. Architecture

### 3.1 Module Structure

```
android/app/src/main/java/com/guappa/app/
└── channels/
    ├── ChannelHub.kt                — manages all channels, routes messages, health checks
    ├── ChannelInterface.kt          — base interface (connect, disconnect, send, listen, health)
    ├── ChannelConfig.kt             — per-channel configuration
    ├── ChannelFormatter.kt          — base formatter (markdown → channel-specific format)
    ├── ChannelRouter.kt             — route agent responses to correct channel
    ├── ChannelHealthMonitor.kt      — periodic health checks, auto-reconnect
    │
    ├── inapp/
    │   └── InAppChannel.kt          — direct MessageBus bridge (always active)
    │
    ├── telegram/
    │   ├── TelegramChannel.kt       — Telegram Bot API client
    │   ├── TelegramPolling.kt       — long polling for updates
    │   ├── TelegramWebhook.kt       — webhook mode (if server available)
    │   ├── TelegramFormatter.kt     — markdown → Telegram MarkdownV2
    │   └── TelegramKeyboard.kt      — inline keyboard builder for questions
    │
    ├── discord/
    │   ├── DiscordChannel.kt        — Discord bot (gateway WebSocket)
    │   ├── DiscordGateway.kt        — WebSocket connection, heartbeat, resume
    │   ├── DiscordFormatter.kt      — markdown → Discord formatting
    │   └── DiscordSlashCommands.kt  — register and handle slash commands
    │
    ├── slack/
    │   ├── SlackChannel.kt          — Slack Socket Mode client
    │   ├── SlackSocketMode.kt       — WebSocket for events
    │   ├── SlackFormatter.kt        — markdown → Slack Block Kit mrkdwn
    │   └── SlackInteractions.kt     — handle button clicks, modals
    │
    ├── whatsapp/
    │   ├── WhatsAppChannel.kt       — WhatsApp Cloud API
    │   ├── WhatsAppFormatter.kt     — message templates, rich messages
    │   └── WhatsAppMedia.kt         — media upload/download
    │
    ├── signal/
    │   └── SignalChannel.kt         — Signal via signald/signal-cli (subprocess or socket)
    │
    ├── matrix/
    │   ├── MatrixChannel.kt         — Matrix Client-Server API
    │   ├── MatrixSync.kt            — long-poll sync loop
    │   ├── MatrixE2EE.kt            — end-to-end encryption (vodozemac)
    │   └── MatrixFormatter.kt       — markdown → Matrix HTML
    │
    ├── email/
    │   ├── EmailChannel.kt          — IMAP receive + SMTP send
    │   ├── ImapListener.kt          — IMAP IDLE push for new emails
    │   ├── SmtpSender.kt            — send emails via SMTP
    │   └── EmailParser.kt           — parse email body (HTML → text)
    │
    └── sms/
        └── SmsChannel.kt            — Android SMS as a channel
```

### 3.2 Channel Interface

```kotlin
interface Channel {
    val channelId: String         // "telegram", "discord", etc.
    val displayName: String       // "Telegram Bot"

    /** Connect to the channel (start polling/WebSocket/IMAP idle). */
    suspend fun connect(config: ChannelConfig): Result<Unit>

    /** Disconnect gracefully. */
    suspend fun disconnect()

    /** Send a message to the channel. */
    suspend fun send(message: OutgoingMessage): Result<Unit>

    /** Listen for incoming messages (Flow). */
    fun incoming(): Flow<IncomingMessage>

    /** Check if channel is connected and healthy. */
    suspend fun healthCheck(): HealthStatus

    /** Whether the channel is currently connected. */
    val isConnected: StateFlow<Boolean>
}

data class IncomingMessage(
    val channelId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val attachments: List<Attachment> = emptyList(),
    val replyToMessageId: String? = null,
    val rawPayload: JsonObject? = null,
)

data class OutgoingMessage(
    val channelId: String,
    val text: String,                      // markdown
    val attachments: List<Attachment> = emptyList(),
    val replyToMessageId: String? = null,
    val keyboard: List<KeyboardButton>? = null,  // inline buttons
)
```

### 3.3 Channel Hub

```kotlin
class ChannelHub(
    private val channels: Map<String, Channel>,
    private val messageBus: MessageBus,
    private val config: StateFlow<Map<String, ChannelConfig>>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        // Connect all configured channels
        scope.launch {
            config.collect { configs ->
                for ((channelId, channelConfig) in configs) {
                    if (channelConfig.enabled) {
                        connectChannel(channelId, channelConfig)
                    } else {
                        disconnectChannel(channelId)
                    }
                }
            }
        }
    }

    private fun connectChannel(channelId: String, config: ChannelConfig) {
        val channel = channels[channelId] ?: return

        scope.launch {
            channel.connect(config)
            // Forward incoming messages to MessageBus
            channel.incoming().collect { msg ->
                messageBus.emit(AgentEvent.UserMessage(
                    sessionId = "channel:${channelId}:${msg.senderId}",
                    text = msg.text,
                    channel = channelId,
                    attachments = msg.attachments,
                ))
            }
        }
    }

    /** Route agent response to the correct channel. */
    suspend fun sendResponse(sessionId: String, message: OutgoingMessage) {
        val channelId = extractChannelId(sessionId)
        val channel = channels[channelId] ?: return
        val formatted = formatForChannel(channelId, message)
        channel.send(formatted)
    }
}
```

---

## 4. Channel-Specific Details

### 4.1 Telegram

```kotlin
class TelegramChannel : Channel {
    override val channelId = "telegram"

    /** Long polling for updates. */
    override fun incoming(): Flow<IncomingMessage> = flow {
        var offset = 0L
        while (currentCoroutineContext().isActive) {
            val updates = api.getUpdates(offset = offset, timeout = 30)
            for (update in updates) {
                offset = update.updateId + 1
                val msg = update.message ?: continue

                emit(IncomingMessage(
                    channelId = "telegram",
                    senderId = msg.from.id.toString(),
                    senderName = msg.from.firstName,
                    text = msg.text ?: "",
                    attachments = parseAttachments(msg),
                ))
            }
        }
    }

    override suspend fun send(message: OutgoingMessage): Result<Unit> {
        val chatId = message.recipientId
        api.sendMessage(
            chatId = chatId,
            text = TelegramFormatter.format(message.text),
            parseMode = "MarkdownV2",
            replyMarkup = message.keyboard?.let { buildInlineKeyboard(it) },
        )
        return Result.success(Unit)
    }
}
```

**Telegram features:**
- Long polling (no server needed) or webhook (if HTTPS endpoint available)
- Inline keyboards for questions/approvals
- File/photo/voice message support
- Bot commands (/start, /help, /status)
- Allowlist: only respond to configured chat IDs

### 4.2 Discord

**Discord features:**
- Gateway WebSocket (persistent connection, heartbeat)
- Slash commands (/ask, /task, /status)
- Message components (buttons, select menus)
- File upload/download
- Channel-specific: only respond in configured channels
- Thread support for multi-step tasks

### 4.3 Email

```kotlin
class EmailChannel : Channel {
    override val channelId = "email"

    /** IMAP IDLE for push notifications of new emails. */
    override fun incoming(): Flow<IncomingMessage> = callbackFlow {
        val store = Session.getInstance(imapProperties).getStore("imaps")
        store.connect(config.host, config.email, config.password)
        val inbox = store.getFolder("INBOX").apply { open(Folder.READ_WRITE) }

        inbox.addMessageCountListener(object : MessageCountAdapter() {
            override fun messagesAdded(e: MessageCountEvent) {
                for (msg in e.messages) {
                    trySend(IncomingMessage(
                        channelId = "email",
                        senderId = msg.from.firstOrNull()?.toString() ?: "",
                        senderName = msg.from.firstOrNull()?.toString() ?: "",
                        text = "Subject: ${msg.subject}\n\n${extractBody(msg)}",
                    ))
                }
            }
        })

        // Start IDLE loop
        while (isActive) {
            (inbox as IMAPFolder).idle()
        }

        awaitClose { inbox.close(false); store.close() }
    }
}
```

---

## 5. Access Control (Allowlist)

```kotlin
data class ChannelConfig(
    val enabled: Boolean = false,
    val token: String = "",  // bot token / API key
    val allowedSenders: Set<String> = emptySet(),  // empty = allow all (dangerous!)
    val allowedChannelIds: Set<String> = emptySet(),  // for Discord/Slack
    val customSettings: Map<String, String> = emptyMap(),
)

class ChannelAccessControl {
    fun isAllowed(channelId: String, senderId: String, config: ChannelConfig): Boolean {
        if (config.allowedSenders.isEmpty()) {
            // Default: only allow configured sender (security)
            return false
        }
        return senderId in config.allowedSenders
    }
}
```

---

## 6. Configuration

```kotlin
data class ChannelHubConfig(
    val channels: Map<String, ChannelConfig> = mapOf(
        "telegram" to ChannelConfig(),
        "discord" to ChannelConfig(),
        "slack" to ChannelConfig(),
        "whatsapp" to ChannelConfig(),
        "signal" to ChannelConfig(),
        "matrix" to ChannelConfig(),
        "email" to ChannelConfig(),
        "sms" to ChannelConfig(enabled = true),  // SMS always available
    ),
    val healthCheckIntervalMinutes: Int = 5,
    val autoReconnect: Boolean = true,
    val autoReconnectMaxRetries: Int = 10,
    val autoReconnectBackoffMs: Long = 5_000,
)
```

---

## 7. Test Plan

| Test | Description |
|------|-------------|
| `TelegramChannel_Polling` | Mock API → messages flow through |
| `TelegramChannel_Send` | Send message → correct API call |
| `DiscordChannel_Gateway` | Mock WebSocket → connection established |
| `EmailChannel_IMAP` | Mock IMAP → new email → IncomingMessage |
| `ChannelHub_Routing` | Agent response → sent to correct channel |
| `ChannelHub_HotSwap` | Enable Telegram in settings → connects immediately |
| `AccessControl_Blocked` | Unknown sender → message rejected |
| `HealthCheck_Reconnect` | Disconnect → auto-reconnect |

---

## 8. Acceptance Criteria

- [ ] Telegram bot receives and responds to messages via long polling
- [ ] Discord bot handles messages and slash commands via gateway
- [ ] Slack bot receives events via Socket Mode
- [ ] Email channel listens via IMAP IDLE and sends via SMTP
- [ ] SMS channel works via Android API
- [ ] Agent proactive messages sent to all connected channels
- [ ] Access control (allowlist) prevents unauthorized access
- [ ] Auto-reconnect on connection loss
- [ ] Hot-swap: enable/disable channels from Settings without restart
- [ ] Health monitoring with status in Settings UI
