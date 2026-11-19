package com.shufflepix.client.remote;

import com.shufflepix.common.api.ApiPaths;

import java.util.UUID;

public class ThumbnailServiceProxy implements ThumbnailService {
    private final RestClient rest;

    public ThumbnailServiceProxy(RestClient rest) {
        this.rest = rest;
    }

    @Override
    public byte[] getThumbnail(UUID projectId) {
        return rest.getBytes(RestClient.path(ApiPaths.PROJECT_THUMBNAIL, projectId));
    }
}
