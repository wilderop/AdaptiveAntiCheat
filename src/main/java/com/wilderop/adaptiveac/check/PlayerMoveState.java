package com.wilderop.adaptiveac.check;

import org.bukkit.Location;

final class PlayerMoveState {
    Location lastLoc;
    int lastTick = -1;
    int ignoreUntilTick;
    int fireworkUntilTick;
    int windUntilTick;
    int riptideUntilTick;
    int lastSampleTick = Integer.MIN_VALUE;
    int lastSkipTick = Integer.MIN_VALUE;

    int airTicks;
    int hoverTicks;
    boolean flyLogged;

    int windowStartTick = -1;
    int windowMoveEvents;
    int lastTimerLogTick = Integer.MIN_VALUE;

    SpeedSession speedSession;
}
