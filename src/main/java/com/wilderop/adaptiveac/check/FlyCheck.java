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
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

/**
 * Log-only air-time / hover heuristic. Ignores elytra, vehicles, riptide, levitation.
 * Does not punish.
 */
public class FlyCheck implements Listener {

    private final AdaptiveAC plugin;
    private final MovementTracker tracker;
    private CheckLogger log;

    private boolean enabled;
    private int hoverAirTicks;
    private int flyAirTicks;
    private double hoverDy;

    public FlyCheck(AdaptiveAC plugin, MovementTracker tracker) {
        this.plugin = plugin;
        this.tracker = tracker;
        reload();
        this.log = new CheckLogger(plugin, "fly.log");
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("checks.fly.enabled", true);
        hoverAirTicks = plugin.getConfig().getInt("checks.fly.hover-air-ticks", 40);
        flyAirTicks = plugin.getConfig().getInt("checks.fly.fly-air-ticks", 60);
        hoverDy = plugin.getConfig().getDouble("checks.fly.hover-dy", 0.08);
    }

    public void shutdown() {
        if (log != null) log.close();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) return;
        if (player.hasPermission("adaptiveac.bypass") || player.hasPermission("adaptiveac.admin")) return;

        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || from.getWorld() != to.getWorld()) return;

        int tick = Bukkit.getCurrentTick();
        PlayerMoveState state = tracker.state(player);
        if (tick < state.ignoreUntilTick) return;

        boolean airborne = !player.isOnGround()
                && !player.isInWater()
                && !player.isGliding()
                && !player.isInsideVehicle()
                && !player.isRiptiding()
                && !player.isClimbing()
                && !player.isFlying()
                && player.getPotionEffect(PotionEffectType.LEVITATION) == null
                && player.getPotionEffect(PotionEffectType.SLOW_FALLING) == null
                && tick >= state.riptideUntilTick
                && tick >= state.windUntilTick;

        if (!airborne) {
            state.airTicks = 0;
            state.hoverTicks = 0;
            state.flyLogged = false;
            return;
        }

        state.airTicks++;
        double dy = to.getY() - from.getY();
        if (Math.abs(dy) <= hoverDy) {
            state.hoverTicks++;
        } else {
            state.hoverTicks = 0;
        }

        if (state.flyLogged) return;

        String kind = null;
        if (state.hoverTicks >= hoverAirTicks) {
            kind = "HOVER";
        } else if (state.airTicks >= flyAirTicks && dy >= 0) {
            kind = "FLY";
        }
        if (kind == null) return;

        state.flyLogged = true;
        boolean trusted = plugin.getTrustManager().isTrusted(player);
        log.log(String.format(Locale.US,
                "%s player=%s trusted=%s pt=%.1fh air_ticks=%d hover_ticks=%d dy=%.3f ping=%d",
                kind,
                player.getName(),
                trusted,
                plugin.getTrustManager().getPlaytimeHours(player),
                state.airTicks,
                state.hoverTicks,
                dy,
                player.getPing()));
    }
}
