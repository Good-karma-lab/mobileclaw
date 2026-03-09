# Available Tools Reference

GUAPPA has 65+ built-in tools that let her interact with your device, the web, other apps, and more. Tools are invoked automatically when GUAPPA determines they are needed to fulfill your request.

## How Tools Work

When you ask GUAPPA to do something (e.g., "set an alarm for 7 AM"), the AI model selects the appropriate tool, provides the parameters, and GUAPPA executes it. Tool results are fed back to the model for composing the response.

Each tool has:
- A JSON schema defining its parameters
- Required Android permissions (requested on first use)
- Rate limiting to prevent abuse
- Audit logging for review

### Permissions

Tools that access sensitive device features require Android permissions. GUAPPA requests these at runtime when a tool is first needed. You can revoke permissions at any time in Android Settings > Apps > GUAPPA > Permissions.

### Approval Mode

Some high-impact tools (sending messages, making calls, deleting files) can be configured to require your approval before execution. Enable this in **Settings** > **Agent** > **Require Approval for Sensitive Actions**.

---

## Device Tools

Tools for controlling device hardware and accessing device data.

| Tool | Description | Permissions |
|------|-------------|-------------|
| `sms_send` | Send an SMS message | SEND_SMS |
| `sms_read` | Read SMS messages | READ_SMS |
| `call_place` | Place a phone call | CALL_PHONE |
| `call_log` | Read call history | READ_CALL_LOG |
| `contacts_search` | Search contacts | READ_CONTACTS |
| `contacts_create` | Create a contact | WRITE_CONTACTS |
| `calendar_query` | Query calendar events | READ_CALENDAR |
| `calendar_create` | Create a calendar event | WRITE_CALENDAR |
| `camera_capture` | Capture a photo or video | CAMERA |
| `location_get` | Get current GPS location | ACCESS_FINE_LOCATION |
| `location_geocode` | Convert address to coordinates | None |
| `sensor_read` | Read device sensors (accelerometer, gyro, light, proximity, step counter) | None |
| `battery_status` | Get battery level, charging state, health | None |
| `network_status` | Get WiFi/cellular status, SSID, signal strength | None |
| `bluetooth_scan` | Scan for nearby BLE devices | BLUETOOTH_SCAN |
| `bluetooth_connect` | Connect to a BLE device | BLUETOOTH_CONNECT |
| `nfc_read` | Read NFC tag | NFC |
| `nfc_write` | Write to NFC tag | NFC |
| `clipboard_get` | Get clipboard content | None |
| `clipboard_set` | Set clipboard content | None |
| `vibrate` | Trigger haptic vibration pattern | None |
| `flashlight_toggle` | Toggle the flashlight on/off | None |
| `screen_brightness` | Set screen brightness | None |
| `volume_set` | Set media/ring/alarm volume | None |

**Examples:**
- "Send a text to Mom saying I'll be late" -- uses `sms_send`
- "What's my battery level?" -- uses `battery_status`
- "Turn on the flashlight" -- uses `flashlight_toggle`
- "Set volume to 50%" -- uses `volume_set`

---

## App Management Tools

Tools for launching and interacting with other apps.

| Tool | Description | Permissions |
|------|-------------|-------------|
| `app_launch` | Launch any app by package name | None |
| `app_list` | List installed apps with metadata | None |
| `intent_fire` | Fire an arbitrary Android Intent | None |
| `alarm_set` | Set an alarm via AlarmClock Intent | None |
| `alarm_cancel` | Cancel an alarm | None |
| `timer_set` | Start a countdown timer | None |
| `reminder_create` | Create a reminder | None |
| `email_compose` | Compose an email via Intent | None |
| `email_read` | Read emails | None |
| `browser_open` | Open a URL in the system browser | None |
| `maps_navigate` | Open maps with directions | None |
| `maps_search` | Search for a place on maps | None |
| `music_control` | Play, pause, skip, or control media playback | None |
| `settings_open` | Open a specific Android settings page | None |
| `share_content` | Share content via the Android share sheet | None |
| `download_file` | Download a file from a URL | WRITE_EXTERNAL_STORAGE |

**Examples:**
- "Set an alarm for 7 AM" -- uses `alarm_set`
- "Open YouTube" -- uses `app_launch`
- "Navigate to the nearest coffee shop" -- uses `maps_navigate`
- "Skip this song" -- uses `music_control`

---

## Communication Tools

Tools for social media and messaging via deep links.

| Tool | Description |
|------|-------------|
| `twitter_post` | Compose a tweet via deep link |
| `instagram_share` | Share an image to Instagram |
| `telegram_send` | Send a message via Telegram deep link |
| `whatsapp_send` | Send a message via WhatsApp deep link |
| `social_share` | Universal social share via chooser |

**Examples:**
- "Post a tweet saying 'Hello world'" -- uses `twitter_post`
- "Send a WhatsApp message to John" -- uses `whatsapp_send`

---

## File Management Tools

Tools for reading, writing, and managing files on the device.

