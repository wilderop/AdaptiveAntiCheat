package com.wilderop.adaptiveac;

import com.wilderop.adaptiveac.check.SpeedCheck;
import com.wilderop.adaptiveac.command.ACCommand;
import com.wilderop.adaptiveac.listener.PlayerListener;
import com.wilderop.adaptiveac.manager.AdaptiveManager;
import com.wilderop.adaptiveac.manager.DataManager;
import com.wilderop.adaptiveac.manager.TrustManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class AdaptiveAC extends JavaPlugin {

    private static AdaptiveAC instance;

    private DataManager dataManager;
    private TrustManager trustManager;
    private AdaptiveManager adaptiveManager;
    private SpeedCheck speedCheck;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        reloadConfig();

        this.dataManager = new DataManager(this);
        this.trustManager = new TrustManager(this);
        this.adaptiveManager = new AdaptiveManager(this);
        this.speedCheck = new SpeedCheck(this);

        // Register listeners
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(speedCheck, this);

        // Commands
        var acCommand = getCommand("ac");
        if (acCommand != null) {
            ACCommand executor = new ACCommand(this);
            acCommand.setExecutor(executor);
            acCommand.setTabCompleter(executor);
        }

        // Periodic tasks
        long recheckTicks = getConfig().getLong("trust.recheck-interval-minutes", 30) * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(this, () -> trustManager.recheckOnlinePlayers(), recheckTicks, recheckTicks);

        // Decay buffers every second
        Bukkit.getScheduler().runTaskTimer(this, () -> speedCheck.decayBuffers(), 20L, 20L);

        getLogger().info("AdaptiveAntiCheat enabled. Auto-trust threshold: "
                + getConfig().getInt("trust.auto-trust-hours") + " hours.");
    }

    @Override
    public void onDisable() {
        if (dataManager != null) {
            dataManager.saveAll();
        }
        getLogger().info("AdaptiveAntiCheat disabled. Data saved.");
    }

    public void reload() {
        reloadConfig();
        trustManager.reload();
        adaptiveManager.reload();
        speedCheck.reload();
        getLogger().info("Configuration reloaded.");
    }

    public static AdaptiveAC getInstance() {
        return instance;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public TrustManager getTrustManager() {
        return trustManager;
    }

    public AdaptiveManager getAdaptiveManager() {
        return adaptiveManager;
    }

    public SpeedCheck getSpeedCheck() {
        return speedCheck;
    }
}
