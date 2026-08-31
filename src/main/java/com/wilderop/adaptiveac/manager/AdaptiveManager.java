package com.wilderop.adaptiveac.manager;

import com.wilderop.adaptiveac.AdaptiveAC;
import com.wilderop.adaptiveac.check.MoveContext;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Core adaptive logic.
 * When a trusted player triggers a check, we treat it as a confirmed false positive
 * and slowly raise that check's tolerance multiplier.
 */
public class AdaptiveManager {

    private final AdaptiveAC plugin;
    private final Map<String, Double> multipliers = new HashMap<>();
    private final Map<String, Integer> falsePositiveCounts = new HashMap<>();

    private int fpsPerAdjustment;
    private double adjustmentAmount;
    private double maxMultiplier;
    private double minMultiplier;

    public AdaptiveManager(AdaptiveAC plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        fpsPerAdjustment = plugin.getConfig().getInt("adaptive.false-positives-per-adjustment", 8);
        adjustmentAmount = plugin.getConfig().getDouble("adaptive.adjustment-amount", 0.03);
        maxMultiplier = plugin.getConfig().getDouble("adaptive.max-multiplier", 1.75);
        minMultiplier = plugin.getConfig().getDouble("adaptive.min-multiplier", 0.85);

        multipliers.clear();
        falsePositiveCounts.clear();
        for (MoveContext ctx : MoveContext.values()) {
            loadCheck(ctx.checkKey());
        }
        loadCheck("ore.diamond");
        loadCheck("ore.debris");
    }

    private void loadCheck(String name) {
        multipliers.put(name, plugin.getDataManager().getMultiplier(name));
        falsePositiveCounts.put(name, plugin.getDataManager().getFalsePositiveCount(name));
    }

    public double getMultiplier(String checkName) {
        return multipliers.getOrDefault(checkName, 1.0);
    }

    /**
     * One trusted over-threshold session counts as a single false positive
     * for that context. Tick-level flags must not train the multiplier.
     */
    public void onTrustedSession(String checkName) {
        recordFalsePositive(checkName);
    }

    private void recordFalsePositive(String checkName) {
        int count = falsePositiveCounts.getOrDefault(checkName, 0) + 1;
        falsePositiveCounts.put(checkName, count);
        plugin.getDataManager().setFalsePositiveCount(checkName, count);

        if (count >= fpsPerAdjustment) {
            // Reset counter and raise multiplier
            falsePositiveCounts.put(checkName, 0);
            plugin.getDataManager().setFalsePositiveCount(checkName, 0);

            double current = multipliers.getOrDefault(checkName, 1.0);
            double next = Math.min(maxMultiplier, current + adjustmentAmount);

            if (next > current) {
                multipliers.put(checkName, next);
                plugin.getDataManager().setMultiplier(checkName, next);

                plugin.getLogger().log(Level.INFO,
                        "[Adaptive] Raised " + checkName + " multiplier from "
                                + String.format("%.3f", current) + " → " + String.format("%.3f", next)
                                + " after " + fpsPerAdjustment + " trusted false positives.");
            }
        }
    }

    public Map<String, Double> getAllMultipliers() {
        return Map.copyOf(multipliers);
    }

    public Map<String, Integer> getAllFalsePositiveCounts() {
        return Map.copyOf(falsePositiveCounts);
    }
}
