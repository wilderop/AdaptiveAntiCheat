package com.wilderop.adaptiveac.manager;

import com.wilderop.adaptiveac.AdaptiveAC;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class DataManager {

    private final AdaptiveAC plugin;
    private final File trustedFile;
    private final File adaptiveFile;
    private FileConfiguration trustedConfig;
    private FileConfiguration adaptiveConfig;

    public DataManager(AdaptiveAC plugin) {
        this.plugin = plugin;
        this.trustedFile = new File(plugin.getDataFolder(), "trusted.yml");
        this.adaptiveFile = new File(plugin.getDataFolder(), "adaptive.yml");
        load();
    }

    private void load() {
        if (!trustedFile.exists()) {
            try {
                trustedFile.getParentFile().mkdirs();
                trustedFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create trusted.yml", e);
            }
        }
        if (!adaptiveFile.exists()) {
            try {
                adaptiveFile.getParentFile().mkdirs();
                adaptiveFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create adaptive.yml", e);
            }
        }

        trustedConfig = YamlConfiguration.loadConfiguration(trustedFile);
        adaptiveConfig = YamlConfiguration.loadConfiguration(adaptiveFile);
    }

    public Set<UUID> getManualTrusted() {
        Set<UUID> set = new HashSet<>();
        for (String s : trustedConfig.getStringList("manual-trusted")) {
            try {
                set.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {}
        }
        return set;
    }

    public Set<UUID> getManualUntrusted() {
        Set<UUID> set = new HashSet<>();
        for (String s : trustedConfig.getStringList("manual-untrusted")) {
            try {
                set.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {}
        }
        return set;
    }

    public void setManualTrusted(Set<UUID> trusted, Set<UUID> untrusted) {
        trustedConfig.set("manual-trusted", trusted.stream().map(UUID::toString).toList());
        trustedConfig.set("manual-untrusted", untrusted.stream().map(UUID::toString).toList());
        saveTrusted();
    }

    /**
     * v1 stored a single {@code multipliers.speed} scalar. That value was
     * trained by elytra/boats and must not carry over to per-context keys.
     */
    public void resetLegacyGlobalSpeedMultiplier() {
        if (!adaptiveConfig.contains("multipliers.speed")) return;
        if (adaptiveConfig.isConfigurationSection("multipliers.speed")) return;
        plugin.getLogger().info("Resetting v1 global speed multiplier; per-context keys start at 1.0");
        adaptiveConfig.set("multipliers", null);
        adaptiveConfig.set("false-positives", null);
        saveAdaptive();
    }

    public double getMultiplier(String checkName) {
        return adaptiveConfig.getDouble("multipliers." + checkName, 1.0);
    }

    public void setMultiplier(String checkName, double value) {
        adaptiveConfig.set("multipliers." + checkName, value);
        saveAdaptive();
    }

    public int getFalsePositiveCount(String checkName) {
        return adaptiveConfig.getInt("false-positives." + checkName, 0);
    }

    public void setFalsePositiveCount(String checkName, int count) {
        adaptiveConfig.set("false-positives." + checkName, count);
        saveAdaptive();
    }

    public void saveAll() {
        saveTrusted();
        saveAdaptive();
    }

    private void saveTrusted() {
        try {
            trustedConfig.save(trustedFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save trusted.yml", e);
        }
    }

    private void saveAdaptive() {
        try {
            adaptiveConfig.save(adaptiveFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save adaptive.yml", e);
        }
    }
}
