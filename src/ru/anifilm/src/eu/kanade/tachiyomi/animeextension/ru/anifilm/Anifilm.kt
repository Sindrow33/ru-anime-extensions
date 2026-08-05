package eu.kanade.tachiyomi.animeextension.ru.anifilm

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory

class Anifilm : AnimeSourceFactory {
    override fun createSources(): List<AnimeSource> = listOf(
        AnifilmSource("Anifilm", "https://anifilm.pro"),
    )
}
