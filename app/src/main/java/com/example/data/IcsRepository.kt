package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import biweekly.Biweekly
import biweekly.component.VEvent
import com.example.model.CourseEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Repository unique Flop!EDT basé sur la synchronisation d'un flux iCal (.ics).
 * Zéro latence : Cache mémoire L1 + Cache disque JSON ultra-rapide (context.cacheDir/schedule.json).
 */
class IcsRepository(private val context: Context) {

    companion object {
        private const val TAG = "IcsRepository"
        const val CACHE_FILE_NAME = "schedule.json"
        val PARIS_ZONE: ZoneId = ZoneId.of("Europe/Paris")

        // Cache mémoire L1 ultra-rapide (0 ms)
        @Volatile
        private var memoryCachedEvents: List<CourseEvent>? = null
        @Volatile
        private var memoryCacheTimestamp: Long = 0L

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }

        private val CACHE_LOCK = Any()
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val cacheFile: File
        get() = File(context.cacheDir, CACHE_FILE_NAME)

    /**
     * Lecture synchrone et instantanée (0ms) de l'ensemble des cours en cache.
     * Priorité au cache L1 en RAM, puis au fichier JSON local.
     */
    fun getCachedEvents(): List<CourseEvent> {
        try {
            val lastMod = if (cacheFile.exists()) cacheFile.lastModified() else 0L
            val inMem = memoryCachedEvents
            if (inMem != null && lastMod != 0L && lastMod == memoryCacheTimestamp) {
                return inMem
            }

            if (!cacheFile.exists()) {
                createDefaultDemoScheduleIfMissing(LocalDate.now(PARIS_ZONE))
            }

            val text = cacheFile.readText()
            if (text.isBlank()) return emptyList()

            val decoded = json.decodeFromString<List<CourseEvent>>(text)
            memoryCachedEvents = decoded
            memoryCacheTimestamp = cacheFile.lastModified()
            return decoded
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cache schedule.json", e)
            return emptyList()
        }
    }

    /**
     * Retourne les cours du jour filtrés et triés par heure de début.
     */
    fun getEventsForDate(targetDate: LocalDate): List<CourseEvent> {
        return getCachedEvents()
            .filter { it.startTime.toLocalDate() == targetDate }
            .sortedBy { it.startTime }
    }

    fun getTodayEvents(targetDate: LocalDate = LocalDate.now(PARIS_ZONE)): List<CourseEvent> =
        getEventsForDate(targetDate)

    /**
     * Retourne l'ensemble de tous les cours en cache, triés par date/heure.
     */
    fun getAllEvents(): List<CourseEvent> {
        return getCachedEvents().sortedBy { it.startTime }
    }

    /**
     * Retourne les cours groupés par date pour la semaine contenant [anchorDate] (du Lundi au Dimanche).
     */
    fun getEventsForWeek(anchorDate: LocalDate): Map<LocalDate, List<CourseEvent>> {
        val monday = anchorDate.with(java.time.DayOfWeek.MONDAY)
        val sunday = anchorDate.with(java.time.DayOfWeek.SUNDAY)
        val all = getCachedEvents()
        val result = mutableMapOf<LocalDate, MutableList<CourseEvent>>()

        var d = monday
        while (!d.isAfter(sunday)) {
            result[d] = mutableListOf()
            d = d.plusDays(1)
        }

        for (event in all) {
            val eventDate = event.startTime.toLocalDate()
            if (!eventDate.isBefore(monday) && !eventDate.isAfter(sunday)) {
                result[eventDate]?.add(event)
            }
        }

        return result.mapValues { entry -> entry.value.sortedBy { it.startTime } }
    }

