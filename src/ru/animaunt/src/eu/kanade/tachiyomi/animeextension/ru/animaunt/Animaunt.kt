package eu.kanade.tachiyomi.animeextension.ru.animaunt

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory

class Animaunt : AnimeSourceFactory {
    override fun createSources(): List<AnimeSource> = listOf(
        AnimauntSource("Animaunt", "https://v12.animaunt.com"),
    )
}
