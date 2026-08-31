package com.wilderop.adaptiveac.check;

/**
 * Most-specific movement mode for a sample. Ice-boat and elytra must never
 * train the ground threshold.
 */
public enum MoveContext {
    RIPTIDE,
    ELYTRA_FIREWORK,
    ELYTRA,
    VEHICLE_ICE,
    VEHICLE_WATER,
    VEHICLE_LAND,
    WIND_BURST,
    SOUL_SPEED,
    ICE,
    SPEED_POTION,
    GROUND;

    public String checkKey() {
        return "speed." + name().toLowerCase();
    }
}
