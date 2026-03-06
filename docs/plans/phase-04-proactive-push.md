# Phase 4: Proactive Agent & Push Notifications

**Date**: 2026-03-06
**Status**: Proposal
**Depends On**: Phase 1 (Foundation), Phase 3 (Tool Engine)
**Blocks**: Phase 5 (Channel Hub)

---

## 1. Objective

Make Guappa proactive — she initiates conversations, reports task completion, asks clarifying questions, reacts to device events, and delivers everything via push notifications when the app is in background.

---

## 2. Research Checklist

- [ ] Android `NotificationManager` — channels, priorities, styles, actions, direct reply
- [ ] `NotificationListenerService` — intercepting other apps' notifications
- [ ] `MessagingStyle` notifications (Android 11+) — conversation bubbles
- [ ] Android 13+ `POST_NOTIFICATIONS` runtime permission
- [ ] Android 15+ Notification Cooldown — reduces volume for rapid notifications
- [ ] Android 16 Notification Organizer — AI auto-categorization (Pixel)
- [ ] App standby buckets (active/working_set/frequent/restricted) — throttling
- [ ] OEM battery kill behavior (Samsung, Xiaomi, OnePlus, Huawei)
- [ ] Firebase Cloud Messaging (FCM) — high-priority for Doze bypass
- [ ] `WorkManager` for deferrable scheduled tasks
- [ ] `AlarmManager.setExactAndAllowWhileIdle()` for time-critical triggers
- [ ] `ContentObserver` for monitoring SMS, call log, calendar changes
- [ ] `BroadcastReceiver` for system events (SMS_RECEIVED, PHONE_STATE, etc.)

---

## 3. Architecture

### 3.1 Module Structure

```
android/app/src/main/java/com/guappa/app/
├── notifications/
│   ├── GuappaNotificationManager.kt    — notification creation, channels, grouping, dedup
│   ├── NotificationChannels.kt         — channel definitions (IDs, importance, behavior)
│   ├── ConversationNotification.kt     — MessagingStyle chat notifications with avatar
│   ├── TaskNotification.kt             — task progress/completion (BigTextStyle + progress bar)
│   ├── QuestionNotification.kt         — clarifying question with inline reply action
│   ├── AlertNotification.kt            — urgent alerts (MAX priority, full-screen intent)
│   ├── SilentNotification.kt           — low-priority status updates (no sound)
│   ├── NotificationActionReceiver.kt   — handles notification button clicks + inline replies
│   ├── NotificationDeduplicator.kt     — prevent duplicate notifications for same event
│   └── NotificationHistory.kt          — track sent notifications for cooldown logic
│
├── proactive/
│   ├── ProactiveEngine.kt              — decision engine: should Guappa message the user?
│   ├── ProactiveConfig.kt              — user preferences for proactive behavior
│   ├── TaskCompletionReporter.kt       — reports task completion to chat + push
│   ├── ClarificationRequester.kt       — asks user questions when agent is stuck
│   ├── ScheduledCheckIn.kt             — periodic check-ins (morning briefing, daily summary)
│   ├── EventReactor.kt                 — react to device events (SMS, call, calendar)
│   ├── SmartTiming.kt                  — don't notify at 3am, respect DND mode
│   └── ProactiveRules.kt              — configurable rules engine (trigger → condition → action)
│
├── triggers/
│   ├── TriggerManager.kt               — register and manage all trigger sources
│   ├── SmsReceiver.kt                  — BroadcastReceiver for incoming SMS
│   ├── CallStateReceiver.kt            — BroadcastReceiver for incoming/missed calls
│   ├── CalendarObserver.kt             — ContentObserver for calendar changes
│   ├── NotificationListener.kt         — NotificationListenerService for other apps
│   ├── BatteryReceiver.kt              — low battery, charging state changes
│   ├── NetworkReceiver.kt              — WiFi/cellular state changes
│   ├── LocationGeofenceReceiver.kt     — geofence enter/exit events
│   ├── ScreenStateReceiver.kt          — screen on/off, user present
│   └── ScheduledTrigger.kt             — WorkManager-based scheduled triggers
│
└── scheduling/
    ├── TaskScheduler.kt                — schedule future tasks (reminders, check-ins)
    ├── MorningBriefing.kt              — generate morning briefing content
    ├── DailySummary.kt                 — end-of-day summary
    └── RecurringTaskRunner.kt          — execute recurring scheduled tasks
```

