package eu.kanade.tachiyomi.animeextension.ru.yummyanime

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.OkHttpClient
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@SuppressLint("SetJavaScriptEnabled")
class AllohaExtractor(private val client: OkHttpClient) {

    private val context: Application by injectLazy()

    private val cache = ConcurrentHashMap<String, Pair<Long, List<Video>>>()
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<List<Video>>>()

    private val verifyClient: OkHttpClient by lazy {
        client.newBuilder().cookieJar(CookieJar.NO_COOKIES).build()
    }

    fun videosFromUrl(
        iframeUrl: String,
        siteUrl: String,
        prefix: String = "Alloha",
        episodePlaybackIdentity: String? = null,
        cacheKey: String? = null,
    ): List<Video> {
        val playerUrl = normalizeUrl(iframeUrl)
        val key = cacheKey ?: playerUrl
        cachedVideos(key)?.let { return it }

        val future = CompletableFuture<List<Video>>()
        inFlight.putIfAbsent(key, future)?.let { running ->
            return runCatching { running.get(TIMEOUT_MS * 3, TimeUnit.MILLISECONDS) }
                .getOrDefault(emptyList())
        }
        try {
            EXTRACTION_SEMAPHORE.acquire()
            val videos = try {
                cachedVideos(key)
                    ?: extract(playerUrl, prefix, episodePlaybackIdentity).also {
                        if (it.isNotEmpty()) {
                            cache[key] = System.currentTimeMillis() to it
                        }
                    }
            } finally {
                EXTRACTION_SEMAPHORE.release()
            }
            future.complete(videos)
            return videos
        } catch (e: Throwable) {
            future.complete(emptyList())
            throw e
        } finally {
            inFlight.remove(key)
        }
    }

    private fun cachedVideos(playerUrl: String): List<Video>? = cache[playerUrl]?.let { (timestamp, videos) ->
        videos.takeIf { System.currentTimeMillis() - timestamp < CACHE_TTL_MS }
    }

    private fun extract(
        playerUrl: String,
        prefix: String,
        episodePlaybackIdentity: String?,
    ): List<Video> {
        val playbackHeaders = playbackHeaders(playerUrl)
        val future = CompletableFuture<List<String>>()
        val handler = Handler(Looper.getMainLooper())

        var webView: WebView? = null
        var delivered = false
        val capturedUrls = LinkedHashSet<String>()
        val capturedSubs = LinkedHashSet<String>()
        var settleRunnable: Runnable? = null

        fun deliver(urls: List<String>) {
            if (delivered) return
            delivered = true
            val wv = webView
            webView = null
            settleRunnable?.let(handler::removeCallbacks)
            handler.post {
                wv?.run {
                    runCatching { stopLoading() }
                    runCatching { destroy() }
                }
            }
            future.complete(urls)
        }

        fun deliverCaptured() {
            if (capturedUrls.isEmpty()) return
            deliver(capturedUrls.toList())
        }

        val timeoutRunnable = Runnable { deliver(emptyList()) }
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS)

        webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null
                    if (url.contains(".m3u8")) {
                        capturedUrls.add(url)
                    } else if (url.contains(".vtt") || url.contains(".srt")) {
                        capturedSubs.add(url)
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }
            loadUrl(playerUrl)
        }

        val urls = try {
            future.get(TIMEOUT_MS * 2, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            emptyList()
        }

        return urls.mapNotNull { url ->
            val quality = when {
                url.contains("1080") -> "1080p"
                url.contains("720") -> "720p"
                url.contains("480") -> "480p"
                else -> "Default"
            }
            Video(url, "$prefix $quality", url, headers = playbackHeaders)
        }
    }

    private fun normalizeUrl(url: String): String = url
    private fun playbackHeaders(url: String): Headers = Headers.Builder().build()

    companion object {
        private const val TIMEOUT_MS = 25000L
        private const val CACHE_TTL_MS = 300000L
        private val EXTRACTION_SEMAPHORE = java.util.concurrent.Semaphore(3)
    }
}
