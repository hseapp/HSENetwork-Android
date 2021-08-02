package com.hse.network

import kotlinx.coroutines.suspendCancellableCoroutine
import org.chromium.net.CronetException
import org.chromium.net.UploadDataProviders
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

abstract class Request<T>(private val url: String) {
    var method = Method.GET
    val params = Params()
    val bodyParams = Params()
    val headers = Params()

    private var attempts = 0
    private val bytesReceived = ByteArrayOutputStream()
    private val receiveChannel = Channels.newChannel(bytesReceived)
    private lateinit var network: Network

    abstract fun parse(response: String): T

    init {
       id++
    }

    @Throws(Throwable::class)
    suspend fun run(network: Network): T? {
        this.network = network
        return runInternal()
    }

    private suspend fun runInternal(): T? = suspendCancellableCoroutine { c ->
        if (attempts == MAX_REQUEST_ATTEMPTS) {
            Timber.e("%s Max attempts reached. Request cancelled.", url)
            c.resume(null)
            return@suspendCancellableCoroutine
        }
        if (requests.contains(hashCode())) {
            while (requests.contains(hashCode())) { }
            c.resume(null)
            return@suspendCancellableCoroutine
        }
        attempts++

        val urlRequest: UrlRequest?
        val finalUrl = if (isParamsForUrl()) "$url?$params" else url
        val builder =
            network.getCronetEngine().newUrlRequestBuilder(
                finalUrl,
                object : UrlRequest.Callback() {
                    override fun onResponseStarted(request: UrlRequest?, info: UrlResponseInfo?) {
                        request?.read(ByteBuffer.allocateDirect(32 * 1024))
                    }

                    override fun onReadCompleted(
                        request: UrlRequest?,
                        info: UrlResponseInfo?,
                        byteBuffer: ByteBuffer?
                    ) {
                        if (byteBuffer == null || request == null) return
                        byteBuffer.flip()
                        while (byteBuffer.hasRemaining()) {
                            receiveChannel.write(byteBuffer)
                        }
                        byteBuffer.clear()
                        request.read(byteBuffer)
                    }

                    override fun onFailed(
                        request: UrlRequest?,
                        info: UrlResponseInfo?,
                        error: CronetException?
                    ) {
                        requests.remove(this@Request.hashCode())
                        if (error != null) {
                            c.resumeWithException(error)
                        } else {
                            c.resumeWithException(Exception("unknown error"))
                        }
                    }

                    override fun onSucceeded(request: UrlRequest?, info: UrlResponseInfo?) {
                        try {
                            bytesReceived.close()
                            val response = bytesReceived.toByteArray().toString(Charsets.UTF_8)
                            Timber.i("Response ID=$id $response")
                            bytesReceived.reset()

                            try {
                                val json = JSONObject(response)
                                if (json.has("error")) {
                                    c.resumeWithException(RequestException.parse(json.optJSONObject("error")))
                                    return
                                }
                            } catch (e: Throwable) {
                                // not a json object
                            }
                            requests.remove(this@Request.hashCode())
                            c.resume(parse(response))
                        } catch (e: Throwable) {
                            requests.remove(this@Request.hashCode())
                            c.resumeWithException(e)
                        }
                    }

                    override fun onRedirectReceived(
                        request: UrlRequest?,
                        info: UrlResponseInfo?,
                        newLocationUrl: String?
                    ) {
                        request?.followRedirect()
                    }

                },
                network.getExecutor()
            )

        Timber.i("Request ID=$id $finalUrl\nParams: $params\nBody: $bodyParams")
        builder.setHttpMethod(method.name)

        fun addData(params: Params) {
            builder.apply {
                addHeader("Content-Type", "application/json")
                addHeader("Accept-Language", Locale.getDefault().toLanguageTag())
                if (!params.isEmpty()) {
                    try {
                        setUploadDataProvider(
                            UploadDataProviders.create(params.toBytesArray()),
                            network.getExecutor()
                        )
                    } catch (error: Throwable) {
                        c.resumeWithException(error)
                    }
                }
            }
        }

        when (method) {
            Method.POST -> addData(params)
            Method.GET -> addData(bodyParams)
            Method.DELETE -> addData(bodyParams)
            Method.PUT -> addData(bodyParams)
        }

        if (!headers.isEmpty()) {
            for ((key, value) in headers.params) {
                builder.addHeader(key, value.toString())
            }
        }

        requests[this@Request.hashCode()] = this

        urlRequest = builder.build()
        urlRequest.start()

        c.invokeOnCancellation {
            requests.remove(this@Request.hashCode())
            urlRequest?.cancel()
        }
    }

    private fun isParamsForUrl() = !params.isEmpty() &&
            (method == Method.GET || method == Method.DELETE || method == Method.PUT)

    override fun toString(): String {
        return "$url $params"
    }

    override fun hashCode() = url.hashCode() + params.hashCode() + bodyParams.hashCode()

    companion object {
        enum class Method {
            GET,
            POST,
            DELETE,
            PUT,
            HEAD
        }

        const val MAX_REQUEST_ATTEMPTS = 3
        private val requests = ConcurrentHashMap<Int, Request<*>>()
        private @Volatile var id = 0L
    }

}