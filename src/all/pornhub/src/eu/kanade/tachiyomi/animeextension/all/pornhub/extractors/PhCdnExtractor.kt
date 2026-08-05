package eu.kanade.tachiyomi.animeextension.all.pornhub.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import keiyoushi.utils.useAsJsoup
import okhttp3.OkHttpClient

class PhCdnExtractor(private val client: OkHttpClient) {
    fun videoFromUrl(videoUrl: String): MutableList<Video> {
        val pattern = Regex("(?<=viewkey=)[^&]+")
        val key = pattern.find(videoUrl)?.value
        val document = client.newCall(GET("https://www.pornhub.com/embed/$key")).execute().useAsJsoup()
        val scriptPart = document.select("body > script:nth-child(7)").html()
        var vars = scriptPart.subSequence(
            scriptPart.indexOf("var ra"),
            scriptPart.indexOf(";flashvars.mediaDefinitions.hls") + 1,
        ).toString().split(";").toMutableList()
        vars = vars.map { it.replace("var ", "") }.toMutableList()

        var hls = String()
        var quality = String()
        val data = mutableMapOf<String, String>()
        for (v in vars) {
            if (v.isEmpty()) continue
            val index = v.indexOf("=")
            if (v.startsWith("hls")) {
                val tmp = removeComment(v)
                quality = tmp.replace("hls", "").split("=")[0]
                hls = tmp.subSequence(index + 1, tmp.length).toString()
            } else {
                val x = v.subSequence(0, index).toString()
                val y = v.subSequence(index + 1, v.length).toString().replace("\"", "").replace(" + ", "")
                data[x] = y
            }
        }
        var finalHls = String()
        for (part in hls.split(" + ")) {
            finalHls += data[part]
        }
        val streamFile = client.newCall(GET(finalHls)).execute().body.string().split("\n").filter {
            it.contains("index")
        }[0]
        val streamUrl = finalHls.subSequence(0, finalHls.indexOf("master")).toString() + streamFile
        val videoList = mutableListOf<Video>()
        videoList.add(Video(streamUrl, quality + "p", streamUrl))
        return videoList
    }

    private fun removeComment(v: String): String = v.replace(Regex("""(\/\/[^\n]*|\/\*(.|[\r\n])*?\*\/)"""), "")
}