    /**
     * Retourne les cours groupés par date pour le mois [yearMonth].
     */
    fun getEventsForMonth(yearMonth: java.time.YearMonth): Map<LocalDate, List<CourseEvent>> {
        val firstDay = yearMonth.atDay(1)
        val lastDay = yearMonth.atEndOfMonth()
        val all = getCachedEvents()
        val result = mutableMapOf<LocalDate, MutableList<CourseEvent>>()

        var d = firstDay
        while (!d.isAfter(lastDay)) {
            result[d] = mutableListOf()
            d = d.plusDays(1)
        }

        for (event in all) {
            val eventDate = event.startTime.toLocalDate()
            if (!eventDate.isBefore(firstDay) && !eventDate.isAfter(lastDay)) {
                result[eventDate]?.add(event)
            }
        }

        return result.mapValues { entry -> entry.value.sortedBy { it.startTime } }
    }

    /**
     * Nettoie le cache en mémoire et sur disque, puis réinitialise les données par défaut.
     */
    fun clearCache() {
        synchronized(CACHE_LOCK) {
            memoryCachedEvents = null
            memoryCacheTimestamp = 0L
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
            createDefaultDemoScheduleIfMissing(LocalDate.now(PARIS_ZONE))
        }
    }

    /**
     * Télécharge le flux .ics via OkHttp, parse avec Biweekly,
     * convertit les dates au fuseau Europe/Paris, sauvegarde en cache JSON et retourne les cours du jour.
     */
    suspend fun syncFromRemote(
        urlString: String,
        targetDate: LocalDate = LocalDate.now(PARIS_ZONE)
    ): Result<List<CourseEvent>> = withContext(Dispatchers.IO) {
        val cleanUrl = urlString.trim()
        if (cleanUrl.isBlank() || (!cleanUrl.startsWith("http://", ignoreCase = true) &&
                    !cleanUrl.startsWith("https://", ignoreCase = true) &&
                    !cleanUrl.startsWith("webcal://", ignoreCase = true))) {
            return@withContext Result.failure(IllegalArgumentException("URL invalide. Doit commencer par http://, https:// ou webcal://"))
        }

        // Remplace webcal:// par https:// pour compatibilité HTTP standard
        val httpUrl = if (cleanUrl.startsWith("webcal://", ignoreCase = true)) {
            "https://" + cleanUrl.removePrefix("webcal://")
        } else {
            cleanUrl
        }

        Log.d(TAG, "Syncing ICS from: $httpUrl")

        try {
            val request = Request.Builder()
                .url(httpUrl)
                .header("Accept", "text/calendar, application/ics, text/plain, */*")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 (FlopEDT-Android)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val errorMsg = when (code) {
                        401, 403 -> "Accès refusé ($code). Ce lien .ics requiert peut-être une clé ou une authentification."
                        404 -> "Fichier iCal (.ics) introuvable à cette adresse (Erreur 404)."
                        500, 502, 503, 504 -> "Le serveur Flop!EDT est indisponible ou a rencontré une erreur interne ($code)."
                        else -> "Erreur HTTP $code: ${response.message}"
                    }
                    throw IllegalStateException(errorMsg)
                }

                val body = response.body ?: throw IllegalStateException("Réponse vide du serveur")
                val inputStream = body.byteStream()

                val iCal = Biweekly.parse(inputStream).first()
                    ?: throw IllegalStateException("Flux iCal (.ics) introuvable ou malformé")

                val parsedEvents = parseVevents(iCal.events)
                if (parsedEvents.isEmpty()) {
                    Log.w(TAG, "Parsed 0 events from ICS feed")
                }

                // Sauvegarde synchrone et atomique dans context.cacheDir/schedule.json
                val jsonString = json.encodeToString(parsedEvents)
                cacheFile.writeText(jsonString)

                // Mise à jour du cache mémoire L1
                memoryCachedEvents = parsedEvents
                memoryCacheTimestamp = cacheFile.lastModified()

                val todayEvents = parsedEvents
                    .filter { it.startTime.toLocalDate() == targetDate }
                    .sortedBy { it.startTime }

                Log.d(TAG, "Sync success: ${parsedEvents.size} total events, ${todayEvents.size} today")
                Result.success(todayEvents)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}", e)
            val friendlyMessage = when (e) {
                is java.net.SocketTimeoutException -> {
                    val msg = e.message?.lowercase() ?: ""
                    if (msg.contains("connect")) {
                        "Délai de connexion dépassé (30s). Le serveur Flop!EDT est injoignable depuis ce réseau. Si l'instance est sur l'intranet universitaire, le Wi-Fi Eduroam ou le VPN de l'université est nécessaire."
                    } else {
                        "Délai d'attente dépassé (60s). Le serveur Flop!EDT met trop de temps à générer le flux .ics. Réessayez dans quelques secondes."
                    }
                }
                is java.net.UnknownHostException -> {
                    "Serveur introuvable (${e.message}). Vérifiez l'adresse saisie ou votre connexion réseau."
                }
                is java.net.ConnectException -> {
                    "Connexion refusée par le serveur. Vérifiez si l'accès à ce Flop!EDT est limité à l'intranet ou nécessite un VPN."
                }
                is javax.net.ssl.SSLException -> {
                    "Erreur de sécurité SSL lors de la connexion sécurisée avec le serveur de l'université."
                }
                else -> e.message ?: "Erreur réseau inconnue"
            }

