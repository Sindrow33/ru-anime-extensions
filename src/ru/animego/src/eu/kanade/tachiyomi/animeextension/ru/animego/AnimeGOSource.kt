package eu.kanade.tachiyomi.animeextension.ru.animego

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class AnimeGOSource(
    override val name: String,
    override val baseUrl: String,
) : ParsedAnimeHttpSource() {

    override val lang = "ru"
    override val supportsLatest = true

    // Обход Cloudflare
    override val client = network.cloudflareClient

    private val ajaxHeaders: Headers
        get() = headers.newBuilder()
            .set("Accept", "application/json, text/javascript, */*; q=0.01")
            .set("X-Requested-With", "XMLHttpRequest")
            .set("Referer", "$baseUrl/")
            .build()

    // ============================== Popular ==============================
    override fun popularAnimeRequest(page: Int): Request {
        val url = if (page == 1) "$baseUrl/anime?sort=rating" else "$baseUrl/anime/$page?sort=rating"
        return GET(url, headers)
    }

    override fun popularAnimeSelector(): String = "div.ani-list__item"
    override fun popularAnimeNextPageSelector(): String = "a.button-list-loading"

    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        val link = element.selectFirst("div.ani-list__item-title a")!!
        anime.setUrlWithoutDomain(link.attr("href"))
        anime.title = link.text()
        anime.thumbnail_url = element.select("a.ani-list__item-picture img").attr("abs:src")
        return anime
    }

    // ============================== Latest ==============================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = if (page == 1) "$baseUrl/anime?sort=createdAt" else "$baseUrl/anime/$page?sort=createdAt"
        return GET(url, headers)
    }

    override fun latestUpdatesSelector(): String = "div.ani-list__item"
    override fun latestUpdatesNextPageSelector(): String = "a.button-list-loading"

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    // ============================== Search ==============================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = if (query.isNotEmpty()) {
            "$baseUrl/search/all?q=$query"
        } else {
            if (page == 1) "$baseUrl/anime" else "$baseUrl/anime/$page"
        }
        return GET(url, headers)
    }

    override fun searchAnimeSelector(): String = "div.ani-list__item, div.search-result-item"
    override fun searchAnimeNextPageSelector(): String = "a.button-list-loading"

    override fun searchAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        val link = element.selectFirst("div.ani-list__item-title a, a[href*=/anime/]")!!
        anime.setUrlWithoutDomain(link.attr("href"))
        anime.title = link.text()
        anime.thumbnail_url = element.select("img").attr("abs:src")
        return anime
    }

    // ============================== Details ==============================
    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()
        anime.title = document.select("h1").text()
        anime.description = document.select("div.description").text()
        anime.thumbnail_url = document.select("div.entity__poster img").attr("abs:src")
        anime.genre = document.select("div.entity-field__genres a").joinToString { it.text() }

        val statusText = document.select("div.entity-field div:contains(Статус) + div").text()
        anime.status = when {
            statusText.contains("Онгоинг", ignoreCase = true) -> SAnime.ONGOING
            statusText.contains("Вышел", ignoreCase = true) -> SAnime.COMPLETED
            else -> SAnime.UNKNOWN
        }
        return anime
    }

    // ============================== Episodes ==============================
    override fun episodeListRequest(anime: SAnime): Request {
        val animeId = anime.url
            .substringBefore("?")
            .trimEnd('/')
            .substringAfterLast("-")
            .filter(Char::isDigit)

        return GET("$baseUrl/player/$animeId", ajaxHeaders)
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val body = response.body.string()

        val content = runCatching {
            JSONObject(body)
                .optJSONObject("data")
                ?.optString("content")
                .orEmpty()
        }.getOrDefault("")

        if (content.isBlank()) return emptyList()

        val document = Jsoup.parse(content, baseUrl)

        val episodes = document
            .select(".player-video-bar__item[data-episode], [data-episode][data-episode-number]")
            .mapNotNull { element ->
                val episodeId = element.attr("data-episode")
                if (episodeId.isBlank()) return@mapNotNull null

                val rawNumber = element.attr("data-episode-number")
                val number = Regex("""\d+(?:[.,]\d+)?""")
                    .find(rawNumber)
                    ?.value
                    ?.replace(',', '.')
                    ?.toFloatOrNull()
                    ?: return@mapNotNull null

                val title = element.attr("data-episode-title").trim()

                SEpisode.create().apply {
                    name = if (title.isNotBlank()) {
                        "${number.cleanNumber()}. $title"
                    } else {
                        "Серия ${number.cleanNumber()}"
                    }
                    episode_number = number

                    // Здесь хранится именно ID серии, а не ID аниме.
                    url = episodeId
                }
            }
            .distinctBy { it.url }
            .sortedByDescending { it.episode_number }

        // Для полнометражных фильмов AnimeGO может сразу вернуть кнопки
        // плееров без списка серий.
        if (episodes.isEmpty() && document.select("button[data-player]").isNotEmpty()) {
            val animeId = response.request.url.pathSegments.last()

            return listOf(
                SEpisode.create().apply {
                    name = "Фильм"
                    episode_number = 1f
                    url = "film:$animeId"
                },
            )
        }

        return episodes
    }

    override fun episodeListSelector(): String = ".player-video-bar__item[data-episode]"

    override fun episodeFromElement(element: Element): SEpisode = throw UnsupportedOperationException("Используется episodeListParse")

    // ============================== Video ==============================
    override fun videoListRequest(episode: SEpisode): Request {
        val url = if (episode.url.startsWith("film:")) {
            val animeId = episode.url.substringAfter("film:")
            "$baseUrl/player/$animeId"
        } else {
            "$baseUrl/player/videos/${episode.url}"
        }

        return GET(url, ajaxHeaders)
    }

    override fun videoListParse(response: Response): List<Video> {
        val body = response.body.string()

        val content = runCatching {
            JSONObject(body)
                .optJSONObject("data")
                ?.optString("content")
                .orEmpty()
        }.getOrDefault("")

        if (content.isBlank()) return emptyList()

        val document = Jsoup.parse(content, baseUrl)

        return document.select("button[data-player]")
            .mapNotNull { button ->
                val rawUrl = button.attr("data-player").trim()
                if (rawUrl.isBlank()) return@mapNotNull null

                val playerUrl = when {
                    rawUrl.startsWith("//") -> "https:$rawUrl"
                    rawUrl.startsWith("/") -> "$baseUrl$rawUrl"
                    rawUrl.startsWith("http") -> rawUrl
                    else -> "https://$rawUrl"
                }

                val translation = button.attr("data-translation-title")
                    .ifBlank { button.text().trim() }
                    .ifBlank { "Неизвестная озвучка" }

                val provider = button.attr("data-provider")
                    .ifBlank {
                        runCatching { java.net.URI(playerUrl).host }.getOrNull().orEmpty()
                    }
                    .ifBlank { "Player" }

                val playerHeaders = headers.newBuilder()
                    .set("Referer", "$baseUrl/")
                    .build()

                Video(playerUrl, "$translation ($provider)", playerUrl, playerHeaders)
            }
            .distinctBy { it.videoUrl }
    }

    override fun videoListSelector(): String = "button[data-player]"

    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException("Используется videoListParse")

    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException("Используется videoListParse")

    // ============================== Helpers ==============================
    private fun Float.cleanNumber(): String = if (this % 1f == 0f) toInt().toString() else toString()
}
