package eu.kanade.tachiyomi.animeextension.ru.animego

import aniyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import keiyoushi.utils.parseAs
import keiyoushi.utils.useAsJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class AnimeGOSource(
    override val name: String,
    override val baseUrl: String,
) : AnimeHttpSource() {

    override val lang = "ru"
    override val supportsLatest = true

    // AnimeGO защищён DDoS-Guard/Cloudflare — используем клиент с поддержкой JS-челленджей
    override val client = network.cloudflareClient

    private val json = Json { ignoreUnknownKeys = true }

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    // Заголовки для XHR-запросов к API плеера AnimeGO
    private val xhrHeaders: Headers by lazy {
        headers.newBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .set("Referer", "$baseUrl/")
            .build()
    }

    // Заголовки для запросов к embed-плееру и CDN AniBoom
    private val aniboomHeaders: Headers by lazy {
        headers.newBuilder()
            .set("Origin", "https://aniboom.one")
            .set("Referer", "https://aniboom.one/")
            .build()
    }

    // Заголовки для запросов к API CdnVideoHub (CVH)
    private val cvhHeaders: Headers by lazy {
        headers.newBuilder()
            .set("Accept", "application/json")
            .set("Referer", "$baseUrl/")
            .build()
    }

    // ============================== Popular ==============================

    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/anime?page=$page", headers)

    override fun popularAnimeParse(response: Response): AnimesPage {
        val document = response.useAsJsoup()
        val animes = document.select("div.ani-grid__item").mapNotNull(::animeFromElement)
        val hasNextPage = document.selectFirst("ul.pagination li.active + li a, a[rel=next]") != null
        return AnimesPage(animes, hasNextPage)
    }

    private fun animeFromElement(element: Element): SAnime? {
        val link = element.selectFirst(
            "div.ani-grid__item-title a, a.ani-grid__item-body, a[href*='/anime/']",
        ) ?: return null
        val href = link.attr("href")
        if ("/anime/" !in href) return null

        return SAnime.create().apply {
            setUrlWithoutDomain(href)
            title = element.selectFirst("div.ani-grid__item-title")?.text()?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: link.text().trim()
            thumbnail_url = element.selectFirst("img")?.let { img ->
                img.attr("abs:src").ifEmpty { img.attr("abs:data-src") }
            }
        }
    }

    // ============================== Latest ==============================

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val document = response.useAsJsoup()
        val animes = document.select("div.updates-body a.aw-item").mapNotNull { element ->
            val href = element.attr("href").substringBefore('#')
            if ("/anime/" !in href) return@mapNotNull null
            val title = element.selectFirst("div.aw-name")?.text()?.trim()
                ?.takeUnless { it.isEmpty() }
                ?: return@mapNotNull null
            SAnime.create().apply {
                setUrlWithoutDomain(href)
                this.title = title
                thumbnail_url = element.selectFirst("img")?.let { img ->
                    img.attr("abs:src").ifEmpty { img.attr("abs:data-src") }
                }
            }
        }.distinctBy { it.url }
        return AnimesPage(animes, false)
    }

    // ============================== Search ==============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addPathSegment("anime")
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage = popularAnimeParse(response)

    // ============================== Details ==============================

    override fun animeDetailsParse(response: Response): SAnime {
        val document = response.useAsJsoup()
        val item = document.selectFirst("div.entity") ?: document

        return SAnime.create().apply {
            title = item.selectFirst("div.entity__title")?.text()?.trim()
                ?: document.selectFirst("h1")?.text()?.trim().orEmpty()
            thumbnail_url = item.selectFirst("img.image__img")?.attr("abs:src")

            val synonyms = item.selectFirst("div.entity__title-synonyms")?.text()?.trim()
            val desc = item.selectFirst("div.description")?.text()?.trim()
            description = buildString {
                if (!synonyms.isNullOrEmpty()) {
                    append("Другие названия: ").append(synonyms).append("\n\n")
                }
                if (!desc.isNullOrEmpty()) append(desc)
            }.ifEmpty { null }

            val fields = item.selectFirst("div.entity-field")?.select("> div") ?: return@apply
            var studio: String? = null
            var episodesInfo: String? = null
            var aired: String? = null
            var translations: String? = null
            for (i in fields.indices step 2) {
                val key = fields[i].text().trim()
                val value = fields.getOrNull(i + 1) ?: break
                when (key) {
                    "Жанры" -> genre = value.select("a").joinToString { it.text() }
                    "Статус" -> status = when {
                        value.text().contains("Онгоинг", ignoreCase = true) -> SAnime.ONGOING
                        value.text().contains("Вышел", ignoreCase = true) -> SAnime.COMPLETED
                        else -> SAnime.UNKNOWN
                    }
                    "Студия" -> studio = value.text().trim().ifEmpty { null }
                    "Эпизоды" -> episodesInfo = value.text().trim().ifEmpty { null }
                    "Выпуск" -> aired = value.text().trim().ifEmpty { null }
                    "Озвучка" -> translations = value.select("a").joinToString { it.text() }.ifEmpty { null }
                }
            }
            author = studio

            val extra = listOfNotNull(
                episodesInfo?.let { "Эпизоды: $it" },
                aired?.let { "Выпуск: $it" },
                translations?.let { "Озвучка: $it" },
            ).joinToString("\n")
            if (extra.isNotEmpty()) {
                description = listOfNotNull(description, extra).joinToString("\n\n")
            }
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListRequest(anime: SAnime): Request {
        val id = anime.url.substringAfterLast('-').substringBefore('/')
        return GET("$baseUrl/player/$id", xhrHeaders)
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val content = parsePlayerContent(response)
        val document = Jsoup.parse(content, baseUrl)
        val animeId = response.request.url.pathSegments.last()

        val episodes = document.select("[data-episode]").mapNotNull { element ->
            val videoId = element.attr("data-episode")
            if (videoId.isBlank()) return@mapNotNull null
            val number = element.attr("data-episode-number").toFloatOrNull()
                ?: EPISODE_NUMBER_REGEX.find(element.text())?.groupValues?.get(1)?.toFloatOrNull()
                ?: return@mapNotNull null
            val numberStr = formatEpisodeNumber(number)
            SEpisode.create().apply {
                // Номер серии сохраняем во фрагменте URL — он понадобится
                // для сопоставления серии в плейлисте CVH при получении видео
                url = "/player/videos/$videoId#ep=$numberStr"
                name = "Серия $numberStr"
                episode_number = number
            }
        }

        if (episodes.isNotEmpty()) {
            return episodes.distinctBy { it.url }.sortedByDescending { it.episode_number }
        }

        // Фильм / одиночный релиз — списка серий нет, плеер один
        return listOf(
            SEpisode.create().apply {
                url = "/player/$animeId#ep=1"
                name = "Фильм"
                episode_number = 1f
            },
        )
    }

    // ============================== Videos ==============================

    override fun videoListRequest(episode: SEpisode): Request =
        GET(baseUrl + episode.url, xhrHeaders)

    override fun videoListParse(response: Response): List<Video> {
        val content = parsePlayerContent(response)
        val document = Jsoup.parse(content, baseUrl)
        val episodeNumber = response.request.url.fragment
            ?.substringAfter("ep=")
            ?.toFloatOrNull()?.toInt() ?: 1

        val videos = mutableListOf<Video>()
        document.select("#provider button").forEach { button ->
            val voice = button.attr("data-translation-title")
                .replace(" (ошибка)", "")
                .trim()
            val provider = button.attr("data-provider-title").trim()
            val embed = button.attr("data-player").let {
                if (it.startsWith("//")) "https:$it" else it
            }
            if (voice.isEmpty() || embed.isEmpty()) return@forEach

            runCatching {
                when {
                    provider.equals("AniBoom", ignoreCase = true) || "aniboom" in embed ->
                        videos += aniboomVideos(embed, voice)
                    provider.equals("CVH", ignoreCase = true) || "cdn-iframe" in embed || "cdnvideohub" in embed ->
                        videos += cvhVideos(embed, voice, episodeNumber)
                }
            }
        }
        return videos.distinctBy { it.url }
    }

    /**
     * Извлекает потоки (DASH/HLS) с embed-страницы AniBoom.
     * Ссылки на потоки лежат в атрибуте data-parameters тега <video id="video">
     * в виде HTML-encoded JSON.
     */
    private fun aniboomVideos(embedUrl: String, voice: String): List<Video> {
        val embedHeaders = headers.newBuilder().set("Referer", "$baseUrl/").build()
        val document = client.newCall(GET(embedUrl, embedHeaders)).execute().useAsJsoup()
        val params = document.selectFirst("video#video")?.attr("data-parameters")
            ?.takeUnless { it.isBlank() }
            ?: return emptyList()
        val data = json.parseToJsonElement(params).jsonObject

        val videos = mutableListOf<Video>()

        // DASH — приоритетный формат
        data["dash"]?.jsonPrimitive?.contentOrNull?.let { dash ->
            runCatching {
                val mpdUrl = json.parseToJsonElement(dash).jsonObject["src"]
                    ?.jsonPrimitive?.contentOrNull ?: return@runCatching
                videos += playlistUtils.extractFromDash(
                    mpdUrl = mpdUrl,
                    videoNameGen = { quality -> "AniBoom $voice ($quality)" },
                    mpdHeaders = aniboomHeaders,
                    videoHeaders = aniboomHeaders,
                    referer = "https://aniboom.one/",
                )
            }
        }
        if (videos.isNotEmpty()) return videos

        // HLS — fallback
        data["hls"]?.jsonPrimitive?.contentOrNull?.let { hls ->
            runCatching {
                val hlsUrl = json.parseToJsonElement(hls).jsonObject["src"]
                    ?.jsonPrimitive?.contentOrNull ?: return@runCatching
                videos += playlistUtils.extractFromHls(
                    playlistUrl = hlsUrl,
                    referer = "https://aniboom.one/",
                    masterHeaders = aniboomHeaders,
                    videoHeaders = aniboomHeaders,
                    videoNameGen = { quality -> "AniBoom $voice ($quality)" },
                )
            }
        }
        return videos
    }

    /**
     * Извлекает потоки через API CdnVideoHub (CVH).
     * Плейлист: /playlist?pub=...&aggr=...&id={cvhId}
     * Потоки конкретного видео: /video/{vkId}
     */
    private fun cvhVideos(embedUrl: String, voice: String, episodeNumber: Int): List<Video> {
        val cvhId = embedUrl.substringAfter("cdn-iframe/", "").substringBefore('/')
        if (cvhId.isBlank()) return emptyList()

        val playlist = client.newCall(
            GET("$CVH_API/playlist?pub=$CVH_PUB&aggr=$CVH_AGGR&id=$cvhId", cvhHeaders),
        ).execute().parseAs<JsonObject>()

        val items = playlist["items"]?.jsonArray ?: return emptyList()
        val candidates = items.map { it.jsonObject }
            .filter { it["episode"]?.jsonPrimitive?.intOrNull == episodeNumber }
        if (candidates.isEmpty()) return emptyList()

        val studios = candidates.mapNotNull { it["voiceStudio"]?.jsonPrimitive?.contentOrNull }
        val matchedStudio = matchStudio(voice, studios)
            ?: studios.firstOrNull()
            ?: return emptyList()
        val vkId = candidates.firstOrNull {
            it["voiceStudio"]?.jsonPrimitive?.contentOrNull == matchedStudio
        }?.get("vkId")?.jsonPrimitive?.contentOrNull ?: return emptyList()

        val videoData = client.newCall(GET("$CVH_API/video/$vkId", cvhHeaders))
            .execute().parseAs<JsonObject>()
        val sources = videoData["sources"]?.jsonObject ?: return emptyList()

        val videos = mutableListOf<Video>()

        sources["hlsUrl"]?.jsonPrimitive?.contentOrNull?.let { hlsUrl ->
            runCatching {
                videos += playlistUtils.extractFromHls(
                    playlistUrl = hlsUrl,
                    referer = "$baseUrl/",
                    masterHeaders = headers,
                    videoHeaders = headers,
                    videoNameGen = { quality -> "CVH $voice ($quality)" },
                )
            }
        }

        val dashUrl = sources["dashUrl"]?.jsonPrimitive?.contentOrNull
            ?: sources["dashManifestUrl"]?.jsonPrimitive?.contentOrNull
        dashUrl?.let {
            runCatching {
                videos += playlistUtils.extractFromDash(
                    mpdUrl = dashUrl,
                    videoNameGen = { quality -> "CVH $voice ($quality)" },
                    mpdHeaders = headers,
                    videoHeaders = headers,
                    referer = "$baseUrl/",
                )
            }
        }

        // Прямые MP4-ссылки хранятся под ключами url360, url480, url720 и т.п.
        for ((key, value) in sources) {
            if (value is JsonPrimitive && key.startsWith("url") && value.contentOrNull?.startsWith("http") == true) {
                val quality = key.removePrefix("url")
                videos += Video(value.content, "CVH $voice (${quality}p)", value.content, headers)
            }
        }

        return videos
    }

    // ============================== Helpers ==============================

    /**
     * Сервер возвращает JSON, HTML плеера лежит в поле data.content
     */
    private fun parsePlayerContent(response: Response): String {
        val obj = response.parseAs<JsonObject>()
        return obj["data"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    /**
     * Fuzzy-сопоставление названия озвучки AnimeGO с именем студии в CVH:
     * сначала точное совпадение (без учёта регистра), затем по подстроке.
     */
    private fun matchStudio(label: String, studios: List<String>): String? {
        val lower = label.lowercase()
        studios.firstOrNull { it.lowercase() == lower }?.let { return it }
        return studios.firstOrNull {
            val s = it.lowercase()
            lower in s || s in lower
        }
    }

    private fun formatEpisodeNumber(number: Float): String =
        if (number % 1f == 0f) number.toInt().toString() else number.toString()

    companion object {
        private const val CVH_API = "https://plapi.cdnvideohub.com/api/v1/player/sv"
        private const val CVH_PUB = "747"
        private const val CVH_AGGR = "mali"
        private val EPISODE_NUMBER_REGEX = Regex("""(\d+(?:\.\d+)?)""")
    }
}
