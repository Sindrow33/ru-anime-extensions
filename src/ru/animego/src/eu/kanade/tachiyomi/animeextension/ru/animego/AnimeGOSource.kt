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

        // Статус
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
        // Из URL вида /anime/sekirei-3745 извлекаем ID (3745)
        val animeId = anime.url.substringAfterLast("-").substringBefore("/")
        // Загружаем ВСЕ серии через API endpoint (не HTML страницу)
        return GET("$baseUrl/anime/$animeId/10/schedule/load?entities=true", headers)
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val episodes = mutableListOf<SEpisode>()
        val animeId = extractAnimeIdFromReferer(response)

        // Ищем все элементы с data-number (это серии)
        val episodeElements = document.select("div[data-number]")

        for (element in episodeElements) {
            val number = element.attr("data-number").toFloatOrNull() ?: continue
            val episodeId = element.attr("data-episode") ?: ""

            // Ищем название серии в соседнем элементе
            val row = element.parent()
            val nameElement = row?.selectFirst("div.g-col-5 span[data-readmore], div.fw-bold")
            val name = nameElement?.text() ?: "Серия ${number.toInt()}"

            val episode = SEpisode.create().apply {
                this.name = "$number. $name"
                this.episode_number = number
                // URL для плеера с конкретной серией
                this.url = "/player/$animeId?episode=$episodeId"
            }
            episodes.add(episode)
        }

        return episodes.sortedByDescending { it.episode_number }
    }

    override fun episodeListSelector(): String = "div[data-number]"
    override fun episodeFromElement(element: Element): SEpisode = throw UnsupportedOperationException("Use episodeListParse instead")

    // ============================== Video ==============================
    override fun videoListRequest(episode: SEpisode): Request {
        // episode.url = /player/{animeId}?episode={episodeId}
        return GET("$baseUrl${episode.url}", headers)
    }

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videos = mutableListOf<Video>()

        // Плеер подгружается через AJAX, ищем iframe или ссылки
        // Вариант 1: iframe с видео
        val iframes = document.select("iframe[src*=player], iframe[src*=video], iframe[data-src], iframe[src*=kodik]")

        for (iframe in iframes) {
            val src = iframe.attr("abs:src").ifEmpty { iframe.attr("abs:data-src") }
            if (src.isNotEmpty()) {
                videos.add(Video(src, "AnimeGO Player", src, headers))
            }
        }

        // Вариант 2: ссылки на видео в script или data-атрибутах
        if (videos.isEmpty()) {
            val html = document.html()
            // Ищем прямые ссылки на видео
            val videoUrlRegex = Regex("""https?://[^"'\s]+(?:\.m3u8|\.mp4)[^"'\s]*""")
            videoUrlRegex.findAll(html).forEach { match ->
                videos.add(Video(match.value, "Direct", match.value, headers))
            }
        }

        // Вариант 3: если видео в JSON (player.php или api)
        if (videos.isEmpty()) {
            // Попробуем найти data-ajax-url и сделать запрос
            val ajaxUrl = document.selectFirst("[data-ajax-url*=player], [data-ajax-url*=video]")?.attr("data-ajax-url")
            if (ajaxUrl != null) {
                // Рекурсивно запрашиваем (но это может зациклиться, так что осторожно)
                // Пока просто вернём пустой список и лог
            }
        }

        return videos
    }

    override fun videoListSelector(): String = "iframe, video source"
    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException()

    // ============================== Helpers ==============================
    private fun extractAnimeIdFromReferer(response: Response): String {
        // Из URL запроса извлекаем ID аниме
        val url = response.request.url.toString()
        return url.substringAfter("/anime/").substringBefore("/")
    }
}
