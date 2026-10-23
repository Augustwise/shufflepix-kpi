package com.shufflepix.server.service;

import com.shufflepix.common.api.Limits;
import com.shufflepix.common.dto.EffectDto;
import com.shufflepix.common.dto.SaveLayerRequest;
import com.shufflepix.common.dto.SaveProjectRequest;
import com.shufflepix.common.image.PixelEffects;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class SaveProjectValidator {
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    public void validate(SaveProjectRequest request, Map<String, byte[]> contents) {
        if (request == null) {
            throw new InvalidRequestException("Відсутні властивості проєкту");
        }
        validateName(request.name());
        validateSize(request.width(), request.height());
        if (request.backgroundColor() == null || !request.backgroundColor().matches(Limits.COLOR_PATTERN)) {
            throw new InvalidRequestException("Колір фону має відповідати формату #RRGGBBAA");
        }
        if (request.layers() == null || request.layers().isEmpty()) {
            throw new InvalidRequestException("Проєкт повинен містити щонайменше один шар");
        }
        Map<String, byte[]> safeContents = contents == null ? Map.of() : contents;
        Set<Integer> positions = new HashSet<>();
        Set<UUID> layerIds = new HashSet<>();
        for (SaveLayerRequest layer : request.layers()) {
            validateLayer(layer, request.width(), request.height(), safeContents, positions, layerIds);
        }
    }

    public void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException("Назва не може бути порожньою");
        }
        if (name.strip().length() > Limits.MAX_NAME_LENGTH) {
            throw new InvalidRequestException("Назва не може бути довшою за " + Limits.MAX_NAME_LENGTH + " символів");
        }
    }

    private void validateSize(int width, int height) {
        if (width < 1 || width > Limits.MAX_CANVAS_SIZE || height < 1 || height > Limits.MAX_CANVAS_SIZE) {
            throw new InvalidRequestException(
                    "Розміри полотна мають бути від 1 до " + Limits.MAX_CANVAS_SIZE + " пікселів");
        }
    }

    private void validateLayer(SaveLayerRequest layer, int expectedWidth, int expectedHeight,
                               Map<String, byte[]> contents, Set<Integer> positions, Set<UUID> layerIds) {
        if (layer == null) {
            throw new InvalidRequestException("Список шарів містить порожній елемент");
        }
        validateName(layer.name());
        if (layer.position() < 0) {
            throw new InvalidRequestException("Порядковий номер шару «" + layer.name() + "» не може бути від'ємним");
        }
        if (!positions.add(layer.position())) {
            throw new InvalidRequestException("Порядкові номери шарів не можуть повторюватися: " + layer.position());
        }
        if (layer.id() != null && !layerIds.add(layer.id())) {
            throw new InvalidRequestException("Ідентифікатори шарів у запиті не можуть повторюватися");
        }
        double opacity = layer.opacity();
        if (Double.isNaN(opacity) || Double.isInfinite(opacity) || opacity < 0 || opacity > 1) {
            throw new InvalidRequestException("Непрозорість шару має бути від 0 до 1");
        }
        if (layer.effects() != null) {
            for (EffectDto effect : layer.effects()) {
                validateEffect(effect, layer.name());
            }
        }
        String part = layer.contentPart();
        if (part != null && part.isBlank()) {
            throw new InvalidRequestException("Некоректна назва частини вмісту для шару «" + layer.name() + "»");
        }
        if (layer.id() == null && part == null) {
            throw new InvalidRequestException("Для нового шару «" + layer.name() + "» не передано вміст");
        }
        if (part != null) {
            byte[] png = contents.get(part);
            if (png == null || png.length == 0) {
                throw new InvalidRequestException("Відсутній вміст шару «" + layer.name() + "»");
            }
            if (png.length > Limits.MAX_LAYER_PNG_BYTES) {
                throw new InvalidRequestException("Вміст шару «" + layer.name() + "» перевищує 20 МБ");
            }
            validatePng(png, layer.name(), expectedWidth, expectedHeight);
        }
    }

    private void validateEffect(EffectDto effect, String layerName) {
        if (effect == null || effect.effectCode() == null || effect.effectCode().isBlank()) {
            throw new InvalidRequestException("Некоректний ефект для шару «" + layerName + "»");
        }
        if (PixelEffects.CODE_BLUR.equals(effect.effectCode())
                && effect.parameters() != null
                && effect.parameters().containsKey(PixelEffects.PARAM_RADIUS)) {
            Object raw = effect.parameters().get(PixelEffects.PARAM_RADIUS);
            if (!(raw instanceof Number num)) {
                throw new InvalidRequestException("Радіус розмиття має бути додатним цілим числом");
            }
            double val = num.doubleValue();
            if (!Double.isFinite(val) || val != Math.floor(val) || val < 1 || val > Limits.MAX_CANVAS_SIZE) {
                throw new InvalidRequestException("Радіус розмиття має бути цілим числом від 1 до " + Limits.MAX_CANVAS_SIZE);
            }
        }
    }

    void validatePng(byte[] png, String layerName, int expectedWidth, int expectedHeight) {
        if (!hasPngSignature(png)) {
            throw new InvalidRequestException("Вміст шару «" + layerName + "» не є зображенням у форматі PNG");
        }
        try (ImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(png))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new InvalidRequestException("Вміст шару «" + layerName + "» пошкоджений або не є PNG");
            }
            ImageReader reader = readers.next();
            try {
                if (!"png".equalsIgnoreCase(reader.getFormatName())) {
                    throw new InvalidRequestException("Вміст шару «" + layerName + "» має бути у форматі PNG");
                }
                AtomicReference<String> fatalWarning = new AtomicReference<>();
                reader.addIIOReadWarningListener((src, warning) -> {
                    if (isCorruptionWarning(warning)) {
                        fatalWarning.compareAndSet(null, warning);
                    }
                });
                reader.setInput(iis, true, true);

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || width > Limits.MAX_CANVAS_SIZE || height < 1 || height > Limits.MAX_CANVAS_SIZE) {
                    throw new InvalidRequestException(
                            "Розміри зображення шару «" + layerName + "» виходять за допустимі межі");
                }
                if (width != expectedWidth || height != expectedHeight) {
                    throw new InvalidRequestException(
                            "Розміри шару «" + layerName + "» (" + width + "×" + height
                                    + ") не збігаються з розмірами полотна (" + expectedWidth + "×" + expectedHeight + ")");
                }

                BufferedImage decoded = reader.read(0);
                if (decoded == null || fatalWarning.get() != null) {
                    throw new InvalidRequestException("Вміст шару «" + layerName + "» пошкоджений або обірваний");
                }
            } finally {
                reader.dispose();
            }
        } catch (InvalidRequestException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new InvalidRequestException("Не вдалося прочитати PNG-вміст шару «" + layerName + "»");
        }
    }

    private static boolean hasPngSignature(byte[] data) {
        if (data.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (data[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCorruptionWarning(String warning) {
        if (warning == null) {
            return false;
        }
        String lower = warning.toLowerCase(Locale.ROOT);
        return lower.contains("corrupt") || lower.contains("truncated") || lower.contains("premature end");
    }
}
