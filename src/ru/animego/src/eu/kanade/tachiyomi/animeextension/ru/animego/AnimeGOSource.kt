package eu.kanade.tachiyomi.animeextension.ru.animego

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

class AnimeGOSource(
    override val name: String,
    override val baseUrl: String,
) : ParsedAnimeHttpSource() {

    override val lang = "ru"
    override val supportsLatest = true
    override val client: OkHttpClient by injectLazy()

    // ============================== Popular ==============================
    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/anime?page=$page", headers)

    override fun popularAnimeSelector(): String = "div.animes-grid-item"
    override fun popularAnimeNextPageSelector(): String = "ul.pagination li.active + li a"

    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        val link = element.selectFirst("a.d-block")!!
        anime.setUrlWithoutDomain(link.attr("href"))
        anime.title = element.select("h3").text()
        anime.thumbnail_url = element.select("img").attr("abs:src")
        return anime
    }

    // ============================== Latest ==============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/anime?page=$page", headers)

    override fun latestUpdatesSelector(): String = popularAnimeSelector()
    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    // ============================== Search ==============================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = if (query.isNotEmpty()) {
            "$baseUrl/search/anime?q=$query&page=$page"
        } else {
            "$baseUrl/anime?page=$page"
        }
        return GET(url, headers)
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()
    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    // ============================== Details ==============================
    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()
        anime.title = document.select("h1").text()
        anime.description = document.select("div.description").text()
        anime.thumbnail_url = document.select("div.poster img").attr("abs:src")
        anime.genre = document.select("div.genres a").joinToString { it.text() }
        anime.status = SAnime.UNKNOWN
        return anime
    }

    // ============================== Episodes ==============================
    override fun episodeListSelector(): String = "div.episodes a, div[data-episode]"

    override fun episodeFromElement(element: Element): SEpisode {
        val episode = SEpisode.create()
        episode.setUrlWithoutDomain(element.attr("href"))
        episode.name = element.text()
        episode.episode_number = element.text().filter { it.isDigit() }.toFloatOrNull() ?: 0f
        return episode
    }

    // ============================== Video ==============================
    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videos = mutableListOf<Video>()

        // AnimeGO использует iframe с Kodik или другими плеерами
        val iframe = document.selectFirst("iframe[src*=kodik], iframe[src*=player], iframe[src*=video]")
        if (iframe != null) {
            val iframeUrl = iframe.attr("abs:src")
            videos.add(Video(iframeUrl, "AnimeGO Player", iframeUrl, headers))
        }

        // Прямые ссылки на видео (если есть)
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
