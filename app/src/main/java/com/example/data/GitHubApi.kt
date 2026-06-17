package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GitHubUser(
    @Json(name = "login") val login: String,
    @Json(name = "avatar_url") val avatarUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubIssue(
    @Json(name = "id") val id: Long,
    @Json(name = "number") val number: Int,
    @Json(name = "title") val title: String,
    @Json(name = "state") val state: String,
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "user") val user: GitHubUser?,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "comments") val comments: Int
)

@JsonClass(generateAdapter = true)
data class GitHubCommit(
    @Json(name = "sha") val sha: String,
    @Json(name = "commit") val commit: CommitInfo
)

@JsonClass(generateAdapter = true)
data class CommitInfo(
    @Json(name = "message") val message: String,
    @Json(name = "author") val author: CommitAuthor
)

@JsonClass(generateAdapter = true)
data class CommitAuthor(
    @Json(name = "name") val name: String,
    @Json(name = "date") val date: String
)

interface GitHubApiService {
    @GET("repos/{owner}/{repo}/issues")
    suspend fun getOpenIssues(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("per_page") perPage: Int = 30,
        @Header("User-Agent") userAgent: String = "SmartFileManager-App"
    ): List<GitHubIssue>

    @GET("repos/{owner}/{repo}/commits")
    suspend fun getRecentCommits(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("since") since: String, // ISO 8601 format: YYYY-MM-DDTHH:MM:SSZ
        @Query("per_page") perPage: Int = 100,
        @Header("User-Agent") userAgent: String = "SmartFileManager-App"
    ): List<GitHubCommit>
}

object GitHubRetrofitClient {
    private const val BASE_URL = "https://api.github.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    val service: GitHubApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(RetrofitClient.moshi))
            .build()
            .create(GitHubApiService::class.java)
    }
}
