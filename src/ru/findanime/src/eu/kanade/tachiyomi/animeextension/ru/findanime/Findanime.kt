package eu.kanade.tachiyomi.animeextension.ru.findanime

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory

class Findanime : AnimeSourceFactory {
    override fun createSources(): List<AnimeSource> = listOf(
        FindanimeSource("Findanime", "https://22.findanime.me"),
    )
}