| Tool | Description | Permissions |
|------|-------------|-------------|
| `file_read` | Read file content (text or binary info) | READ_EXTERNAL_STORAGE |
| `file_write` | Write or create a file | WRITE_EXTERNAL_STORAGE |
| `file_search` | Search files by name, content, or type | READ_EXTERNAL_STORAGE |
| `document_pick` | Open the system document picker (SAF) | None |
| `media_browse` | Browse photos and videos from MediaStore | READ_MEDIA_IMAGES |
| `pdf_read` | Extract text from a PDF file | READ_EXTERNAL_STORAGE |

**Examples:**
- "Read the file notes.txt from Downloads" -- uses `file_read`
- "Find all PDF files on my phone" -- uses `file_search`
- "What does this PDF say?" -- uses `pdf_read`

---

## Web Tools

Tools for accessing the internet. These are core tools that enable GUAPPA to find and retrieve information.

| Tool | Description |
|------|-------------|
| `web_search` | Search the web (via Brave Search or Google Custom Search) |
| `web_fetch` | Fetch a URL and convert HTML to markdown |
| `web_scrape` | Extract structured data from a web page using CSS selectors |
| `web_browser_session` | Interactive headless browser session (via WebView) |
| `rss_read` | Parse RSS/Atom feeds |
| `web_api` | Call a REST API with custom headers and JSON body |

**Examples:**
- "What's the latest news about AI?" -- uses `web_search`
- "Summarize this article: https://example.com/article" -- uses `web_fetch` + AI summarization
- "What are the top stories on Hacker News?" -- uses `rss_read` or `web_fetch`

### Web Search Configuration

GUAPPA uses Brave Search API by default (free tier: 2,000 queries/month). You can also configure Google Custom Search. Set your search API key in **Settings** > **Tools** > **Web Search**.

---

## AI-Powered Tools

Tools that use AI models for specialized tasks.

| Tool | Description |
|------|-------------|
| `image_analyze` | Analyze an image using a vision model (describe, identify objects, read text) |
| `ocr` | Extract text from an image (via Google ML Kit) |
| `translate` | Translate text between languages |
| `calculator` | Evaluate mathematical expressions safely |
| `code_interpret` | Execute code snippets in a sandboxed environment |
| `summarize` | Summarize long text using the AI model |
| `qr_generate` | Generate a QR code from text |
| `qr_read` | Read and decode a QR code from an image |
| `barcode_scan` | Scan barcodes (via ML Kit) |
| `image_generate` | Generate images from text descriptions (DALL-E, Imagen, Stable Diffusion) |

**Examples:**
- "What's in this photo?" -- uses `image_analyze`
- "Translate 'good morning' to French, German, and Japanese" -- uses `translate`
- "Generate an image of a sunset over mountains" -- uses `image_generate`
- "Scan this QR code" -- uses `qr_read`

---

## Automation Tools

Tools for scheduling, automating, and creating rules.

| Tool | Description |
|------|-------------|
| `reminder_set` | Set a one-time or recurring reminder |
| `cron_create` | Create a scheduled task with cron-like timing |
| `cron_list` | List active scheduled tasks |
| `cron_delete` | Delete a scheduled task |
| `geofence_create` | Create a geofence trigger (action when entering/leaving a location) |
| `auto_reply_set` | Set an auto-reply for incoming messages |
| `auto_reply_clear` | Clear auto-reply rules |

**Examples:**
- "Remind me to take medicine every day at 9 AM" -- uses `reminder_set`
- "When I arrive at the office, send a message to my team" -- uses `geofence_create`
- "Auto-reply to messages while I'm in a meeting: 'I'm busy, will reply later'" -- uses `auto_reply_set`

---

## System Tools

Tools for system-level operations.

| Tool | Description | Permissions |
|------|-------------|-------------|
| `system_info` | Get device model, OS version, storage, RAM, CPU | None |
| `package_info` | Get app info (version, size, permissions) | None |
| `wifi_manage` | Connect/disconnect WiFi networks | ACCESS_FINE_LOCATION |
| `datetime` | Get current date, time, timezone, or do calendar math | None |
| `shell_execute` | Execute shell commands (heavily sandboxed) | None |

**Examples:**
- "How much storage do I have left?" -- uses `system_info`
- "What version of Chrome is installed?" -- uses `package_info`
- "What time is it in Tokyo?" -- uses `datetime`

### Shell Tool Security

The `shell_execute` tool runs in a restricted sandbox. It cannot access sensitive system directories, modify system files, or execute privileged commands. It is intended for basic operations like listing files, checking disk usage, and running simple scripts.

---

## Tool Rate Limiting

To prevent runaway tool execution, GUAPPA enforces rate limits:
- Default: 30 tool calls per minute per session
- Configurable in **Settings** > **Agent** > **Tool Rate Limit**

## Tool Audit Log

All tool executions are logged for your review. View the log in **Settings** > **Agent** > **Tool Audit Log**. Each entry shows the tool name, parameters, result, and timestamp.
