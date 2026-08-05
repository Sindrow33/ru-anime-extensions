package eu.kanade.tachiyomi.animeextension.ru.amd

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory

class Amd : AnimeSourceFactory {
    override fun createSources(): List<AnimeSource> = listOf(
        AmdSource("Amd", "https://amd.online"),
    )
}
