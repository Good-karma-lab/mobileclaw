# Troubleshooting

Common issues and their solutions.

## App Issues

### App crashes on startup

**Symptoms:** GUAPPA closes immediately after opening or shows a blank screen.

**Solutions:**
1. **Clear app cache:** Android Settings > Apps > GUAPPA > Storage > Clear Cache.
2. **Check available storage:** GUAPPA needs at least 500 MB free. Go to Android Settings > Storage.
3. **Check RAM:** Close other apps to free memory. GUAPPA requires at least 4 GB RAM.
4. **Reinstall:** Uninstall and reinstall the app. This clears all data including downloaded models.
5. **Check Android version:** GUAPPA requires Android 8.0 (API 26) or newer.

### App freezes or becomes unresponsive

**Symptoms:** UI stops responding, buttons don't work.

**Solutions:**
1. **Wait 10-15 seconds:** The agent may be processing a complex task.
2. **Force stop:** Android Settings > Apps > GUAPPA > Force Stop, then reopen.
3. **If using local inference:** The model may be loading. First inference after app launch can take several seconds on slower devices.

### High battery drain

**Symptoms:** GUAPPA uses a lot of battery in the background.

**Solutions:**
1. **Disable wake word** when not needed. Continuous microphone listening uses battery.
2. **Reduce channel polling frequency** for channels that use polling.
3. **Disable swarm** if not actively using it.
4. **Use cloud providers** instead of local inference. On-device model inference is CPU-intensive.
5. **OEM-specific battery optimization:**
   - **Samsung:** Settings > Battery > App Power Management > Never sleeping apps > Add GUAPPA
   - **Xiaomi/Redmi:** Settings > Battery > App Battery Saver > GUAPPA > No restrictions
   - **OnePlus:** Settings > Battery > Battery Optimization > GUAPPA > Don't optimize
   - **Huawei/Honor:** Settings > Battery > Launch Apps > GUAPPA > Manage manually (enable all toggles)
   - **OPPO/Realme:** Settings > Battery > More settings > Optimize battery use > GUAPPA > Don't optimize

---

## Provider Issues

### "API key invalid" or "401 Unauthorized"

**Solutions:**
1. Verify the API key is correct -- copy and paste it again, ensuring no extra spaces.
2. Check that the key is active in the provider's dashboard.
3. Some providers require billing setup before the key works (e.g., OpenAI requires adding a payment method).
4. For GitHub Copilot, ensure your account has an active Copilot subscription.

### "Model not found" or "404"

**Solutions:**
1. The model may have been renamed or deprecated. Check the provider's model list.
2. Try a different model from the dropdown.
3. Some models are restricted to certain account tiers (e.g., GPT-4.1 requires a paid OpenAI account).
4. Tap **Refresh Models** in Settings to fetch the latest model list.

### "Rate limit exceeded" or "429"

**Solutions:**
1. Wait a few minutes and try again.
2. Reduce request frequency. Set a longer delay in agent retry settings.
3. Upgrade your provider plan for higher rate limits.
4. Switch to a different provider temporarily.

### Slow responses

**Solutions:**
1. **Cloud providers:** Check your internet connection speed. Try switching to WiFi.
2. **Local inference:** Close other apps. Try a smaller model (0.8B instead of 2B). Reduce context size.
3. **Streaming:** Ensure streaming is enabled in Settings > Provider. Streaming shows partial responses as they arrive.

### "Connection timeout" or "Network error"

