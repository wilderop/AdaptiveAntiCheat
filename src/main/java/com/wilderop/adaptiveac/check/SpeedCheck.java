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
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;

/**
 * Tick-based horizontal speed with per-context thresholds.
 * Logging only: session summaries plus rate-limited samples. No punishment.
 */
public class SpeedCheck implements Listener {

    private final AdaptiveAC plugin;
    private final MovementTracker tracker;

    private boolean enabled;
    private int maxGapTicks;
    private double teleportDistance;
    private double minDistance;
    private int sessionGapTicks;
    private int sampleIntervalTicks;
    private boolean logConsole;
    private boolean logSamples;

    private CheckLogger sessions;
    private CheckLogger samples;
    private CheckLogger skips;

    public SpeedCheck(AdaptiveAC plugin, MovementTracker tracker) {
        this.plugin = plugin;
        this.tracker = tracker;
        reload();
        this.sessions = new CheckLogger(plugin, "sessions.log");
        this.samples = new CheckLogger(plugin, "samples.log");
        this.skips = new CheckLogger(plugin, "skips.log");
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("checks.speed.enabled", true);
        maxGapTicks = plugin.getConfig().getInt("checks.speed.max-gap-ticks", 5);
        teleportDistance = plugin.getConfig().getDouble("checks.speed.teleport-distance", 10.0);
        minDistance = plugin.getConfig().getDouble("checks.speed.min-distance", 0.08);
        sessionGapTicks = plugin.getConfig().getInt("checks.speed.session-gap-ticks", 40);
        sampleIntervalTicks = plugin.getConfig().getInt("logging.sample-interval-ticks", 20);
        logConsole = plugin.getConfig().getBoolean("logging.console", false);
        logSamples = plugin.getConfig().getBoolean("logging.samples", true);
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            flushSession(player, tracker.state(player), "shutdown");
        }
        if (sessions != null) sessions.close();
        if (samples != null) samples.close();
        if (skips != null) skips.close();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.hasPermission("adaptiveac.bypass") || player.hasPermission("adaptiveac.admin")) return;

        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null) return;

        int tick = Bukkit.getCurrentTick();
        PlayerMoveState state = tracker.state(player);

        if (from.getWorld() != to.getWorld()) {
            skip(player, state, tick, "WORLD_CHANGE", 0, 0);
            flushSession(player, state, "world-change");
            tracker.remember(player, to, tick);
            return;
        }

        if (tick < state.ignoreUntilTick) {
            tracker.remember(player, to, tick);
            return;
        }

        Location last = state.lastLoc != null ? state.lastLoc : from;
        if (last.getWorld() != to.getWorld()) {
            tracker.remember(player, to, tick);
            return;
        }

        int elapsed = state.lastTick < 0 ? 1 : tick - state.lastTick;
        double dx = to.getX() - last.getX();
        double dz = to.getZ() - last.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (elapsed <= 0) {
            elapsed = 1;
        }

        if (elapsed > maxGapTicks) {
            skip(player, state, tick, "GAP", dist, elapsed);
            flushSession(player, state, "gap");
            tracker.remember(player, to, tick);
            return;
        }

        if (dist >= teleportDistance) {
            skip(player, state, tick, "TELEPORT", dist, elapsed);
            flushSession(player, state, "teleport");
            tracker.remember(player, to, tick);
            return;
        }

        if (dist < minDistance) {
            maybeEndSession(player, state, tick);
            tracker.remember(player, to, tick);
            return;
        }

        double speed = dist / elapsed;
        ContextResolver.Resolved resolved = ContextResolver.resolve(player, to, state, tick);
        double base = plugin.getConfig().getDouble(
                "checks.speed.contexts." + resolved.context().name().toLowerCase(Locale.ROOT),
                defaultBase(resolved.context()));
        if (resolved.context() == MoveContext.SPEED_POTION && resolved.speedAmp() > 1) {
            base *= 1.0 + 0.2 * (resolved.speedAmp() - 1);
        }

        double multiplier = plugin.getAdaptiveManager().getMultiplier(resolved.context().checkKey());
        double thresh = base * multiplier;
        int ping = player.getPing();
        if (ping > 150) {
            thresh *= 1.0 + Math.min(0.4, (ping - 150) / 500.0);
        }

        boolean over = speed > thresh;
        boolean trusted = plugin.getTrustManager().isTrusted(player);
        double hours = plugin.getTrustManager().getPlaytimeHours(player);

        SpeedSession session = state.speedSession;
        if (session != null && (session.context != resolved.context()
                || tick - session.lastTick > sessionGapTicks)) {
            flushSession(player, state, session.context != resolved.context() ? "context" : "idle");
            session = null;
        }

        if (over) {
            if (session == null) {
                session = new SpeedSession(resolved.context(), player.getName(), trusted, hours,
                        resolved.vehicleType(), tick);
                state.speedSession = session;
            }
            double ratio = thresh <= 0 ? 0 : speed / thresh;
            session.add(tick, speed, ratio, ping, true);

            if (logSamples && tick - state.lastSampleTick >= sampleIntervalTicks) {
                state.lastSampleTick = tick;
                String line = String.format(Locale.US,
                        "SAMPLE speed ctx=%s player=%s trusted=%s pt=%.1fh speed=%.3f thresh=%.3f ratio=%.3f ticks=%d dist=%.3f ping=%d ice=%s vehicle=%s soul=%s spdAmp=%d",
                        resolved.context().name(),
                        player.getName(),
                        trusted,
                        hours,
                        speed,
                        thresh,
                        ratio,
                        elapsed,
                        dist,
                        ping,
                        resolved.onIce(),
                        resolved.vehicleType(),
                        resolved.soulSpeed(),
                        resolved.speedAmp());
                samples.log(line);
                if (logConsole) {
                    plugin.getLogger().info(line);
                }
            }
        } else if (session != null) {
            session.add(tick, speed, thresh <= 0 ? 0 : speed / thresh, ping, false);
            maybeEndSession(player, state, tick);
        }

        tracker.remember(player, to, tick);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        flushSession(player, tracker.state(player), "quit");
        tracker.reset(player);
    }

    public void flushSession(Player player, PlayerMoveState state, String reason) {
        SpeedSession session = state.speedSession;
        if (session == null) return;
        state.speedSession = null;
        if (session.overSamples < 1) return;

        String line = String.format(Locale.US,
                "SESSION speed ctx=%s player=%s trusted=%s pt=%.1fh n=%d over=%d dur_s=%.2f mean=%.3f max=%.3f max_ratio=%.3f ping=%d vehicle=%s reason=%s",
                session.context.name(),
                session.playerName,
                session.trusted,
                session.playtimeHours,
                session.samples,
                session.overSamples,
                session.durationSeconds(),
                session.medianApprox(),
                session.maxSpeed,
                session.maxRatio,
                session.pingAvg(),
                session.vehicleType,
                reason);
        sessions.log(line);
        if (logConsole) {
            plugin.getLogger().info(line);
        }

        // One trusted session = one false-positive, not one per tick.
        if (session.trusted && session.overSamples >= 3) {
            plugin.getAdaptiveManager().onTrustedSession(session.context.checkKey());
        }
    }

    private void maybeEndSession(Player player, PlayerMoveState state, int tick) {
        SpeedSession session = state.speedSession;
        if (session != null && tick - session.lastTick > sessionGapTicks) {
            flushSession(player, state, "idle");
        }
    }

    private void skip(Player player, PlayerMoveState state, int tick, String reason, double dist, int elapsed) {
        if (tick - state.lastSkipTick < 100) return;
        state.lastSkipTick = tick;
        skips.log(String.format(Locale.US,
                "SKIP reason=%s player=%s dist=%.3f ticks=%d",
                reason, player.getName(), dist, elapsed));
    }

    private static double defaultBase(MoveContext ctx) {
        return switch (ctx) {
            case GROUND -> 0.42;
            case SPEED_POTION -> 0.55;
            case ICE -> 1.20;
            case SOUL_SPEED -> 0.70;
            case VEHICLE_LAND -> 1.00;
            case VEHICLE_WATER -> 0.80;
            case VEHICLE_ICE -> 6.00;
            case ELYTRA -> 3.50;
            case ELYTRA_FIREWORK -> 12.00;
            case RIPTIDE -> 8.00;
            case WIND_BURST -> 2.50;
        };
    }
}
