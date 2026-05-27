package com.nothing.news.data.repository

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.text.HtmlCompat

@Singleton
class AiRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val preferenceManager: com.nothing.news.data.local.PreferenceManager
) {
    private val modelName = "gemini-2.5-flash"
 
    suspend fun summarizeArticle(title: String, content: String?, link: String): String? = withContext(Dispatchers.IO) {
        if (content.isNullOrBlank() && link.isBlank()) return@withContext null
        
        // Fetch API key from preferences
        val userApiKey = preferenceManager.geminiApiKey.first()
        
        if (userApiKey.isNullOrBlank()) {
            return@withContext "• Funzionalità IA Disabilitata.\n• Inserisci la tua chiave API di Gemini nelle Impostazioni per generare i riassunti."
        }

        try {
            // Tentativo di scaricare il testo intero dell'articolo dal link
            val fullText = fetchFullWebText(link)
            val finalContent = if (!fullText.isNullOrBlank()) fullText else content ?: ""

            val prompt = """
                Compito: Scrivi un UNICO paragrafo discorsivo di massimo 3-4 righe.
                Il tuo focus principale DEVE essere rispondere direttamente alle anticipazioni o alle domande sollevate dal Titolo della notizia.
                Ad esempio: se il titolo parla di un calo di prezzo, estrai e scrivi il prezzo esatto; se fa riferimento a una data di uscita, indica la data; se annuncia una novità, spiegala subito in modo chiaro.
                Stile: Nothing OS (minimale, tecnico, essenziale, diretto).
                
                Titolo originale: $title
                Contenuto articolo: $finalContent
                
                REGOLE DA SEGUIRE (ORDINI TASSATIVI):
                1. NON riscrivere MAI il titolo della notizia nel riassunto.
                2. NON usare MAI elenchi puntati o trattini (niente 1., 2., •, -).
                3. Inizia DIRETTAMENTE con i fatti o la risposta (es. niente "Questo articolo parla di...", "Il prezzo è...", "La data è...").
                4. Scrivi solo testo continuo e fluido.
            """.trimIndent()

            val requestBody = """
                {
                  "contents": [{
                    "parts": [{
                      "text": ${Gson().toJson(prompt)}
                    }]
                  }]
                }
            """.trimIndent()

            val maxAttempts = 3
            var attempts = 0
            var lastException: Exception? = null
            var resultText: String? = null

            while (attempts < maxAttempts) {
                attempts++
                try {
                    val request = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$userApiKey")
                        .post(requestBody.toRequestBody("application/json".toMediaType()))
                        .build()

                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string() ?: ""
                            throw Exception("API Error ${response.code}: $errorBody")
                        }

                        val responseBody = response.body?.string() ?: throw Exception("Empty response")
                        val jsonResponse = JSONObject(responseBody)
                        resultText = jsonResponse.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")
                    }
                    // Call succeeded, exit the loop
                    break
                } catch (e: Exception) {
                    lastException = e
                    if (attempts < maxAttempts) {
                        val delayMs = attempts * 1000L
                        kotlinx.coroutines.delay(delayMs)
                    }
                }
            }

            if (resultText != null) {
                resultText
            } else {
                throw lastException ?: Exception("Unknown error")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val message = e.localizedMessage ?: ""
            when {
                message.contains("429") || message.contains("quota", ignoreCase = true) -> 
                    "• Limite giornaliero raggiunto per i riassunti IA.\n• La versione gratuita di Gemini ha un numero limitato di richieste.\n• Riprova tra qualche minuto o domani."
                message.contains("safety", ignoreCase = true) ->
                    "• Il contenuto dell'articolo è stato bloccato dai filtri di sicurezza dell'IA.\n• Prova con un'altra notizia meno sensibile."
                else -> 
                    "• Errore IA: ${e.localizedMessage ?: "Connessione fallita"}\n• Verifica la tua connessione o riprova più tardi."
            }
        }
    }

    suspend fun suggestCalendarEvent(
        articleTitle: String,
        content: String?,
        link: String,
        currentDate: String
    ): CalendarEventSuggestion? = withContext(Dispatchers.IO) {
        val userApiKey = preferenceManager.geminiApiKey.first()
        if (userApiKey.isNullOrBlank()) return@withContext null

        try {
            val fullText = fetchFullWebText(link)
            val finalContent = if (!fullText.isNullOrBlank()) fullText else content ?: ""

            val prompt = """
                Compito: Analizza il titolo e il testo del seguente articolo per identificare e suggerire un evento da inserire in calendario.
                In particolare, cerca la data di uscita di un prodotto (videogioco, film, serie tv, hardware) o la data di svolgimento di un evento (conferenza, fiera, presentazione, lancio, ecc.).
                
                Restituisci esclusivamente un oggetto JSON valido con la seguente struttura:
                {
                  "title": "Nome dell'evento o del prodotto (es. 'Uscita GTA VI', 'Presentazione PlayStation 5 Pro')",
                  "date": "YYYY-MM-DD",
                  "time": "HH:mm"
                }

                Regole per la data:
                1. Utilizza il formato standard 'YYYY-MM-DD' (es. '2025-10-24').
                2. Se nell'articolo viene menzionata una finestra temporale (es. 'autunno 2025', 'Q4 2025', 'fine anno', 'metà 2026'), convertila in una data indicativa realistica (es. 'autunno 2025' -> '2025-10-01', 'metà 2026' -> '2026-06-30').
                3. Se non è presente alcuna data, finestra temporale o riferimento temporale nel testo, usa la data odierna: $currentDate.
                4. La data odierna di riferimento è: $currentDate. Usala per interpretare termini relativi come 'oggi', 'domani', 'la prossima settimana', 'questo venerdì'.

                Regole per l'ora:
                1. Utilizza il formato 'HH:mm' in 24 ore (es. '18:30', '09:00').
                2. Se l'articolo menziona un'ora specifica (es. 'alle 21:00', 'at 3pm', 'ore 15:00', 'a mezzanotte'), usala convertita in 24h.
                3. Se l'articolo menziona solo una data di uscita senza ora (es. gioco in uscita il 15 ottobre), usa '00:01' come ora simbolica.
                4. Se non si riesce a determinare nessuna ora, usa '09:00' come valore predefinito.
                
                Titolo articolo: $articleTitle
                Contenuto articolo: $finalContent
            """.trimIndent()

            val requestBody = """
                {
                  "contents": [{
                    "parts": [{
                      "text": ${Gson().toJson(prompt)}
                    }]
                  }],
                  "generationConfig": {
                    "responseMimeType": "application/json"
                  }
                }
            """.trimIndent()

            val maxAttempts = 3
            var attempts = 0
            var lastException: Exception? = null
            var resultJson: String? = null

            while (attempts < maxAttempts) {
                attempts++
                try {
                    val request = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$userApiKey")
                        .post(requestBody.toRequestBody("application/json".toMediaType()))
                        .build()

                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string() ?: ""
                            throw Exception("API Error ${response.code}: $errorBody")
                        }

                        val responseBody = response.body?.string() ?: throw Exception("Empty response")
                        val jsonResponse = JSONObject(responseBody)
                        resultJson = jsonResponse.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")
                    }
                    break
                } catch (e: Exception) {
                    lastException = e
                    if (attempts < maxAttempts) {
                        val delayMs = attempts * 1000L
                        kotlinx.coroutines.delay(delayMs)
                    }
                }
            }

            if (resultJson != null) {
                val cleanJson = resultJson!!.trim().removePrefix("```json").removeSuffix("```").trim()
                val obj = JSONObject(cleanJson)
                val title = obj.getString("title")
                val dateStr = obj.getString("date")
                val timeStr = if (obj.has("time")) obj.getString("time") else null
                CalendarEventSuggestion(title, dateStr, timeStr)
            } else {
                throw lastException ?: Exception("Unknown error")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun fetchFullWebText(url: String): String? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body ?: return@use null
                val bytes = body.bytes()
                
                // Detect encoding with a 3-level robust logic
                val totalSize = bytes.size
                if (totalSize == 0) return@withContext null
                
                // 1. Check for BOM
                val isUtf8 = if (totalSize >= 3 && (bytes[0].toInt() and 0xFF) == 0xEF && (bytes[1].toInt() and 0xFF) == 0xBB && (bytes[2].toInt() and 0xFF) == 0xBF) {
                    true
                } else {
                    // 2. Probabilistic UTF-8 check
                    val testString = String(bytes, Charsets.UTF_8)
                    !testString.contains("\uFFFD") && bytes.any { (it.toInt() and 0xFF) > 0x7F }
                }

                var finalIsUtf8 = isUtf8
                if (!finalIsUtf8) {
                    // 3. Aggressive heuristic
                    var utf8Score = 0
                    for (j in 0 until totalSize - 1) {
                        val b1 = bytes[j].toInt() and 0xFF
                        val b2 = bytes[j + 1].toInt() and 0xFF
                        if ((b1 == 0xC3 || b1 == 0xC2) && b2 in 0x80..0xBF) {
                            utf8Score++
                            if (utf8Score > 0) break
                        }
                    }
                    finalIsUtf8 = utf8Score > 0
                }

                val headSize = minOf(totalSize, 8192)
                val detectionString = String(bytes, 0, headSize, Charsets.ISO_8859_1)
                val charsetName = Regex("""<meta.*?charset=["']?([^"'>\s]+)["']?""", RegexOption.IGNORE_CASE).find(detectionString)?.groupValues?.get(1)
                    ?: body.contentType()?.charset()?.name()
                    ?: if (!finalIsUtf8 && (detectionString.contains("it-IT", ignoreCase = true) || detectionString.contains("hwupgrade.it", ignoreCase = true))) "Windows-1252" else "UTF-8"
                
                val charset = if (finalIsUtf8) Charsets.UTF_8 else try {
                    val cs = java.nio.charset.Charset.forName(charsetName)
                    if (cs.name().contains("ISO-8859-1", ignoreCase = true)) {
                        java.nio.charset.Charset.forName("Windows-1252")
                    } else cs
                } catch (e: Exception) {
                    Charsets.UTF_8
                }
                
                val html = String(bytes, charset)
                
                // Estrai il testo contenuto nei tag <p>
                val pRegex = """<p[^>]*>(.*?)</p>""".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                val paragraphs = pRegex.findAll(html).map {
                    // Rimuovi eventuali altri tag HTML all'interno del paragrafo e decodifica entità base
                    val cleanText = it.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
                    try {
                        val spanned = HtmlCompat.fromHtml(cleanText, HtmlCompat.FROM_HTML_MODE_LEGACY)
                        var result = spanned.toString().trim()
                        result = repairEncoding(result)
                        result = result.replace('\u00A0', ' ')
                        result = result.replace("\u200B", "")
                        result
                    } catch (e: Exception) {
                        cleanText
                    }
                }.filter { it.length > 30 }.joinToString("\n")
                
                // Restituisci il testo estratto solo se è abbastanza lungo, altrimenti null
                if (paragraphs.length > 150) paragraphs else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun repairEncoding(text: String): String {
        if (!text.contains("\u00C3") && !text.contains("\u00C2")) return text
        
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if ((c == '\u00C3' || c == '\u00C2') && i + 1 < text.length) {
                val next = text[i + 1].code
                if (next in 0x80..0xBF || next in 0xA0..0xBF) {
                    val base = if (c == '\u00C3') 0xC0 else 0x80
                    val repairedChar = ((next and 0x3F) or base).toChar()
                    out.append(repairedChar)
                    i += 2
                    continue
                }
            }
            out.append(c)
            i++
        }
        val result = out.toString()
        
        return try {
            val bytes = result.toByteArray(java.nio.charset.Charset.forName("ISO-8859-1"))
            val decoded = String(bytes, Charsets.UTF_8)
            if (decoded.contains("\uFFFD")) result else decoded
        } catch (e: Exception) {
            result
        }
    }
}

data class CalendarEventSuggestion(
    val title: String,
    val date: String,
    val time: String? = null
)