**Solutions:**
1. Check your internet connection.
2. For Ollama/LM Studio, verify:
   - The server is running on your computer.
   - Your phone and computer are on the same WiFi network.
   - The endpoint URL is correct (use your computer's local IP, not `localhost`).
   - The server port is not blocked by a firewall.
3. Try increasing the timeout in Settings > Provider > Connection Timeout.

---

## Voice Issues

### Microphone not working

**Solutions:**
1. Check microphone permission: Android Settings > Apps > GUAPPA > Permissions > Microphone.
2. Close other apps that might be using the microphone (voice recorders, video calls).
3. Restart the app.
4. Test the microphone with another app (e.g., Android's built-in voice recorder).

### Speech-to-text produces incorrect text

**Solutions:**
1. Speak clearly and at a moderate pace.
2. Reduce background noise.
3. Hold the phone closer to your mouth.
4. Try a different STT engine:
   - Switch to on-device Whisper for best accuracy.
   - Download a larger Whisper model if using the smallest one.
5. Set the correct language in Settings > Voice > STT Language instead of using auto-detect.

### Text-to-speech not working

**Solutions:**
1. Check that device volume is not muted or set to zero.
2. Check if Do Not Disturb mode is blocking audio.
3. Try switching to Android Native TTS (always available, no download needed).
4. If using Bluetooth, check the Bluetooth connection.
5. Restart the app.

### Wake word not triggering

**Solutions:**
1. Verify wake word is enabled: Settings > Voice > Wake Word.
2. Increase sensitivity: Settings > Voice > Wake Word Sensitivity.
3. Speak clearly: "Hey GUAPPA" (emphasize both words).
4. Check microphone permission.
5. The wake word does not work when the microphone is already in use by another app.

### Wake word triggers too often (false positives)

**Solutions:**
1. Lower wake word sensitivity in Settings.
2. Reduce background noise (TV, music, conversations).

---

## Notification Issues

### Notifications not appearing

**Solutions:**
1. Grant notification permission: Android Settings > Apps > GUAPPA > Notifications.
2. Check that notification channels are not muted:
   - Android Settings > Apps > GUAPPA > Notifications > Show all channels.
   - Ensure "Task Completion," "Agent Questions," and other channels are enabled.
3. Check Do Not Disturb settings -- GUAPPA notifications may be filtered.
4. **OEM-specific:** Some manufacturers aggressively restrict background notifications:
   - **Samsung:** Disable "Put unused apps to sleep" for GUAPPA.
   - **Xiaomi:** Enable "Autostart" for GUAPPA in Settings > Apps > Manage Apps > GUAPPA.
   - **Huawei:** Add GUAPPA to "Protected Apps" in Settings > Battery > Launch Management.

### GUAPPA foreground service notification won't go away

This is expected behavior. GUAPPA runs as an Android foreground service to stay alive in the background. The persistent notification indicates the agent is running. You can minimize its visibility:
1. Android Settings > Apps > GUAPPA > Notifications.
2. Find the "Agent Service" channel.
3. Set importance to "Low" or "Min" (removes sound, may minimize the notification).

---

## Channel Issues

### Telegram bot not responding

**Solutions:**
1. Check that the bot token is correct.
2. Verify the chat ID matches your conversation with the bot.
3. Make sure GUAPPA is running (check the foreground service notification).
4. Start a new conversation: go to the bot in Telegram and send `/start`.

### Discord bot offline

**Solutions:**
1. Verify the bot token.
2. Check that "Message Content Intent" is enabled in the Discord Developer Portal.
3. Ensure the bot is invited to the correct server and has permissions in the target channel.

### Channel shows "Error" status

**Solutions:**
1. Check the channel configuration (token, endpoint, permissions).
2. Check your internet connection.
3. Disable and re-enable the channel in Settings.
4. Check the channel's health status for a specific error message.

---

## Memory Issues

### GUAPPA forgot something it should remember

**Solutions:**
1. Check if long-term memory extraction is enabled: Settings > Memory > Auto-Extract Facts.
2. The fact may not have been extracted yet. Extraction runs periodically (default: every 10 messages).
3. Explicitly tell GUAPPA to remember: "Remember that my favorite color is blue."
4. Check if the memory was accidentally deleted in Settings > Memory > View Memories.

### GUAPPA remembers incorrect information

**Solutions:**
1. Go to Settings > Memory > View Memories.
2. Find the incorrect fact.
3. Tap **Edit** to correct it, or **Delete** to remove it.
4. Tell GUAPPA the correct information: "Actually, I moved to Berlin, not Munich."

---

## Performance Tips

1. **Close unused channels** to reduce background network connections.
2. **Use cloud providers** for best response speed if you have a good internet connection.
3. **Use local inference** for privacy and offline access, but expect slower responses.
4. **Reduce context size** for local models if responses are slow (Settings > Local Model > Context Size).
5. **Keep the app updated** for performance improvements and bug fixes.
6. **Restart the app periodically** to clear accumulated memory usage.