### 3.2 Event Flow

```
Device Event (SMS, call, calendar, battery, etc.)
        │
        ▼
TriggerManager.onEvent(trigger)
        │
        ▼
ProactiveEngine.shouldReact(trigger, context)
        │  ├── Check user preferences (proactive enabled?)
        │  ├── Check SmartTiming (DND? night time?)
        │  ├── Check cooldown (already notified recently?)
        │  └── Check relevance (is this worth notifying?)
        │
        ▼ (if yes)
Create agent session (type = TRIGGER)
        │
        ▼
Agent processes event with context
        │  ├── "Incoming SMS from Мама: Привет, когда будешь дома?"
        │  └── Agent decides: suggest reply
        │
        ▼
MessageBus.emit(AgentMessage)
        │
        ├── ChatScreen visible? → add to chat, scroll to bottom
        │
        └── App in background? → Push notification
                │
                ├── ConversationNotification (MessagingStyle)
                │   "Guappa: Пришло SMS от Мама. Ответить?"
                │   [Reply] [Open Chat] [Ignore]
                │
                └── User taps → opens ChatScreen with context
```

---

## 4. Notification Channels

```kotlin
object NotificationChannels {
    val CHANNELS = listOf(
        ChannelDef(
            id = "guappa_chat",
            name = "Chat Messages",
            description = "Messages from Guappa in conversation",
            importance = NotificationManager.IMPORTANCE_HIGH,
            sound = true,
            vibration = true,
            showBadge = true,
            conversationStyle = true,
        ),
        ChannelDef(
            id = "guappa_tasks",
            name = "Task Updates",
            description = "Task completion and progress notifications",
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            sound = true,
            vibration = false,
            showBadge = true,
        ),
        ChannelDef(
            id = "guappa_questions",
            name = "Questions",
            description = "When Guappa needs your input",
            importance = NotificationManager.IMPORTANCE_HIGH,
            sound = true,
            vibration = true,
            showBadge = true,
        ),
        ChannelDef(
            id = "guappa_alerts",
            name = "Urgent Alerts",
            description = "Important alerts (missed calls, errors)",
            importance = NotificationManager.IMPORTANCE_MAX,
            sound = true,
            vibration = true,
            showBadge = true,
            fullScreenIntent = true,
        ),
        ChannelDef(
            id = "guappa_proactive",
            name = "Proactive Messages",
            description = "Proactive suggestions and briefings",
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            sound = false,
            vibration = false,
            showBadge = false,
        ),
        ChannelDef(
            id = "guappa_service",
            name = "Service Status",
            description = "Foreground service notification (always shown)",
            importance = NotificationManager.IMPORTANCE_LOW,
            sound = false,
            vibration = false,
            showBadge = false,
        ),
    )
}
```

---

## 5. Proactive Behavior Rules

### 5.1 Built-in Rules

| Trigger | Condition | Action | Channel |
|---------|-----------|--------|---------|
| Incoming SMS | Always | Notify + suggest reply | guappa_chat |
| Missed call | After 2 minutes | Notify + offer callback | guappa_alerts |
| Calendar event (15 min) | Event in 15 min | Remind user | guappa_proactive |
| Calendar event (now) | Event starting now | Alert | guappa_alerts |
| New email | Unread count > 0 | Offer to read/summarize | guappa_proactive |
| Task completed | Background task done | Report result | guappa_tasks |
| Task failed | Background task error | Report error + suggest fix | guappa_tasks |
| Agent needs input | Stuck without info | Ask clarifying question | guappa_questions |
| Low battery (15%) | Battery < 15% | Suggest actions (reduce workload) | guappa_proactive |
| Morning (8:00 AM) | Scheduled | Morning briefing | guappa_proactive |
| Evening (9:00 PM) | Scheduled | Daily summary | guappa_proactive |
| Geofence enter | Arrives at saved location | Context-based suggestion | guappa_proactive |

### 5.2 Custom Rules Engine

```kotlin
data class ProactiveRule(
    val id: String,
    val trigger: TriggerType,
    val conditions: List<Condition>,
    val action: ProactiveAction,
    val enabled: Boolean = true,
    val cooldownMinutes: Int = 5,
)

sealed class Condition {
    data class TimeWindow(val startHour: Int, val endHour: Int) : Condition()
    data class DayOfWeek(val days: Set<Int>) : Condition()
    data class BatteryAbove(val percent: Int) : Condition()
    data class AppInBackground(val value: Boolean) : Condition()
    data class ContactMatch(val namePattern: String) : Condition()
}

sealed class ProactiveAction {
    data class SendChat(val templateMessage: String) : ProactiveAction()
    data class SendNotification(val channel: String) : ProactiveAction()
    data class RunTask(val taskDescription: String) : ProactiveAction()
    object AskAgent : ProactiveAction()  // let agent decide
}
```

