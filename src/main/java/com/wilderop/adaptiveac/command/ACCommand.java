package com.wilderop.adaptiveac.command;

import com.wilderop.adaptiveac.AdaptiveAC;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class ACCommand implements CommandExecutor, TabCompleter {

    private final AdaptiveAC plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public ACCommand(AdaptiveAC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("adaptiveac.admin")) {
            sender.sendMessage(mm.deserialize(plugin.getConfig().getString("messages.prefix", "") +
                    plugin.getConfig().getString("messages.no-permission", "<red>No permission.</red>")));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "trust" -> {
                if (args.length < 2) {
                    sender.sendMessage(mm.deserialize("<red>Usage: /ac trust <player></red>"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(mm.deserialize(plugin.getConfig().getString("messages.player-not-found")));
                    return true;
                }
                plugin.getTrustManager().trust(target.getUniqueId());
                sender.sendMessage(mm.deserialize("<green>Manually trusted " + target.getName() + "</green>"));
            }
            case "untrust" -> {
                if (args.length < 2) {
                    sender.sendMessage(mm.deserialize("<red>Usage: /ac untrust <player></red>"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(mm.deserialize(plugin.getConfig().getString("messages.player-not-found")));
                    return true;
                }
                plugin.getTrustManager().untrust(target.getUniqueId());
                sender.sendMessage(mm.deserialize("<yellow>Manually untrusted " + target.getName() + "</yellow>"));
            }
            case "info" -> {
                if (args.length < 2) {
                    sender.sendMessage(mm.deserialize("<red>Usage: /ac info <player></red>"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(mm.deserialize(plugin.getConfig().getString("messages.player-not-found")));
                    return true;
                }
                double hours = plugin.getTrustManager().getPlaytimeHours(target);
                boolean trusted = plugin.getTrustManager().isTrusted(target);
                sender.sendMessage(mm.deserialize("<gold>=== " + target.getName() + " ===</gold>"));
                sender.sendMessage(mm.deserialize("<gray>Playtime: <white>" + String.format("%.1f", hours) + " hours</white>"));
                sender.sendMessage(mm.deserialize("<gray>Trusted: " + (trusted ? "<green>yes</green>" : "<red>no</red>")));
                sender.sendMessage(mm.deserialize("<gray>Auto-trust threshold: <white>" + plugin.getTrustManager().getAutoTrustHours() + "h</white>"));
            }
            case "status", "thresholds" -> {
                sender.sendMessage(mm.deserialize("<gold>=== Adaptive Thresholds ===</gold>"));
                Map<String, Double> mults = plugin.getAdaptiveManager().getAllMultipliers();
                Map<String, Integer> fps = plugin.getAdaptiveManager().getAllFalsePositiveCounts();
                for (String check : mults.keySet()) {
                    double m = mults.get(check);
                    int count = fps.getOrDefault(check, 0);
                    sender.sendMessage(mm.deserialize(
                            "<gray>" + check + ": <white>x" + String.format("%.3f", m)
                                    + "</white>  (FP progress: " + count + "/"
                                    + plugin.getConfig().getInt("adaptive.false-positives-per-adjustment", 8) + ")"
                    ));
                }
                sender.sendMessage(mm.deserialize("<gray>Auto-trust hours: <white>" + plugin.getTrustManager().getAutoTrustHours() + "</white>"));
            }
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(mm.deserialize("<green>AdaptiveAntiCheat reloaded.</green>"));
            }
            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(mm.deserialize("<gold>AdaptiveAntiCheat commands:</gold>"));
        sender.sendMessage(mm.deserialize("<yellow>/ac trust <player></yellow> <gray>- Manually trust a player</gray>"));
        sender.sendMessage(mm.deserialize("<yellow>/ac untrust <player></yellow> <gray>- Manually untrust a player</gray>"));
        sender.sendMessage(mm.deserialize("<yellow>/ac info <player></yellow> <gray>- Show trust & playtime info</gray>"));
        sender.sendMessage(mm.deserialize("<yellow>/ac status</yellow> <gray>- Show current adaptive multipliers</gray>"));
        sender.sendMessage(mm.deserialize("<yellow>/ac reload</yellow> <gray>- Reload configuration</gray>"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("adaptiveac.admin")) return List.of();

        if (args.length == 1) {
            return filter(Arrays.asList("trust", "untrust", "info", "status", "thresholds", "reload"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust") || args[0].equalsIgnoreCase("info"))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String opt : options) {
            if (opt.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(opt);
            }
        }
        return result;
    }
}
