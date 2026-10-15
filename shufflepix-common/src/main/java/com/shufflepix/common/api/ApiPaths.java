package com.shufflepix.common.api;

public final class ApiPaths {
    public static final String API = "/api";

    public static final String PROJECTS = API + "/projects";
    public static final String PROJECT = PROJECTS + "/{projectId}";

    public static final String PROJECT_LAYERS = PROJECT + "/layers";
    public static final String LAYER_CONTENT = API + "/layers/{layerId}/content";
    public static final String EFFECT_TYPES = API + "/effect-types";

    public static final String PROJECT_THUMBNAIL = PROJECT + "/thumbnail";

    public static final String PART_PROJECT = "project";

    public static final String IMAGE_PNG = "image/png";

    private ApiPaths() {
    }
}
