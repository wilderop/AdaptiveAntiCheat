package com.wilderop.adaptiveac.util;

import com.wilderop.adaptiveac.AdaptiveAC;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

/**
 * Timestamped file logger. Not used for console spam.
 */
public final class CheckLogger implements AutoCloseable {

    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final AdaptiveAC plugin;
    private final String fileName;
    private PrintWriter writer;

    public CheckLogger(AdaptiveAC plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
        open();
    }

    private void open() {
        try {
            File folder = plugin.getDataFolder();
            if (!folder.exists() && !folder.mkdirs()) {
                plugin.getLogger().warning("Could not create data folder for " + fileName);
                return;
            }
            writer = new PrintWriter(new FileWriter(new File(folder, fileName), true), true);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not open " + fileName, e);
        }
    }

    public synchronized void log(String line) {
        if (writer == null) return;
        writer.println(LocalDateTime.now().format(TS) + " " + line);
    }

    @Override
    public synchronized void close() {
        if (writer != null) {
            writer.close();
            writer = null;
        }
    }
}
