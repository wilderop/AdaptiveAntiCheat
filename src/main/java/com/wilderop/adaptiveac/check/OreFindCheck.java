package com.wilderop.adaptiveac.check;

import com.wilderop.adaptiveac.AdaptiveAC;
import com.wilderop.adaptiveac.util.CheckLogger;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Log-only x-ray heuristic: live ore-find rate plus path shape.
 * CoreProtect is a second-stage audit after a flag, never the counter.
 */
public class OreFindCheck implements Listener {

    private static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    enum Kind {
        DIAMOND("ore.diamond"),
        DEBRIS("ore.debris");

        final String checkKey;

        Kind(String checkKey) {
            this.checkKey = checkKey;
        }
    }

    enum SampleKind { WASTE, ORE }

    record Sample(int tick, String world, int x, int y, int z, SampleKind kind, Kind oreKind) {
        int manhattan(Sample other) {
            return Math.abs(x - other.x) + Math.abs(y - other.y) + Math.abs(z - other.z);
        }
    }

    static final class Trail {
        final ArrayDeque<Sample> samples = new ArrayDeque<>();
        int lastFlagTick = Integer.MIN_VALUE / 4;
    }

    private final AdaptiveAC plugin;
    private final CoreProtectAudit audit;
    private final Map<UUID, Trail> trails = new ConcurrentHashMap<>();
    private CheckLogger log;

    private boolean enabled;
    private int windowTicks;
    private int trailSize;
    private int cooldownTicks;
    private int minAxisLength;
    private boolean coreProtectEnabled;
    private int coreProtectSeconds;

    private Set<Material> diamondOres = EnumSet.noneOf(Material.class);
    private Set<Material> diamondWaste = EnumSet.noneOf(Material.class);
    private Set<Material> debrisOres = EnumSet.noneOf(Material.class);
    private Set<Material> debrisWaste = EnumSet.noneOf(Material.class);
    private int diamondMinY;
    private int diamondMaxY;
    private int debrisMinY;
    private int debrisMaxY;
    private int diamondMinOres;
    private int debrisMinOres;
    private double diamondDefaultWaste;
    private double debrisDefaultWaste;
    private double diamondFlagFactor;
    private double debrisFlagFactor;

    public OreFindCheck(AdaptiveAC plugin) {
        this.plugin = plugin;
        this.audit = new CoreProtectAudit(plugin);
        reload();
        this.log = new CheckLogger(plugin, "xray-sessions.log");
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("checks.ore.enabled", true);
        windowTicks = plugin.getConfig().getInt("checks.ore.window-seconds", 600) * 20;
        trailSize = plugin.getConfig().getInt("checks.ore.trail-size", 400);
        cooldownTicks = plugin.getConfig().getInt("checks.ore.cooldown-seconds", 180) * 20;
        minAxisLength = plugin.getConfig().getInt("checks.ore.min-axis-length", 8);
        coreProtectEnabled = plugin.getConfig().getBoolean("checks.ore.coreprotect.enabled", true);
        coreProtectSeconds = plugin.getConfig().getInt("checks.ore.coreprotect.lookup-seconds", 1800);

        diamondOres = materials("checks.ore.diamond.materials",
                List.of("DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE"));
        diamondWaste = materials("checks.ore.diamond.waste",
                List.of("STONE", "DEEPSLATE", "TUFF", "GRANITE", "DIORITE", "ANDESITE", "CALCITE", "SMOOTH_BASALT"));
        debrisOres = materials("checks.ore.debris.materials", List.of("ANCIENT_DEBRIS"));
        debrisWaste = materials("checks.ore.debris.waste",
                List.of("NETHERRACK", "BLACKSTONE", "BASALT", "SOUL_SOIL", "MAGMA_BLOCK"));

        diamondMinY = plugin.getConfig().getInt("checks.ore.diamond.min-y", -64);
        diamondMaxY = plugin.getConfig().getInt("checks.ore.diamond.max-y", 16);
        debrisMinY = plugin.getConfig().getInt("checks.ore.debris.min-y", 8);
        debrisMaxY = plugin.getConfig().getInt("checks.ore.debris.max-y", 22);
        diamondMinOres = plugin.getConfig().getInt("checks.ore.diamond.min-ores", 4);
        debrisMinOres = plugin.getConfig().getInt("checks.ore.debris.min-ores", 3);
        diamondDefaultWaste = plugin.getConfig().getDouble("checks.ore.diamond.default-waste-per-ore", 50.0);
        debrisDefaultWaste = plugin.getConfig().getDouble("checks.ore.debris.default-waste-per-ore", 80.0);
        diamondFlagFactor = plugin.getConfig().getDouble("checks.ore.diamond.flag-factor", 2.0);
        debrisFlagFactor = plugin.getConfig().getDouble("checks.ore.debris.flag-factor", 2.0);
    }

