package com.wilderop.adaptiveac.check;

import com.wilderop.adaptiveac.AdaptiveAC;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class SpeedCheck implements Listener {

    private final AdaptiveAC plugin;
    private final Map<UUID, Double> violationBuffer = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastLocation = new ConcurrentHashMap<>();

    private boolean enabled;
    private double baseMaxSpeed;
    private double vehicleMultiplier;
    private double elytraMultiplier;
    private double minDistance;
    private double buffer;
    private double bufferDecay;

    private boolean logConsole;
    private boolean logFile;
    private boolean trustedOnlyLog;

    private PrintWriter logWriter;

    public SpeedCheck(AdaptiveAC plugin) {
        this.plugin = plugin;
        reload();
        setupLogFile();
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("checks.speed.enabled", true);
        baseMaxSpeed = plugin.getConfig().getDouble("checks.speed.base-max-speed", 0.42);
        vehicleMultiplier = plugin.getConfig().getDouble("checks.speed.vehicle-multiplier", 2.8);
        elytraMultiplier = plugin.getConfig().getDouble("checks.speed.elytra-multiplier", 3.5);
        minDistance = plugin.getConfig().getDouble("checks.speed.min-distance", 0.08);
        buffer = plugin.getConfig().getDouble("checks.speed.buffer", 4);
        bufferDecay = plugin.getConfig().getDouble("checks.speed.buffer-decay-per-second", 1.0);

        logConsole = plugin.getConfig().getBoolean("logging.console", true);
        logFile = plugin.getConfig().getBoolean("logging.file", true);
        trustedOnlyLog = plugin.getConfig().getBoolean("logging.trusted-only", false);
    }

    private void setupLogFile() {
        if (!logFile) return;
        try {
            File folder = plugin.getDataFolder();
            if (!folder.exists()) folder.mkdirs();
            File logFile = new File(folder, "violations.log");
            this.logWriter = new PrintWriter(new FileWriter(logFile, true), true);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not open violations.log", e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.hasPermission("adaptiveac.bypass") || player.hasPermission("adaptiveac.admin")) return;

        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || from.getWorld() != to.getWorld()) return;

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        if (horizontalDist < minDistance) return;

        // Approximate speed in blocks per tick (move events are usually 1 tick apart)
        double speed = horizontalDist;

        double multiplier = plugin.getAdaptiveManager().getMultiplier("speed");
        double maxAllowed = baseMaxSpeed * multiplier;

        if (player.isInsideVehicle()) {
            maxAllowed *= vehicleMultiplier;
        } else if (player.isGliding()) {
            maxAllowed *= elytraMultiplier;
        }

        // Slight extra tolerance for high ping (very rough)
        int ping = player.getPing();
        if (ping > 150) {
            maxAllowed *= 1.0 + Math.min(0.4, (ping - 150) / 500.0);
        }

        if (speed > maxAllowed) {
            // Violation
            double currentBuffer = violationBuffer.getOrDefault(player.getUniqueId(), 0.0) + 1.0;
            violationBuffer.put(player.getUniqueId(), currentBuffer);

            boolean trusted = plugin.getTrustManager().isTrusted(player);

            // Always feed adaptive system
            plugin.getAdaptiveManager().onViolation(player, "speed", speed, maxAllowed);

            if (currentBuffer >= buffer) {
                // Flag
                logViolation(player, speed, maxAllowed, trusted, currentBuffer);

                // Soft action for now: just log + reset some buffer
                // (Real punishment can be added later once confidence is high)
                violationBuffer.put(player.getUniqueId(), buffer * 0.6);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        violationBuffer.remove(uuid);
        lastLocation.remove(uuid);
    }

    public void decayBuffers() {
        violationBuffer.replaceAll((uuid, val) -> Math.max(0.0, val - bufferDecay));
    }

    private void logViolation(Player player, double speed, double threshold, boolean trusted, double bufferLevel) {
        if (trustedOnlyLog && !trusted) return;

        String msg = String.format(
                "[Speed] %s | speed=%.3f thresh=%.3f buffer=%.1f trusted=%s playtime=%.1fh",
                player.getName(),
                speed,
                threshold,
                bufferLevel,
                trusted,
                plugin.getTrustManager().getPlaytimeHours(player)
        );

        if (logConsole) {
            plugin.getLogger().info(msg);
        }

        if (logFile && logWriter != null) {
            String time = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            logWriter.println(time + " " + msg);
        }
    }

    public Map<UUID, Double> getViolationBuffers() {
        return Map.copyOf(violationBuffer);
    }
}
