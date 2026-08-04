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
        val url = if (page == 1) "$baseUrl/anime" else "$baseUrl/anime/$page"
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
        val url = if (page == 1) "$baseUrl/anime" else "$baseUrl/anime/$page"
        return GET(url, headers)
    }

    override fun latestUpdatesSelector(): String = popularAnimeSelector()
    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

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
        anime.description = document.select("div.description, .anime-description, .story, .about, .info-description").text()

        // Постер
        anime.thumbnail_url = document.select("div.poster img, .anime-poster img, img.poster, .cover img, .image img").attr("abs:src")

        // Жанры (ищем в карточке или на странице деталей)
        anime.genre = document.select("div.ani-list__item-genres a, .genres a, a[href*=genre]").joinToString { it.text() }

        // Статус
        val metaText = document.select("div.anime-info, .info, .meta, table, .details").text()
        anime.status = when {
            metaText.contains("Онгоинг", ignoreCase = true) ||
                metaText.contains("Выходит", ignoreCase = true) -> SAnime.ONGOING
            metaText.contains("Завершён", ignoreCase = true) ||
                metaText.contains("Вышел", ignoreCase = true) -> SAnime.COMPLETED
            else -> SAnime.UNKNOWN
        }

        return anime
    }

    // ============================== Episodes ==============================
    override fun episodeListSelector(): String = "div.episodes-list a, .episode-item a, table.episodes tr a, a[href*=episode], .series-list a, .episodes a"

    override fun episodeFromElement(element: Element): SEpisode {
        val episode = SEpisode.create()
        episode.setUrlWithoutDomain(element.attr("href"))

        val text = element.text()
        episode.name = text.ifEmpty { "Серия" }

        val numberRegex = Regex("""(\d+)\s*(?:серия|episode|ep)?""", RegexOption.IGNORE_CASE)
        episode.episode_number = numberRegex.find(text)?.groupValues?.get(1)?.toFloatOrNull()
            ?: element.attr("href").filter { it.isDigit() }.toFloatOrNull()
            ?: 0f

        return episode
    }

    // ============================== Video ==============================
    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videos = mutableListOf<Video>()

        val iframes = document.select("iframe[src*=kodik], iframe[src*=player], iframe[src*=video], iframe[data-src], iframe[src*=sibnet], iframe[src*=vk], iframe[src*=aniboom]")

        for (iframe in iframes) {
            val src = iframe.attr("abs:src").ifEmpty { iframe.attr("abs:data-src") }
            if (src.isNotEmpty()) {
                val playerName = when {
                    src.contains("kodik") -> "Kodik"
                    src.contains("aniboom") -> "AniBoom"
                    src.contains("sibnet") -> "Sibnet"
                    src.contains("vk") -> "VK"
                    else -> "AnimeGO Player"
                }
                videos.add(Video(src, playerName, src, headers))
            }
        }

        document.select("video source").forEach { source ->
            val url = source.attr("abs:src")
            val quality = source.attr("label").ifEmpty { "Default" }
            if (url.isNotEmpty()) {
                videos.add(Video(url, "Direct $quality", url, headers))
            }
        }

        return videos
    }

    override fun videoListSelector(): String = "video source, iframe"
    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException()
}
