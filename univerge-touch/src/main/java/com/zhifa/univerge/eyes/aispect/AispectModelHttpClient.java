package com.zhifa.univerge.eyes.aispect;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;

interface AispectModelHttpClient {
    byte[] get(String url, int maxBytes) throws IOException;
    default void post(String url, byte[] body, int maxBytes) throws IOException {
        throw new IOException("post unsupported");
    }
}

final class AispectModelHttpException extends IOException {
    final int statusCode;

    AispectModelHttpException(int statusCode) {
        super("http " + statusCode);
        this.statusCode = statusCode;
    }
}

final class AispectUrlConnectionModelHttpClient implements AispectModelHttpClient {
    private static final int CONNECT_TIMEOUT_MILLIS = 5000;
    private static final int READ_TIMEOUT_MILLIS = 15000;

    @Override
    public byte[] get(String url, int maxBytes) throws IOException {
        throwIfInterrupted();
        if (maxBytes <= 0) {
            throw new IOException("response limit invalid");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setRequestMethod("GET");
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(false);
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new AispectModelHttpException(code);
        }
        int declaredLength = connection.getContentLength();
        if (declaredLength > maxBytes) {
            connection.disconnect();
            throw new IOException("response too large");
        }
        try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                throwIfInterrupted();
                total += read;
                if (total > maxBytes) {
                    throw new IOException("response too large");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    @Override
    public void post(String url, byte[] body, int maxBytes) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setFixedLengthStreamingMode(body.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body);
        }
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new AispectModelHttpException(code);
        }
        connection.disconnect();
    }

    private static void throwIfInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("model update cancelled");
        }
    }
}
