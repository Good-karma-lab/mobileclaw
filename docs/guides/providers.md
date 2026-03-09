# Provider Setup Guide

GUAPPA supports 20+ AI providers. You can use cloud APIs, local inference on your device, or local servers running on your network. This guide covers setup for each provider.

## How Provider Routing Works

GUAPPA uses capability-based routing. Each AI task type (text chat, vision, image generation, speech-to-text, text-to-speech, embedding, code, etc.) can be routed to a different provider and model. When you set a provider in Settings, GUAPPA uses it for general text chat. Specialized tasks (vision, image generation) can be configured separately.

Model lists are fetched dynamically from each provider's API. GUAPPA caches the available models locally and refreshes them hourly.

## Cloud Providers

### OpenAI

- **Endpoint:** `https://api.openai.com/v1`
- **Get an API key:** [platform.openai.com/api-keys](https://platform.openai.com/api-keys)
- **Models:** GPT-4.1, GPT-4.1-mini, GPT-4o-mini, o3, o4-mini, DALL-E 3, Whisper, TTS
- **Capabilities:** Text, vision, image generation, speech-to-text, text-to-speech, embeddings, tool use, streaming, reasoning
- **Setup:**
  1. Go to **Settings** > **Provider** > **OpenAI**.
  2. Paste your API key.
  3. Select a model (default: `gpt-4.1-mini`).

### Anthropic

- **Endpoint:** `https://api.anthropic.com/v1`
- **Get an API key:** [console.anthropic.com](https://console.anthropic.com)
- **Models:** Claude Opus 4, Claude Sonnet 4.5, Claude Haiku 3.5
- **Capabilities:** Text, vision, tool use, streaming, reasoning
- **Setup:**
  1. Go to **Settings** > **Provider** > **Anthropic**.
  2. Paste your API key.
  3. Select a model (default: `claude-3-5-sonnet-latest`).

### Google Gemini

- **Endpoint:** `https://generativelanguage.googleapis.com/v1beta`
- **Get an API key:** [aistudio.google.com/apikey](https://aistudio.google.com/apikey)
- **Models:** Gemini 2.5 Pro, Gemini 2.5 Flash, Gemini 2.0 Flash, Gemma
- **Capabilities:** Text, vision, image generation, embeddings, tool use, streaming
- **Setup:**
  1. Go to **Settings** > **Provider** > **Google Gemini**.
  2. Paste your API key (or use OAuth token).
  3. Select a model (default: `gemini-1.5-pro`).

### OpenRouter

- **Endpoint:** `https://openrouter.ai/api/v1`
- **Get an API key:** [openrouter.ai/keys](https://openrouter.ai/keys)
- **Why use it:** Access hundreds of models from many providers through a single API key. Useful for trying different models without separate accounts.
- **Setup:**
  1. Go to **Settings** > **Provider** > **OpenRouter**.
  2. Paste your API key.
  3. GUAPPA fetches the full model list automatically. Select any model.

### DeepSeek

- **Endpoint:** `https://api.deepseek.com`
- **Get an API key:** [platform.deepseek.com](https://platform.deepseek.com)
- **Models:** DeepSeek-V3.1, DeepSeek-R1
- **Capabilities:** Text, tool use, streaming, reasoning
- **Setup:** Settings > Provider > DeepSeek. Paste API key.

### Mistral AI

- **Endpoint:** `https://api.mistral.ai/v1`
- **Get an API key:** [console.mistral.ai](https://console.mistral.ai)
- **Models:** Magistral 2, Codestral, Pixtral, Mistral Large/Medium/Small
- **Capabilities:** Text, vision, code, tool use, streaming

### xAI / Grok

- **Endpoint:** `https://api.x.ai`
- **Get an API key:** [console.x.ai](https://console.x.ai)
- **Models:** Grok 3, Grok 3 Mini, Grok Vision
- **Capabilities:** Text, vision, tool use, streaming

### GitHub Copilot

- **Endpoint:** `https://api.githubcopilot.com`
- **Auth:** OAuth token from a Copilot-enabled GitHub account
- **Models:** GPT-4o-mini, GPT-4.1, Claude 3.5 Sonnet (via Copilot)
- **Capabilities:** Text, code, streaming

### Groq

- **Endpoint:** `https://api.groq.com/openai`
- **Get an API key:** [console.groq.com](https://console.groq.com)
- **Models:** Llama 3.1 70B, Llama 3.1 8B, Gemma2 9B
- **Why use it:** Extremely fast inference speeds.
- **Capabilities:** Text, tool use, streaming

### Together AI

- **Endpoint:** `https://api.together.xyz`
- **Get an API key:** [api.together.ai](https://api.together.ai)
- **Models:** Llama 3.1 70B Instruct Turbo, Mixtral 8x7B, and many more
- **Capabilities:** Text, embeddings, image generation, streaming

### Fireworks AI

- **Endpoint:** `https://api.fireworks.ai/inference/v1`
- **Get an API key:** [fireworks.ai](https://fireworks.ai)
- **Models:** Llama 3.1 70B, Mixtral, and more
- **Capabilities:** Text, streaming

### Perplexity

- **Endpoint:** `https://api.perplexity.ai`
- **Get an API key:** [perplexity.ai/settings](https://perplexity.ai/settings)
- **Models:** Sonar Large 128K, Sonar Small 128K
- **Why use it:** Models have built-in web search, so answers include real-time information.
- **Capabilities:** Text, search, streaming

### Cohere

- **Endpoint:** `https://api.cohere.com/compatibility`
- **Get an API key:** [dashboard.cohere.com](https://dashboard.cohere.com)
- **Models:** Command R+, Command R
- **Capabilities:** Text, embeddings, tool use, streaming

### MiniMax

- **Endpoint:** `https://api.minimaxi.com/v1`
- **Get an API key:** [platform.minimaxi.com](https://platform.minimaxi.com)
- **Models:** MiniMax-Text-01, abab6.5s
- **Capabilities:** Text, streaming

### Venice AI

- **Endpoint:** `https://api.venice.ai`
- **Get an API key:** [venice.ai](https://venice.ai)
- **Models:** Llama 3.3 70B, Mistral 31 24B
- **Why use it:** Privacy-focused, uncensored models.
- **Capabilities:** Text, streaming

### Moonshot / Kimi

- **Endpoint:** `https://api.moonshot.cn/v1`
- **Get an API key:** [platform.moonshot.cn](https://platform.moonshot.cn)
- **Models:** Moonshot V1 8K, V1 32K, V1 128K
- **Capabilities:** Text, streaming

### GLM / Zhipu AI

- **Endpoint:** `https://open.bigmodel.cn/api/paas/v4`
- **Get an API key:** [bigmodel.cn](https://bigmodel.cn)
- **Models:** GLM-4, GLM-4-Air
- **Capabilities:** Text, vision, tool use, streaming

### Qwen / Dashscope

- **Endpoint:** `https://dashscope.aliyuncs.com/compatible-mode/v1`
- **Get an API key:** [dashscope.aliyuncs.com](https://dashscope.aliyuncs.com)
- **Models:** Qwen Max, Qwen Plus, Qwen Turbo
- **Capabilities:** Text, vision, tool use, streaming

## Local Inference

### On-Device (Built-in)

Run AI models directly on your phone with no internet connection and no API key.

- **Engine:** llama.cpp via llama.rn (GGUF format)
- **Models:** Qwen3.5-0.8B, Qwen3.5-2B
- **Setup:**
  1. Go to **Settings** > **Provider** > **Local Inference (on-device)**.
  2. Tap **Download Model** and select a model.
  3. Wait for the download (models are 0.5-2 GB).
  4. Once downloaded, GUAPPA runs entirely offline.

**Performance tips:**
- Close other apps to free RAM.
- The 0.8B model is faster but less capable. Start with 2B if your device has 6+ GB RAM.
- First inference after download may take a few seconds to load the model into memory.
- Inference is slower on older devices. If responses take too long, consider a cloud provider.

### Ollama (Local Server)

Run models on your computer and connect GUAPPA over your local network.

- **Default endpoint:** `http://10.0.2.2:11434` (Android emulator addressing for localhost)
- **Setup:**
  1. Install Ollama on your computer: [ollama.com](https://ollama.com)
  2. Pull a model: `ollama pull llama3.1:8b`
  3. Start Ollama: `ollama serve`
  4. In GUAPPA Settings, select **Ollama** and set the endpoint.
  5. If running on a physical device, use your computer's local IP address (e.g., `http://192.168.1.100:11434`).

### LM Studio (Local Server)

- **Default endpoint:** `http://10.0.2.2:1234/v1`
- **Setup:**
  1. Install LM Studio on your computer: [lmstudio.ai](https://lmstudio.ai)
  2. Download and load a model in LM Studio.
  3. Start the local server in LM Studio.
  4. In GUAPPA Settings, select **LM Studio** and set the endpoint.

## Switching Providers

You can switch providers at any time from the **Settings** or **Command Center** screen. Changes apply instantly -- GUAPPA does not need to restart. The current conversation context is preserved when switching.

## Troubleshooting

**"API key invalid" error:**
- Double-check that you copied the full key, with no extra spaces.
- Some providers require billing setup before the key works.

**"Model not found" error:**
- The model may not be available on your account tier. Try a different model.
- For OpenRouter, ensure the model is not restricted.

**Slow responses:**
- Cloud providers: Check your internet connection.
- Local inference: Close other apps to free memory. Try a smaller model.

**Connection timeout:**
- For Ollama/LM Studio, verify the server is running and the endpoint is correct.
- On a physical device, make sure your phone and computer are on the same network.
