package com.shufflepix.client.remote;

import java.util.UUID;

public interface ThumbnailService {
    byte[] getThumbnail(UUID projectId);
}
