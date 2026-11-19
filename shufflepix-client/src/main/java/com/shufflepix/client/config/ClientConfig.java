package com.shufflepix.client.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

public final class ClientConfig {
    private static final String FILE = "client.properties";

    private final Properties props = new Properties();

    private ClientConfig() {
    }

    public static ClientConfig load() {
        ClientConfig config = new ClientConfig();
        try (InputStream in = ClientConfig.class.getResourceAsStream("/" + FILE)) {
            if (in != null) {
                config.props.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            }
            Path local = Path.of(FILE);
            if (Files.isRegularFile(local)) {
                try (Reader reader = Files.newBufferedReader(local, StandardCharsets.UTF_8)) {
                    config.props.load(reader);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Не вдалося прочитати " + FILE, e);
        }
        return config;
    }

    public URI getServerUrl() {
        return URI.create(get("shufflepix.server.url", "http://localhost:8080"));
    }

    public Duration getConnectTimeout() {
        return Duration.ofSeconds(Long.parseLong(get("shufflepix.server.connect-timeout-seconds", "3")));
    }

    public Duration getRequestTimeout() {
        return Duration.ofSeconds(Long.parseLong(get("shufflepix.server.request-timeout-seconds", "30")));
    }

    private String get(String key, String defaultValue) {
        return System.getProperty(key, props.getProperty(key, defaultValue));
    }
}
