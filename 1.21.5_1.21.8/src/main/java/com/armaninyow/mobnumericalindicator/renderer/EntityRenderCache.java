package com.armaninyow.mobnumericalindicator.renderer;

import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Bridges the 1.21.2 renderer refactor.
 *
 * In 1.21.2 Mojang split entity rendering into two phases:
 *   1. extractRenderState() — has access to the live LivingEntity
 *   2. render()             — only receives a LivingEntityRenderState snapshot
 *
 * We hook phase 1 to snapshot everything we need, keyed by render-state
 * identity (System.identityHashCode), then consume it in phase 2.
 */
public class EntityRenderCache {

    public static class Snapshot {
        public final int      entityId;
        public final float    health;
        public final float    maxHealth;
        public final int      armorValue;
        public final int      airSupply;
        public final int      maxAir;
        public final boolean  isInvisible;
        public final boolean  isAggressive;
        public final boolean  isFullyFrozen;
        public final boolean  hasAbsorption;
        public final boolean  hasWither;
        public final boolean  hasPoison;
        public final boolean  wearingArmor;
        public final float    partialTick;
        // We still need the entity for position/attachment math done in render()
        public final LivingEntity entity;

        public Snapshot(LivingEntity entity, float partialTick) {
            this.entityId      = entity.getId();
            this.health        = entity.getHealth();
            this.maxHealth     = entity.getMaxHealth();
            this.armorValue    = entity.getArmorValue();
            this.airSupply     = entity.getAirSupply();
            this.maxAir        = entity.getMaxAirSupply();
            this.isInvisible   = entity.isInvisible();
            this.isFullyFrozen = entity.isFullyFrozen();
            this.partialTick   = partialTick;
            this.entity        = entity;

            net.minecraft.world.entity.Mob mob =
                    entity instanceof net.minecraft.world.entity.Mob m ? m : null;
            this.isAggressive = mob != null && mob.getTarget() != null;

            this.hasAbsorption = entity.hasEffect(net.minecraft.world.effect.MobEffects.ABSORPTION);
            this.hasWither     = entity.hasEffect(net.minecraft.world.effect.MobEffects.WITHER);
            this.hasPoison     = entity.hasEffect(net.minecraft.world.effect.MobEffects.POISON);

            boolean armor = false;
            for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{
                    net.minecraft.world.entity.EquipmentSlot.HEAD,
                    net.minecraft.world.entity.EquipmentSlot.CHEST,
                    net.minecraft.world.entity.EquipmentSlot.LEGS,
                    net.minecraft.world.entity.EquipmentSlot.FEET
            }) {
                net.minecraft.world.item.ItemStack stack = entity.getItemBySlot(slot);
                if (!stack.isEmpty() && stack.has(net.minecraft.core.component.DataComponents.EQUIPPABLE)) {
                    armor = true;
                    break;
                }
            }
            this.wearingArmor = armor;
        }
    }

    // Keyed by render-state identity hash; populated in extractRenderState, consumed in render
    private static final Map<Integer, Snapshot> PENDING = new HashMap<>();

    public static void put(Object renderState, Snapshot snapshot) {
        PENDING.put(System.identityHashCode(renderState), snapshot);
    }

    public static Snapshot take(Object renderState) {
        return PENDING.remove(System.identityHashCode(renderState));
    }

    public static void clear(int entityId) {
        // Belt-and-suspenders: also prune by entity id on unload
        PENDING.values().removeIf(s -> s.entityId == entityId);
    }
}