# Channel Integration Guide

GUAPPA can communicate through multiple messaging platforms simultaneously, in addition to the in-app chat. This guide covers setup for each supported channel.

## How Channels Work

Each channel runs as a separate connection managed by GUAPPA's Channel Hub. When a message arrives on any channel, it is routed to the agent for processing. GUAPPA's response is sent back through the same channel. All channels share the same agent session, so GUAPPA maintains context across platforms.

You can enable multiple channels at the same time. GUAPPA keeps track of which channel each conversation came from and responds on the correct one.

## Telegram

### Prerequisites
- A Telegram account
- The Telegram app or web client

### Setup

1. **Create a bot with BotFather:**
   - Open Telegram and search for [@BotFather](https://t.me/BotFather).
   - Send `/newbot`.
   - Choose a display name (e.g., "My GUAPPA").
   - Choose a username ending in `bot` (e.g., `my_guappa_bot`).
   - BotFather replies with your **bot token**. Copy it.

2. **Get your Chat ID:**
   - Start a conversation with your new bot (send it any message).
   - Open this URL in a browser, replacing `YOUR_TOKEN` with your bot token:
     ```
     https://api.telegram.org/botYOUR_TOKEN/getUpdates
     ```
   - Find the `"chat":{"id":` value in the response. This is your chat ID.

3. **Configure in GUAPPA:**
   - Go to **Settings** > **Channels** > **Telegram**.
   - Paste the **bot token**.
   - Enter your **chat ID** (for allowlist security).
   - Toggle **Enable** on.

4. **Test:** Send a message to your bot in Telegram. GUAPPA should respond.

### Features
- Text messages, photos, documents, voice messages
- Inline keyboard buttons for questions
- MarkdownV2 formatting
- Long polling (default) or webhook mode

### Security
Only messages from the configured chat ID are processed. Messages from other users are ignored.

## Discord

### Prerequisites
- A Discord account
- Admin access to a Discord server

### Setup

1. **Create a Discord Application:**
   - Go to the [Discord Developer Portal](https://discord.com/developers/applications).
   - Click **New Application**, give it a name, and create it.
   - Go to the **Bot** section and click **Add Bot**.
   - Under Token, click **Copy** to get your bot token.
   - Enable **Message Content Intent** under Privileged Gateway Intents.

2. **Invite the bot to your server:**
   - Go to **OAuth2** > **URL Generator**.
   - Select scopes: `bot`, `applications.commands`.
   - Select permissions: `Send Messages`, `Read Message History`, `Embed Links`, `Attach Files`.
   - Copy the generated URL and open it in your browser to invite the bot.

3. **Configure in GUAPPA:**
   - Go to **Settings** > **Channels** > **Discord**.
   - Paste the **bot token**.
   - Enter the **channel ID** (right-click a channel in Discord > Copy Channel ID; enable Developer Mode in Discord settings if needed).
   - Toggle **Enable** on.

4. **Test:** Send a message in the configured Discord channel. GUAPPA should respond.

### Features
- Text messages with Discord Markdown formatting
- Slash commands
- Embeds for rich responses
- File uploads

## Slack

### Prerequisites
- A Slack workspace with admin access

### Setup

1. **Create a Slack App:**
   - Go to [api.slack.com/apps](https://api.slack.com/apps) and click **Create New App**.
   - Choose **From scratch**, name it, and select your workspace.

2. **Configure bot permissions:**
   - Go to **OAuth & Permissions** > **Scopes** > **Bot Token Scopes**.
   - Add: `chat:write`, `channels:history`, `channels:read`, `im:history`, `im:read`.

3. **Enable Socket Mode:**
   - Go to **Socket Mode** and toggle it on.
   - Generate an **App-Level Token** with `connections:write` scope. Copy it.

4. **Enable Events:**
   - Go to **Event Subscriptions** and toggle on.
   - Subscribe to bot events: `message.channels`, `message.im`.

5. **Install to workspace:**
   - Go to **Install App** and click **Install to Workspace**.
   - Copy the **Bot User OAuth Token** (`xoxb-...`).

6. **Configure in GUAPPA:**
   - Go to **Settings** > **Channels** > **Slack**.
   - Paste the **Bot Token** and **App-Level Token**.
   - Toggle **Enable** on.

7. **Test:** Invite the bot to a channel (`/invite @your-bot-name`) and send a message.

### Features
- Block Kit formatting (mrkdwn)
- Socket Mode (no public URL needed)
- Button interactions
- Thread support

## WhatsApp

### Prerequisites
- A Meta Business account
- A WhatsApp Business API setup

### Setup

1. **Set up WhatsApp Cloud API:**
   - Go to [developers.facebook.com](https://developers.facebook.com).
   - Create an app with WhatsApp product.
   - Follow the WhatsApp Getting Started guide to get:
     - **Access Token** (permanent)
     - **Phone Number ID**
     - **WhatsApp Business Account ID**

2. **Configure webhook:**
   - GUAPPA needs a public URL to receive incoming messages. You can use a tunnel service (ngrok, Cloudflare Tunnel) or deploy a webhook endpoint.
   - Set the webhook URL in the Meta developer console.

3. **Configure in GUAPPA:**
   - Go to **Settings** > **Channels** > **WhatsApp**.
   - Enter the **Access Token**, **Phone Number ID**, and **Webhook Verify Token**.
   - Toggle **Enable** on.

### Features
- Text, image, document, and voice messages
- Message templates for business messages
- 24-hour messaging window (free-form replies within 24 hours of last user message)

### Notes
WhatsApp Cloud API has specific requirements around message templates and the 24-hour window. GUAPPA handles these automatically, but proactive messages outside the window require approved templates.

## Signal

### Prerequisites
- Signal CLI REST API server running on your network

### Setup

1. **Install Signal CLI REST API:**
   ```bash
   docker run -d --name signal-api \
     -p 8080:8080 \
     -v signal-data:/home/.local/share/signal-cli \
     bbernhard/signal-cli-rest-api
   ```

2. **Register or link a phone number:**
   - Follow the Signal CLI REST API documentation to register a number or link to your existing Signal account.

3. **Configure in GUAPPA:**
   - Go to **Settings** > **Channels** > **Signal**.
   - Enter the **API endpoint** (e.g., `http://192.168.1.100:8080`).
   - Enter the registered **phone number**.
   - Toggle **Enable** on.

### Features
- End-to-end encrypted messages
- Text and attachments
- Group messages

## Matrix

### Prerequisites
- A Matrix account on any homeserver (e.g., matrix.org, your own Synapse server)

### Setup

1. **Create a bot account:**
   - Register a new account on your Matrix homeserver for the bot.
   - Note the full user ID (e.g., `@guappa-bot:matrix.org`).

2. **Get an access token:**
   - Log in with the bot account using Element or another client.
   - Go to Settings > Help & About > Access Token (Element), or use the Matrix login API.

3. **Configure in GUAPPA:**
   - Go to **Settings** > **Channels** > **Matrix**.
   - Enter the **homeserver URL** (e.g., `https://matrix.org`).
   - Enter the **access token**.
   - Enter the **room ID** to monitor (e.g., `!abc123:matrix.org`).
   - Toggle **Enable** on.

4. **Invite the bot** to the room you want it to monitor.

### Features
- End-to-end encryption support (via vodozemac)
- HTML formatted messages
- Room sync via long-poll

## Email

### Prerequisites
- An email account with IMAP and SMTP access

### Setup

1. **Configure in GUAPPA:**
   - Go to **Settings** > **Channels** > **Email**.
   - Enter IMAP settings:
     - **Server:** e.g., `imap.gmail.com`
     - **Port:** `993` (SSL)
     - **Username:** your email address
     - **Password:** your password or app-specific password
   - Enter SMTP settings:
     - **Server:** e.g., `smtp.gmail.com`
     - **Port:** `587` (TLS)
   - Toggle **Enable** on.

2. **For Gmail:** You need to generate an [App Password](https://myaccount.google.com/apppasswords) if you have 2FA enabled.

### Features
- IMAP IDLE push for real-time incoming email
- SMTP sending
- HTML to text parsing for incoming emails
- Attachment handling

## SMS

### Prerequisites
- A device with a SIM card and SMS capability

### Setup

1. **Grant permissions:**
   - GUAPPA needs `SEND_SMS`, `READ_SMS`, and `RECEIVE_SMS` permissions.
   - These are requested when you enable the SMS channel.

2. **Configure in GUAPPA:**
   - Go to **Settings** > **Channels** > **SMS**.
   - Optionally set an **allowlist** of phone numbers to respond to.
   - Toggle **Enable** on.

### Features
- Send and receive SMS
- Incoming SMS triggers the agent automatically
- Allowlist filtering for security

### Notes
Standard carrier SMS rates apply. GUAPPA does not use any third-party SMS service -- it sends messages directly through your device's SIM card.

## Managing Channels

### Health Monitoring

GUAPPA continuously monitors the health of all connected channels. You can see the status of each channel in **Settings** > **Channels**:

- **Connected** -- Channel is active and receiving messages
- **Reconnecting** -- Temporary connection loss, auto-reconnecting
- **Error** -- Connection failed, check configuration
- **Disabled** -- Channel is turned off

### Auto-Reconnect

If a channel connection drops (network issues, server restart), GUAPPA automatically attempts to reconnect with exponential backoff.

### Channel-Specific Formatting

GUAPPA automatically converts its responses to the appropriate format for each channel:
- **Telegram:** MarkdownV2
- **Discord:** Discord Markdown
- **Slack:** Block Kit mrkdwn
- **Matrix:** HTML
- **Email:** HTML
- **SMS/Signal:** Plain text
