package com.wilderop.adaptiveac.check;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MovementTracker {

    private final Map<UUID, PlayerMoveState> states = new ConcurrentHashMap<>();

    PlayerMoveState state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), id -> new PlayerMoveState());
    }

    public void reset(Player player) {
        states.remove(player.getUniqueId());
    }

    public void ignoreUntil(Player player, int tick) {
        PlayerMoveState s = state(player);
        s.ignoreUntilTick = Math.max(s.ignoreUntilTick, tick);
        s.lastLoc = null;
        s.lastTick = -1;
    }

    public void markFirework(Player player, int tick, int durationTicks) {
        state(player).fireworkUntilTick = tick + durationTicks;
    }

    public void markWind(Player player, int tick, int durationTicks) {
        state(player).windUntilTick = tick + durationTicks;
    }

    public void markRiptide(Player player, int tick, int durationTicks) {
        state(player).riptideUntilTick = tick + durationTicks;
    }

    public void remember(Player player, Location loc, int tick) {
        PlayerMoveState s = state(player);
        s.lastLoc = loc.clone();
        s.lastTick = tick;
    }
}
