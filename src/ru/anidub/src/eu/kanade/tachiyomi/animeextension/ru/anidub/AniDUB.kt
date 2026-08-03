package eu.kanade.tachiyomi.animeextension.ru.anidub

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory

class AniDUB : AnimeSourceFactory {
    override fun createSources(): List<AnimeSource> = listOf<AnimeSource>(
        AniDUBSource("AniDUB", "https://anidub.live"),
        AniDUBSource("AniDUB Mirror", "https://anidub.com"),
    )
}
