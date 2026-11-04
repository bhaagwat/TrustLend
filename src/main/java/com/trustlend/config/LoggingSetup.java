package com.trustlend.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/** Configures java.util.logging from {@code /logging.properties} (Unit 3: logging). */
public final class LoggingSetup {

    private static boolean initialised = false;

    private LoggingSetup() { }

    public static synchronized void init() {
        if (initialised) {
            return;
        }
        try {
            // FileHandler fails if the folder does not exist
            Files.createDirectories(Paths.get("data", "logs"));
            try (InputStream in = LoggingSetup.class.getResourceAsStream("/logging.properties")) {
                if (in != null) {
                    LogManager.getLogManager().readConfiguration(in);
                }
            }
        } catch (IOException e) {
            System.err.println("Logging setup failed, using JVM defaults: " + e.getMessage());
        }
        initialised = true;
        Logger.getLogger(LoggingSetup.class.getName()).info("Logging initialised");
    }
}
