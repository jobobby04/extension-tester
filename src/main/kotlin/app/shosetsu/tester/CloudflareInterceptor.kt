package app.shosetsu.tester

import app.shosetsu.lib.ShosetsuSharedLib
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * This code was taken from Suwayomi-Server here:
 * https://github.com/Suwayomi/Suwayomi-Server/blob/633ea97848ad851103c23b5c0196d8786c8e90cd/server/src/main/kotlin/eu/kanade/tachiyomi/network/interceptor/CloudflareInterceptor.kt
 * and as such is released under the MPL v2 license and the GNU AFFERO GENERAL PUBLIC LICENSE of this project
 */
class CloudflareInterceptor(
    private val setUserAgent: (String) -> Unit,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        logger.debug { "CloudflareInterceptor is being used." }

        val originalResponse = chain.proceed(originalRequest)

        // Check if Cloudflare anti-bot is on
        if (!(originalResponse.code in ERROR_CODES && originalResponse.header("Server") in SERVER_CHECK)) {
            return originalResponse
        }

        if (Config.flareSolverrUrl.isEmpty()) {
            return originalResponse
        }

        logger.debug { "Cloudflare anti-bot is on, CloudflareInterceptor is kicking in..." }

        return try {
            originalResponse.close()
            // network.cookieStore.remove(originalRequest.url.toUri())

            val flareResponseFallback = Config.useFlareSolverrAsFallback
            val flareResponse =
                runBlocking {
                    CFClearance.resolveWithFlareSolver(originalRequest, !flareResponseFallback)
                }

            if (flareResponse.message.contains("not detected", ignoreCase = true)) {
                logger.debug { "FlareSolverr failed to detect Cloudflare challenge" }

                if (flareResponseFallback &&
                    flareResponse.solution.status in 200..299 &&
                    flareResponse.solution.response != null
                ) {
                    val isImage = flareResponse.solution.response.contains(CHROME_IMAGE_TEMPLATE_REGEX)
                    if (!isImage) {
                        logger.debug { "Falling back to FlareSolverr response" }

                        setUserAgent(flareResponse.solution.userAgent)

                        return originalResponse
                            .newBuilder()
                            .code(flareResponse.solution.status)
                            .body(flareResponse.solution.response.toResponseBody())
                            .build()
                    } else {
                        logger.debug { "FlareSolverr response is an image html template, not falling back" }
                    }
                }
            }

            val request = CFClearance.requestWithFlareSolverr(flareResponse, setUserAgent, originalRequest)

            chain.proceed(request)
        } catch (e: Exception) {
            // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that
            // we don't crash the entire app
            throw IOException(e)
        }
    }

    companion object {
        private val ERROR_CODES = listOf(403, 503)
        private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
        private val COOKIE_NAMES = listOf("cf_clearance")
        private val CHROME_IMAGE_TEMPLATE_REGEX = Regex("""<title>(.*?) \(\d+×\d+\)</title>""")
    }
}

/*
 * This class is ported from https://github.com/vvanglro/cf-clearance
 * The original code is licensed under Apache 2.0
*/
object CFClearance {
    private val client by lazy {
        val timeout = Config.flareSolverrTimeout.seconds
        ShosetsuSharedLib.httpClient
            .newBuilder()
            .callTimeout(timeout.plus(10.seconds).toJavaDuration())
            .readTimeout(timeout.plus(5.seconds).toJavaDuration())
            .build()
    }
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val jsonMediaType = "application/json".toMediaType()
    private val mutex = Mutex()

    @kotlinx.serialization.Serializable
    data class FlareSolverCookie(
        val name: String,
        val value: String,
    )

    @kotlinx.serialization.Serializable
    data class FlareSolverRequest(
        val cmd: String,
        val url: String,
        val maxTimeout: Int? = null,
        val session: String? = null,
        @SerialName("session_ttl_minutes")
        val sessionTtlMinutes: Int? = null,
        val cookies: List<FlareSolverCookie>? = null,
        val returnOnlyCookies: Boolean? = null,
        val proxy: String? = null,
        val postData: String? = null, // only used with cmd 'request.post'
    )

