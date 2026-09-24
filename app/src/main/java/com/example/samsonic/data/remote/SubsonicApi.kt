package com.example.samsonic.data.remote

import retrofit2.http.GET
import retrofit2.http.QueryMap

/**
 * Every Subsonic REST call takes the same auth params (u/t/s/v/c/f) plus method-specific
 * ones, so callers build a single flat query map (see [SubsonicAuth]) instead of dozens of
 * per-endpoint parameter lists.
 */
interface SubsonicApi {
    @GET("rest/ping.view")
    suspend fun ping(@QueryMap params: Map<String, String>): SubsonicEnvelope

    @GET("rest/getArtists.view")
    suspend fun getArtists(@QueryMap params: Map<String, String>): SubsonicEnvelope

    @GET("rest/getArtist.view")
    suspend fun getArtist(@QueryMap params: Map<String, String>): SubsonicEnvelope

    @GET("rest/getAlbum.view")
    suspend fun getAlbum(@QueryMap params: Map<String, String>): SubsonicEnvelope

    @GET("rest/getAlbumList2.view")
    suspend fun getAlbumList2(@QueryMap params: Map<String, String>): SubsonicEnvelope

    @GET("rest/getPlaylists.view")
    suspend fun getPlaylists(@QueryMap params: Map<String, String>): SubsonicEnvelope

    @GET("rest/getPlaylist.view")
    suspend fun getPlaylist(@QueryMap params: Map<String, String>): SubsonicEnvelope

    @GET("rest/getGenres.view")
    suspend fun getGenres(@QueryMap params: Map<String, String>): SubsonicEnvelope

    @GET("rest/getSongsByGenre.view")
    suspend fun getSongsByGenre(@QueryMap params: Map<String, String>): SubsonicEnvelope

    @GET("rest/search3.view")
    suspend fun search3(@QueryMap params: Map<String, String>): SubsonicEnvelope

    @GET("rest/getRandomSongs.view")
    suspend fun getRandomSongs(@QueryMap params: Map<String, String>): SubsonicEnvelope

    @GET("rest/getTopSongs.view")
    suspend fun getTopSongs(@QueryMap params: Map<String, String>): SubsonicEnvelope

    @GET("rest/getStarred2.view")
    suspend fun getStarred2(@QueryMap params: Map<String, String>): SubsonicEnvelope

    @GET("rest/star.view")
    suspend fun star(@QueryMap params: Map<String, String>): SubsonicEnvelope

    @GET("rest/unstar.view")
    suspend fun unstar(@QueryMap params: Map<String, String>): SubsonicEnvelope

    @GET("rest/scrobble.view")
    suspend fun scrobble(@QueryMap params: Map<String, String>): SubsonicEnvelope

    @GET("rest/getLyricsBySongId.view")
    suspend fun getLyricsBySongId(@QueryMap params: Map<String, String>): SubsonicEnvelope
}
