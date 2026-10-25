package com.shufflepix.server.service;

import com.shufflepix.common.api.Limits;
import com.shufflepix.common.image.PixelEffects;
import com.shufflepix.server.domain.LayerContentEntity;
import com.shufflepix.server.domain.LayerEffectEntity;
import com.shufflepix.server.domain.LayerEntity;
import com.shufflepix.server.domain.ProjectEntity;
import com.shufflepix.server.domain.ProjectThumbnailEntity;
import com.shufflepix.server.repository.LayerContentRepository;
import com.shufflepix.server.repository.LayerRepository;
import com.shufflepix.server.repository.ProjectRepository;
import com.shufflepix.server.repository.ProjectThumbnailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

@Service
public class ThumbnailService {
    private static final Logger log = LoggerFactory.getLogger(ThumbnailService.class);

    private final ProjectRepository projects;
    private final LayerRepository layers;
    private final LayerContentRepository contents;
    private final ProjectThumbnailRepository thumbnails;

    public ThumbnailService(ProjectRepository projects, LayerRepository layers,
                            LayerContentRepository contents, ProjectThumbnailRepository thumbnails) {
        this.projects = projects;
        this.layers = layers;
        this.contents = contents;
        this.thumbnails = thumbnails;
    }

    @Transactional(readOnly = true)
    public byte[] getThumbnail(UUID projectId) {
        return thumbnails.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Мініатюру не знайдено"))
                .getImagePng();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onProjectSaved(ProjectSavedEvent event) {
        try {
            regenerate(event.projectId());
        } catch (RuntimeException e) {
            log.warn("Не вдалося сформувати мініатюру проєкту {}", event.projectId(), e);
        }
    }

    private void regenerate(UUID projectId) {
        ProjectEntity project = projects.findById(projectId).orElse(null);
        if (project == null) {
            return;
        }
        List<LayerEntity> projectLayers = layers.findByProjectIdOrderByPositionAsc(projectId);
        BufferedImage composite = composeVisibleLayers(project, projectLayers);
        BufferedImage thumbnail = scale(composite, Limits.MAX_THUMBNAIL_SIZE);
        byte[] png = encodePng(thumbnail);

        ProjectThumbnailEntity entity = thumbnails.findById(projectId)
                .orElseGet(() -> new ProjectThumbnailEntity(project));
        entity.update(png, thumbnail.getWidth(), thumbnail.getHeight());
        thumbnails.saveAndFlush(entity);
    }

    private BufferedImage composeVisibleLayers(ProjectEntity project, List<LayerEntity> projectLayers) {
        int width = project.getWidth();
        int height = project.getHeight();
        BufferedImage composite = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = composite.createGraphics();
        try {
            for (LayerEntity layer : projectLayers) {
                if (!layer.isVisible()) {
                    continue;
                }
                double opacity = Math.max(0.0, Math.min(1.0, layer.getOpacity()));
                if (opacity <= 0.0) {
                    continue;
                }
                LayerContentEntity content = contents.findById(layer.getId()).orElse(null);
                if (content == null || content.getImagePng() == null) {
                    continue;
                }
                BufferedImage layerImage = decodePng(content.getImagePng());
                if (layerImage == null) {
                    throw new IllegalStateException("Не вдалося декодувати PNG шару " + layer.getId());
                }
                if (!layer.getEffects().isEmpty()) {
                    layerImage = toArgb(layerImage);
                    applyEffects(layerImage, layer.getEffects());
                }
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) opacity));
                g.drawImage(layerImage, 0, 0, null);
            }
        } finally {
            g.dispose();
        }
        return composite;
    }

    private static void applyEffects(BufferedImage argbImage, List<LayerEffectEntity> effects) {
        int width = argbImage.getWidth();
        int height = argbImage.getHeight();
        int[] pixels = ((DataBufferInt) argbImage.getRaster().getDataBuffer()).getData();
        for (LayerEffectEntity effect : effects) {
            String code = effect.getEffectType().getCode();
            boolean applied = PixelEffects.apply(pixels, width, height, code, effect.getParameters());
            if (!applied) {
                log.debug("Ефект {} не підтримується сервісом мініатюр, пропущено", code);
            }
        }
    }

    BufferedImage scale(BufferedImage source, int maxSide) {
        if (source == null) {
            throw new IllegalArgumentException("Зображення для масштабування не може бути null");
        }
        if (maxSide < 1) {
            throw new IllegalArgumentException("Максимальний розмір сторони має бути додатним");
        }
        int srcW = source.getWidth();
        int srcH = source.getHeight();
        if (srcW <= maxSide && srcH <= maxSide) {
            return copyAsArgb(source);
        }

        double ratio = Math.min((double) maxSide / srcW, (double) maxSide / srcH);
        int targetW = Math.max(1, Math.min(maxSide, (int) Math.round(srcW * ratio)));
        int targetH = Math.max(1, Math.min(maxSide, (int) Math.round(srcH * ratio)));

        BufferedImage current = source;
        int curW = srcW;
        int curH = srcH;
        while (curW > targetW * 2 || curH > targetH * 2) {
            int nextW = Math.max(targetW, curW / 2);
            int nextH = Math.max(targetH, curH / 2);
            current = resizeStep(current, nextW, nextH);
            curW = nextW;
            curH = nextH;
        }
        return resizeStep(current, targetW, targetH);
    }

    private static BufferedImage resizeStep(BufferedImage src, int width, int height) {
        BufferedImage dst = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setComposite(AlphaComposite.Src);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            g.drawImage(src, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    private static BufferedImage toArgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
            return source;
        }
        return copyAsArgb(source);
    }

    private static BufferedImage copyAsArgb(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        try {
            g.setComposite(AlphaComposite.Src);
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return copy;
    }

    private static BufferedImage decodePng(byte[] png) {
        try {
            return ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException e) {
            throw new UncheckedIOException("Не вдалося прочитати PNG шару", e);
        }
    }

    private static byte[] encodePng(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, "png", out)) {
                throw new IllegalStateException("Кодувальник PNG недоступний");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Не вдалося закодувати мініатюру в PNG", e);
        }
        return out.toByteArray();
    }
}
