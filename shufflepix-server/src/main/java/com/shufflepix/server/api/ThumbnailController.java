package com.shufflepix.server.api;

import com.shufflepix.common.api.ApiPaths;
import com.shufflepix.server.service.ThumbnailService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ThumbnailController {
    private final ThumbnailService thumbnailService;

    public ThumbnailController(ThumbnailService thumbnailService) {
        this.thumbnailService = thumbnailService;
    }

    @GetMapping(path = ApiPaths.PROJECT_THUMBNAIL, produces = ApiPaths.IMAGE_PNG)
    public byte[] thumbnail(@PathVariable UUID projectId) {
        return thumbnailService.getThumbnail(projectId);
    }
}
