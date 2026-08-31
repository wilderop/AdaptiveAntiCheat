package com.wilderop.adaptiveac.check;

import com.wilderop.adaptiveac.AdaptiveAC;
import com.wilderop.adaptiveac.util.CheckLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

/**
 * Second-stage x-ray audit. Runs only after {@link OreFindCheck} flags,
 * and only if CoreProtect is present. Lookups are async so SQLite does not hitch.
 */
final class CoreProtectAudit {

    private final AdaptiveAC plugin;

    CoreProtectAudit(AdaptiveAC plugin) {
        this.plugin = plugin;
    }

    void audit(Player player, String kind, String world, int x, int y, int z,
               int lookupSeconds, Set<Material> ores, Set<Material> waste, CheckLogger log) {
        String name = player.getName();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String line;
            try {
                line = lookup(name, kind, world, x, y, z, lookupSeconds, ores, waste);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "CoreProtect x-ray audit failed for " + name, e);
                return;
            }
            if (line != null) {
                log.log(line);
            }
        });
    }

    private String lookup(String player, String kind, String world, int x, int y, int z,
                          int lookupSeconds, Set<Material> ores, Set<Material> waste) throws Exception {
        Plugin cp = Bukkit.getPluginManager().getPlugin("CoreProtect");
        if (cp == null || !cp.isEnabled()) {
            return String.format(Locale.US,
                    "AUDIT xray kind=%s player=%s skipped=coreprotect-missing xyz=%d,%d,%d",
                    kind, player, x, y, z);
        }

        Object api = cp.getClass().getMethod("getAPI").invoke(cp);
        if (api == null) {
            return String.format(Locale.US,
                    "AUDIT xray kind=%s player=%s skipped=coreprotect-api-null xyz=%d,%d,%d",
                    kind, player, x, y, z);
        }

        Integer version = (Integer) api.getClass().getMethod("APIVersion").invoke(api);
        if (version == null || version < 9) {
            return String.format(Locale.US,
                    "AUDIT xray kind=%s player=%s skipped=coreprotect-api=%s xyz=%d,%d,%d",
                    kind, player, version, x, y, z);
        }

        List<Object> restrict = new ArrayList<>();
        restrict.addAll(ores);
        restrict.addAll(waste);

        Method perform = api.getClass().getMethod(
                "performLookup",
                int.class, List.class, List.class, List.class, List.class, List.class, int.class, Location.class);
        @SuppressWarnings("unchecked")
        List<String[]> rows = (List<String[]>) perform.invoke(
                api,
                lookupSeconds,
                List.of(player),
                null,
                restrict,
                null,
                List.of(0),
                0,
                null);
        if (rows == null) {
            return String.format(Locale.US,
                    "AUDIT xray kind=%s player=%s skipped=coreprotect-disabled xyz=%d,%d,%d",
                    kind, player, x, y, z);
        }

        Method parseResult = api.getClass().getMethod("parseResult", String[].class);
        List<Break> breaks = new ArrayList<>();
        for (String[] row : rows) {
            if (row == null || row.length < 8) continue;
            Object parsed = parseResult.invoke(api, (Object) row);
            int px = (Integer) parsed.getClass().getMethod("getX").invoke(parsed);
            int py = (Integer) parsed.getClass().getMethod("getY").invoke(parsed);
            int pz = (Integer) parsed.getClass().getMethod("getZ").invoke(parsed);
            long ts = (Long) parsed.getClass().getMethod("getTimestamp").invoke(parsed);
            Material type = (Material) parsed.getClass().getMethod("getType").invoke(parsed);
            if (type == null) continue;
            boolean ore = ores.contains(type);
            boolean w = waste.contains(type);
            if (!ore && !w) continue;
            breaks.add(new Break(ts, px, py, pz, ore));
        }
        breaks.sort(Comparator.comparingLong(b -> b.ts));

        int oreCount = 0;
        int wasteCount = 0;
        int axisToOre = 0;
        int maxAxis = 0;
        int currentAxis = -1;
        int currentLen = 0;
        Break prev = null;
        for (Break b : breaks) {
            if (b.ore) oreCount++;
            else wasteCount++;
            if (prev != null) {
                int axis = axisOf(prev, b);
                if (axis >= 0 && axis == currentAxis) {
                    currentLen++;
                } else if (axis >= 0) {
                    currentAxis = axis;
                    currentLen = 1;
                } else {
                    currentAxis = -1;
                    currentLen = 0;
                }
                if (currentLen > maxAxis) maxAxis = currentLen;
                if (b.ore && currentLen >= 8) axisToOre++;
            }
            prev = b;
        }

        double wastePerOre = oreCount == 0 ? wasteCount : wasteCount / (double) oreCount;
        String coCmd = String.format(Locale.US, "/co l u:%s t:%dm a:-block", player, Math.max(1, lookupSeconds / 60));
        return String.format(Locale.US,
                "AUDIT xray kind=%s player=%s world=%s xyz=%d,%d,%d breaks=%d waste=%d ores=%d waste_per_ore=%.1f max_axis=%d axis_to_ore=%d co=%s",
                kind, player, world, x, y, z,
                breaks.size(), wasteCount, oreCount, wastePerOre, maxAxis, axisToOre, coCmd);
    }

    private static int axisOf(Break a, Break b) {
        int dx = a.x == b.x ? 0 : 1;
        int dy = a.y == b.y ? 0 : 1;
        int dz = a.z == b.z ? 0 : 1;
        if (dx + dy + dz != 1) return -1;
        if (dx == 1) return 0;
        if (dy == 1) return 1;
        return 2;
    }

    private record Break(long ts, int x, int y, int z, boolean ore) {}
}
