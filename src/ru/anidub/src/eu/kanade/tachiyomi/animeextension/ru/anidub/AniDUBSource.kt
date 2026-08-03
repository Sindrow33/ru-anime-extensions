package eu.kanade.tachiyomi.animeextension.ru.anidub

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy

class AniDUBSource(
    override val name: String,
    override val baseUrl: String,
) : ParsedAnimeHttpSource() {

    override val lang = "ru"
    override val supportsLatest = true
    override val client: OkHttpClient by injectLazy()

    // ============================== Popular ==============================
    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/anime/page/$page/", headers)

    override fun popularAnimeSelector(): String = "div.thumb"
    override fun popularAnimeNextPageSelector(): String = "a.next"

    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        val link = element.selectFirst("a")!!
        anime.setUrlWithoutDomain(link.attr("href"))
        anime.title = link.attr("title").ifEmpty { element.select("img").attr("alt") }
        anime.thumbnail_url = element.select("img").attr("abs:src")
        return anime
    }

    // ============================== Latest ==============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page/", headers)

    override fun latestUpdatesSelector(): String = "div.thumb"
    override fun latestUpdatesNextPageSelector(): String = "a.next"

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    // ============================== Search ==============================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = if (query.isNotEmpty()) {
            "$baseUrl/index.php?do=search&subaction=search&story=$query&search_start=$page"
        } else {
            "$baseUrl/anime/page/$page/"
        }
        return GET(url, headers)
    }

    override fun searchAnimeSelector(): String = "div.thumb"
    override fun searchAnimeNextPageSelector(): String = "a.next"

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    // ============================== Details ==============================
    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()
        anime.title = document.select("h1").text()
        anime.description = document.select("div.story").text()
        anime.thumbnail_url = document.select("div.poster img").attr("abs:src")
        anime.genre = document.select("div.story a[href*=genre]").joinToString { it.text() }
        anime.status = SAnime.UNKNOWN
        return anime
    }

    // ============================== Episodes ==============================
    override fun episodeListSelector(): String = "div.video-box a, div.playlists-videos a"

    override fun episodeFromElement(element: Element): SEpisode {
        val episode = SEpisode.create()
        episode.setUrlWithoutDomain(element.attr("href"))
        episode.name = element.text().ifEmpty { "Серия" }
        episode.episode_number = element.text().filter { it.isDigit() }.toFloatOrNull() ?: 0f
        return episode
    }

    // ============================== Video ==============================
    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videos = mutableListOf<Video>()

        // AniDUB обычно использует iframe с плеером или прямые ссылки
        val iframe = document.selectFirst("iframe[src*=player], iframe[src*=video]")
        if (iframe != null) {
            val iframeUrl = iframe.attr("abs:src")
            // Попытка найти прямую ссылку в iframe или вернуть iframe как есть
            videos.add(Video(iframeUrl, "AniDUB Player", iframeUrl, headers))
        }

        // Если есть прямые ссылки на видео
        document.select("video source").forEach { source ->
            val url = source.attr("abs:src")
            val quality = source.attr("label").ifEmpty { "Default" }
            videos.add(Video(url, quality, url, headers))
        }

        return videos
    }

    override fun videoListSelector(): String = "video source, iframe"
    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException()
}
