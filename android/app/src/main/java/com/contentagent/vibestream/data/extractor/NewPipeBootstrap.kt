package com.contentagent.vibestream.data.extractor

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/** One-time NewPipeExtractor setup using the app's OkHttp client. */
object NewPipeBootstrap {
    @Volatile private var ready = false

    fun ensureInit(client: OkHttpClient) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            NewPipe.init(OkDownloader(client))
            ready = true
        }
    }

    private class OkDownloader(private val client: OkHttpClient) : Downloader() {
        override fun execute(request: Request): Response {
            val data = request.dataToSend()
            val body = if (data != null) data.toRequestBody() else null
            val builder = okhttp3.Request.Builder()
                .url(request.url())
                .method(request.httpMethod(), body ?: if (request.httpMethod() == "POST") ByteArray(0).toRequestBody() else null)
                .header("User-Agent", USER_AGENT)
            for ((name, values) in request.headers()) {
                builder.removeHeader(name)
                for (v in values) builder.addHeader(name, v)
            }
            client.newCall(builder.build()).execute().use { r ->
                if (r.code == 429) throw ReCaptchaException("reCaptcha Challenge requested", request.url())
                return Response(
                    r.code,
                    r.message,
                    r.headers.toMultimap(),
                    r.body?.string(),
                    r.request.url.toString()
                )
            }
        }
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"
}
