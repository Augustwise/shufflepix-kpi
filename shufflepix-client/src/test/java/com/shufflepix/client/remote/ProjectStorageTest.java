package com.shufflepix.client.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shufflepix.client.core.effect.BlurEffect;
import com.shufflepix.client.core.effect.EffectRegistry;
import com.shufflepix.client.core.effect.GrayscaleEffect;
import com.shufflepix.client.core.effect.NegativeEffect;
import com.shufflepix.client.core.effect.SepiaEffect;
import com.shufflepix.client.core.history.AddLayerCommand;
import com.shufflepix.client.core.history.DrawCommand;
import com.shufflepix.client.core.image.RasterImage;
import com.shufflepix.client.core.model.Layer;
import com.shufflepix.client.core.model.Project;
import com.shufflepix.client.core.shape.CanvasPreviewRenderer;
import com.shufflepix.client.core.shape.RectangleShape;
import com.shufflepix.client.core.style.Color;
import com.shufflepix.client.core.style.Style;
import com.shufflepix.client.core.style.StyleFactory;
import com.shufflepix.client.io.ImageFileService;
import com.shufflepix.common.api.ApiPaths;
import com.shufflepix.common.dto.EffectDto;
import com.shufflepix.common.dto.ErrorResponse;
import com.shufflepix.common.dto.LayerDto;
import com.shufflepix.common.dto.ProjectDto;
import com.shufflepix.common.dto.ProjectSummaryDto;
import com.shufflepix.common.dto.SaveLayerRequest;
import com.shufflepix.common.dto.SaveProjectRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import javafx.scene.image.Image;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectStorageTest {
    private static final Color RED = new Color(255, 0, 0, 255);
    private static final Color GREEN = new Color(0, 200, 0, 255);
    private static final Color BLUE = new Color(0, 80, 220, 255);
    private static final Color CUSTOM_BG = new Color(240, 245, 250, 255);

    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ImageFileService imageFiles = new ImageFileService();
    private final EffectRegistry effects = EffectRegistry.withDefaults();

    private HttpServer server;
    private FakeBackend backend;
    private ProjectStorage storage;

    @BeforeEach
    void setUp() throws IOException {
        backend = new FakeBackend();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        backend.register(server);
        server.start();

        URI baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        RestClient rest = new RestClient(baseUrl, Duration.ofSeconds(2), Duration.ofSeconds(5));
        storage = new ProjectStorage(
                new ProjectServiceProxy(rest),
                new LayerServiceProxy(rest),
                new ThumbnailServiceProxy(rest),
                imageFiles,
                effects);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void savesNewProjectWithNonDestructiveEffectsAndUpdatesOnlyModifiedLayers() {
        Project project = new Project(16, 12, CUSTOM_BG);
        project.setName("Пейзаж");

        Layer bottom = new Layer("Фон", RasterImage.filled(16, 12, RED));
        bottom.applyEffect(effects, NegativeEffect.CODE, Map.of());
        project.addLayer(0, bottom);

        Layer top = new Layer("Деталі", RasterImage.filled(16, 12, BLUE));
        top.setOpacity(0.6);
        top.applyEffect(effects, BlurEffect.CODE, Map.of(BlurEffect.PARAM_RADIUS, 4));
        project.addLayer(1, top);

        Layer hidden = new Layer("Чернетка", RasterImage.filled(16, 12, GREEN));
        hidden.setVisible(false);
        project.addLayer(2, hidden);

        // 1) Initial POST save uploads all 3 layers with raw base pixels (effects NOT baked into PNG)
        ProjectDto created = storage.save(project);
        assertNotNull(created.id());
        assertEquals(created.id(), project.getServerId());
        assertEquals(0L, project.getServerVersion());
        assertFalse(project.isDirty());
        for (Layer l : project.getLayers()) {
            assertNotNull(l.getServerId());
            assertFalse(l.isContentChanged());
        }
        assertEquals(Set.of("layer-0", "layer-1", "layer-2"), backend.lastUploadedPartNames);

        // Verify stored PNG of bottom layer still has raw RED pixels (NegativeEffect stored separately!)
        Image storedBottomRaw = imageFiles.decodePng(backend.layerContents.get(bottom.getServerId()));
        assertEquals(RED.toArgb(), storedBottomRaw.getPixelReader().getArgb(4, 4));

        // 2) Modify ONLY top layer pixels, change bottom layer metadata/effects, remove hidden, duplicate bottom
        project.removeLayer(2); // remove "Чернетка"
        bottom.setOpacity(0.9);
        bottom.removeEffect(0);
        bottom.applyEffect(effects, SepiaEffect.CODE, Map.of());
        assertFalse(bottom.isContentChanged(), "Зміна ефектів і непрозорості не повинна позначати растр зміненим");

        // Draw a green rectangle onto top layer -> marks ONLY top layer contentChanged=true
        Style greenFill = new StyleFactory().get(GREEN, GREEN, 1, "System", 12);
        new DrawCommand(top, new RectangleShape((CanvasPreviewRenderer) null, greenFill, 0, 0, 16, 12)).execute();
        assertTrue(top.isContentChanged());

        // Duplicate bottom layer -> new layer without serverId, must upload its PNG
        project.setActiveIndex(0);
        AddLayerCommand.duplicateActive(project).execute();
        Layer duplicated = project.getLayers().get(1);
        assertNull(duplicated.getServerId());

        // Save update (PUT): bottom (pos 0) must NOT re-upload PNG; duplicated (pos 1) & top (pos 2) MUST upload PNG
        ProjectDto updated = storage.save(project);
        assertEquals(1L, updated.version());
        assertEquals(1L, project.getServerVersion());
        assertFalse(project.isDirty());
        assertEquals(Set.of("layer-1", "layer-2"), backend.lastUploadedPartNames,
                "Незмінений шар (layer-0) не повинен повторно передавати PNG");

        assertNotNull(duplicated.getServerId());
        assertNotEquals(bottom.getServerId(), duplicated.getServerId());
        assertFalse(duplicated.isContentChanged());
        assertFalse(top.isContentChanged());
    }

    @Test
    void opensProjectLazilyViaRemoteImageProxyAndRestoresLayersAndEffectChains() {
        // Prepare a 3-layer project on the server (2 visible, 1 hidden)
        Project source = new Project(20, 14, CUSTOM_BG);
        source.setName("Віддалений проєкт");

        Layer bgLayer = new Layer("Небо", RasterImage.filled(20, 14, RED));
        bgLayer.applyEffect(effects, NegativeEffect.CODE, Map.of());
        source.addLayer(0, bgLayer);

        Layer fgLayer = new Layer("Передній план", RasterImage.filled(20, 14, BLUE));
        fgLayer.setOpacity(0.75);
        fgLayer.applyEffect(effects, GrayscaleEffect.CODE, Map.of());
        source.addLayer(1, fgLayer);

        Layer hiddenLayer = new Layer("Прихований запас", RasterImage.filled(20, 14, GREEN));
        hiddenLayer.setVisible(false);
        hiddenLayer.applyEffect(effects, BlurEffect.CODE, Map.of(BlurEffect.PARAM_RADIUS, 3));
        source.addLayer(2, hiddenLayer);

        ProjectDto saved = storage.save(source);
        backend.contentDownloadCounts.clear();

        // Open from server
        Project opened = storage.open(saved.id());
        assertEquals("Віддалений проєкт", opened.getName());
        assertEquals(20, opened.getWidth());
        assertEquals(14, opened.getHeight());
        assertEquals(CUSTOM_BG, opened.getBackgroundColor());
        assertEquals(saved.id(), opened.getServerId());
        assertEquals(0L, opened.getServerVersion());
        assertFalse(opened.isDirty());
        assertEquals(3, opened.getLayers().size());
        // Topmost visible layer (index 1, since index 2 is hidden) is active
        assertEquals(1, opened.getActiveIndex());

        // Virtual Proxy check: NO layer PNGs have been downloaded yet during open()!
        assertTrue(backend.contentDownloadCounts.isEmpty(),
                "Під час open() растрові дані шарів не повинні завантажуватися");
        for (Layer layer : opened.getLayers()) {
            RemoteImageProxy proxy = assertInstanceOf(RemoteImageProxy.class, layer.getContent());
            assertFalse(proxy.isLoaded());
            assertFalse(layer.isContentChanged());
        }

        // Rendering visible layers triggers download ONLY for visible layers, and caches them
        Layer openedBg = opened.getLayers().get(0);
        Image renderedBg1 = openedBg.getRendered().getImage();
        Image renderedBg2 = openedBg.getRendered().getImage();
        assertSame(renderedBg1, renderedBg2);
        // RED (255,0,0) with NegativeEffect -> CYAN (0,255,255)
        assertEquals(new Color(0, 255, 255, 255).toArgb(), renderedBg1.getPixelReader().getArgb(5, 5));
        assertEquals(1, backend.downloadsFor(openedBg.getServerId()));

        // Hidden layer is STILL unloaded on the client!
        Layer openedHidden = opened.getLayers().get(2);
        RemoteImageProxy hiddenProxy = assertInstanceOf(RemoteImageProxy.class, openedHidden.getContent());
        assertFalse(hiddenProxy.isLoaded());
        assertEquals(0, backend.downloadsFor(openedHidden.getServerId()));

        // Saving opened project without drawing does NOT download hidden layer and uploads 0 PNG parts!
        openedBg.setName("Оновлене небо");
        storage.save(opened);
        assertFalse(hiddenProxy.isLoaded(), "Збереження метаданих не повинно завантажувати прихований шар");
        assertEquals(0, backend.downloadsFor(openedHidden.getServerId()));
        assertTrue(backend.lastUploadedPartNames.isEmpty());

        // Toggling hidden layer visible and rendering loads its PNG on demand
        openedHidden.setVisible(true);
        Image renderedHidden = openedHidden.getRendered().getImage();
        assertTrue(hiddenProxy.isLoaded());
        assertEquals(1, backend.downloadsFor(openedHidden.getServerId()));
        assertEquals(GREEN.toArgb(), renderedHidden.getPixelReader().getArgb(10, 7));
    }

    @Test
    void handlesOptimisticLockVersionConflictAndAllowsSavingDetachedCopyAsNewProject() {
        Project clientA = Project.blank(12, 12, Color.WHITE);
        clientA.setName("Спільний проєкт");
        ProjectDto created = storage.save(clientA);

        // Client B opens the same project at v0 and saves -> server advances to v1
        Project clientB = storage.open(created.id());
        clientB.getActiveLayer().setOpacity(0.5);
        ProjectDto v1 = storage.save(clientB);
        assertEquals(1L, v1.version());

        // Client A still has stale version 0 -> saving throws 409 VERSION_CONFLICT
        clientA.getActiveLayer().setName("Зміна клієнта А");
        RemoteException conflict = assertThrows(RemoteException.class, () -> storage.save(clientA));
        assertTrue(conflict.isVersionConflict());

        // Client A detaches and saves its version as a new project
        clientA.detachFromServer();
        clientA.setName("Спільний проєкт (копія)");
        ProjectDto savedCopy = storage.save(clientA);
        assertNotEquals(created.id(), savedCopy.id());
        assertEquals(0L, savedCopy.version());
        assertEquals(2, storage.catalog().size());
    }

    @Test
    void remoteImageProxyRejectsCorruptedOrMismatchedPngFromServer() {
        UUID badLayerId = UUID.randomUUID();
        UUID wrongSizeLayerId = UUID.randomUUID();
        backend.layerContents.put(badLayerId, "not-a-png".getBytes(StandardCharsets.UTF_8));
        backend.layerContents.put(wrongSizeLayerId,
                imageFiles.encodePng(RasterImage.filled(8, 8, RED).getImage()));

        RestClient rest = new RestClient(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                Duration.ofSeconds(2), Duration.ofSeconds(5));
        LayerService layerProxy = new LayerServiceProxy(rest);

        RemoteImageProxy corruptProxy = new RemoteImageProxy(badLayerId, 16, 16, layerProxy, imageFiles);
        RemoteException corruptEx = assertThrows(RemoteException.class, corruptProxy::getImage);
        assertEquals("BAD_IMAGE", corruptEx.getCode());

        RemoteImageProxy mismatchProxy = new RemoteImageProxy(wrongSizeLayerId, 16, 16, layerProxy, imageFiles);
        RemoteException mismatchEx = assertThrows(RemoteException.class, mismatchProxy::getImage);
        assertEquals("BAD_IMAGE", mismatchEx.getCode());
    }

    private final class FakeBackend {
        final Map<UUID, ProjectDto> projects = new ConcurrentHashMap<>();
        final Map<UUID, List<LayerDto>> projectLayers = new ConcurrentHashMap<>();
        final Map<UUID, byte[]> layerContents = new ConcurrentHashMap<>();
        final Map<UUID, AtomicInteger> contentDownloadCounts = new ConcurrentHashMap<>();
        volatile Set<String> lastUploadedPartNames = Set.of();

        int downloadsFor(UUID layerId) {
            AtomicInteger counter = contentDownloadCounts.get(layerId);
            return counter == null ? 0 : counter.get();
        }

        void register(HttpServer http) {
            http.createContext("/api/projects", this::handleProjects);
            http.createContext("/api/layers/", this::handleLayerContent);
        }

        private void handleProjects(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if (ApiPaths.PROJECTS.equals(path) && "GET".equals(method)) {
                List<ProjectSummaryDto> summaries = projects.values().stream()
                        .sorted(Comparator.comparing(ProjectDto::updatedAt).reversed())
                        .map(p -> new ProjectSummaryDto(p.id(), p.name(), p.width(), p.height(), p.updatedAt()))
                        .toList();
                respondJson(exchange, 200, summaries);
                return;
            }
            if (ApiPaths.PROJECTS.equals(path) && "POST".equals(method)) {
                handleSave(exchange, null);
                return;
            }
            String prefix = ApiPaths.PROJECTS + "/";
            if (path.startsWith(prefix)) {
                String rest = path.substring(prefix.length());
                if (rest.endsWith("/layers") && "GET".equals(method)) {
                    UUID projectId = UUID.fromString(rest.substring(0, rest.length() - "/layers".length()));
                    List<LayerDto> list = projectLayers.get(projectId);
                    if (list == null) {
                        respondJson(exchange, 404, new ErrorResponse(404, "NOT_FOUND", "Проєкт не знайдено"));
                    } else {
                        respondJson(exchange, 200, list);
                    }
                    return;
                }
                UUID projectId = UUID.fromString(rest);
                if ("GET".equals(method)) {
                    ProjectDto dto = projects.get(projectId);
                    if (dto == null) {
                        respondJson(exchange, 404, new ErrorResponse(404, "NOT_FOUND", "Проєкт не знайдено"));
                    } else {
                        respondJson(exchange, 200, dto);
                    }
                    return;
                }
                if ("PUT".equals(method)) {
                    handleSave(exchange, projectId);
                    return;
                }
            }
            respondJson(exchange, 404, new ErrorResponse(404, "NOT_FOUND", "Невідомий шлях: " + path));
        }

        private void handleSave(HttpExchange exchange, UUID existingId) throws IOException {
            String ct = exchange.getRequestHeaders().getFirst("Content-Type");
            String boundary = ct.substring("multipart/form-data; boundary=".length());
            Map<String, RestClientTest.ParsedPart> parts = RestClientTest.parseMultipart(
                    exchange.getRequestBody().readAllBytes(), boundary);

            SaveProjectRequest req = json.readValue(parts.get(ApiPaths.PART_PROJECT).body(), SaveProjectRequest.class);
            Map<String, byte[]> pngParts = new LinkedHashMap<>();
            for (Map.Entry<String, RestClientTest.ParsedPart> e : parts.entrySet()) {
                if (!ApiPaths.PART_PROJECT.equals(e.getKey())) {
                    pngParts.put(e.getKey(), e.getValue().body());
                }
            }
            lastUploadedPartNames = Set.copyOf(pngParts.keySet());

            Instant now = Instant.now();
            UUID projectId;
            long newVersion;
            Instant createdAt;

            if (existingId == null) {
                projectId = UUID.randomUUID();
                newVersion = 0L;
                createdAt = now;
            } else {
                ProjectDto current = projects.get(existingId);
                if (current == null) {
                    respondJson(exchange, 404, new ErrorResponse(404, "NOT_FOUND", "Проєкт не знайдено"));
                    return;
                }
                if (!Long.valueOf(current.version()).equals(req.version())) {
                    respondJson(exchange, 409, new ErrorResponse(409, "VERSION_CONFLICT",
                            "Проєкт змінено з іншого робочого місця."));
                    return;
                }
                projectId = existingId;
                newVersion = current.version() + 1;
                createdAt = current.createdAt();
            }

            List<LayerDto> savedLayers = new ArrayList<>(req.layers().size());
            for (SaveLayerRequest l : req.layers()) {
                UUID layerId = l.id() != null ? l.id() : UUID.randomUUID();
                if (l.contentPart() != null) {
                    byte[] bytes = pngParts.get(l.contentPart());
                    assertNotNull(bytes, "Відсутня частина PNG: " + l.contentPart());
                    layerContents.put(layerId, bytes);
                } else {
                    assertTrue(layerContents.containsKey(layerId),
                            "Шар без contentPart має вже мати збережений вміст: " + layerId);
                }
                savedLayers.add(new LayerDto(layerId, l.name().strip(), l.position(),
                        l.visible(), l.opacity(), l.effects() == null ? List.of() : l.effects()));
            }
            savedLayers.sort(Comparator.comparingInt(LayerDto::position));

            ProjectDto saved = new ProjectDto(projectId, req.name().strip(), req.width(), req.height(),
                    req.backgroundColor(), newVersion, createdAt, now);
            projects.put(projectId, saved);
            projectLayers.put(projectId, savedLayers);
            respondJson(exchange, existingId == null ? 201 : 200, saved);
        }

        private void handleLayerContent(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String idPart = path.substring("/api/layers/".length(), path.length() - "/content".length());
            UUID layerId = UUID.fromString(idPart);
            byte[] bytes = layerContents.get(layerId);
            if (bytes == null) {
                respondJson(exchange, 404, new ErrorResponse(404, "NOT_FOUND", "Вміст шару не знайдено"));
                return;
            }
            contentDownloadCounts.computeIfAbsent(layerId, k -> new AtomicInteger()).incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", ApiPaths.IMAGE_PNG);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }

        private void respondJson(HttpExchange exchange, int status, Object payload) throws IOException {
            byte[] bytes = json.writeValueAsBytes(payload);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }
}
