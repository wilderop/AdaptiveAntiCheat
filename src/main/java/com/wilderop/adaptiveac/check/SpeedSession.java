package com.wilderop.adaptiveac.check;

final class SpeedSession {
    final MoveContext context;
    final String playerName;
    final boolean trusted;
    final double playtimeHours;
    final String vehicleType;

    final int startTick;
    int lastTick;
    int samples;
    int overSamples;
    double sumSpeed;
    double maxSpeed;
    double maxRatio;
    int pingSum;
    int pingSamples;

    SpeedSession(MoveContext context, String playerName, boolean trusted, double playtimeHours,
                 String vehicleType, int startTick) {
        this.context = context;
        this.playerName = playerName;
        this.trusted = trusted;
        this.playtimeHours = playtimeHours;
        this.vehicleType = vehicleType;
        this.startTick = startTick;
        this.lastTick = startTick;
    }

    void add(int tick, double speed, double ratio, int ping, boolean over) {
        lastTick = tick;
        samples++;
        sumSpeed += speed;
        if (speed > maxSpeed) maxSpeed = speed;
        if (ratio > maxRatio) maxRatio = ratio;
        pingSum += ping;
        pingSamples++;
        if (over) overSamples++;
    }

    double medianApprox() {
        // Running median is expensive; mean is enough for session summaries.
        return samples == 0 ? 0 : sumSpeed / samples;
    }

    double durationSeconds() {
        return (lastTick - startTick + 1) / 20.0;
    }

    int pingAvg() {
        return pingSamples == 0 ? 0 : pingSum / pingSamples;
    }
}
