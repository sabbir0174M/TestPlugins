package com.lagradost.cloudstream3.peachify

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.Jsoup

class PeachifyProvider : MainAPI() {
    override var mainUrl = "https://peachify.top"
    override var name = "Peachify"
    override val hasMainPage = false
    override var lang = "en"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override suspend fun search(query: String): List<SearchResponse> {
        return getTmdbSearch(
            query = query,
            includeAdult = false,
            lang = lang
        ).map { result ->
            when (result) {
                is MovieSearchResponse -> {
                    newMovieSearchResponse(
                        name = result.name,
                        url = "$mainUrl/embed/movie/${result.id}",
                        result.posterUrl
                    ) {
                        this.year = result.year
                        this.plot = result.overview
                    }
                }
                is TvSeriesSearchResponse -> {
                    newTvSeriesSearchResponse(
                        name = result.name,
                        url = "$mainUrl/embed/tv/${result.id}",
                        result.posterUrl
                    ) {
                        this.year = result.year
                        this.plot = result.overview
                    }
                }
                else -> null
            }
        }.filterNotNull()
    }

    override suspend fun load(url: String): LoadResponse? {
        return when {
            url.contains("/movie/") -> {
                val tmdbId = url.substringAfterLast("/").toIntOrNull() ?: return null
                val movie = getTmdbMovie(tmdbId, lang) ?: return null
                newMovieLoadResponse(
                    name = movie.title,
                    url = url,
                    posterUrl = movie.posterPath
                ) {
                    this.plot = movie.overview
                    this.year = movie.releaseDate?.take(4)?.toIntOrNull()
                    this.rating = movie.voteAverage?.toFloat()
                    addTrailer(movie.trailer)
                }
            }
            url.contains("/tv/") -> {
                val tmdbId = url.substringAfterLast("/").toIntOrNull() ?: return null
                val tvShow = getTmdbTv(tmdbId, lang) ?: return null
                newTvSeriesLoadResponse(
                    name = tvShow.name,
                    url = url,
                    posterUrl = tvShow.posterPath
                ) {
                    this.plot = tvShow.overview
                    this.year = tvShow.firstAirDate?.take(4)?.toIntOrNull()
                    this.rating = tvShow.voteAverage?.toFloat()
                    tvShow.seasons?.forEach { season ->
                        if (season.seasonNumber == 0) return@forEach
                        val seasonNum = season.seasonNumber
                        val episodes = getTmdbEpisodes(tmdbId, seasonNum, lang)
                        addSeason(
                            seasonNum = seasonNum,
                            episodes = episodes?.map { ep ->
                                Episode(
                                    name = "E${ep.episodeNumber}: ${ep.name}",
                                    url = "$mainUrl/embed/tv/$tmdbId/$seasonNum/${ep.episodeNumber}",
                                    posterUrl = ep.stillPath,
                                    episode = ep.episodeNumber
                                )
                            } ?: emptyList()
                        )
                    }
                }
            }
            else -> null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, referer = mainUrl).document
        
        val videoSrc = document.select("video[src]").attr("src")
        if (videoSrc.isNotBlank()) {
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = "Peachify Direct",
                    url = videoSrc,
                    quality = Qualities.Unknown,
                    type = if (videoSrc.endsWith(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.DIRECT,
                    headers = mapOf("Referer" to mainUrl)
                )
            )
            return true
        }

        val iframeSrc = document.select("iframe[src]").attr("src")
        if (iframeSrc.isNotBlank()) {
            val iframeDoc = app.get(iframeSrc, referer = data).document
            val innerVideo = iframeDoc.select("video[src]").attr("src")
            if (innerVideo.isNotBlank()) {
                callback.invoke(
                    ExtractorLink(
                        source = name,
                        name = "Peachify Iframe",
                        url = innerVideo,
                        quality = Qualities.Unknown,
                        type = if (innerVideo.endsWith(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.DIRECT,
                        headers = mapOf("Referer" to mainUrl)
                    )
                )
                return true
            }
            val scriptData = iframeDoc.select("script").html()
            val regex = Regex("""https?://[^\s"']+\.(m3u8|mp4)""")
            val match = regex.find(scriptData)
            if (match != null) {
                callback.invoke(
                    ExtractorLink(
                        source = name,
                        name = "Peachify Scraped",
                        url = match.value,
                        quality = Qualities.Unknown,
                        type = if (match.value.endsWith(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.DIRECT,
                        headers = mapOf("Referer" to mainUrl)
                    )
                )
                return true
            }
        }

        val allHtml = document.html()
        val allMatches = Regex("""https?://[^\s"']+\.(m3u8|mp4)""").findAll(allHtml)
        val firstMatch = allMatches.firstOrNull()
        if (firstMatch != null) {
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = "Peachify Direct Scan",
                    url = firstMatch.value,
                    quality = Qualities.Unknown,
                    type = if (firstMatch.value.endsWith(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.DIRECT,
                    headers = mapOf("Referer" to mainUrl)
                )
            )
            return true
        }

        return false
    }
}
