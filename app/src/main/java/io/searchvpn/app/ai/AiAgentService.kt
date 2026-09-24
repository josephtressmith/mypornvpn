package io.searchvpn.app.ai

import android.content.Context
import io.searchvpn.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object AiAgentService {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val systemPrompt = """
        You are SearchVPN Assistant, an expert, friendly AI guide built into the SearchVPN Android app on a Samsung Galaxy S24 Ultra.
        The user is very new to coding and uses Termux as their coding environment on their S24 Ultra.
        
        Guidelines:
        1. Explain everything step-by-step in plain language, avoiding confusing jargon.
        2. When giving shell or Termux commands, make them standalone, safe, and enclose them in backticks or code blocks so the user can easily copy and run them.
        3. App Context:
           - SearchVPN is an adult search aggregator & video downloader with WireGuard VPN privacy.
           - Downloaded videos can be saved to private internal storage or exported to public Android Movies (`~/storage/movies/SearchVPN`).
           - Users can stream videos, play them in Samsung Gallery, or play them via Termux (`pkg install mpv` -> `mpv ~/storage/movies/SearchVPN/filename.mp4`).
           - WireGuard VPN supports custom configurations or quick test configs.
        4. Assist with ANY request:
           - Coding help (Python, Bash, Git, HTML/JS, Node).
           - Termux setup, package installation, storage management (`termux-setup-storage`).
           - Search queries, video downloading, format transcoding with ffmpeg.
           - General trivia, explanations, productivity, or writing.
    """.trimIndent()

    val conversationHistory = mutableListOf<GeminiContent>()

    fun hasApiKey(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return !key.isNullOrBlank() && key != "GEMINI_API_KEY" && key != "AIzaSyDummyKey"
    }

    suspend fun sendMessage(userPrompt: String): ChatMessage = withContext(Dispatchers.IO) {
        val command = extractShellCommand(userPrompt)

        if (hasApiKey()) {
            try {
                val response = callGeminiApi(userPrompt)
                val responseCommand = extractShellCommand(response)
                ChatMessage(
                    id = System.currentTimeMillis().toString(),
                    sender = Sender.ASSISTANT,
                    text = response,
                    extractedCommand = responseCommand
                )
            } catch (e: Exception) {
                // If API call fails (e.g. invalid key or network error), fallback to smart offline assistant
                getOfflineAnswer(userPrompt, failureNotice = "*(Gemini API connection note: ${e.localizedMessage ?: "Network issue"}. Using smart offline assistant)*")
            }
        } else {
            getOfflineAnswer(userPrompt, failureNotice = null)
        }
    }

    private fun callGeminiApi(prompt: String): String {
        val apiKey = BuildConfig.GEMINI_API_KEY

        val userContent = GeminiContent(
            role = "user",
            parts = listOf(GeminiPart(text = prompt))
        )
        conversationHistory.add(userContent)

        // Keep last 10 turns to avoid token overflow
        val trimmedHistory = if (conversationHistory.size > 10) {
            conversationHistory.takeLast(10)
        } else {
            conversationHistory
        }

        val requestPayload = GeminiRequest(
            contents = trimmedHistory,
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
            generationConfig = GeminiGenerationConfig(temperature = 0.7f, maxOutputTokens = 1500)
        )

        val requestJson = json.encodeToString(GeminiRequest.serializer(), requestPayload)
        val url = "$BASE_URL?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw IllegalStateException("Empty response from Gemini")

        if (!response.isSuccessful) {
            throw IllegalStateException("API error ${response.code}: $responseBody")
        }

        val geminiResponse = json.decodeFromString(GeminiResponse.serializer(), responseBody)
        val text = geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("No text candidate returned")

        conversationHistory.add(GeminiContent(role = "model", parts = listOf(GeminiPart(text = text))))
        return text
    }

    /**
     * Extracts shell command from text if present (e.g. within backticks or lines starting with pkg, apt, termux, mpv, cd, ls, etc.)
     */
    fun extractShellCommand(text: String): String? {
        val codeBlockPattern = Pattern.compile("```(?:bash|sh)?\\n?([\\s\\S]*?)```")
        val matcher = codeBlockPattern.matcher(text)
        if (matcher.find()) {
            return matcher.group(1)?.trim()
        }

        val singleBacktickPattern = Pattern.compile("`([^`]+)`")
        val singleMatcher = singleBacktickPattern.matcher(text)
        while (singleMatcher.find()) {
            val candidate = singleMatcher.group(1)?.trim() ?: ""
            if (candidate.startsWith("termux-") || candidate.startsWith("pkg ") || candidate.startsWith("mpv ") || candidate.startsWith("ffmpeg") || candidate.startsWith("ls ") || candidate.startsWith("cd ")) {
                return candidate
            }
        }

        val lines = text.lines()
        for (line in lines) {
            val trimmed = line.trim().removePrefix("$").trim()
            if (trimmed.startsWith("termux-setup-storage") ||
                trimmed.startsWith("pkg install") ||
                trimmed.startsWith("mpv ~/storage") ||
                trimmed.startsWith("ls -la ~/storage") ||
                trimmed.startsWith("cp ~/storage")
            ) {
                return trimmed
            }
        }
        return null
    }

    private fun getOfflineAnswer(prompt: String, failureNotice: String?): ChatMessage {
        val lower = prompt.lowercase()
        val builder = StringBuilder()

        if (failureNotice != null) {
            builder.append(failureNotice).append("\n\n")
        } else {
            builder.append("*(Offline Assistant Mode active. To enable live Gemini AI queries on any topic, enter your Gemini API key in the Secrets panel in AI Studio)*\n\n")
        }

        var command: String? = null

        when {
            lower.contains("termux") && (lower.contains("storage") || lower.contains("permission") || lower.contains("access")) -> {
                builder.append("### How to Connect Termux to Storage on Galaxy S24 Ultra:\n\n")
                builder.append("1. Open the **Termux** app on your Samsung Galaxy S24 Ultra.\n")
                builder.append("2. Type this command and press Enter:\n\n")
                builder.append("```bash\ntermux-setup-storage\n```\n\n")
                builder.append("3. A Samsung Android popup will ask: *\"Allow Termux to access photos, media, and files?\"*\n")
                builder.append("4. Tap **Allow**.\n")
                builder.append("5. Now Termux creates a folder called `~/storage` that directly links to your phone's internal storage!")
                command = "termux-setup-storage"
            }

            lower.contains("where") && (lower.contains("download") || lower.contains("video") || lower.contains("file")) -> {
                builder.append("### Where SearchVPN Downloads Are Stored on Your S24 Ultra:\n\n")
                builder.append("When you choose **'Save to Samsung Gallery'** in the download dialog:\n\n")
                builder.append("• **In Termux path:**\n")
                builder.append("```bash\nls -la ~/storage/movies/SearchVPN\n```\n\n")
                builder.append("• **In Samsung Gallery App:**\n")
                builder.append("Open **Gallery** → tap the **Albums** tab at the bottom → open the **\"SearchVPN\"** album.\n\n")
                builder.append("• **In Samsung My Files App:**\n")
                builder.append("Open **My Files** → Internal Storage → **Movies** → **SearchVPN**.")
                command = "ls -la ~/storage/movies/SearchVPN"
            }

            (lower.contains("play") || lower.contains("watch") || lower.contains("mpv")) && (lower.contains("termux") || lower.contains("video")) -> {
                builder.append("### Playing Videos inside Termux with mpv:\n\n")
                builder.append("1. Install `mpv` player inside Termux by running:\n")
                builder.append("```bash\npkg install mpv -y\n```\n\n")
                builder.append("2. Then play any video downloaded from SearchVPN:\n")
                builder.append("```bash\nmpv ~/storage/movies/SearchVPN/*.mp4\n```\n\n")
                builder.append("Tip: You can also use hardware decoding with `--hwdec=auto` for smooth playback on your Snapdragon 8 Gen 3 chip!")
                command = "pkg install mpv -y && mpv ~/storage/movies/SearchVPN/*.mp4"
            }

            lower.contains("wireguard") || lower.contains("vpn") -> {
                builder.append("### How WireGuard VPN Works in SearchVPN:\n\n")
                builder.append("1. Go to the **VPN** tab in the bottom bar.\n")
                builder.append("2. Tap **'Load Demo / Test Config'** or paste your own WireGuard configuration (.conf).\n")
                builder.append("3. Toggle the **Connect** switch.\n")
                builder.append("4. Android will display a system dialog requesting permission to create a VPN connection. Tap **OK**.\n\n")
                builder.append("Once connected, all your search requests and video downloads are encrypted and tunneled securely!")
            }

            lower.contains("theme") || lower.contains("wallpaper") || lower.contains("dark") || lower.contains("light") -> {
                builder.append("### Customizing Themes & Wallpapers:\n\n")
                builder.append("1. Tap the **Settings** tab in the bottom navigation bar.\n")
                builder.append("2. Under **Display Mode**, switch between **☀️ Light**, **🌙 Dark**, or **⚙️ System**.\n")
                builder.append("3. Under **Movie, Scene & Landscape Themes**, choose from **The Matrix**, **Blade Runner 2049**, **Interstellar**, **Nordic Aurora**, and more.\n")
                builder.append("4. Tap **'Upload Movie / Landscape Art'** to pick any photo from your Samsung Gallery. SearchVPN will extract the vibrant dominant accent colors and apply it to the app background!")
            }

            lower.contains("python") || lower.contains("code") || lower.contains("coding") || lower.contains("git") -> {
                builder.append("### Getting Started with Coding in Termux on Galaxy S24 Ultra:\n\n")
                builder.append("Here is how to set up a full beginner Python & Git environment in Termux:\n\n")
                builder.append("1. Update your packages:\n")
                builder.append("```bash\npkg update && pkg upgrade -y\n```\n\n")
                builder.append("2. Install Python, Git, and Nano editor:\n")
                builder.append("```bash\npkg install python git nano -y\n```\n\n")
                builder.append("3. Test Python:\n")
                builder.append("```bash\npython -c \"print('Hello from Galaxy S24 Ultra!')\"\n```")
                command = "pkg update && pkg install python git nano -y"
            }

            else -> {
                builder.append("I am your **SearchVPN AI Assistant** on your Samsung Galaxy S24 Ultra!\n\n")
                builder.append("I can help you with:\n")
                builder.append("• **Termux Coding & Storage**: Commands, scripts, `termux-setup-storage`, ffmpeg, python, and package tips.\n")
                builder.append("• **Video Library & Downloads**: Finding videos in Samsung Gallery or `~/storage/movies/SearchVPN`.\n")
                builder.append("• **VPN & Privacy**: WireGuard tunneling and secure searches.\n")
                builder.append("• **General Questions & Coding**: Explanations, debugging, search queries, and learning to code step-by-step.\n\n")
                builder.append("Feel free to ask me any question or pick a suggested topic below!")
            }
        }

        return ChatMessage(
            id = System.currentTimeMillis().toString(),
            sender = Sender.ASSISTANT,
            text = builder.toString(),
            extractedCommand = command
        )
    }
}
