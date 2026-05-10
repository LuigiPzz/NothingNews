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

@Singleton
class AiRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val preferenceManager: com.nothing.news.data.local.PreferenceManager
) {
    private val modelName = "gemini-2.5-flash"
 
    suspend fun summarizeArticle(title: String, content: String?, link: String): String? = withContext(Dispatchers.IO) {
        if (content.isNullOrBlank() && link.isBlank()) return@withContext null
        
        // Fetch API key from preferences, fallback to BuildConfig if not set
        var userApiKey = preferenceManager.geminiApiKey.first()
        
        if (userApiKey.isNullOrBlank()) {
            userApiKey = com.nothing.news.BuildConfig.GEMINI_API_KEY
        }
        
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
                val text = jsonResponse.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                
                text
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val message = e.localizedMessage ?: ""
            when {
                message.contains("429") || message.contains("quota", ignoreCase = true) -> 
                    "• Limite giornaliero raggiunto per i riassunti IA.\n• La versione gratuita di Gemini ha un numero limitato di richieste.\n• Riprova tra qualche minute o domani."
                message.contains("safety", ignoreCase = true) ->
                    "• Il contenuto dell'articolo è stato bloccato dai filtri di sicurezza dell'IA.\n• Prova con un'altra notizia meno sensibile."
                else -> 
                    "• Errore IA: ${e.localizedMessage ?: "Connessione fallita"}\n• Verifica la tua connessione o riprova più tardi."
            }
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
                val html = response.body?.string() ?: return@use null
                
                // Estrai il testo contenuto nei tag <p>
                val pRegex = """<p[^>]*>(.*?)</p>""".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                val paragraphs = pRegex.findAll(html).map {
                    // Rimuovi eventuali altri tag HTML all'interno del paragrafo
                    it.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
                }.filter { it.length > 30 }.joinToString("\n")
                
                // Restituisci il testo estratto solo se è abbastanza lungo, altrimenti null
                if (paragraphs.length > 150) paragraphs else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