    @kotlinx.serialization.Serializable
    data class FlareSolverSolutionCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String? = null,
        val expires: Double? = null,
        val size: Int? = null,
        val httpOnly: Boolean? = null,
        val secure: Boolean? = null,
        val session: Boolean? = null,
        val sameSite: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class FlareSolverSolution(
        val url: String,
        val status: Int,
        val headers: Map<String, String>? = null,
        val response: String? = null,
        val cookies: List<FlareSolverSolutionCookie>,
        val userAgent: String,
    )

    @kotlinx.serialization.Serializable
    data class FlareSolverResponse(
        val solution: FlareSolverSolution,
        val status: String,
        val message: String,
        val startTimestamp: Long,
        val endTimestamp: Long,
        val version: String,
    )

    suspend fun resolveWithFlareSolver(
        originalRequest: Request,
        onlyCookies: Boolean,
    ): FlareSolverResponse {
        val timeout = Config.flareSolverrTimeout.seconds

        return mutex.withLock {
            json.decodeFromString<FlareSolverResponse>(
                client
                    .newCall(
                        Request.Builder()
                            .url(Config.flareSolverrUrl.removeSuffix("/") + "/v1")
                            .post(
                                Json
                                    .encodeToString(
                                        FlareSolverRequest(
                                            "request.get",
                                            originalRequest.url.toString(),
                                            session = "shosetsu",
                                            sessionTtlMinutes = 15,
                                            cookies =
                                                Cookies.loadForRequest(originalRequest.url).map {
                                                    FlareSolverCookie(it.name, it.value)
                                                },
                                            returnOnlyCookies = onlyCookies,
                                            maxTimeout = timeout.inWholeMilliseconds.toInt(),
                                        ),
                                    ).toRequestBody(jsonMediaType)
                            )
                            .build()
                    ).execute().body!!.string()
            )
        }
    }

    fun requestWithFlareSolverr(
        flareSolverResponse: FlareSolverResponse,
        setUserAgent: (String) -> Unit,
        originalRequest: Request,
    ): Request {
        if (flareSolverResponse.solution.status in 200..299) {
            setUserAgent(flareSolverResponse.solution.userAgent)
            val cookies =
                flareSolverResponse.solution.cookies
                    .map { cookie ->
                        Cookie
                            .Builder()
                            .name(cookie.name)
                            .value(cookie.value)
                            .domain(cookie.domain.removePrefix("."))
                            .also {
                                if (cookie.httpOnly != null && cookie.httpOnly) it.httpOnly()
                                if (cookie.secure != null && cookie.secure) it.secure()
                                if (!cookie.path.isNullOrEmpty()) it.path(cookie.path)
                                // We need to convert the expires time to milliseconds for the persistent cookie store
                                if (cookie.expires != null && cookie.expires > 0) it.expiresAt((cookie.expires * 1000).toLong())
                            }.build()
                    }.groupBy { it.domain }
                    .flatMap { (domain, cookies) ->
                        Cookies.saveFromResponse(
                            HttpUrl
                                .Builder()
                                .scheme("http")
                                .host(domain.removePrefix("."))
                                .build(),
                            cookies,
                        )

                        cookies
                    }
            logger.debug { "New cookies\n${cookies.joinToString("; ")}" }
            val finalCookies =
                Cookies.loadForRequest(originalRequest.url).joinToString("; ", postfix = "; ") {
                    "${it.name}=${it.value}"
                }
            logger.debug { "Final cookies\n$finalCookies" }
            return originalRequest
                .newBuilder()
                .header("Cookie", finalCookies)
                .header("User-Agent", flareSolverResponse.solution.userAgent)
                .build()
        } else {
            logger.debug { "Cloudflare challenge failed to resolve" }
            throw CloudflareBypassException()
        }
    }

    private class CloudflareBypassException : Exception()
}