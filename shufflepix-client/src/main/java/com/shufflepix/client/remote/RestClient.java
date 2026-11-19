package com.shufflepix.client.remote;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shufflepix.client.config.ClientConfig;
import com.shufflepix.common.dto.ErrorResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class RestClient {
    private final URI baseUrl;
    private final Duration requestTimeout;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public RestClient(ClientConfig config) {
        this.baseUrl = config.getServerUrl();
        this.requestTimeout = config.getRequestTimeout();
        this.http = HttpClient.newBuilder()
                .connectTimeout(config.getConnectTimeout())
                .build();
    }

    public <T> T getJson(String path, TypeReference<T> type) {
        HttpResponse<byte[]> response = send(request(path).GET().build());
        return read(response.body(), type);
    }

    public byte[] getBytes(String path) {
        return send(request(path).GET().build()).body();
    }

    public <T> T sendJson(String method, String path, Object body, Class<T> type) {
        HttpRequest req = request(path)
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofByteArray(write(body)))
                .build();
        return read(send(req).body(), json.getTypeFactory().constructType(type));
    }

    public void delete(String path) {
        send(request(path).DELETE().build());
    }

    public <T> T sendMultipart(String method, String path, Object projectPart,
                               Map<String, byte[]> pngParts, Class<T> type) {
        throw new UnsupportedOperationException("RestClient.sendMultipart");
    }

    public static String path(String template, Object... values) {
        String result = template;
        for (Object value : values) {
            result = result.replaceFirst("\\{[^}]+}", String.valueOf(value));
        }
        return result;
    }

    HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(baseUrl.resolve(path)).timeout(requestTimeout);
    }

    HttpResponse<byte[]> send(HttpRequest request) {
        HttpResponse<byte[]> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new ServerUnavailableException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServerUnavailableException(e);
        }
        if (response.statusCode() >= 400) {
            throw toException(response);
        }
        return response;
    }

    byte[] write(Object value) {
        try {
            return json.writeValueAsBytes(value);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T read(byte[] body, TypeReference<T> type) {
        return read(body, json.getTypeFactory().constructType(type));
    }

    private <T> T read(byte[] body, JavaType type) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            return json.readValue(body, type);
        } catch (IOException e) {
            throw new RemoteException(0, "BAD_RESPONSE", "Некоректна відповідь сервера");
        }
    }

    private RemoteException toException(HttpResponse<byte[]> response) {
        try {
            ErrorResponse error = json.readValue(response.body(), ErrorResponse.class);
            return new RemoteException(response.statusCode(), error.code(), error.message());
        } catch (IOException | RuntimeException e) {
            return new RemoteException(response.statusCode(), "HTTP_" + response.statusCode(),
                    "Помилка сервера (код " + response.statusCode() + ")");
        }
    }
}
