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
import okio.Buffer
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * This code was taken from Suwayomi-Server here:
 * https://github.com/Suwayomi/Suwayomi-Server/blob/57f234030248cd189766f5f48b4f9bec93e04c5f/server/src/main/kotlin/eu/kanade/tachiyomi/network/interceptor/CloudflareInterceptor.kt
 * and as such is released under the MPL v2 license and the GNU AFFERO GENERAL PUBLIC LICENSE of this project
 */

class CloudflareInterceptor(
    private val setUserAgent: (String) -> Unit,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        if (Config.flareSolverrUrl.isEmpty()) {
            return chain.proceed(originalRequest)
        }

        logger.debug { "CloudflareInterceptor is being used." }

        val originalResponse = chain.proceed(originalRequest)

        // Check if Cloudflare anti-bot is on
        if (!(originalResponse.code in ERROR_CODES && originalResponse.header("Server") in SERVER_CHECK)) {
            return originalResponse
        }

        logger.debug { "Cloudflare anti-bot is on, CloudflareInterceptor is kicking in..." }

        val flareResponseFallback = Config.useFlareSolverrAsFallback

        return try {
            originalResponse.close()
            resolveCloudflare(chain, originalRequest, originalResponse, flareResponseFallback)
        } catch (e: Exception) {
            // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that we don't crash the entire app
            throw IOException(e)
        }
    }

    private fun resolveCloudflare(
        chain: Interceptor.Chain,
        originalRequest: Request,
        originalResponse: Response,
        flareResponseFallback: Boolean,
    ): Response {
        val host = originalRequest.url.host

        while (true) {
            val bypassRequest = CompletableFuture<CFClearance.Result>()
            val inflightRequest = CFClearance.inflightCalls.putIfAbsent(host, bypassRequest)

            val awaitInflightResult = inflightRequest != null
            if (awaitInflightResult) {
                logger.debug { "Waiting for inflight call for host $host" }

                when (val result = awaitInflightResult(inflightRequest)) {
                    is CFClearance.Result.CloudflareBypassed -> {
                        val request =
                            CFClearance.buildRequestWithStoredCookies(
                                originalRequest,
                                result.userAgent,
                            )

                        return chain.proceed(request)
                    }

                    is CFClearance.Result.CloudflareNotDetected -> {
                        logger.debug { "Inflight call did not detect Cloudflare for $host, retrying" }
                        continue
                    }
                }
            }

            logger.debug { "Calling FlareSolverr for host $host" }
            try {
                val flareResponse =
                    runBlocking {
                        CFClearance.resolveWithFlareSolver(originalRequest, !flareResponseFallback)
                    }

                val cloudflareDetected =
                    !flareResponse.message.contains("not detected", ignoreCase = true)
                return if (cloudflareDetected) {
                    val request =
                        CFClearance.requestWithFlareSolverr(
                            flareResponse,
                            setUserAgent,
                            originalRequest,
                        )
                    bypassRequest.complete(
                        CFClearance.Result.CloudflareBypassed(
                            flareResponse.solution.userAgent,
                        ),
                    )

                    chain.proceed(request)
                } else {
                    CFClearance.inflightCalls.remove(host, bypassRequest)
                    bypassRequest.complete(CFClearance.Result.CloudflareNotDetected)

                    maybeFallbackToFlareSolverResponse(
                        flareResponse,
                        chain,
                        originalRequest,
                        originalResponse,
                        flareResponseFallback,
                    )
                }
            } catch (e: Exception) {
                bypassRequest.completeExceptionally(e)
                throw e
            } finally {
                CFClearance.inflightCalls.remove(host, bypassRequest)
            }
        }
    }

    private fun maybeFallbackToFlareSolverResponse(
        flareResponse: CFClearance.FlareSolverResponse,
        chain: Interceptor.Chain,
        originalRequest: Request,
        originalResponse: Response,
        flareResponseFallback: Boolean,
    ): Response {
        logger.debug { "FlareSolverr failed to detect Cloudflare challenge" }

        if (flareResponseFallback &&
            flareResponse.solution.status in 200..299 &&
            flareResponse.solution.response != null
        ) {
            val isImage =
                flareResponse.solution.response.contains(CHROME_IMAGE_TEMPLATE_REGEX)
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

        val request =
            CFClearance.requestWithFlareSolverr(flareResponse, setUserAgent, originalRequest)

        return chain.proceed(request)
    }

    private fun awaitInflightResult(future: CompletableFuture<CFClearance.Result>): CFClearance.Result {
        while (true) {
            try {
                return future.get()
            } catch (_: TimeoutException) {
                continue
            } catch (e: ExecutionException) {
                throw e.cause ?: e
            }
        }
    }

    companion object {
        private val ERROR_CODES = listOf(403, 503)
        private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
        val COOKIE_NAMES = listOf("cf_clearance")
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

    sealed class Result {
        data class CloudflareBypassed(
            val userAgent: String,
        ) : Result()

        data object CloudflareNotDetected : Result()
    }

    val inflightCalls = ConcurrentHashMap<String, CompletableFuture<Result>>()

    fun buildRequestWithStoredCookies(
        request: Request,
        userAgent: String,
    ): Request {
        val cookies =
            Cookies.loadForRequest(request.url).joinToString("; ", postfix = "; ") {
                "${it.name}=${it.value}"
            }

        logger.debug { "Final cookies\n$cookies" }

        return request
            .newBuilder()
            .header("Cookie", cookies)
            .header("User-Agent", userAgent)
            .build()
    }

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
                                            "request.${originalRequest.method.lowercase()}",
                                            originalRequest.url.toString(),
                                            session = "shosetsu",
                                            sessionTtlMinutes = 15,
                                            cookies =
                                                Cookies.loadForRequest(originalRequest.url)
                                                    .filter { it.name !in CloudflareInterceptor.COOKIE_NAMES }
                                                    .map { cookie ->
                                                        FlareSolverCookie(cookie.name, cookie.value)
                                                    },
                                            returnOnlyCookies = onlyCookies,
                                            maxTimeout = timeout.inWholeMilliseconds.toInt(),
                                            postData =
                                                if (originalRequest.method == "POST") {
                                                    originalRequest.body
                                                        ?.let { body ->
                                                            Buffer()
                                                                .also { body.writeTo(it) }
                                                                .readUtf8()
                                                        }.orEmpty()
                                                } else {
                                                    null
                                                },
                                        ),
                                    ).toRequestBody(jsonMediaType),
                            ).build()
                    ).execute().body.string()
            )
        }
    }

    fun requestWithFlareSolverr(
        flareSolverResponse: FlareSolverResponse,
        setUserAgent: (String) -> Unit,
        originalRequest: Request,
    ): Request {
        if (flareSolverResponse.solution.cookies.none { it.name in CloudflareInterceptor.COOKIE_NAMES }) {
            logger.debug { "Cloudflare challenge failed to resolve" }
            throw CloudflareBypassException()
        } else {
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
                                if (!cookie.domain.startsWith('.')) {
                                    it.hostOnlyDomain(cookie.domain.removePrefix("."))
                                }
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

            return buildRequestWithStoredCookies(
                request = originalRequest,
                userAgent = flareSolverResponse.solution.userAgent,
            )
        }
    }

    private class CloudflareBypassException : Exception()
}