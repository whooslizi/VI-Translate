package com.vitranslate.pdf.repository

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class AiProvider {
    OPENAI,
    DEEPSEEK,
    GEMINI,
    OPENROUTER,
    GROQ,
    SILICONFLOW,
    CUSTOM_OPENAI
}

class AiTranslateEngine(
    val provider: AiProvider = AiProvider.OPENAI,
    private val apiKey: String = "",
    val modelName: String = "gpt-4o-mini",
    val customEndpoint: String = "",
    val targetLang: String = "vi"
) : TranslationEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, String>()

    @Throws(IOException::class, FormulaPlaceholderException::class)
    override fun translate(rawText: String): String {
        if (rawText.isBlank()) return rawText

        val text = SourceTextNormaliser.normalise(rawText)
        if (cache.containsKey(text)) return cache[text]!!

        val encodedText = FormulaPlaceholder.encodeFormulaPlaceholders(text)
        val rawTranslation = when (provider) {
            AiProvider.GEMINI -> fetchGeminiTranslation(encodedText)
            else -> fetchOpenAiCompatibleTranslation(encodedText)
        }

        val restoredText = FormulaPlaceholder.restoreFormulaPlaceholders(text, rawTranslation)
        cache[text] = restoredText
        return restoredText
    }

    private fun systemPrompt(): String {
        val langName = if (targetLang.equals("vi", ignoreCase = true)) "Vietnamese" else targetLang
        return "You are a professional document translator. Translate into $langName.\n" +
                "CRITICAL INSTRUCTIONS:\n" +
                "1. Preserve ALL tags like <b0></b0>, <b1></b1>, <s1></s1> in their exact position.\n" +
                "2. Do NOT translate or alter tag IDs.\n" +
                "3. Output ONLY the raw translated text with tags intact. Do NOT add Markdown code blocks or explanation."
    }

    private fun resolveEndpoint(): String {
        if (customEndpoint.isNotBlank()) return customEndpoint
        return when (provider) {
            AiProvider.OPENAI -> "https://api.openai.com/v1/chat/completions"
            AiProvider.DEEPSEEK -> "https://api.deepseek.com/v1/chat/completions"
            AiProvider.OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions"
            AiProvider.GROQ -> "https://api.groq.com/openai/v1/chat/completions"
            AiProvider.SILICONFLOW -> "https://api.siliconflow.cn/v1/chat/completions"
            AiProvider.CUSTOM_OPENAI -> "http://10.0.2.2:11434/v1/chat/completions"
            else -> "https://api.openai.com/v1/chat/completions"
        }
    }

    private fun fetchOpenAiCompatibleTranslation(query: String): String {
        val endpoint = resolveEndpoint()
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt())
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", query)
            })
        }

        val json = JSONObject().apply {
            put("model", modelName)
            put("messages", messages)
            put("temperature", 0.1)
        }

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("AI API HTTP ${response.code}: ${response.message}")
            val jsonResp = JSONObject(response.body?.string() ?: "")
            val choices = jsonResp.optJSONArray("choices") ?: throw IOException("Invalid AI response")
            if (choices.length() == 0) throw IOException("AI returned no content")
            val content = choices.getJSONObject(0).getJSONObject("message").optString("content", "")
            return FormulaPlaceholder.removeControlCharacters(content.trim())
        }
    }

    private fun fetchGeminiTranslation(query: String): String {
        val model = if (modelName.isNotBlank()) modelName else "gemini-2.0-flash"
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val contents = JSONArray().apply {
            put(JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "${systemPrompt()}\n\n$query")
                    })
                })
            })
        }

        val json = JSONObject().apply { put("contents", contents) }

        val request = Request.Builder()
            .url(url)
            .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Gemini API HTTP ${response.code}: ${response.message}")
            val jsonResp = JSONObject(response.body?.string() ?: "")
            val candidates = jsonResp.optJSONArray("candidates") ?: throw IOException("Invalid Gemini response")
            if (candidates.length() == 0) throw IOException("Gemini returned no candidates")
            val parts = candidates.getJSONObject(0).getJSONObject("content").optJSONArray("parts") ?: throw IOException("Invalid Gemini parts")
            val content = parts.getJSONObject(0).optString("text", "")
            return FormulaPlaceholder.removeControlCharacters(content.trim())
        }
    }
}