### 5.3 Smart Timing

```kotlin
class SmartTiming(private val config: ProactiveConfig) {
    fun canNotifyNow(): Boolean {
        val now = LocalTime.now()

        // Respect quiet hours
        if (now.isBefore(config.quietHoursEnd) || now.isAfter(config.quietHoursStart)) {
            return false
        }

        // Respect system DND mode
        val notificationManager = context.getSystemService<NotificationManager>()
        if (notificationManager.currentInterruptionFilter != INTERRUPTION_FILTER_ALL) {
            return false  // DND is active
        }

        // Respect notification cooldown
        if (notificationHistory.lastNotificationWithin(config.minIntervalMinutes)) {
            return false
        }

        return true
    }
}
```

---

## 6. Morning Briefing & Daily Summary

### 6.1 Morning Briefing

```kotlin
class MorningBriefing(
    private val calendarTool: CalendarTool,
    private val emailTool: EmailReadTool,
    private val weatherProvider: ProviderRouter,
    private val memoryManager: MemoryManager,
) {
    /**
     * Generate morning briefing at configured time (default 8:00 AM).
     *
     * Contents:
     * 1. Greeting ("Доброе утро, Саша!")
     * 2. Weather forecast
     * 3. Today's calendar events
     * 4. Unread emails (count + top 3 subjects)
     * 5. Pending tasks from yesterday
     * 6. Any scheduled reminders
     */
    suspend fun generate(): String {
        val userName = memoryManager.getFact("name")?.value ?: ""
        val events = calendarTool.getTodayEvents()
        val unreadEmails = emailTool.getUnreadCount()
        val pendingTasks = taskManager.getPendingTasks()

        val prompt = """
            Generate a morning briefing for the user.
            User name: $userName
            Today's events: ${events.joinToString("\n")}
            Unread emails: $unreadEmails
            Pending tasks: ${pendingTasks.joinToString("\n") { it.description }}

            Keep it brief, friendly, and in the user's preferred language.
            Use female persona (Guappa speaking).
        """.trimIndent()

        return providerRouter.chatSimple(userMessage = prompt).text
    }
}
```

---

## 7. Notification Styles

### 7.1 MessagingStyle (Chat)

```kotlin
class ConversationNotification {
    fun build(message: AgentMessage): Notification {
        val person = Person.Builder()
            .setName("Guappa")
            .setIcon(IconCompat.createWithResource(context, R.drawable.guappa_avatar))
            .setBot(true)
            .build()

        val style = NotificationCompat.MessagingStyle(person)
            .setConversationTitle("Guappa")
            .addMessage(message.text, message.timestamp, person)

        // Direct reply action
        val remoteInput = RemoteInput.Builder("reply_text")
            .setLabel("Ответить...")
            .build()

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_reply, "Ответить", replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        return NotificationCompat.Builder(context, "guappa_chat")
            .setSmallIcon(R.drawable.ic_guappa)
            .setStyle(style)
            .addAction(replyAction)
            .addAction(R.drawable.ic_open, "Открыть", openChatPendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setShortcutId("guappa_conversation")
            .build()
    }
}
```

### 7.2 Inline Reply Handling

```kotlin
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REPLY -> {
                val replyText = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence("reply_text")?.toString()
                if (replyText != null) {
                    // Send reply to agent via MessageBus
                    val sessionId = intent.getStringExtra("session_id")!!
                    messageBus.emit(AgentEvent.UserMessage(
                        sessionId = sessionId,
                        text = replyText,
                        channel = "notification_reply",
                    ))

                    // Update notification to show "Sending..."
                    notificationManager.updateReplyStatus(
                        notificationId = intent.getIntExtra("notification_id", 0),
                        status = "Отправлено",
                    )
                }
            }
            ACTION_APPROVE -> { /* approve tool action */ }
            ACTION_DENY -> { /* deny tool action */ }
            ACTION_SNOOZE -> { /* snooze for N minutes */ }
        }
    }
}
```

---

## 8. Configuration

