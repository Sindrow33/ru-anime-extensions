package eu.kanade.tachiyomi.animeextension.ru.animego

import aniyomi.lib.sibnetextractor.SibnetExtractor
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
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
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

class AnimeGOSource(
    override val name: String,
    override val baseUrl: String,
) : ParsedAnimeHttpSource() {

    override val lang = "ru"
    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val kodikExtractor by lazy { KodikExtractor(client, baseUrl) }
    private val aniboomExtractor by lazy { AniboomExtractor(client, baseUrl) }
    private val cvhExtractor by lazy { CvhExtractor(client, baseUrl) }
    private val sibnetExtractor by lazy { SibnetExtractor(client) }

    // AnimeGO проверяет Referer у /player/videos/<episodeId>.
    // Здесь хранится URL страницы тайтла, а не главная страница сайта.
    private var episodeReferer = "$baseUrl/"

    private fun ajaxHeaders(referer: String): Headers = headers.newBuilder()
        .set("Accept", "application/json, text/javascript, */*; q=0.01")
        .set("X-Requested-With", "XMLHttpRequest")
        .set("Referer", referer)
        .build()

    // ============================== Popular ==============================

    override fun popularAnimeRequest(page: Int): Request {
        val url = if (page == 1) {
            "$baseUrl/anime?sort=rating"
        } else {
            "$baseUrl/anime/$page?sort=rating"
        }
        return GET(url, headers)
    }

    override fun popularAnimeSelector(): String = "div.ani-list__item"

    override fun popularAnimeNextPageSelector(): String = "a.button-list-loading"

    override fun popularAnimeParse(response: Response): AnimesPage {
        val document = Jsoup.parse(
            response.body.string(),
            response.request.url.toString(),
        )

        val anime = document.select(popularAnimeSelector())
            .map { popularAnimeFromElement(it) }
            .filter { it.url.isNotBlank() && it.title.isNotBlank() }
            .distinctBy {
                it.url
                    .substringBefore('?')
                    .substringBefore('#')
                    .trimEnd('/')
                    .lowercase()
            }

        val hasNextPage = document.selectFirst(
            popularAnimeNextPageSelector(),
        ) != null

        return AnimesPage(anime, hasNextPage)
    }

    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        val link = element.selectFirst("div.ani-list__item-title a[href*=/anime/]")
            ?: element.selectFirst("a[href*=/anime/]")
            ?: return anime

        anime.setUrlWithoutDomain(link.attr("href"))
        anime.title = link.attr("title").ifBlank { link.text() }
        anime.thumbnail_url = imageUrl(
            element.selectFirst(
                "a.ani-list__item-picture img, img.image__img, img",
            ),
        )

        return anime
    }

    // ============================== Latest ==============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = if (page == 1) {
            "$baseUrl/anime?sort=createdAt"
        } else {
            "$baseUrl/anime/$page?sort=createdAt"
        }
        return GET(url, headers)
    }

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    // ============================== Search ==============================

    override fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): Request {
        val url = if (query.isNotBlank()) {
            "$baseUrl/search/all?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
        } else if (page == 1) {
            "$baseUrl/anime"
        } else {
            "$baseUrl/anime/$page"
        }

        return GET(url, headers)
    }

    override fun searchAnimeSelector(): String = "div.ani-list__item, a.ajax-search__item, div.search-result-item"

    override fun searchAnimeNextPageSelector(): String = "a.button-list-loading"

    override fun searchAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        val link = when {
            element.tagName() == "a" && element.attr("href").contains("/anime/") ->
                element
            else ->
                element.selectFirst(
                    "div.ani-list__item-title a[href*=/anime/], " +
                        "a.ajax-search__item[href*=/anime/], a[href*=/anime/]",
                )
        } ?: return anime

        anime.setUrlWithoutDomain(link.attr("href"))
        anime.title = link.attr("title")
            .ifBlank {
                element.selectFirst(
                    ".ajax-search__item-title, .ani-list__item-title",
                )?.text().orEmpty()
            }
            .ifBlank { link.text() }

        anime.thumbnail_url = imageUrl(element.selectFirst("img"))

        return anime
    }

    // ============================== Details ==============================

    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()

        anime.title = document.selectFirst("h1")?.text().orEmpty()
        anime.description = document.selectFirst("div.description")
            ?.text()
            .orEmpty()

        anime.thumbnail_url = imageUrl(
            document.selectFirst(
                ".entity__poster img.image__img, .entity__poster img, " +
                    "meta[property=og:image]",
            ),
        )

        anime.genre = document.select(
            ".entity-field__genres a[href*=/anime/genre/]",
        ).joinToString { it.text() }

        val statusText = document.select(
            ".entity-field.grid, .entity-field",
        ).firstOrNull {
            it.text().contains("Статус", ignoreCase = true)
        }?.text().orEmpty()

        anime.status = when {
            statusText.contains("Онгоинг", ignoreCase = true) ->
                SAnime.ONGOING
            statusText.contains("Анонс", ignoreCase = true) ->
                SAnime.LICENSED
            statusText.contains("Вышел", ignoreCase = true) ||
                statusText.contains("Заверш", ignoreCase = true) ->
                SAnime.COMPLETED
            else -> SAnime.UNKNOWN
        }

        return anime
    }

    // ============================== Episodes ==============================

    /*
     * Сначала запрашиваем страницу тайтла. Нельзя брать player ID из хвоста
     * slug: это соглашение AnimeGO, но не гарантия. Настоящий endpoint
     * находится в .player__video[data-ajax-url].
     */
    override fun episodeListRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)

    override fun episodeListParse(response: Response): List<SEpisode> {
        val titleUrl = response.request.url.toString()
        episodeReferer = titleUrl
        val titleDocument = Jsoup.parse(
            response.body.string(),
            titleUrl,
        )

        val playerPath = titleDocument.selectFirst(
            ".player__video[data-ajax-url], [data-ajax-url^=/player/]",
        )?.attr("data-ajax-url")
            ?.trim()
            .orEmpty()

        if (playerPath.isBlank()) return emptyList()

        val playerUrl = resolveUrl(playerPath)
        val playerResponse = runCatching {
            client.newCall(
                GET(playerUrl, ajaxHeaders(titleUrl)),
            ).execute().use { it.body.string() }
        }.getOrNull() ?: return emptyList()

        val content = jsonContent(playerResponse)
        if (content.isBlank()) return emptyList()

        val document = Jsoup.parse(content, baseUrl)
        val playerId = playerUrl
            .substringBefore("?")
            .trimEnd('/')
            .substringAfterLast('/')

        val selectedEpisodeId = document.selectFirst(
            "select[name=series] option[selected]",
        )?.attr("value")
            ?.ifBlank {
                document.selectFirst(
                    ".player-video-bar__item.active[data-episode], " +
                        ".player-video-bar__item.selected[data-episode]",
                )?.attr("data-episode").orEmpty()
            }
            .orEmpty()

        val episodes = document.select(
            ".player-video-bar__item[data-episode]",
        ).mapNotNull { element ->
            episodeFromPlayerElement(element, playerId, selectedEpisodeId)
        }.distinctBy { rawEpisodeUrl(it.url).substringAfterLast(':') }
            .toMutableList()

        /*
         * AnimeGO может показывать в плитках только последние серии,
         * а полный список хранить в select[name=series].
         */
        document.select(
            "select[name=series] option[value]",
        ).mapNotNullTo(episodes) { option ->
            val episodeId = option.attr("value").trim()
            if (episodeId.isBlank()) return@mapNotNullTo null

            val rawNumber = option.attr("data-episode-number")
                .ifBlank { option.text() }

            val number = parseEpisodeNumber(rawNumber)
                ?: return@mapNotNullTo null

            SEpisode.create().apply {
                name = "Серия ${number.cleanNumber()}"
                episode_number = number
                url = episodeUrl(episodeId)
            }
        }

        val uniqueEpisodes = episodes
            .distinctBy { rawEpisodeUrl(it.url).substringAfterLast(':') }
            .sortedByDescending { it.episode_number }
            .toMutableList()

        /*
         * Если серия только одна, кнопки её плееров уже находятся в ответе
         * /player/<id>. Повторный /player/videos/<episodeId> часто даёт 404.
         */
        if (uniqueEpisodes.size == 1) {
            val episode = uniqueEpisodes.first()
            val episodeId = rawEpisodeUrl(episode.url).substringAfterLast(':')
            episode.url = episodeUrl(episodeId)
        }

        /*
         * Фильм или спецвыпуск: списка серий нет, но плееры присутствуют.
         */
        if (uniqueEpisodes.isEmpty() && hasPlayerButtons(document)) {
            return listOf(
                SEpisode.create().apply {
                    name = "Фильм"
                    episode_number = 1f
                    url = episodeUrl("initial:$playerId:film")
                },
            )
        }

        return uniqueEpisodes
    }

    private fun episodeFromPlayerElement(
        element: Element,
        playerId: String,
        selectedEpisodeId: String,
    ): SEpisode? {
        val episodeId = element.attr("data-episode").trim()
        if (episodeId.isBlank()) return null

        val rawNumber = element.attr("data-episode-number")
            .ifBlank { element.attr("data-number") }
            .ifBlank { element.text() }

        val number = parseEpisodeNumber(rawNumber) ?: return null

        val title = element.attr("data-episode-title")
            .ifBlank { element.attr("data-title") }
            .trim()

        val isInitial = episodeId == selectedEpisodeId

        return SEpisode.create().apply {
            name = if (title.isNotBlank()) {
                "${number.cleanNumber()}. $title"
            } else {
                "Серия ${number.cleanNumber()}"
            }
            episode_number = number
            url = episodeUrl(
                if (isInitial) {
                    episodeId
                } else {
                    episodeId
                },
            )
        }
    }

    private fun parseEpisodeNumber(value: String): Float? = Regex("""\d+(?:[.,]\d+)?""")
        .find(value)
        ?.value
        ?.replace(',', '.')
        ?.toFloatOrNull()

    override fun episodeListSelector(): String = ".player-video-bar__item[data-episode]"

    override fun episodeFromElement(element: Element): SEpisode = throw UnsupportedOperationException("Используется episodeListParse")

    // ============================== Video ==============================

    private val episodeRefererMarker = "#animego-ref="

    private fun episodeUrl(rawUrl: String): String {
        val encodedReferer = URLEncoder.encode(
            episodeReferer,
            Charsets.UTF_8.name(),
        )
        return "$rawUrl$episodeRefererMarker$encodedReferer"
    }

    private fun rawEpisodeUrl(url: String): String = url.substringBefore(episodeRefererMarker)

    private fun episodeRefererFromUrl(url: String): String = url.substringAfter(episodeRefererMarker, "")
        .takeIf { it.isNotBlank() }
        ?.let {
            URLDecoder.decode(it, Charsets.UTF_8.name())
        }
        ?: episodeReferer

    override fun videoListRequest(episode: SEpisode): Request {
        val rawUrl = rawEpisodeUrl(episode.url)
        val referer = episodeRefererFromUrl(episode.url)

        val requestUrl = if (rawUrl.startsWith("initial:")) {
            val playerId = rawUrl
                .removePrefix("initial:")
                .substringBefore(':')
            "$baseUrl/player/$playerId"
        } else {
            "$baseUrl/player/videos/$rawUrl"
        }

        return GET(requestUrl, ajaxHeaders(referer))
    }

    override fun videoListParse(response: Response): List<Video> {
        val content = jsonContent(response.body.string())
        if (content.isBlank()) return emptyList()

        val document = Jsoup.parse(content, baseUrl)

        val translations = document.select("[data-translation]")
            .associate { element ->
                element.attr("data-translation") to element.text().trim()
            }

        return document.select(
            "[data-player][data-provider], button[data-player], [data-player]",
        ).mapNotNull { button ->
            val rawUrl = button.attr("data-player").trim()
            if (rawUrl.isBlank()) return@mapNotNull null

            val playerUrl = resolveUrl(rawUrl)

            val translationId = button.attr("data-ptranslation")
                .ifBlank { button.attr("data-translation") }

            val translation = button.attr("data-translation-title")
                .ifBlank { translations[translationId].orEmpty() }
                .ifBlank { "Неизвестная озвучка" }

            val provider = button.attr("data-provider-title")
                .ifBlank { button.attr("data-provider") }
                .ifBlank { playerHost(playerUrl) }
                .ifBlank { "Player" }

            PlayerButton(
                url = playerUrl,
                translation = translation,
                provider = provider,
            )
        }.distinctBy {
            "${it.translation}|${it.provider}|${it.url}"
        }.flatMap { button ->
            val prefix = "${button.translation} • "
            val host = playerHost(button.url)

            runCatching {
                when {
                    button.url.contains("/cdn-iframe/") ||
                        host.contains("cdnvideohub") ->
                        cvhExtractor.videosFromUrl(button.url, prefix)

                    host.contains("kodik") ||
                        host.contains("anivod") ||
                        host.contains("aniqit") ->
                        kodikExtractor.videosFromUrl(button.url, prefix)

                    host.contains("aniboom") ->
                        aniboomExtractor.videosFromUrl(button.url, prefix)

                    host.contains("sibnet") ->
                        sibnetExtractor.videosFromUrl(button.url, prefix)

                    /*
                     * Не передаём iframe как прямое видео. Иначе плеер
                     * пытается воспроизвести HTML и показывает HTTP 404
                     * или Unrecognized file format.
                     */
                    else -> emptyList()
                }
            }.getOrElse { emptyList() }
        }.distinctBy { "${it.quality}|${it.videoUrl}" }
    }

    override fun videoListSelector(): String = "[data-player][data-provider]"

    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException("Используется videoListParse")

    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException("Используется videoListParse")

    // ============================== Helpers ==============================

    private fun jsonContent(body: String): String = runCatching {
        JSONObject(body)
            .optJSONObject("data")
            ?.optString("content")
            .orEmpty()
    }.getOrDefault("")

    private fun hasPlayerButtons(document: Document): Boolean = document.select("[data-player][data-provider], button[data-player]")
        .isNotEmpty()

    private fun imageUrl(element: Element?): String? {
        if (element == null) return null

        if (element.tagName() == "meta") {
            return resolveUrl(element.attr("content"))
                .takeUnless { it.isBlank() }
        }

        val candidate = listOf(
            element.attr("data-src"),
            element.attr("data-original"),
            element.attr("data-lazy-src"),
            element.attr("src"),
            element.attr("srcset").substringBefore(' '),
        ).firstOrNull {
            it.isNotBlank() &&
                !it.startsWith("data:image", ignoreCase = true)
        }.orEmpty()

        return resolveUrl(candidate).takeUnless { it.isBlank() }
    }

    private fun resolveUrl(value: String): String {
        val url = value.trim()
        return when {
            url.isBlank() -> ""
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "$baseUrl$url"
            url.startsWith("http://") || url.startsWith("https://") -> url
            else -> "$baseUrl/${url.trimStart('/')}"
        }
    }

    private fun playerHost(url: String): String = runCatching {
        URI(url).host.orEmpty().lowercase()
    }.getOrDefault("")

    private fun Float.cleanNumber(): String = if (this % 1f == 0f) toInt().toString() else toString()

    private data class PlayerButton(
        val url: String,
        val translation: String,
        val provider: String,
    )
}
