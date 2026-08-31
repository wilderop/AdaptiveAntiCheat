package com.wilderop.adaptiveac.listener;

import com.wilderop.adaptiveac.AdaptiveAC;
import com.wilderop.adaptiveac.check.MovementTracker;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.UUID;

public class PlayerListener implements Listener {

    private static final UUID SURVIVAL_SPECTATOR_ALLOW = UUID.fromString("159f09c0-7590-45c2-943e-0393cc0eb2d1");

    private final AdaptiveAC plugin;
    private final MovementTracker tracker;

    public PlayerListener(AdaptiveAC plugin, MovementTracker tracker) {
        this.plugin = plugin;
        this.tracker = tracker;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                enforceSurvivalGamemode(player);
            }
        }, 20L, 20L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        tracker.reset(event.getPlayer());
        tracker.ignoreUntil(event.getPlayer(), Bukkit.getCurrentTick() + 20);
        enforceSurvivalGamemode(event.getPlayer());
        Bukkit.getScheduler().runTask(plugin, () -> enforceSurvivalGamemode(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() != GameMode.SPECTATOR) {
            return;
        }
        Player player = event.getPlayer();
        if (mayUseSpectator(player)) {
            return;
        }
        event.setCancelled(true);
        plugin.getLogger().info("Blocked spectator gamemode for " + player.getName());
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    private void enforceSurvivalGamemode(Player player) {
        if (!player.isOnline() || mayUseSpectator(player)) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
            plugin.getLogger().info("Reset " + player.getName() + " from spectator to survival");
        }
    }

    private static boolean mayUseSpectator(Player player) {
        return SURVIVAL_SPECTATOR_ALLOW.equals(player.getUniqueId())
                || "wilder0p".equalsIgnoreCase(player.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        int extra = plugin.getConfig().getInt("checks.speed.ignore-after-teleport-ticks", 10);
        tracker.ignoreUntil(event.getPlayer(), Bukkit.getCurrentTick() + extra);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        tracker.ignoreUntil(event.getPlayer(), Bukkit.getCurrentTick() + 20);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        tracker.ignoreUntil(event.getPlayer(), Bukkit.getCurrentTick() + 20);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRiptide(PlayerRiptideEvent event) {
        int extra = plugin.getConfig().getInt("checks.speed.ignore-after-riptide-ticks", 40);
        int tick = Bukkit.getCurrentTick();
        tracker.markRiptide(event.getPlayer(), tick, extra);
        tracker.ignoreUntil(event.getPlayer(), tick + 5);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.isGliding()) return;
        if (event.getItem() == null || event.getItem().getType() != Material.FIREWORK_ROCKET) return;
        int extra = plugin.getConfig().getInt("checks.speed.firework-ticks", 40);
        tracker.markFirework(player, Bukkit.getCurrentTick(), extra);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWind(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getDamager() instanceof WindCharge)) return;
        int extra = plugin.getConfig().getInt("checks.speed.wind-ticks", 30);
        tracker.markWind(player, Bukkit.getCurrentTick(), extra);
    }
}
