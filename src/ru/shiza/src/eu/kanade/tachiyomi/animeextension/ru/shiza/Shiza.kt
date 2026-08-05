package eu.kanade.tachiyomi.animeextension.ru.shiza

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory

class Shiza : AnimeSourceFactory {
    override fun createSources(): List<AnimeSource> = listOf(
        ShizaSource("Shiza", "https://shiza-project.com"),
    )
}
