package com.wilderop.adaptiveac.check;

import com.wilderop.adaptiveac.AdaptiveAC;
import com.wilderop.adaptiveac.util.CheckLogger;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Locale;

/**
 * Log-only timer heuristic: position-changing move events per server tick
 * while the player is actually travelling. Does not punish.
 */
public class TimerCheck implements Listener {

    private final AdaptiveAC plugin;
    private final MovementTracker tracker;
    private CheckLogger log;

    private boolean enabled;
    private int windowTicks;
    private double flagRatio;
    private double minDistance;
    private int logCooldownTicks;

    public TimerCheck(AdaptiveAC plugin, MovementTracker tracker) {
        this.plugin = plugin;
        this.tracker = tracker;
        reload();
        this.log = new CheckLogger(plugin, "timer.log");
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("checks.timer.enabled", true);
        windowTicks = plugin.getConfig().getInt("checks.timer.window-ticks", 40);
        flagRatio = plugin.getConfig().getDouble("checks.timer.flag-ratio", 1.35);
        minDistance = plugin.getConfig().getDouble("checks.timer.min-distance", 0.08);
        logCooldownTicks = plugin.getConfig().getInt("checks.timer.log-cooldown-ticks", 100);
    }

    public void shutdown() {
        if (log != null) log.close();
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
        if (Math.sqrt(dx * dx + dz * dz) < minDistance) return;

        int tick = Bukkit.getCurrentTick();
        PlayerMoveState state = tracker.state(player);
        if (tick < state.ignoreUntilTick) return;

        if (state.windowStartTick < 0 || tick - state.windowStartTick >= windowTicks) {
            if (state.windowStartTick >= 0 && state.windowMoveEvents > 0) {
                int elapsed = Math.max(1, tick - state.windowStartTick);
                double ratio = state.windowMoveEvents / (double) elapsed;
                if (ratio >= flagRatio && tick - state.lastTimerLogTick >= logCooldownTicks) {
                    state.lastTimerLogTick = tick;
                    boolean trusted = plugin.getTrustManager().isTrusted(player);
                    log.log(String.format(Locale.US,
                            "TIMER player=%s trusted=%s pt=%.1fh events=%d ticks=%d ratio=%.3f ping=%d",
                            player.getName(),
                            trusted,
                            plugin.getTrustManager().getPlaytimeHours(player),
                            state.windowMoveEvents,
                            elapsed,
                            ratio,
                            player.getPing()));
                }
            }
            state.windowStartTick = tick;
            state.windowMoveEvents = 0;
        }
        state.windowMoveEvents++;
    }
}
