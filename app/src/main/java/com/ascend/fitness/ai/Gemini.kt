package com.ascend.fitness.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class GeminiException(message: String, val busy: Boolean = false) : Exception(message)

data class ChatTurn(val fromUser: Boolean, val text: String)

/**
 * Minimal Gemini REST client. The API key goes only in the x-goog-api-key header, never in the URL or logs.
 * Only https://generativelanguage.googleapis.com is ever contacted.
 */
object Gemini {
    private val MODEL_RE = Regex("^[a-z0-9][a-z0-9.\\-]{0,63}$")

    /** Retries briefly when Gemini is overloaded (500/502/503/504), which happens at busy times. */
    suspend fun generate(
        apiKey: String,
        model: String,
        system: String,
        turns: List<ChatTurn>,
        json: Boolean = false,
        temperature: Double = 0.7,
    ): String {
        var wait = 1_500L
        repeat(2) {
            try { return generateOnce(apiKey, model, system, turns, json, temperature) }
            catch (e: GeminiException) { if (!e.busy) throw e }
            kotlinx.coroutines.delay(wait); wait *= 2
        }
        return generateOnce(apiKey, model, system, turns, json, temperature)
    }

    private suspend fun generateOnce(
        apiKey: String,
        model: String,
        system: String,
        turns: List<ChatTurn>,
        json: Boolean = false,
        temperature: Double = 0.7,
    ): String = withContext(Dispatchers.IO) {
        if (!MODEL_RE.matches(model)) throw GeminiException("Invalid model name in Settings.")
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().apply {
                turns.forEach { t ->
                    put(JSONObject().put("role", if (t.fromUser) "user" else "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", t.text))))
                }
            })
            .put("generationConfig", JSONObject().put("temperature", temperature).apply {
                if (json) put("responseMimeType", "application/json")
            })
        val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .openConnection() as HttpsURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 90_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrNull()
                throw GeminiException(when (code) {
                    400, 403 -> "Gemini rejected the request. Check your API key in Settings." + (msg?.let { "\n$it" } ?: "")
                    429 -> "Gemini rate limit reached. Try again in a minute."
                    404 -> "This Gemini model isn't available for your key. Set the model in Settings to gemini-3.5-flash-lite."
                    in 500..504 -> "Gemini is busy right now. Try again in a minute."
                    else -> "Gemini error $code" + (msg?.let { ": $it" } ?: "")
                }, busy = code in 500..504)
            }
            val parts = JSONObject(text).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            buildString { for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text")) }.trim()
                .ifEmpty { throw GeminiException("Gemini returned an empty answer.") }
        } catch (e: GeminiException) {
            throw e
        } catch (e: java.io.IOException) {
            throw GeminiException("No connection to Gemini. AI features need internet; everything else works offline.")
        } catch (e: org.json.JSONException) {
            throw GeminiException("Gemini sent an answer the app could not read.")
        } finally {
            conn.disconnect()
        }
    }
}
