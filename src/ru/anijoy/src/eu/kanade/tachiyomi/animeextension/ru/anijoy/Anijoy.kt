package eu.kanade.tachiyomi.animeextension.ru.anijoy

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory

class Anijoy : AnimeSourceFactory {
    override fun createSources(): List<AnimeSource> = listOf(
        AnijoySource("Anijoy", "https://anijoy.ru"),
    )
}
