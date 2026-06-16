package com.armaninyow.mobnumericalindicator.renderer;

import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        public final int       lightCoords;
        public final float    partialTick;
        public final List<ClientEffectCache.CachedEffect> effects;
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
            this.effects = ClientEffectCache.getEffects(this.entityId);

            net.minecraft.core.BlockPos lightPos = net.minecraft.core.BlockPos.containing(entity.getLightProbePosition(partialTick));
            int blockLight = entity.isOnFire() ? 15 : entity.level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, lightPos);
            int skyLight   = entity.level().getBrightness(net.minecraft.world.level.LightLayer.SKY, lightPos);
            this.lightCoords = net.minecraft.util.LightCoordsUtil.pack(blockLight, skyLight);
        }
    }

    private static final Map<Integer, Snapshot> PENDING = new HashMap<>();

    public static void put(Object renderState, Snapshot snapshot) {
        PENDING.put(System.identityHashCode(renderState), snapshot);
    }

    public static Snapshot take(Object renderState) {
        return PENDING.remove(System.identityHashCode(renderState));
    }

    public static void clear(int entityId) {
        PENDING.values().removeIf(s -> s.entityId == entityId);
        ClientEffectCache.onEntityRemoved(entityId);
    }
}