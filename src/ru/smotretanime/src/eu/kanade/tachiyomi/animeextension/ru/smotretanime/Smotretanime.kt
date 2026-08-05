package eu.kanade.tachiyomi.animeextension.ru.smotretanime

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory

class Smotretanime : AnimeSourceFactory {
    override fun createSources(): List<AnimeSource> = listOf(
        SmotretanimeSource("Smotretanime", "https://smotret-anime.org"),
    )
}
