package com.ximbica.tubelite;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;

public final class HttpDownloader extends Downloader {
    private final OkHttpClient client;

    public HttpDownloader() {
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build();
    }

    @Override
    public Response execute(@Nonnull Request request) throws IOException, ReCaptchaException {
        okhttp3.Request.Builder builder = new okhttp3.Request.Builder().url(request.url());

        boolean hasUserAgent = false;
        for (Map.Entry<String, List<String>> entry : request.headers().entrySet()) {
            if ("User-Agent".equalsIgnoreCase(entry.getKey())) {
                hasUserAgent = true;
            }
            for (String value : entry.getValue()) {
                builder.addHeader(entry.getKey(), value);
            }
        }

        if (!hasUserAgent) {
            builder.header("User-Agent",
                    "Mozilla/5.0 (Linux; Android 8.1.0; SM-G610M) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36");
        }

        final String method = request.httpMethod();
        if ("HEAD".equalsIgnoreCase(method)) {
            builder.head();
        } else if ("POST".equalsIgnoreCase(method)) {
            byte[] bytes = request.dataToSend() == null ? new byte[0] : request.dataToSend();
            RequestBody body = RequestBody.create(bytes, (MediaType) null);
            builder.post(body);
        } else {
            builder.get();
        }

        try (okhttp3.Response response = client.newCall(builder.build()).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            return new Response(
                    response.code(),
                    response.message(),
                    response.headers().toMultimap(),
                    body,
                    response.request().url().toString()
            );
        }
    }
}
