package com.wilderop.adaptiveac.check;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

final class ContextResolver {

    record Resolved(MoveContext context, String vehicleType, boolean onIce, int speedAmp, boolean soulSpeed) {}

    static Resolved resolve(Player player, Location at, PlayerMoveState state, int tick) {
        String vehicleType = "none";
        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            vehicleType = vehicle.getType().name();
        }

        boolean onIce = isIce(blockUnder(at));
        boolean inWater = player.isInWater() || (vehicle != null && vehicle.isInWater());
        int speedAmp = 0;
        PotionEffect speed = player.getPotionEffect(PotionEffectType.SPEED);
        if (speed != null) {
            speedAmp = speed.getAmplifier() + 1;
        }
        boolean soulSpeed = hasSoulSpeed(player) && isSoul(blockUnder(at));

        MoveContext ctx;
        if (tick < state.riptideUntilTick || player.isRiptiding()) {
            ctx = MoveContext.RIPTIDE;
        } else if (player.isGliding() && tick < state.fireworkUntilTick) {
            ctx = MoveContext.ELYTRA_FIREWORK;
        } else if (player.isGliding()) {
            ctx = MoveContext.ELYTRA;
        } else if (vehicle != null && (onIce || isIceVehicle(vehicle))) {
            ctx = MoveContext.VEHICLE_ICE;
        } else if (vehicle != null && inWater) {
            ctx = MoveContext.VEHICLE_WATER;
        } else if (vehicle != null) {
            ctx = MoveContext.VEHICLE_LAND;
        } else if (tick < state.windUntilTick) {
            ctx = MoveContext.WIND_BURST;
        } else if (soulSpeed) {
            ctx = MoveContext.SOUL_SPEED;
        } else if (onIce) {
            ctx = MoveContext.ICE;
        } else if (speedAmp > 0) {
            ctx = MoveContext.SPEED_POTION;
        } else {
            ctx = MoveContext.GROUND;
        }

        return new Resolved(ctx, vehicleType, onIce, speedAmp, soulSpeed);
    }

    private static Block blockUnder(Location loc) {
        return loc.clone().subtract(0, 0.2, 0).getBlock();
    }

    static boolean isIce(Block block) {
        Material t = block.getType();
        return t == Material.ICE || t == Material.PACKED_ICE
                || t == Material.BLUE_ICE || t == Material.FROSTED_ICE;
    }

    private static boolean isSoul(Block block) {
        Material t = block.getType();
        return t == Material.SOUL_SAND || t == Material.SOUL_SOIL;
    }

    private static boolean isIceVehicle(Entity vehicle) {
        if (!(vehicle instanceof Boat)) return false;
        return isIce(vehicle.getLocation().clone().subtract(0, 0.2, 0).getBlock());
    }

    private static boolean hasSoulSpeed(Player player) {
        ItemStack boots = player.getInventory().getBoots();
        return boots != null && boots.containsEnchantment(Enchantment.SOUL_SPEED);
    }
}
