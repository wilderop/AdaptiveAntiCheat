package com.wilderop.adaptiveac;

import com.wilderop.adaptiveac.check.FlyCheck;
import com.wilderop.adaptiveac.check.MovementTracker;
import com.wilderop.adaptiveac.check.OreFindCheck;
import com.wilderop.adaptiveac.check.SpeedCheck;
import com.wilderop.adaptiveac.check.TimerCheck;
import com.wilderop.adaptiveac.command.ACCommand;
import com.wilderop.adaptiveac.listener.PlayerListener;
import com.wilderop.adaptiveac.manager.AdaptiveManager;
import com.wilderop.adaptiveac.manager.DataManager;
import com.wilderop.adaptiveac.manager.TrustManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class AdaptiveAC extends JavaPlugin {

    private static AdaptiveAC instance;

    private DataManager dataManager;
    private TrustManager trustManager;
    private AdaptiveManager adaptiveManager;
    private MovementTracker movementTracker;
    private SpeedCheck speedCheck;
    private TimerCheck timerCheck;
    private FlyCheck flyCheck;
    private OreFindCheck oreFindCheck;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        migrateConfig();
        reloadConfig();

        this.dataManager = new DataManager(this);
        dataManager.resetLegacyGlobalSpeedMultiplier();
        this.trustManager = new TrustManager(this);
        this.adaptiveManager = new AdaptiveManager(this);
        this.movementTracker = new MovementTracker();
        this.speedCheck = new SpeedCheck(this, movementTracker);
        this.timerCheck = new TimerCheck(this, movementTracker);
        this.flyCheck = new FlyCheck(this, movementTracker);
        this.oreFindCheck = new OreFindCheck(this);

        Bukkit.getPluginManager().registerEvents(new PlayerListener(this, movementTracker), this);
        Bukkit.getPluginManager().registerEvents(speedCheck, this);
        Bukkit.getPluginManager().registerEvents(timerCheck, this);
        Bukkit.getPluginManager().registerEvents(flyCheck, this);
        Bukkit.getPluginManager().registerEvents(oreFindCheck, this);

        var acCommand = getCommand("ac");
        if (acCommand != null) {
            ACCommand executor = new ACCommand(this);
            acCommand.setExecutor(executor);
            acCommand.setTabCompleter(executor);
        }

        long recheckTicks = getConfig().getLong("trust.recheck-interval-minutes", 30) * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(this, () -> trustManager.recheckOnlinePlayers(), recheckTicks, recheckTicks);

        getLogger().info("AdaptiveAntiCheat 1.1 enabled (log-only). Auto-trust: "
                + getConfig().getInt("trust.auto-trust-hours") + "h. Contexts logged to sessions.log.");
    }

    private void migrateConfig() {
        int version = getConfig().getInt("config-version", 1);
        if (version >= 2) return;
        File cfg = new File(getDataFolder(), "config.yml");
        if (cfg.exists()) {
            File bak = new File(getDataFolder(), "config.yml.bak-v1");
            if (!cfg.renameTo(bak)) {
                getLogger().warning("Could not back up config.yml to config.yml.bak-v1");
            } else {
                getLogger().info("Backed up v1 config.yml (global speed check) to config.yml.bak-v1");
            }
        }
        saveResource("config.yml", true);
    }

    @Override
    public void onDisable() {
        if (speedCheck != null) speedCheck.shutdown();
        if (timerCheck != null) timerCheck.shutdown();
        if (flyCheck != null) flyCheck.shutdown();
        if (oreFindCheck != null) oreFindCheck.shutdown();
        if (dataManager != null) dataManager.saveAll();
        getLogger().info("AdaptiveAntiCheat disabled. Data saved.");
    }

    public void reload() {
        reloadConfig();
        trustManager.reload();
        adaptiveManager.reload();
        speedCheck.reload();
        timerCheck.reload();
        flyCheck.reload();
        oreFindCheck.reload();
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
