package com.shufflepix.client.remote;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shufflepix.common.api.ApiPaths;
import com.shufflepix.common.dto.EffectDto;
import com.shufflepix.common.dto.ErrorResponse;
import com.shufflepix.common.dto.ProjectDto;
import com.shufflepix.common.dto.RenameProjectRequest;
import com.shufflepix.common.dto.SaveLayerRequest;
import com.shufflepix.common.dto.SaveProjectRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestClientTest {
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private HttpServer server;
    private RestClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        URI baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        client = new RestClient(baseUrl, Duration.ofSeconds(2), Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsValidMultipartFormDataWithJsonAndMultipleBinaryPngParts() {
        AtomicReference<String> contentTypeHeader = new AtomicReference<>();
        AtomicReference<String> requestMethod = new AtomicReference<>();
        AtomicReference<byte[]> rawBody = new AtomicReference<>();

        UUID createdId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-25T14:00:00Z");

        server.createContext(ApiPaths.PROJECTS, exchange -> {
            requestMethod.set(exchange.getRequestMethod());
            contentTypeHeader.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            rawBody.set(exchange.getRequestBody().readAllBytes());
            respondJson(exchange, 201, new ProjectDto(
                    createdId, "Морський пейзаж", 64, 48, "#FFFFFFFF", 0L, now, now));
        });

        byte[] png0 = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, (byte) 0xFF, 0x00};
        byte[] png1 = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 9, 8, 7, 6};
        Map<String, byte[]> parts = new LinkedHashMap<>();
        parts.put("layer-0", png0);
        parts.put("layer-1", png1);

        SaveProjectRequest request = new SaveProjectRequest(
                "Морський пейзаж",
                64,
                48,
                "#FFFFFFFF",
                null,
                List.of(
                        new SaveLayerRequest(null, "Фон", 0, true, 1.0,
                                List.of(new EffectDto("BLUR", Map.of("radius", 5))), "layer-0"),
                        new SaveLayerRequest(null, "Підпис", 1, false, 0.75, List.of(), "layer-1")
                ));

        ProjectDto response = client.sendMultipart("POST", ApiPaths.PROJECTS, request, parts, ProjectDto.class);
        assertEquals("POST", requestMethod.get());
        assertEquals(createdId, response.id());
        assertEquals("Морський пейзаж", response.name());
        assertEquals(0L, response.version());

        String ct = contentTypeHeader.get();
        assertNotNull(ct);
        assertTrue(ct.startsWith("multipart/form-data; boundary="), "Некоректний Content-Type: " + ct);
        String boundary = ct.substring("multipart/form-data; boundary=".length());

        Map<String, ParsedPart> parsed = parseMultipart(rawBody.get(), boundary);
        assertEquals(3, parsed.size());

        ParsedPart projectPart = parsed.get(ApiPaths.PART_PROJECT);
        assertNotNull(projectPart);
        assertEquals("project.json", projectPart.fileName());
        assertTrue(projectPart.contentType().startsWith("application/json"));
        SaveProjectRequest decodedReq = readJson(projectPart.body(), SaveProjectRequest.class);
        assertEquals("Морський пейзаж", decodedReq.name());
        assertEquals(2, decodedReq.layers().size());
        assertEquals("Фон", decodedReq.layers().get(0).name());

        ParsedPart layer0Part = parsed.get("layer-0");
        assertNotNull(layer0Part);
        assertEquals("layer-0.png", layer0Part.fileName());
        assertEquals(ApiPaths.IMAGE_PNG, layer0Part.contentType());
        assertArrayEquals(png0, layer0Part.body());

        ParsedPart layer1Part = parsed.get("layer-1");
        assertNotNull(layer1Part);
        assertEquals("layer-1.png", layer1Part.fileName());
        assertEquals(ApiPaths.IMAGE_PNG, layer1Part.contentType());
        assertArrayEquals(png1, layer1Part.body());
    }

    @Test
    void sendsPutMultipartWithoutBinaryPartsWhenNoLayerPixelsChanged() {
        UUID projectId = UUID.randomUUID();
        UUID layerId = UUID.randomUUID();
        AtomicReference<byte[]> rawBody = new AtomicReference<>();
        AtomicReference<String> contentTypeHeader = new AtomicReference<>();

        server.createContext("/api/projects/" + projectId, exchange -> {
            assertEquals("PUT", exchange.getRequestMethod());
            contentTypeHeader.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            rawBody.set(exchange.getRequestBody().readAllBytes());
            Instant now = Instant.now();
            respondJson(exchange, 200, new ProjectDto(projectId, "Оновлений", 32, 32, "#000000FF", 2L, now, now));
        });

        SaveProjectRequest update = new SaveProjectRequest(
                "Оновлений", 32, 32, "#000000FF", 1L,
                List.of(new SaveLayerRequest(layerId, "Фон", 0, true, 0.5, List.of(), null)));

        ProjectDto dto = client.sendMultipart("PUT", RestClient.path(ApiPaths.PROJECT, projectId),
                update, Map.of(), ProjectDto.class);
        assertEquals(2L, dto.version());

        String boundary = contentTypeHeader.get().substring("multipart/form-data; boundary=".length());
        Map<String, ParsedPart> parsed = parseMultipart(rawBody.get(), boundary);
        assertEquals(1, parsed.size());
        assertTrue(parsed.containsKey(ApiPaths.PART_PROJECT));
    }

    @Test
    void mapsErrorResponsesAndConnectionFailuresCorrectly() throws IOException {
        UUID id = UUID.randomUUID();
        server.createContext("/api/projects/" + id, exchange -> {
            if ("PUT".equals(exchange.getRequestMethod())) {
                respondJson(exchange, 409, new ErrorResponse(409, "VERSION_CONFLICT",
                        "Проєкт змінено з іншого робочого місця."));
            } else if ("PATCH".equals(exchange.getRequestMethod())) {
                RenameProjectRequest body = readJson(exchange.getRequestBody().readAllBytes(), RenameProjectRequest.class);
                respondJson(exchange, 409, new ErrorResponse(409, "NAME_CONFLICT",
                        "Назва зайнята: " + body.name()));
            } else {
                respondJson(exchange, 404, new ErrorResponse(404, "NOT_FOUND", "Проєкт не знайдено"));
            }
        });

        RemoteException notFound = assertThrows(RemoteException.class,
                () -> client.getJson(RestClient.path(ApiPaths.PROJECT, id), new TypeReference<ProjectDto>() { }));
        assertTrue(notFound.isNotFound());
        assertEquals("NOT_FOUND", notFound.getCode());

        SaveProjectRequest dummy = new SaveProjectRequest("Назва", 10, 10, "#FFFFFFFF", 0L, List.of());
        RemoteException conflict = assertThrows(RemoteException.class,
                () -> client.sendMultipart("PUT", RestClient.path(ApiPaths.PROJECT, id), dummy, Map.of(), ProjectDto.class));
        assertTrue(conflict.isVersionConflict());

        RemoteException nameConflict = assertThrows(RemoteException.class,
                () -> client.sendJson("PATCH", RestClient.path(ApiPaths.PROJECT, id),
                        new RenameProjectRequest("Дублікат"), ProjectDto.class));
        assertEquals(409, nameConflict.getStatus());
        assertEquals("NAME_CONFLICT", nameConflict.getCode());

        // Unreachable port throws ServerUnavailableException
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        RestClient offline = new RestClient(URI.create("http://127.0.0.1:" + closedPort),
                Duration.ofMillis(300), Duration.ofMillis(500));
        assertThrows(ServerUnavailableException.class,
                () -> offline.getJson(ApiPaths.PROJECTS, new TypeReference<List<Object>>() { }));
    }

    private void respondJson(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] bytes = json.writeValueAsBytes(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private <T> T readJson(byte[] bytes, Class<T> type) {
        try {
            return json.readValue(bytes, type);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static Map<String, ParsedPart> parseMultipart(byte[] body, String boundary) {
        Map<String, ParsedPart> parts = new LinkedHashMap<>();
        byte[] delimiter = ("--" + boundary).getBytes(StandardCharsets.US_ASCII);
        byte[] headerSep = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

        int pos = indexOf(body, delimiter, 0);
        assertTrue(pos >= 0, "Не знайдено початкову межу multipart");
        while (pos >= 0) {
            pos += delimiter.length;
            if (pos + 1 < body.length && body[pos] == '-' && body[pos + 1] == '-') {
                break;
            }
            assertEquals('\r', body[pos]);
            assertEquals('\n', body[pos + 1]);
            pos += 2;

            int headerEnd = indexOf(body, headerSep, pos);
            assertTrue(headerEnd > pos, "Не знайдено кінець заголовків частини");
            String headersText = new String(body, pos, headerEnd - pos, StandardCharsets.UTF_8);

            int dataStart = headerEnd + headerSep.length;
            int nextBoundary = indexOf(body, delimiter, dataStart);
            assertTrue(nextBoundary >= dataStart + 2, "Не знайдено наступну межу multipart");
            int dataEnd = nextBoundary - 2; // strip trailing CRLF before --boundary
            assertEquals('\r', body[dataEnd]);
            assertEquals('\n', body[dataEnd + 1]);

            byte[] content = new byte[dataEnd - dataStart];
            System.arraycopy(body, dataStart, content, 0, content.length);

            String name = extractAttribute(headersText, "name=\"");
            String fileName = extractAttribute(headersText, "filename=\"");
            String contentType = null;
            for (String line : headersText.split("\r\n")) {
                if (line.toLowerCase().startsWith("content-type:")) {
                    contentType = line.substring("content-type:".length()).strip();
                }
            }
            parts.put(name, new ParsedPart(name, fileName, contentType, content));
            pos = nextBoundary;
        }
        return parts;
    }

    private static String extractAttribute(String headers, String prefix) {
        int start = headers.indexOf(prefix);
        if (start < 0) {
            return null;
        }
        start += prefix.length();
        int end = headers.indexOf('"', start);
        return headers.substring(start, end);
    }

    private static int indexOf(byte[] haystack, byte[] needle, int fromIndex) {
        outer:
        for (int i = fromIndex; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    record ParsedPart(String name, String fileName, String contentType, byte[] body) {
    }
}
