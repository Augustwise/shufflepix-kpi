package com.shufflepix.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shufflepix.common.api.ApiPaths;
import com.shufflepix.common.dto.EffectDto;
import com.shufflepix.common.dto.LayerDto;
import com.shufflepix.common.dto.ProjectDto;
import com.shufflepix.common.dto.SaveLayerRequest;
import com.shufflepix.common.dto.SaveProjectRequest;
import com.shufflepix.common.image.PixelEffects;
import com.shufflepix.server.repository.LayerContentRepository;
import com.shufflepix.server.repository.ProjectRepository;
import com.shufflepix.server.repository.ProjectThumbnailRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectSaveAndThumbnailIntegrationTest {
    private static final EmbeddedPostgres POSTGRES = start();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private ProjectRepository projects;

    @Autowired
    private LayerContentRepository layerContents;

    @Autowired
    private ProjectThumbnailRepository thumbnails;

    private static EmbeddedPostgres start() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new IllegalStateException("Не вдалося запустити вбудований PostgreSQL", e);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @AfterAll
    static void stop() throws IOException {
        POSTGRES.close();
    }

    @BeforeEach
    void cleanDatabase() {
        projects.deleteAll();
    }

    @Test
    void createsProjectSavesLayersEffectsAndGeneratesThumbnailWithEffectsAndOpacity() throws Exception {
        byte[] redLayerPng = solidPng(400, 200, new Color(255, 0, 0, 255));
        byte[] blueOverlayPng = solidPng(400, 200, new Color(0, 0, 255, 255));
        byte[] hiddenGreenPng = solidPng(400, 200, new Color(0, 255, 0, 255));

        SaveProjectRequest request = new SaveProjectRequest(
                "  Морський пейзаж  ",
                400,
                200,
                "#FFFFFFFF",
                null,
                List.of(
                        new SaveLayerRequest(null, "  Фон ", 0, true, 1.0,
                                List.of(new EffectDto(PixelEffects.CODE_NEGATIVE, Map.of())),
                                "layer-0"),
                        new SaveLayerRequest(null, "Накладка", 1, true, 0.5,
                                List.of(new EffectDto(PixelEffects.CODE_BLUR, Map.of(PixelEffects.PARAM_RADIUS, 3))),
                                "layer-1"),
                        new SaveLayerRequest(null, "Прихований", 2, false, 1.0,
                                List.of(),
                                "layer-2")
                ));

        MvcResult createResult = mvc.perform(multipartProject(HttpMethod.POST, ApiPaths.PROJECTS, request,
                        Map.of("layer-0", redLayerPng, "layer-1", blueOverlayPng, "layer-2", hiddenGreenPng)))
                .andExpect(status().isCreated())
                .andReturn();

        ProjectDto created = json.readValue(createResult.getResponse().getContentAsByteArray(), ProjectDto.class);
        assertNotNull(created.id());
        assertEquals("Морський пейзаж", created.name());
        assertEquals(400, created.width());
        assertEquals(200, created.height());
        assertEquals("#FFFFFFFF", created.backgroundColor());
        assertEquals(0L, created.version());
        assertFalse(created.updatedAt().isBefore(created.createdAt()));

        // Catalog returns created project
        mvc.perform(get(ApiPaths.PROJECTS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Морський пейзаж"));

        // Layers are persisted in order with trimmed names and effects
        List<LayerDto> layers = fetchLayers(created.id());
        assertEquals(3, layers.size());
        assertEquals("Фон", layers.get(0).name());
        assertEquals(0, layers.get(0).position());
        assertEquals(List.of(new EffectDto(PixelEffects.CODE_NEGATIVE, Map.of())), layers.get(0).effects());

        assertEquals("Накладка", layers.get(1).name());
        assertEquals(0.5, layers.get(1).opacity());
        assertEquals(PixelEffects.CODE_BLUR, layers.get(1).effects().get(0).effectCode());
        assertEquals(3, ((Number) layers.get(1).effects().get(0).parameters().get("radius")).intValue());

        assertFalse(layers.get(2).visible());

        // Layer contents match exact uploaded PNGs
        byte[] storedLayer0 = mvc.perform(get("/api/layers/{id}/content", layers.get(0).id()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(ApiPaths.IMAGE_PNG))
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(redLayerPng, storedLayer0);

        // Thumbnail was generated after commit: 400x200 -> 256x128
        byte[] thumbBytes = mvc.perform(get("/api/projects/{id}/thumbnail", created.id()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(ApiPaths.IMAGE_PNG))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(thumbBytes));
        assertNotNull(thumb);
        assertEquals(256, thumb.getWidth());
        assertEquals(128, thumb.getHeight());

        // Bottom red (255,0,0) -> NEGATIVE -> cyan (0,255,255)
        // Top blue (0,0,255) at 50% opacity blended over cyan -> (0, 128, 255). Hidden green is excluded.
        int pixel = thumb.getRGB(128, 64);
        assertEquals(255, alpha(pixel));
        assertEquals(0, red(pixel));
        assertTrue(Math.abs(green(pixel) - 128) <= 2, "Очікувалося G≈128, отримано " + green(pixel));
        assertEquals(255, blue(pixel));
    }

    @Test
    void savesProjectSwappingPositionsPreservingUnchangedContentUpdatingEffectsAndThumbnail() throws Exception {
        byte[] redPng = solidPng(64, 64, new Color(255, 0, 0, 255));
        byte[] bluePng = solidPng(64, 64, new Color(0, 0, 255, 255));
        byte[] greenPng = solidPng(64, 64, new Color(0, 255, 0, 255));
        byte[] yellowPng = solidPng(64, 64, new Color(255, 255, 0, 255));

        ProjectDto created = createSampleProject("Багатошаровий", 64, 64,
                List.of(
                        new SaveLayerRequest(null, "Червоний", 0, true, 1.0,
                                List.of(new EffectDto(PixelEffects.CODE_GRAYSCALE, Map.of())), "p0"),
                        new SaveLayerRequest(null, "Синій", 1, true, 1.0, List.of(), "p1"),
                        new SaveLayerRequest(null, "Зайвий", 2, false, 1.0, List.of(), "p2")
                ),
                Map.of("p0", redPng, "p1", bluePng, "p2", greenPng));

        List<LayerDto> initialLayers = fetchLayers(created.id());
        UUID redId = initialLayers.get(0).id();
        UUID blueId = initialLayers.get(1).id();
        UUID deletedId = initialLayers.get(2).id();
        Instant redContentUpdatedBefore = layerContents.findById(redId).orElseThrow().getUpdatedAt();

        // Update:
        // - swap Red (0 -> 1, unchanged contentPart=null, replace effects GRAYSCALE -> SEPIA)
        // - swap Blue (1 -> 0, changed content -> yellowPng)
        // - delete "Зайвий" (position 2)
        // - add new layer "Новий" at position 2 (hidden)
        SaveProjectRequest update = new SaveProjectRequest(
                "Багатошаровий",
                64,
                64,
                "#FFFFFFFF",
                created.version(),
                List.of(
                        new SaveLayerRequest(blueId, "Жовтий низ", 0, true, 1.0, List.of(), "new-blue"),
                        new SaveLayerRequest(redId, "Червоний верх", 1, true, 1.0,
                                List.of(new EffectDto(PixelEffects.CODE_SEPIA, Map.of())), null),
                        new SaveLayerRequest(null, "Новий", 2, false, 0.8,
                                List.of(new EffectDto(PixelEffects.CODE_INFRARED, Map.of())), "new-layer")
                ));

        MvcResult saveResult = mvc.perform(multipartProject(HttpMethod.PUT, "/api/projects/" + created.id(), update,
                        Map.of("new-blue", yellowPng, "new-layer", greenPng)))
                .andExpect(status().isOk())
                .andReturn();

        ProjectDto updated = json.readValue(saveResult.getResponse().getContentAsByteArray(), ProjectDto.class);
        assertEquals(1L, updated.version());
        assertTrue(updated.updatedAt().isAfter(created.updatedAt()));

        List<LayerDto> afterLayers = fetchLayers(created.id());
        assertEquals(3, afterLayers.size());
        assertEquals(blueId, afterLayers.get(0).id());
        assertEquals("Жовтий низ", afterLayers.get(0).name());
        assertEquals(0, afterLayers.get(0).position());

        assertEquals(redId, afterLayers.get(1).id());
        assertEquals("Червоний верх", afterLayers.get(1).name());
        assertEquals(1, afterLayers.get(1).position());
        assertEquals(List.of(new EffectDto(PixelEffects.CODE_SEPIA, Map.of())), afterLayers.get(1).effects());

        assertEquals("Новий", afterLayers.get(2).name());
        assertEquals(2, afterLayers.get(2).position());
        assertEquals(List.of(new EffectDto(PixelEffects.CODE_INFRARED, Map.of())), afterLayers.get(2).effects());

        // Unchanged layer content was NOT rewritten
        assertEquals(redContentUpdatedBefore, layerContents.findById(redId).orElseThrow().getUpdatedAt());
        assertArrayEquals(redPng, layerContents.findById(redId).orElseThrow().getImagePng());

        // Updated layer content reflects new bytes
        assertArrayEquals(yellowPng, layerContents.findById(blueId).orElseThrow().getImagePng());

        // Deleted layer cascaded its content away
        mvc.perform(get("/api/layers/{id}/content", deletedId))
                .andExpect(status().isNotFound());

        // Thumbnail was updated: top visible layer is Red with Sepia applied -> warm tone (R > G > B)
        byte[] thumbBytes = mvc.perform(get("/api/projects/{id}/thumbnail", created.id()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(thumbBytes));
        int pixel = thumb.getRGB(32, 32);
        int expectedSepiaRed = PixelEffects.sepiaPixel(new Color(255, 0, 0, 255).getRGB());
        assertEquals(expectedSepiaRed, pixel);

        // Deleting project cascades thumbnail deletion
        mvc.perform(delete("/api/projects/{id}", created.id()))
                .andExpect(status().isNoContent());
        assertFalse(thumbnails.existsById(created.id()));
    }

    @Test
    void enforcesOptimisticLockingAndReturns409OnVersionConflict() throws Exception {
        byte[] png = solidPng(32, 32, Color.BLUE);
        ProjectDto v0 = createSampleProject("Конкурентний", 32, 32,
                List.of(new SaveLayerRequest(null, "Фон", 0, true, 1.0, List.of(), "p0")),
                Map.of("p0", png));
        UUID layerId = fetchLayers(v0.id()).get(0).id();

        // First client saves v0 -> becomes v1
        SaveProjectRequest save1 = new SaveProjectRequest("Конкурентний", 32, 32, "#FFFFFFFF", 0L,
                List.of(new SaveLayerRequest(layerId, "Фон v1", 0, true, 1.0, List.of(), null)));
        MvcResult r1 = mvc.perform(multipartProject(HttpMethod.PUT, "/api/projects/" + v0.id(), save1, Map.of()))
                .andExpect(status().isOk())
                .andReturn();
        ProjectDto v1 = json.readValue(r1.getResponse().getContentAsByteArray(), ProjectDto.class);
        assertEquals(1L, v1.version());

        // Second client attempts save with stale version 0 -> 409 VERSION_CONFLICT
        SaveProjectRequest staleSave = new SaveProjectRequest("Застарілий", 32, 32, "#FFFFFFFF", 0L,
                List.of(new SaveLayerRequest(layerId, "Перетерти", 0, true, 1.0, List.of(), null)));
        mvc.perform(multipartProject(HttpMethod.PUT, "/api/projects/" + v0.id(), staleSave, Map.of()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        // Project remains at v1 with "Фон v1"
        mvc.perform(get("/api/projects/{id}", v0.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Конкурентний"))
                .andExpect(jsonPath("$.version").value(1));
        assertEquals("Фон v1", fetchLayers(v0.id()).get(0).name());

        // Missing version on PUT returns 400
        SaveProjectRequest noVersion = new SaveProjectRequest("Без версії", 32, 32, "#FFFFFFFF", null,
                List.of(new SaveLayerRequest(layerId, "Фон", 0, true, 1.0, List.of(), null)));
        mvc.perform(multipartProject(HttpMethod.PUT, "/api/projects/" + v0.id(), noVersion, Map.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rollsBackEntireTransactionWhenLayerSaveFails() throws Exception {
        byte[] redPng = solidPng(32, 32, Color.RED);
        byte[] bluePng = solidPng(32, 32, Color.BLUE);

        // 1) Failed create (unknown effect code) leaves no project and no thumbnail in DB
        SaveProjectRequest badCreate = new SaveProjectRequest("Незбережений", 32, 32, "#FFFFFFFF", null,
                List.of(new SaveLayerRequest(null, "Фон", 0, true, 1.0,
                        List.of(new EffectDto("NON_EXISTENT", Map.of())), "p0")));
        mvc.perform(multipartProject(HttpMethod.POST, ApiPaths.PROJECTS, badCreate, Map.of("p0", redPng)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertEquals(0, projects.count());
        assertEquals(0, thumbnails.count());

        // 2) Failed update rolls back project metadata, version, layers, contents and thumbnail
        ProjectDto created = createSampleProject("Атомарний", 32, 32,
                List.of(new SaveLayerRequest(null, "Оригінал", 0, true, 1.0, List.of(), "p0")),
                Map.of("p0", redPng));
        UUID layerId = fetchLayers(created.id()).get(0).id();
        byte[] initialThumb = thumbnails.findById(created.id()).orElseThrow().getImagePng();

        SaveProjectRequest badUpdate = new SaveProjectRequest("Змінена назва", 32, 32, "#000000FF", 0L,
                List.of(
                        new SaveLayerRequest(layerId, "Змінений шар", 0, true, 0.5, List.of(), "p0"),
                        new SaveLayerRequest(null, "Битий ефект", 1, true, 1.0,
                                List.of(new EffectDto("UNKNOWN_CODE", Map.of())), "p1")
                ));
        mvc.perform(multipartProject(HttpMethod.PUT, "/api/projects/" + created.id(), badUpdate,
                        Map.of("p0", bluePng, "p1", bluePng)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        // Everything must be untouched (NFR-05)
        mvc.perform(get("/api/projects/{id}", created.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Атомарний"))
                .andExpect(jsonPath("$.backgroundColor").value("#FFFFFFFF"))
                .andExpect(jsonPath("$.version").value(0));

        List<LayerDto> layersAfterRollback = fetchLayers(created.id());
        assertEquals(1, layersAfterRollback.size());
        assertEquals("Оригінал", layersAfterRollback.get(0).name());
        assertArrayEquals(redPng, layerContents.findById(layerId).orElseThrow().getImagePng());
        assertArrayEquals(initialThumb, thumbnails.findById(created.id()).orElseThrow().getImagePng());
    }

    @Test
    void enforcesCaseInsensitiveUniqueProjectNames() throws Exception {
        byte[] png = solidPng(16, 16, Color.WHITE);
        ProjectDto p1 = createSampleProject("Логотип", 16, 16,
                List.of(new SaveLayerRequest(null, "Фон", 0, true, 1.0, List.of(), "p0")),
                Map.of("p0", png));

        // Creating another project with different case -> 409 NAME_CONFLICT
        SaveProjectRequest dupCreate = new SaveProjectRequest("логотип", 16, 16, "#FFFFFFFF", null,
                List.of(new SaveLayerRequest(null, "Фон", 0, true, 1.0, List.of(), "p0")));
        mvc.perform(multipartProject(HttpMethod.POST, ApiPaths.PROJECTS, dupCreate, Map.of("p0", png)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NAME_CONFLICT"));

        ProjectDto p2 = createSampleProject("Банер", 16, 16,
                List.of(new SaveLayerRequest(null, "Фон", 0, true, 1.0, List.of(), "p0")),
                Map.of("p0", png));
        UUID p2Layer = fetchLayers(p2.id()).get(0).id();

        // Saving p2 under p1's name -> 409 NAME_CONFLICT
        SaveProjectRequest dupUpdate = new SaveProjectRequest("ЛОГОТИП", 16, 16, "#FFFFFFFF", 0L,
                List.of(new SaveLayerRequest(p2Layer, "Фон", 0, true, 1.0, List.of(), null)));
        mvc.perform(multipartProject(HttpMethod.PUT, "/api/projects/" + p2.id(), dupUpdate, Map.of()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NAME_CONFLICT"));

        // Saving p1 changing only case of its own name -> succeeds
        UUID p1Layer = fetchLayers(p1.id()).get(0).id();
        SaveProjectRequest selfCaseChange = new SaveProjectRequest("ЛОГОТИП", 16, 16, "#FFFFFFFF", 0L,
                List.of(new SaveLayerRequest(p1Layer, "Фон", 0, true, 1.0, List.of(), null)));
        mvc.perform(multipartProject(HttpMethod.PUT, "/api/projects/" + p1.id(), selfCaseChange, Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ЛОГОТИП"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void validatesInputAndPngIntegrityStrictly() throws Exception {
        byte[] valid32 = solidPng(32, 32, Color.RED);
        byte[] valid16 = solidPng(16, 16, Color.RED);
        byte[] jpegBytes = solidJpeg(32, 32, Color.RED);
        byte[] garbage = "це не png".getBytes(StandardCharsets.UTF_8);
        byte[] truncated = Arrays.copyOf(valid32, valid32.length / 2);
        byte[] oversizedHeader = createOversizedPngHeader(9000, 9000);

        // Non-PNG garbage
        assertBadCreate(new SaveProjectRequest("T1", 32, 32, "#FFFFFFFF", null,
                List.of(new SaveLayerRequest(null, "L", 0, true, 1.0, List.of(), "p0"))), Map.of("p0", garbage));

        // JPEG disguised as PNG
        assertBadCreate(new SaveProjectRequest("T2", 32, 32, "#FFFFFFFF", null,
                List.of(new SaveLayerRequest(null, "L", 0, true, 1.0, List.of(), "p0"))), Map.of("p0", jpegBytes));

        // Truncated PNG
        assertBadCreate(new SaveProjectRequest("T3", 32, 32, "#FFFFFFFF", null,
                List.of(new SaveLayerRequest(null, "L", 0, true, 1.0, List.of(), "p0"))), Map.of("p0", truncated));

        // Dimension mismatch (16x16 PNG on 32x32 canvas)
        assertBadCreate(new SaveProjectRequest("T4", 32, 32, "#FFFFFFFF", null,
                List.of(new SaveLayerRequest(null, "L", 0, true, 1.0, List.of(), "p0"))), Map.of("p0", valid16));

        // Oversized PNG header (> 8192) rejected safely without OOM
        assertBadCreate(new SaveProjectRequest("T5", 32, 32, "#FFFFFFFF", null,
                List.of(new SaveLayerRequest(null, "L", 0, true, 1.0, List.of(), "p0"))), Map.of("p0", oversizedHeader));

        // Duplicate layer positions
        assertBadCreate(new SaveProjectRequest("T6", 32, 32, "#FFFFFFFF", null,
                List.of(
                        new SaveLayerRequest(null, "L1", 0, true, 1.0, List.of(), "p0"),
                        new SaveLayerRequest(null, "L2", 0, true, 1.0, List.of(), "p1")
                )), Map.of("p0", valid32, "p1", valid32));

        // Negative layer position
        assertBadCreate(new SaveProjectRequest("T7", 32, 32, "#FFFFFFFF", null,
                List.of(new SaveLayerRequest(null, "L1", -1, true, 1.0, List.of(), "p0"))), Map.of("p0", valid32));

        // Invalid blur radius
        assertBadCreate(new SaveProjectRequest("T8", 32, 32, "#FFFFFFFF", null,
                List.of(new SaveLayerRequest(null, "L1", 0, true, 1.0,
                        List.of(new EffectDto(PixelEffects.CODE_BLUR, Map.of("radius", 0))), "p0"))),
                Map.of("p0", valid32));

        // Layer ID belonging to another project rejected on PUT
        ProjectDto pA = createSampleProject("ProjectA", 32, 32,
                List.of(new SaveLayerRequest(null, "LA", 0, true, 1.0, List.of(), "p0")), Map.of("p0", valid32));
        ProjectDto pB = createSampleProject("ProjectB", 32, 32,
                List.of(new SaveLayerRequest(null, "LB", 0, true, 1.0, List.of(), "p0")), Map.of("p0", valid32));
        UUID layerOfA = fetchLayers(pA.id()).get(0).id();

        SaveProjectRequest crossProjectLayer = new SaveProjectRequest("ProjectB", 32, 32, "#FFFFFFFF", 0L,
                List.of(new SaveLayerRequest(layerOfA, "Stolen", 0, true, 1.0, List.of(), null)));
        mvc.perform(multipartProject(HttpMethod.PUT, "/api/projects/" + pB.id(), crossProjectLayer, Map.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void generatesValidTransparentThumbnailWhenAllLayersHidden() throws Exception {
        byte[] redPng = solidPng(80, 40, Color.RED);
        ProjectDto created = createSampleProject("Усі приховані", 80, 40,
                List.of(new SaveLayerRequest(null, "Фон", 0, false, 1.0, List.of(), "p0")),
                Map.of("p0", redPng));

        byte[] thumbBytes = mvc.perform(get("/api/projects/{id}/thumbnail", created.id()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(thumbBytes));
        assertEquals(80, thumb.getWidth());
        assertEquals(40, thumb.getHeight());
        assertEquals(0, alpha(thumb.getRGB(40, 20)));
    }

    private void assertBadCreate(SaveProjectRequest request, Map<String, byte[]> parts) throws Exception {
        mvc.perform(multipartProject(HttpMethod.POST, ApiPaths.PROJECTS, request, parts))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private ProjectDto createSampleProject(String name, int width, int height,
                                           List<SaveLayerRequest> layers, Map<String, byte[]> parts) throws Exception {
        SaveProjectRequest req = new SaveProjectRequest(name, width, height, "#FFFFFFFF", null, layers);
        MvcResult result = mvc.perform(multipartProject(HttpMethod.POST, ApiPaths.PROJECTS, req, parts))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readValue(result.getResponse().getContentAsByteArray(), ProjectDto.class);
    }

    private List<LayerDto> fetchLayers(UUID projectId) throws Exception {
        MvcResult result = mvc.perform(get("/api/projects/{id}/layers", projectId))
                .andExpect(status().isOk())
                .andReturn();
        return json.readValue(result.getResponse().getContentAsByteArray(), new TypeReference<>() { });
    }

    private MockMultipartHttpServletRequestBuilder multipartProject(
            HttpMethod method, String url, SaveProjectRequest project, Map<String, byte[]> parts) throws Exception {
        MockMultipartHttpServletRequestBuilder builder = multipart(method, url);
        builder.file(new MockMultipartFile(
                ApiPaths.PART_PROJECT,
                "project.json",
                MediaType.APPLICATION_JSON_VALUE,
                json.writeValueAsBytes(project)));
        for (Map.Entry<String, byte[]> entry : parts.entrySet()) {
            builder.file(new MockMultipartFile(
                    entry.getKey(),
                    entry.getKey() + ".png",
                    ApiPaths.IMAGE_PNG,
                    entry.getValue()));
        }
        return builder;
    }

    private static byte[] solidPng(int width, int height, Color color) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(color);
            g.fillRect(0, 0, width, height);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static byte[] solidJpeg(int width, int height, Color color) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(color);
            g.fillRect(0, 0, width, height);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    private static byte[] createOversizedPngHeader(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        byte[] ihdrData = ByteBuffer.allocate(13)
                .putInt(width).putInt(height)
                .put((byte) 8).put((byte) 6).put((byte) 0).put((byte) 0).put((byte) 0)
                .array();
        writeChunk(out, "IHDR", ihdrData);
        writeChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.write(ByteBuffer.allocate(4).putInt(data.length).array());
        out.write(typeBytes);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.write(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    private static int red(int argb) {
        return (argb >>> 16) & 0xFF;
    }

    private static int green(int argb) {
        return (argb >>> 8) & 0xFF;
    }

    private static int blue(int argb) {
        return argb & 0xFF;
    }
}