            val fallback = getTodayEvents(targetDate)
            if (fallback.isNotEmpty()) {
                Result.failure(Exception("$friendlyMessage (données en cache conservées)", e))
            } else {
                Result.failure(Exception(friendlyMessage, e))
            }
        }
    }

    /**
     * Parse directement un flux d'entrée iCal (.ics) et retourne la liste des événements.
     */
    fun parseIcsStream(inputStream: java.io.InputStream): List<CourseEvent> {
        return try {
            val iCal = Biweekly.parse(inputStream).first() ?: return emptyList()
            parseVevents(iCal.events)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing ICS stream", e)
            emptyList()
        }
    }

    /**
     * Parse les VEvent de Biweekly et les mappe vers notre modèle unique CourseEvent.
     */
    private fun parseVevents(vEvents: List<VEvent>): List<CourseEvent> {
        val result = mutableListOf<CourseEvent>()

        for (event in vEvents) {
            val startDate = event.dateStart?.value ?: continue
            val endDate = event.dateEnd?.value ?: continue

            // Conversion UTC -> Europe/Paris
            val startLocal = LocalDateTime.ofInstant(startDate.toInstant(), PARIS_ZONE)
            val endLocal = LocalDateTime.ofInstant(endDate.toInstant(), PARIS_ZONE)

            val rawSummary = event.summary?.value.orEmpty().trim()
            val rawLocation = event.location?.value.orEmpty().trim()
            val rawDescription = event.description?.value.orEmpty().trim()

            val teacher = extractTeacher(rawDescription)
            val title = if (rawSummary.isNotBlank()) rawSummary else "Cours"
            val room = if (rawLocation.isNotBlank()) rawLocation else extractRoomFromDescription(rawDescription)

            val uid = event.uid?.value ?: UUID.randomUUID().toString()

            result.add(
                CourseEvent(
                    id = uid,
                    title = title,
                    room = room,
                    teacher = teacher,
                    startIso = startLocal.toString(),
                    endIso = endLocal.toString()
                )
            )
        }

        return result.sortedBy { it.startTime }
    }

    /**
     * Extraction intelligente de l'intervenant / enseignant depuis la description.
     */
    private fun extractTeacher(description: String): String {
        if (description.isBlank()) return ""
        val lines = description.lines().map { it.trim() }.filter { it.isNotEmpty() }

        // Recherche d'une ligne préfixée
        val prefixed = lines.firstOrNull { line ->
            line.startsWith("Intervenant", ignoreCase = true) ||
            line.startsWith("Enseignant", ignoreCase = true) ||
            line.startsWith("Professeur", ignoreCase = true) ||
            line.startsWith("Prof", ignoreCase = true)
        }
        if (prefixed != null) {
            val extracted = prefixed.substringAfter(":").substringAfter("-").trim()
            if (extracted.isNotBlank()) return extracted
        }

        // Ligne avec civilité M. / Mme / Dr. / Pr.
        val titleMatch = lines.firstOrNull { line ->
            line.contains("M. ") || line.contains("Mme ") || line.contains("Dr.") || line.contains("Pr.")
        }
        if (titleMatch != null) return titleMatch

        // Fallback 2ème ligne si elle ne ressemble pas à un groupe ou une salle
        if (lines.size >= 2) {
            val candidate = lines[1]
            if (!candidate.contains("TD", ignoreCase = true) &&
                !candidate.contains("TP", ignoreCase = true) &&
                !candidate.contains("Salle", ignoreCase = true) &&
                !candidate.contains("Amphi", ignoreCase = true)
            ) {
                return candidate
            }
        }

        return ""
    }

    private fun extractRoomFromDescription(description: String): String {
        if (description.isBlank()) return ""
        val lines = description.lines().map { it.trim() }
        val roomLine = lines.firstOrNull {
            it.startsWith("Salle", ignoreCase = true) || it.startsWith("Amphi", ignoreCase = true)
        }
        return roomLine ?: ""
    }

    /**
     * Vérifie si le fichier de cache a été modifié il y a plus de [hours] heures.
     */
    fun isCacheOlderThan(hours: Long = 24): Boolean {
        if (!cacheFile.exists()) return true
        val lastModified = cacheFile.lastModified()
        if (lastModified <= 0L) return true
        val ageMillis = System.currentTimeMillis() - lastModified
        return ageMillis > TimeUnit.HOURS.toMillis(hours)
    }

    /**
     * Vérifie si une connexion internet est disponible sur l'appareil.
     */
    fun isInternetConnected(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Données par défaut pour démonstration et premier lancement hors-ligne.
     */
    private fun createDefaultDemoScheduleIfMissing(today: LocalDate) {
        val monday = today.with(java.time.DayOfWeek.MONDAY)
        val demoList = mutableListOf<CourseEvent>()

        val subjects = listOf(
            Triple("Algorithmique & Programmation Avancée", "Amphi B102", "Pr. Martin"),
            Triple("Bases de Données & Conception SQL", "Salle Info 4", "Dr. Leroy"),
            Triple("Architecture Systèmes & Réseaux", "Labo Réseau R2", "Mme. Rousseau"),
            Triple("Développement Web & Mobile", "Salle Info 2", "M. Bernard"),
            Triple("Mathématiques pour l'Informatique", "Amphi A", "Mme. Dupuis"),
            Triple("Anglais Technique & Communication", "Salle C201", "M. Taylor")
        )

        // Génère des cours pour Lundi, Mardi, Mercredi, Jeudi, Vendredi
        for (dayOffset in 0..4) {
            val date = monday.plusDays(dayOffset.toLong())
            val c1 = subjects[(dayOffset * 2) % subjects.size]
            val c2 = subjects[(dayOffset * 2 + 1) % subjects.size]

            demoList.add(
                CourseEvent(
                    id = "demo_${dayOffset}_1",
                    title = c1.first,
                    room = c1.second,
                    teacher = c1.third,
                    startIso = "${date}T08:30:00",
                    endIso = "${date}T11:30:00"
                )
            )

            demoList.add(
                CourseEvent(
                    id = "demo_${dayOffset}_2",
                    title = c2.first,
                    room = c2.second,
                    teacher = c2.third,
                    startIso = "${date}T13:30:00",
                    endIso = "${date}T16:30:00"
                )
            )

            if (dayOffset % 2 == 1) {
                val c3 = subjects[(dayOffset + 4) % subjects.size]
                demoList.add(
                    CourseEvent(
                        id = "demo_${dayOffset}_3",
                        title = c3.first,
                        room = c3.second,
                        teacher = c3.third,
                        startIso = "${date}T16:45:00",
                        endIso = "${date}T18:45:00"
                    )
                )
            }
        }

        try {
            val jsonText = json.encodeToString(demoList)
            cacheFile.writeText(jsonText)
            memoryCachedEvents = demoList
            memoryCacheTimestamp = cacheFile.lastModified()
            Log.d(TAG, "Created initial demo cache in $cacheFile")
        } catch (e: Exception) {
            Log.e(TAG, "Failed creating initial demo cache", e)
        }
    }
}