    public void shutdown() {
        trails.clear();
        if (log != null) log.close();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.hasPermission("adaptiveac.bypass") || player.hasPermission("adaptiveac.admin")) return;

        Block block = event.getBlock();
        Material type = block.getType();
        Kind oreKind = classifyOre(type);
        boolean waste = isWaste(type);
        if (oreKind == null && !waste) return;

        int tick = Bukkit.getCurrentTick();
        Trail trail = trails.computeIfAbsent(player.getUniqueId(), id -> new Trail());
        Kind sampleOreKind = oreKind != null ? oreKind : wasteKind(block.getWorld(), type);
        if (sampleOreKind == null) return;

        Sample sample = new Sample(
                tick,
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ(),
                oreKind != null ? SampleKind.ORE : SampleKind.WASTE,
                sampleOreKind);
        trail.samples.addLast(sample);
        prune(trail, tick);

        if (oreKind == null) return;
        if (!inYRange(oreKind, block.getY())) return;

        evaluate(player, trail, sample, block, tick);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        trails.remove(event.getPlayer().getUniqueId());
    }

    private void evaluate(Player player, Trail trail, Sample ore, Block block, int tick) {
        Kind kind = ore.oreKind();
        List<Sample> window = new ArrayList<>();
        for (Sample s : trail.samples) {
            if (tick - s.tick() > windowTicks) continue;
            if (!s.world().equals(ore.world())) continue;
            if (s.oreKind() != kind) continue;
            window.add(s);
        }

        int ores = 0;
        int waste = 0;
        List<Sample> wastePath = new ArrayList<>();
        for (Sample s : window) {
            if (s.kind() == SampleKind.ORE) ores++;
            else {
                waste++;
                wastePath.add(s);
            }
        }

        int minOres = kind == Kind.DIAMOND ? diamondMinOres : debrisMinOres;
        if (ores < minOres) return;

        double wastePerOre = waste / (double) ores;
        double multiplier = Math.max(0.01, plugin.getAdaptiveManager().getMultiplier(kind.checkKey));
        double baseline = kind == Kind.DIAMOND ? diamondDefaultWaste : debrisDefaultWaste;
        double factor = kind == Kind.DIAMOND ? diamondFlagFactor : debrisFlagFactor;
        double threshold = baseline / (factor * multiplier);
        boolean rateHot = wastePerOre < threshold;

        boolean enclosed = isEnclosed(block);
        boolean beeline = enclosed && isBeeline(wastePath, ore);
        int axisRun = axisRunEndingAt(wastePath, ore);
        boolean axisToOre = axisRun >= minAxisLength;
        boolean scout = enclosed && isScout(wastePath, ore);
        boolean patternHot = enclosed || beeline || axisToOre || scout;

        if (!rateHot || !patternHot) return;
        if (tick - trail.lastFlagTick < cooldownTicks) return;
        trail.lastFlagTick = tick;

        boolean trusted = plugin.getTrustManager().isTrusted(player);
        double hours = plugin.getTrustManager().getPlaytimeHours(player);
        String coCmd = String.format(Locale.US, "/co l u:%s t:%dm a:-block",
                player.getName(), Math.max(1, coreProtectSeconds / 60));

        String line = String.format(Locale.US,
                "SESSION xray kind=%s player=%s trusted=%s pt=%.1fh world=%s xyz=%d,%d,%d waste=%d ores=%d waste_per_ore=%.1f thresh=%.1f enclosed=%s beeline=%s axis_run=%d scout=%s co=%s",
                kind.name(),
                player.getName(),
                trusted,
                hours,
                ore.world(),
                ore.x(), ore.y(), ore.z(),
                waste,
                ores,
                wastePerOre,
                threshold,
                enclosed,
                beeline,
                axisRun,
                scout,
                coCmd);
        log.log(line);

        if (trusted) {
            plugin.getAdaptiveManager().onTrustedSession(kind.checkKey);
        }

        if (coreProtectEnabled) {
            Set<Material> oresSet = kind == Kind.DIAMOND ? diamondOres : debrisOres;
            Set<Material> wasteSet = kind == Kind.DIAMOND ? diamondWaste : debrisWaste;
            audit.audit(player, kind.name(), ore.world(), ore.x(), ore.y(), ore.z(),
                    coreProtectSeconds, oresSet, wasteSet, log);
        }
    }

    private void prune(Trail trail, int tick) {
        while (trail.samples.size() > trailSize) {
            trail.samples.removeFirst();
        }
        while (!trail.samples.isEmpty() && tick - trail.samples.peekFirst().tick() > windowTicks) {
            trail.samples.removeFirst();
        }
    }

    private Kind classifyOre(Material type) {
        if (diamondOres.contains(type)) return Kind.DIAMOND;
        if (debrisOres.contains(type)) return Kind.DEBRIS;
        return null;
    }

    private boolean isWaste(Material type) {
        return diamondWaste.contains(type) || debrisWaste.contains(type);
    }

    private Kind wasteKind(World world, Material type) {
        if (diamondWaste.contains(type) && world.getEnvironment() == World.Environment.NORMAL) return Kind.DIAMOND;
        if (debrisWaste.contains(type) && world.getEnvironment() == World.Environment.NETHER) return Kind.DEBRIS;
        if (diamondWaste.contains(type)) return Kind.DIAMOND;
        if (debrisWaste.contains(type)) return Kind.DEBRIS;
        return null;
    }

    private boolean inYRange(Kind kind, int y) {
        if (kind == Kind.DIAMOND) return y >= diamondMinY && y <= diamondMaxY;
        return y >= debrisMinY && y <= debrisMaxY;
    }

    static boolean isEnclosed(Block block) {
        for (BlockFace face : FACES) {
            Material neighbor = block.getRelative(face).getType();
            if (!neighbor.isOccluding()) return false;
        }
        return true;
    }

    static boolean isBeeline(List<Sample> waste, Sample ore) {
        if (waste.size() < 6) return false;
        int start = Math.max(0, waste.size() - 8);
        int closer = 0;
        int prev = waste.get(start).manhattan(ore);
        for (int i = start + 1; i < waste.size(); i++) {
            int d = waste.get(i).manhattan(ore);
            if (d < prev) closer++;
            prev = d;
        }
        int steps = waste.size() - start - 1;
        return steps >= 5 && closer >= steps - 1;
    }

    static int axisRunEndingAt(List<Sample> waste, Sample ore) {
        if (waste.isEmpty()) return 0;
        Sample last = waste.get(waste.size() - 1);
        if (last.manhattan(ore) != 1) return 0;
        int axis = axisOf(last, ore);
        if (axis < 0) return 0;
        int run = 1;
        for (int i = waste.size() - 2; i >= 0; i--) {
            if (axisOf(waste.get(i), waste.get(i + 1)) != axis) break;
            run++;
        }
        return run;
    }

    static boolean isScout(List<Sample> waste, Sample ore) {
        if (waste.size() < 2) return false;
        Sample hole = waste.get(waste.size() - 1);
        Sample before = waste.get(waste.size() - 2);
        return hole.manhattan(ore) == 1 && before.manhattan(hole) > 1;
    }

    /** 0=x, 1=y, 2=z, -1=diagonal/none. */
    static int axisOf(Sample a, Sample b) {
        int dx = Integer.compare(b.x(), a.x()) == 0 ? 0 : 1;
        int dy = Integer.compare(b.y(), a.y()) == 0 ? 0 : 1;
        int dz = Integer.compare(b.z(), a.z()) == 0 ? 0 : 1;
        if (dx + dy + dz != 1) return -1;
        if (dx == 1) return 0;
        if (dy == 1) return 1;
        return 2;
    }

    private Set<Material> materials(String path, List<String> defaults) {
        List<String> names = plugin.getConfig().getStringList(path);
        if (names.isEmpty()) names = defaults;
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (String name : names) {
            Material mat = Material.matchMaterial(name);
            if (mat != null) set.add(mat);
        }
        return set;
    }
}
