package com.wilderop.adaptiveac.manager;

import com.wilderop.adaptiveac.AdaptiveAC;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TrustManager {

    private final AdaptiveAC plugin;
    private final Set<UUID> manualTrusted = new HashSet<>();
    private final Set<UUID> manualUntrusted = new HashSet<>();
    private int autoTrustHours;

    public TrustManager(AdaptiveAC plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        autoTrustHours = plugin.getConfig().getInt("trust.auto-trust-hours", 500);
        manualTrusted.clear();
        manualUntrusted.clear();
        manualTrusted.addAll(plugin.getDataManager().getManualTrusted());
        manualUntrusted.addAll(plugin.getDataManager().getManualUntrusted());
    }

    public boolean isTrusted(Player player) {
        return isTrusted(player.getUniqueId(), player);
    }

    public boolean isTrusted(UUID uuid, Player player) {
        if (manualUntrusted.contains(uuid)) {
            return false;
        }
        if (manualTrusted.contains(uuid)) {
            return true;
        }
        // Auto-trust by playtime
        if (player != null && player.isOnline()) {
            return getPlaytimeHours(player) >= autoTrustHours;
        }
        return false;
    }

    public double getPlaytimeHours(Player player) {
        // Statistic.PLAY_ONE_MINUTE actually returns ticks played
        long ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        return ticks / 20.0 / 60.0 / 60.0;
    }

    public void trust(UUID uuid) {
        manualUntrusted.remove(uuid);
        manualTrusted.add(uuid);
        save();
    }

    public void untrust(UUID uuid) {
        manualTrusted.remove(uuid);
        manualUntrusted.add(uuid);
        save();
    }

    public void clearManual(UUID uuid) {
        manualTrusted.remove(uuid);
        manualUntrusted.remove(uuid);
        save();
    }

    private void save() {
        plugin.getDataManager().setManualTrusted(manualTrusted, manualUntrusted);
    }

    public void recheckOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Just accessing isTrusted will evaluate playtime; no extra action needed
            // unless we want to notify them the first time they cross the threshold.
            // For now we stay silent.
        }
    }

    public int getAutoTrustHours() {
        return autoTrustHours;
    }

    public Set<UUID> getManualTrusted() {
        return Set.copyOf(manualTrusted);
    }
}
