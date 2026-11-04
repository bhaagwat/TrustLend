package com.trustlend.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application configuration backed by {@link Properties} (Unit 3).
 *
 * <ol>
 *   <li>Defaults are read from {@code /config.properties} on the classpath.</li>
 *   <li>A user copy in {@code data/config.properties} overrides them (created on first run).</li>
 * </ol>
 *
 * Implemented as a thread-safe lazy singleton (static holder idiom).
 */
public final class AppConfig {

    private static final Logger LOG = Logger.getLogger(AppConfig.class.getName());
    public static final Path USER_CONFIG = Paths.get("data", "config.properties");

    private final Properties props = new Properties();

    private AppConfig() {
        loadDefaults();
        loadUserOverrides();
    }

    /** Holder class is loaded only when getInstance() is first called. */
    private static final class Holder {
        private static final AppConfig INSTANCE = new AppConfig();
    }

    public static AppConfig getInstance() {
        return Holder.INSTANCE;
    }

    private void loadDefaults() {
        try (InputStream in = AppConfig.class.getResourceAsStream("/config.properties")) {
            if (in == null) {
                throw new IllegalStateException("config.properties missing from classpath");
            }
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read default configuration", e);
        }
    }

    private void loadUserOverrides() {
        if (!Files.exists(USER_CONFIG)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(USER_CONFIG, StandardCharsets.UTF_8)) {
            props.load(reader);
            LOG.info("Loaded user configuration from " + USER_CONFIG.toAbsolutePath());
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Ignoring unreadable user configuration", e);
        }
    }

    /** Writes the current settings to data/config.properties (character stream). */
    public synchronized void save() throws IOException {
        Files.createDirectories(USER_CONFIG.getParent());
        try (Writer writer = Files.newBufferedWriter(USER_CONFIG, StandardCharsets.UTF_8)) {
            props.store(writer, "TrustLend user configuration");
        }
        LOG.info("Configuration saved to " + USER_CONFIG.toAbsolutePath());
    }

    public String get(String key) {
        String value = props.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing configuration key: " + key);
        }
        return value.trim();
    }

    public String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue).trim();
    }

    public int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    public double getDouble(String key) {
        return Double.parseDouble(get(key));
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key));
    }

    public synchronized void set(String key, String value) {
        props.setProperty(key, value);
    }

    public Path getPath(String key) {
        return Paths.get(get(key));
    }
}
