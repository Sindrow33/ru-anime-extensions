package eu.kanade.tachiyomi.animeextension.ru.astar

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory

class Astar : AnimeSourceFactory {
    override fun createSources(): List<AnimeSource> = listOf(
        AstarSource("Astar", "https://v30.astar.bz"),
    )
}
