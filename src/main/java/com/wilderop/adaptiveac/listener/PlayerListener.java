package com.wilderop.adaptiveac.listener;

import com.wilderop.adaptiveac.AdaptiveAC;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerListener implements Listener {

    private final AdaptiveAC plugin;

    public PlayerListener(AdaptiveAC plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Future: optionally notify players the first time they become auto-trusted
        // For now this listener is a placeholder for expansion.
    }
}
