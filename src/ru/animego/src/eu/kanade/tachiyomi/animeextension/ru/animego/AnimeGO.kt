package eu.kanade.tachiyomi.animeextension.ru.animego

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory

class AnimeGO : AnimeSourceFactory {
    override fun createSources(): List<AnimeSource> = listOf<AnimeSource>(
        AnimeGOSource("AnimeGO", "https://animego.one"),
    )
}
