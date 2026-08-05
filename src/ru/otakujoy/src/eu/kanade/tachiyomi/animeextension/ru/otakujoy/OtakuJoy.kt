package eu.kanade.tachiyomi.animeextension.ru.otakujoy

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory

class OtakuJoy : AnimeSourceFactory {
    override fun createSources(): List<AnimeSource> = listOf(
        OtakuJoySource("OtakuJoy", "https://otakujoy.fun"),
    )
}