```kotlin
data class ProactiveConfig(
    // Global
    val proactiveEnabled: Boolean = true,

    // Quiet hours (no notifications except ALERT)
    val quietHoursEnabled: Boolean = true,
    val quietHoursStart: LocalTime = LocalTime.of(23, 0),
    val quietHoursEnd: LocalTime = LocalTime.of(7, 0),

    // Cooldown between notifications
    val minIntervalMinutes: Int = 2,

    // Feature toggles
    val reactToIncomingSms: Boolean = true,
    val reactToMissedCalls: Boolean = true,
    val reactToCalendarEvents: Boolean = true,
    val reactToNewEmails: Boolean = true,
    val reactToLowBattery: Boolean = true,

    // Scheduled
    val morningBriefingEnabled: Boolean = false,
    val morningBriefingTime: LocalTime = LocalTime.of(8, 0),
    val dailySummaryEnabled: Boolean = false,
    val dailySummaryTime: LocalTime = LocalTime.of(21, 0),

    // Notification behavior
    val notificationSound: Boolean = true,
    val notificationVibration: Boolean = true,
    val inlineReplyEnabled: Boolean = true,

    // Custom rules
    val customRules: List<ProactiveRule> = emptyList(),
)
```

---

## 9. Battery & OEM Compatibility

```kotlin
class BatteryOptimizationHelper {
    /**
     * Request battery optimization exemption.
     * Show guided instructions for OEM-specific settings.
     */
    fun requestExemption(context: Context) {
        // 1. Standard Android exemption
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = Uri.parse("package:${context.packageName}")
        context.startActivity(intent)
    }

    fun getOemSpecificInstructions(): String? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("samsung") -> """
                Samsung: Settings → Battery → Background usage limits →
                Move Guappa to "Never sleeping apps"
            """.trimIndent()
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> """
                Xiaomi: Settings → Battery → App battery saver →
                Guappa → No restrictions.
                Also: Settings → Apps → Manage apps → Guappa → Autostart → Enable
            """.trimIndent()
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> """
                Huawei: Settings → Battery → App launch →
                Guappa → Manage manually → Enable all three toggles
            """.trimIndent()
            manufacturer.contains("oneplus") || manufacturer.contains("oppo") -> """
                OnePlus: Settings → Battery → Battery optimization →
                Guappa → Don't optimize
            """.trimIndent()
            else -> null
        }
    }
}
```

---

## 10. Test Plan

### 10.1 Unit Tests

| Test | Description |
|------|-------------|
| `NotificationChannel_Created` | All 6 channels created correctly |
| `ConversationNotification_Build` | MessagingStyle with reply action |
| `SmartTiming_QuietHours` | 3AM → canNotifyNow() returns false |
| `SmartTiming_DND` | DND active → canNotifyNow() returns false |
| `SmartTiming_Cooldown` | Notified 1 min ago, min=2 → returns false |
| `ProactiveEngine_SmsReaction` | Incoming SMS → should react |
| `ProactiveEngine_Disabled` | proactiveEnabled=false → no reaction |
| `InlineReply_Forwarded` | Reply from notification → reaches MessageBus |
| `MorningBriefing_Content` | Calendar+email+tasks → formatted briefing |
| `BatteryOptimization_Samsung` | Samsung device → correct instructions |

### 10.2 Maestro E2E

```yaml
# Background task → push notification
- launchApp: com.guappa.app
- inputText: "Set alarm for tomorrow at 7am and let me know when done"
- tapOn: "Send"
- pressHome  # go to background
- assertVisible:
    text: ".*alarm.*7:00.*"
    timeout: 30000
    # notification should appear

# Inline reply from notification
- tapOn: "Reply"
- inputText: "Thanks!"
- tapOn: "Send"
```

---

## 11. Acceptance Criteria

- [ ] All 6 notification channels created on app install
- [ ] Task completion triggers push notification when app in background
- [ ] Agent questions appear as notifications with inline reply
- [ ] Inline reply flows back to agent and generates response
- [ ] Morning briefing generates at scheduled time (when enabled)
- [ ] Quiet hours respected (no notifications between 23:00-07:00)
- [ ] DND mode respected
- [ ] Notification cooldown prevents spam
- [ ] SMS/call/calendar triggers work when proactive enabled
- [ ] Battery optimization exemption requested on first launch
- [ ] OEM-specific instructions shown for Samsung/Xiaomi/Huawei/OnePlus
