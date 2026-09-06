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
    GEMINI,
    CUSTOM_OPENAI
}

/**
 * AI Translation Engine supporting OpenAI, Google Gemini, and Custom LLM REST APIs.
 *
 * NOTE: The apiKey parameter is held strictly in-memory for the duration of the object's lifecycle.
 * It is NEVER persisted to disk, SharedPreferences, cache files, or log outputs.
 */
class AiTranslateEngine(
    val provider: AiProvider = AiProvider.OPENAI,
    private val apiKey: String = "",
    val modelName: String = "gpt-4o-mini",
    val customEndpoint: String = "https://api.openai.com/v1/chat/completions",
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
        if (cache.containsKey(text)) {
            return cache[text]!!
        }

        val encodedText = FormulaPlaceholder.encodeFormulaPlaceholders(text)
        val rawTranslation = when (provider) {
            AiProvider.OPENAI, AiProvider.CUSTOM_OPENAI -> fetchOpenAiTranslation(encodedText)
            AiProvider.GEMINI -> fetchGeminiTranslation(encodedText)
        }

        val restoredText = FormulaPlaceholder.restoreFormulaPlaceholders(text, rawTranslation)
        cache[text] = restoredText
        return restoredText
    }

    private fun systemPrompt(): String {
        val langName = if (targetLang.equals("vi", ignoreCase = true)) "Vietnamese" else targetLang
        return "You are a professional document translator. Translate the text into $langName. " +
                "CRITICAL: Keep all placeholder tags like <b0></b0>, <b1></b1> and style tags like <s1></s1> EXACTLY as they are. " +
                "Output ONLY the translated text without extra explanations or formatting code blocks."
    }

    private fun fetchOpenAiTranslation(query: String): String {
        val endpoint = if (provider == AiProvider.CUSTOM_OPENAI && customEndpoint.isNotBlank()) {
            customEndpoint
        } else {
            "https://api.openai.com/v1/chat/completions"
        }

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
            put("temperature", 0.3)
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(body)

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("AI API error HTTP ${response.code}: ${response.message}")
            }
            val responseStr = response.body?.string() ?: throw IOException("Empty response from AI API")
            val jsonResp = JSONObject(responseStr)
            val choices = jsonResp.optJSONArray("choices")
                ?: throw IOException("Invalid AI API response structure")
            if (choices.length() == 0) throw IOException("AI API returned no content")
            val content = choices.getJSONObject(0).getJSONObject("message").optString("content", "")
            return FormulaPlaceholder.removeControlCharacters(content.trim())
        }
    }

    private fun fetchGeminiTranslation(query: String): String {
        val model = if (modelName.isNotBlank()) modelName else "gemini-1.5-flash"
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val contents = JSONArray().apply {
            put(JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "${systemPrompt()}\n\nText to translate:\n$query")
                    })
                })
            })
        }

        val json = JSONObject().apply {
            put("contents", contents)
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Gemini API error HTTP ${response.code}: ${response.message}")
            }
            val responseStr = response.body?.string() ?: throw IOException("Empty response from Gemini API")
            val jsonResp = JSONObject(responseStr)
            val candidates = jsonResp.optJSONArray("candidates")
                ?: throw IOException("Invalid Gemini API response structure")
            if (candidates.length() == 0) throw IOException("Gemini API returned no candidates")
            val parts = candidates.getJSONObject(0).getJSONObject("content").optJSONArray("parts")
                ?: throw IOException("Invalid Gemini content parts")
            val content = parts.getJSONObject(0).optString("text", "")
            return FormulaPlaceholder.removeControlCharacters(content.trim())
        }
    }
}
