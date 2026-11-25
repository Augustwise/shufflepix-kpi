package com.shufflepix.client.remote;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shufflepix.client.config.ClientConfig;
import com.shufflepix.common.api.ApiPaths;
import com.shufflepix.common.dto.ErrorResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class RestClient {
    private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.US_ASCII);

    private final URI baseUrl;
    private final Duration requestTimeout;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public RestClient(ClientConfig config) {
        this(config.getServerUrl(), config.getConnectTimeout(), config.getRequestTimeout());
    }

    public RestClient(URI baseUrl, Duration connectTimeout, Duration requestTimeout) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.http = HttpClient.newBuilder()
                .connectTimeout(Objects.requireNonNull(connectTimeout, "connectTimeout"))
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
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(projectPart, "projectPart");
        String boundary = "----ShufflePixBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = buildMultipartBody(boundary, write(projectPart), pngParts);
        HttpRequest req = request(path)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return read(send(req).body(), json.getTypeFactory().constructType(type));
    }

    private static byte[] buildMultipartBody(String boundary, byte[] projectJson, Map<String, byte[]> pngParts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(projectJson.length + 512);
        writePart(out, boundary, ApiPaths.PART_PROJECT, "project.json",
                "application/json; charset=UTF-8", projectJson);
        if (pngParts != null) {
            for (Map.Entry<String, byte[]> entry : pngParts.entrySet()) {
                String name = entry.getKey();
                byte[] content = entry.getValue();
                if (name == null || name.isBlank() || content == null) {
                    throw new IllegalArgumentException("Некоректна частина multipart-запиту: " + name);
                }
                String safeName = sanitizeHeaderToken(name);
                writePart(out, boundary, safeName, safeName + ".png", ApiPaths.IMAGE_PNG, content);
            }
        }
        writeAscii(out, "--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    private static void writePart(ByteArrayOutputStream out, String boundary, String name,
                                  String fileName, String contentType, byte[] data) {
        writeAscii(out, "--" + boundary + "\r\n");
        writeAscii(out, "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fileName + "\"\r\n");
        writeAscii(out, "Content-Type: " + contentType + "\r\n\r\n");
        out.writeBytes(data);
        out.writeBytes(CRLF);
    }

    private static void writeAscii(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String sanitizeHeaderToken(String value) {
        return value.replace("\"", "_").replace("\r", "").replace("\n", "");
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
