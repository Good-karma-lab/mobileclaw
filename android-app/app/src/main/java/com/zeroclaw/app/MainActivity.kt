package com.zeroclaw.app

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private enum class AppTab(val title: String) {
    Chat("Chat"),
    Llm("LLM"),
    Integrations("Integrations"),
    Device("Device"),
    Security("Security")
}

private data class ProviderPreset(
    val id: String,
    val title: String,
    val endpoint: String,
    val model: String,
    val supportsOauthToken: Boolean,
    val docsHint: String
)

private val providerPresets = listOf(
    ProviderPreset(
        id = "ollama",
        title = "Ollama (local)",
        endpoint = "http://10.0.2.2:11434",
        model = "gpt-oss:20b",
        supportsOauthToken = false,
        docsHint = "Runs against local Ollama server."
    ),
    ProviderPreset(
        id = "openrouter",
        title = "OpenRouter",
        endpoint = "https://openrouter.ai/api/v1",
        model = "openai/gpt-4.1-mini",
        supportsOauthToken = false,
        docsHint = "Use OpenRouter API key."
    ),
    ProviderPreset(
        id = "openai",
        title = "OpenAI",
        endpoint = "https://api.openai.com/v1",
        model = "gpt-4.1-mini",
        supportsOauthToken = true,
        docsHint = "Use OpenAI API key, or ChatGPT OAuth token flow for Codex-compatible subscription endpoints."
    ),
    ProviderPreset(
        id = "anthropic",
        title = "Anthropic",
        endpoint = "https://api.anthropic.com/v1",
        model = "claude-3-5-sonnet-latest",
        supportsOauthToken = true,
        docsHint = "Supports API key and setup-token OAuth flows. Claude subscription alone is not API auth."
    ),
    ProviderPreset(
        id = "gemini",
        title = "Google Gemini",
        endpoint = "https://generativelanguage.googleapis.com/v1beta",
        model = "gemini-1.5-pro",
        supportsOauthToken = true,
        docsHint = "Supports API key. Also supports OAuth access token in enterprise/GCP setups."
    ),
    ProviderPreset(
        id = "copilot",
        title = "GitHub Copilot",
        endpoint = "https://api.githubcopilot.com",
        model = "gpt-4o-mini",
        supportsOauthToken = true,
        docsHint = "Use GitHub auth token/PAT tied to a Copilot-enabled account or org policy."
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bridge = RustAgentBridge()
        val broker = AndroidCapabilityBroker(applicationContext)
        val agentStore = AgentConfigStore(applicationContext)
        val integrationsStore = IntegrationsConfigStore(applicationContext)
        val oauthManager = OAuthManager()

        setContent {
            MaterialTheme {
                MobileClawApp(bridge, broker, agentStore, integrationsStore, oauthManager)
            }
        }
    }
}

@Composable
private fun LiquidBackdrop() {
    val transition = rememberInfiniteTransition(label = "bg")
    val drift by transition.animateFloat(
        initialValue = -50f,
        targetValue = 50f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF050A16), Color(0xFF0A1F36), Color(0xFF0D474A))
                )
            )
    ) {
        Box(
            modifier = Modifier
                .padding(start = (drift + 60).dp, top = 16.dp)
                .width(280.dp)
                .fillMaxSize()
                .alpha(0.26f)
                .blur(36.dp)
                .background(Brush.radialGradient(listOf(Color(0xAA8AF6FF), Color.Transparent)))
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileClawApp(
    bridge: RustAgentBridge,
    broker: AndroidCapabilityBroker,
    agentStore: AgentConfigStore,
    integrationsStore: IntegrationsConfigStore,
    oauthManager: OAuthManager
) {
    var tab by remember { mutableStateOf(AppTab.Chat) }
    var agentConfig by remember { mutableStateOf(agentStore.load()) }
    var integrations by remember { mutableStateOf(integrationsStore.load()) }

    Box(modifier = Modifier.fillMaxSize()) {
        LiquidBackdrop()

        Scaffold(
            containerColor = Color.Transparent,
            topBar = { TopAppBar(title = { Text("MobileClaw") }) },
            bottomBar = {
                NavigationBar(containerColor = Color(0x3A2D4458)) {
                    AppTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Text(item.title.take(1)) },
                            label = { Text(item.title) }
                        )
                    }
                }
            }
        ) { insets ->
            when (tab) {
                AppTab.Chat -> ChatScreen(Modifier.padding(insets), bridge, agentConfig)
                AppTab.Llm -> LlmScreen(
                    modifier = Modifier.padding(insets),
                    config = agentConfig,
                    oauthManager = oauthManager,
                    onSave = {
                        agentConfig = it
                        agentStore.save(it)
                    }
                )

                AppTab.Integrations -> IntegrationsScreen(
                    modifier = Modifier.padding(insets),
                    config = integrations,
                    onSave = {
                        integrations = it
                        integrationsStore.save(it)
                    }
                )

                AppTab.Device -> DeviceScreen(Modifier.padding(insets), broker)
                AppTab.Security -> SecurityScreen(Modifier.padding(insets))
            }
        }
    }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0x66CAF5FF), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x2B2C4656))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}

