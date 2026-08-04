package eu.kanade.tachiyomi.animeextension.ru.animego

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
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

        // Описание
        anime.description = document.select("div.description").text()

        // Постер
        anime.thumbnail_url = document.select("div.entity__poster img").attr("abs:src")

        // Жанры
        anime.genre = document.select("div.entity-field__genres a").joinToString { it.text() }

        // Статус из meta
        val statusText = document.select("div.entity-field div:contains(Статус) + div").text()
        anime.status = when {
            statusText.contains("Онгоинг", ignoreCase = true) -> SAnime.ONGOING
            statusText.contains("Вышел", ignoreCase = true) -> SAnime.COMPLETED
            else -> SAnime.UNKNOWN
        }

        return anime
    }

    // ============================== Episodes ==============================
    override fun episodeListSelector(): String = "div.schedule-episodes-table__tbody .grid > div.g-col-4, div[data-number]"

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val episodes = mutableListOf<SEpisode>()

        // Ищем строки с сериями в таблице
        val rows = document.select("div.schedule-episodes-table__tbody .grid")

        for (row in rows) {
            // Номер серии из data-number или текста
            val numberElement = row.selectFirst("div[data-number]")
            val number = numberElement?.attr("data-number")?.toFloatOrNull()
                ?: row.text().filter { it.isDigit() }.toFloatOrNull()
                ?: continue

            // Название серии
            val nameElement = row.selectFirst("div.g-col-5 span[data-readmore], div.fw-bold span")
            val name = nameElement?.text() ?: "Серия ${number.toInt()}"

            // ID эпизода из data-episode
            val episodeId = numberElement?.attr("data-episode") ?: ""

            val episode = SEpisode.create().apply {
                this.name = "$number. $name"
                this.episode_number = number
                // URL для загрузки плеера с конкретной серией
                this.url = "/player/${extractAnimeId(response.request.url.toString())}?episode=$episodeId&number=$number"
            }
            episodes.add(episode)
        }

        // Если не нашли через таблицу, пробуем альтернативный метод
        if (episodes.isEmpty()) {
            return super.episodeListParse(response)
        }

        return episodes.sortedByDescending { it.episode_number }
    }

    override fun episodeFromElement(element: Element): SEpisode {
        // Fallback для стандартного парсера
        val episode = SEpisode.create()
        val number = element.attr("data-number").toFloatOrNull() ?: 0f
        episode.episode_number = number
        episode.name = "Серия ${number.toInt()}"
        episode.url = element.selectFirst("a")?.attr("href") ?: ""
        return episode
    }

    // ============================== Video ==============================
    override fun videoListRequest(episode: SEpisode): Request {
        // episode.url содержит /player/{id}?episode=...&number=...
        return GET("$baseUrl${episode.url}", headers)
    }

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videos = mutableListOf<Video>()

        // Плеер загружается динамически, ищем iframe или ссылки на видео
        val iframes = document.select("iframe[src*=kodik], iframe[src*=player], iframe[src*=video], iframe[data-src]")

        for (iframe in iframes) {
            val src = iframe.attr("abs:src").ifEmpty { iframe.attr("abs:data-src") }
            if (src.isNotEmpty()) {
                val playerName = when {
                    src.contains("kodik") -> "Kodik"
                    src.contains("sibnet") -> "Sibnet"
                    src.contains("vk") -> "VK"
                    else -> "AnimeGO Player"
                }
                videos.add(Video(src, playerName, src, headers))
            }
        }

        // Если iframe не найден, возможно видео в JSON или нужно дополнительный запрос
        if (videos.isEmpty()) {
            // Ищем ссылки в script или data-атрибутах
            val scriptContent = document.select("script").html()
            val videoUrlRegex = Regex("""https?://[^"'\s]+(?:\.m3u8|\.mp4|player\.php|video\.php)[^"'\s]*""")
            val match = videoUrlRegex.find(scriptContent)
            match?.let {
                videos.add(Video(it.value, "Direct", it.value, headers))
            }
        }

        return videos
    }

    override fun videoListSelector(): String = "iframe, video source"
    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException()

    // ============================== Helpers ==============================
    private fun extractAnimeId(url: String): String {
        // Из URL вида /anime/sekirei-3745 извлекаем 3745
        return url.substringAfterLast("-").substringBefore("/")
    }
}