@Composable
private fun ChatScreen(modifier: Modifier, bridge: RustAgentBridge, config: AgentRuntimeConfig) {
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready") }
    var transcript by remember { mutableStateOf(listOf<String>()) }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard {
            Text("Chat", style = MaterialTheme.typography.titleLarge)
            Text("${config.provider} • ${config.model}")
            Text(status)
            OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                if (input.isNotBlank()) {
                    val prompt = input
                    input = ""
                    transcript = transcript + "You: $prompt"
                    status = "Request in progress"
                    scope.launch {
                        try {
                            val reply = bridge.sendMessage(prompt, config)
                            transcript = transcript + "Agent: $reply"
                            status = "Done"
                        } catch (error: Throwable) {
                            transcript = transcript + "Error: ${error.message}"
                            status = "Failed"
                        }
                    }
                }
            }) { Text("Send") }
        }

        GlassCard(modifier = Modifier.weight(1f)) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(transcript) { Text(it) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LlmScreen(
    modifier: Modifier,
    config: AgentRuntimeConfig,
    oauthManager: OAuthManager,
    onSave: (AgentRuntimeConfig) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var providerExpanded by remember { mutableStateOf(false) }
    var authExpanded by remember { mutableStateOf(false) }

    var provider by remember { mutableStateOf(config.provider) }
    var model by remember { mutableStateOf(config.model) }
    var endpoint by remember { mutableStateOf(config.apiUrl) }
    var authMode by remember { mutableStateOf(config.authMode) }
    var apiKey by remember { mutableStateOf(config.apiKey) }
    var oauthToken by remember { mutableStateOf(config.oauthAccessToken) }
    var oauthRefreshToken by remember { mutableStateOf(config.oauthRefreshToken) }
    var oauthExpiresAtMs by remember { mutableStateOf(config.oauthExpiresAtMs) }
    var accountId by remember { mutableStateOf(config.accountId) }
    var enterpriseUrl by remember { mutableStateOf(config.enterpriseUrl) }
    var temperature by remember { mutableStateOf(config.temperature.toString()) }
    var oauthCode by remember { mutableStateOf("") }
    var oauthUrl by remember { mutableStateOf("") }
    var oauthBusy by remember { mutableStateOf(false) }
    var saveStatus by remember { mutableStateOf("") }

    val selected = providerPresets.firstOrNull { it.id == provider } ?: providerPresets.first()
    val authOptions = if (selected.supportsOauthToken) {
        listOf("api_key" to "API Key", "oauth_token" to "OAuth Access Token")
    } else {
        listOf("api_key" to "API Key")
    }
    if (authOptions.none { it.first == authMode }) {
        authMode = "api_key"
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard {
            Text("LLM Provider", style = MaterialTheme.typography.titleLarge)

            ExposedDropdownMenuBox(expanded = providerExpanded, onExpandedChange = { providerExpanded = !providerExpanded }) {
                OutlinedTextField(
                    value = selected.title,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                    label = { Text("Provider") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                )
                ExposedDropdownMenu(expanded = providerExpanded, onDismissRequest = { providerExpanded = false }) {
                    providerPresets.forEach { preset ->
                        DropdownMenuItem(text = { Text(preset.title) }, onClick = {
                            provider = preset.id
                            model = preset.model
                            endpoint = preset.endpoint
                            authMode = "api_key"
                            providerExpanded = false
                        })
                    }
                }
            }

            ExposedDropdownMenuBox(expanded = authExpanded, onExpandedChange = { authExpanded = !authExpanded }) {
                val selectedAuth = authOptions.firstOrNull { it.first == authMode }?.second ?: "API Key"
                OutlinedTextField(
                    value = selectedAuth,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = authExpanded) },
                    label = { Text("Credential type") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                )
                ExposedDropdownMenu(expanded = authExpanded, onDismissRequest = { authExpanded = false }) {
                    authOptions.forEach { (id, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            authMode = id
                            authExpanded = false
                        })
                    }
                }
            }

            OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, label = { Text("Endpoint") }, modifier = Modifier.fillMaxWidth())
            if (authMode == "api_key") {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OutlinedTextField(
                    value = oauthToken,
                    onValueChange = { oauthToken = it },
                    label = { Text("OAuth access token") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (provider == "openai") {
                    OutlinedTextField(
                        value = accountId,
                        onValueChange = { accountId = it },
                        label = { Text("ChatGPT account id (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (provider == "copilot") {
                    OutlinedTextField(
                        value = enterpriseUrl,
                        onValueChange = { enterpriseUrl = it },
                        label = { Text("GitHub Enterprise URL (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (provider == "openai" || provider == "copilot") {
                    Button(
                        onClick = {
                            oauthBusy = true
                            oauthCode = ""
                            oauthUrl = ""
                            saveStatus = "Starting OAuth..."

                            scope.launch {
                                runCatching {
                                    val session = if (provider == "openai") {
                                        oauthManager.startOpenAIDeviceFlow()
                                    } else {
                                        oauthManager.startCopilotDeviceFlow(enterpriseUrl)
                                    }

                                    oauthCode = session.userCode
                                    oauthUrl = session.verificationUrl
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(session.verificationUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                    saveStatus = "Open browser and enter code: ${session.userCode}"

                                    val result = if (provider == "openai") {
                                        oauthManager.completeOpenAIDeviceFlow(session)
                                    } else {
                                        oauthManager.completeCopilotDeviceFlow(session)
                                    }

                                    oauthToken = result.accessToken
                                    oauthRefreshToken = result.refreshToken
                                    oauthExpiresAtMs = result.expiresAtMs
                                    if (provider == "openai" && result.accountId.isNotBlank()) {
                                        accountId = result.accountId
                                    }
                                    if (provider == "copilot" && result.enterpriseUrl.isNotBlank()) {
                                        enterpriseUrl = result.enterpriseUrl
                                    }

                                    val parsedTemp = temperature.toDoubleOrNull()?.coerceIn(0.0, 2.0) ?: 0.1
                                    onSave(
                                        AgentRuntimeConfig(
                                            provider = provider,
                                            model = model.trim().ifBlank { selected.model },
                                            apiUrl = endpoint.trim(),
                                            apiKey = apiKey.trim(),
                                            authMode = "oauth_token",
                                            oauthAccessToken = oauthToken.trim(),
                                            oauthRefreshToken = oauthRefreshToken.trim(),
                                            oauthExpiresAtMs = oauthExpiresAtMs,
                                            accountId = accountId.trim(),
                                            enterpriseUrl = enterpriseUrl.trim(),
                                            temperature = parsedTemp
                                        )
                                    )
                                    saveStatus = "OAuth connected"
                                }.onFailure {
                                    saveStatus = "OAuth failed: ${it.message}"
                                }
                                oauthBusy = false
                            }
                        },
                        enabled = !oauthBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (oauthBusy) "Authorizing..." else "Connect OAuth in browser")
                    }

                    if (oauthCode.isNotBlank()) {
                        Text("Code: $oauthCode")
                    }
                    if (oauthUrl.isNotBlank()) {
                        Text("URL: $oauthUrl")
                    }
                }
            }
            OutlinedTextField(value = temperature, onValueChange = { temperature = it }, label = { Text("Temperature") }, modifier = Modifier.fillMaxWidth())
            Text(selected.docsHint)
            Text("Subscription support is provider-specific. ChatGPT/Codex and Copilot can use OAuth-style tokens; OpenAI/Anthropic/Gemini platform APIs still require API credentials or supported OAuth access tokens depending on provider.")

            Button(onClick = {
                val parsedTemp = temperature.toDoubleOrNull()?.coerceIn(0.0, 2.0) ?: 0.1
                val updated = AgentRuntimeConfig(
                    provider = provider,
                    model = model.trim().ifBlank { selected.model },
                    apiUrl = endpoint.trim(),
                    apiKey = apiKey.trim(),
                    authMode = authMode,
                    oauthAccessToken = oauthToken.trim(),
                    oauthRefreshToken = oauthRefreshToken.trim(),
                    oauthExpiresAtMs = oauthExpiresAtMs,
                    accountId = accountId.trim(),
                    enterpriseUrl = enterpriseUrl.trim(),
                    temperature = parsedTemp
                )
                onSave(updated)
                saveStatus = "Saved"
            }) {
                Text("Save LLM Config")
            }
            if (saveStatus.isNotBlank()) Text(saveStatus)
        }
    }
}

@Composable
private fun IntegrationsScreen(modifier: Modifier, config: IntegrationsConfig, onSave: (IntegrationsConfig) -> Unit) {
    var state by remember { mutableStateOf(config) }
    var status by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                GlassCard {
                    Text("Integrations", style = MaterialTheme.typography.titleLarge)
                    Text("Configure channels directly here. Sensitive values are encrypted on-device.")
                }
            }

            item {
                IntegrationCard("Telegram", state.telegramEnabled, { state = state.copy(telegramEnabled = it) }) {
                    SecretField("Bot token", state.telegramBotToken) { state = state.copy(telegramBotToken = it) }
                    OutlinedTextField(value = state.telegramChatId, onValueChange = { state = state.copy(telegramChatId = it) }, label = { Text("Chat ID") }, modifier = Modifier.fillMaxWidth())
                    Text("Setup: create bot with @BotFather, paste token and target chat id.")
                }
            }

            item {
                IntegrationCard("Discord", state.discordEnabled, { state = state.copy(discordEnabled = it) }) {
                    SecretField("Bot token", state.discordBotToken) { state = state.copy(discordBotToken = it) }
                    Text("Setup: create Discord app bot, grant permissions, paste bot token.")
                }
            }

            item {
                IntegrationCard("Slack", state.slackEnabled, { state = state.copy(slackEnabled = it) }) {
                    SecretField("Bot token", state.slackBotToken) { state = state.copy(slackBotToken = it) }
                    Text("Setup: create Slack app, add bot token scopes, install to workspace.")
                }
            }

            item {
                IntegrationCard("WhatsApp", state.whatsappEnabled, { state = state.copy(whatsappEnabled = it) }) {
                    SecretField("Access token", state.whatsappAccessToken) { state = state.copy(whatsappAccessToken = it) }
                    Text("Setup: use Meta WhatsApp Cloud API token and app configuration.")
                }
            }

            item {
                IntegrationCard("Composio", state.composioEnabled, { state = state.copy(composioEnabled = it) }) {
                    SecretField("Composio API key", state.composioApiKey) { state = state.copy(composioApiKey = it) }
                }
            }

            item {
                Button(onClick = {
                    onSave(state)
                    status = "Saved"
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Save Integrations")
                }
                if (status.isNotBlank()) Text(status, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun IntegrationCard(name: String, enabled: Boolean, onToggle: (Boolean) -> Unit, content: @Composable () -> Unit) {
    GlassCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
        if (enabled) content() else Text("Disabled")
    }
}

@Composable
private fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DeviceScreen(modifier: Modifier, broker: AndroidCapabilityBroker) {
    var packageName by remember { mutableStateOf("com.android.settings") }
    var sensor by remember { mutableStateOf("accelerometer") }
    var result by remember { mutableStateOf("Ready") }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard {
            Text("Device", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(value = packageName, onValueChange = { packageName = it }, label = { Text("Package") })
            Button(onClick = { result = broker.launchApp(packageName).fold({ it }, { it.message ?: "Failed" }) }) { Text("Launch App") }
            OutlinedTextField(value = sensor, onValueChange = { sensor = it }, label = { Text("Sensor") })
            Button(onClick = { result = broker.readSensorSnapshot(sensor) }) { Text("Read Sensor") }
            Text("Result: $result")
        }
    }
}

@Composable
private fun SecurityScreen(modifier: Modifier) {
    var requireApproval by remember { mutableStateOf(true) }
    var highRisk by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard {
            Text("Security", style = MaterialTheme.typography.titleLarge)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Require approval for calls/SMS")
                Switch(checked = requireApproval, onCheckedChange = { requireApproval = it })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Enable high-risk actions")
                Switch(checked = highRisk, onCheckedChange = { highRisk = it })
            }
        }
    }
}
