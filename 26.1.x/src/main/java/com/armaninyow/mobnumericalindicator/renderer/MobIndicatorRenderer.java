package com.armaninyow.mobnumericalindicator.renderer;

import com.armaninyow.mobnumericalindicator.config.ModConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MobIndicatorRenderer {

    private static final Identifier GUI_ATLAS          = net.minecraft.data.AtlasIds.GUI;
    private static final Identifier HEART_CONTAINER    = Identifier.withDefaultNamespace("hud/heart/container");
    private static final Identifier HEART_CONTAINER_HC = Identifier.withDefaultNamespace("hud/heart/container_hardcore");
    private static final Identifier HEART_FULL         = Identifier.withDefaultNamespace("hud/heart/full");
    private static final Identifier HEART_FULL_HC      = Identifier.withDefaultNamespace("hud/heart/hardcore_full");
    private static final Identifier HEART_ABSORBING    = Identifier.withDefaultNamespace("hud/heart/absorbing_full");
    private static final Identifier HEART_FROZEN       = Identifier.withDefaultNamespace("hud/heart/frozen_full");
    private static final Identifier HEART_FROZEN_HC    = Identifier.withDefaultNamespace("hud/heart/frozen_hardcore_full");
    private static final Identifier HEART_WITHERED     = Identifier.withDefaultNamespace("hud/heart/withered_full");
    private static final Identifier HEART_WITHERED_HC  = Identifier.withDefaultNamespace("hud/heart/withered_hardcore_full");
    private static final Identifier HEART_POISONED     = Identifier.withDefaultNamespace("hud/heart/poisoned_full");
    private static final Identifier HEART_POISONED_HC  = Identifier.withDefaultNamespace("hud/heart/poisoned_hardcore_full");
    private static final Identifier HEART_ABSORBING_HC = Identifier.withDefaultNamespace("hud/heart/absorbing_hardcore_full");
    private static final Identifier ARMOR_FULL         = Identifier.withDefaultNamespace("hud/armor_full");
    private static final Identifier ARMOR_EMPTY        = Identifier.withDefaultNamespace("hud/armor_empty");
    private static final Identifier AIR               = Identifier.withDefaultNamespace("hud/air");
    private static final Identifier AIR_EMPTY         = Identifier.withDefaultNamespace("hud/air_empty");

    private static final int   ICON_SIZE         = 9;
    private static final int   GAP               = 2;
    private static final int   ROW_SPACING       = 12;
    private static final long  FLOAT_DURATION_MS = 1000L;
    private static final float FLOAT_TRAVEL      = 7f;

    private static class FloatEntry {
        final String text;
        final boolean isDamage;
        final long startTime;
        FloatEntry(String text, boolean isDamage, long startTime) {
            this.text = text; this.isDamage = isDamage; this.startTime = startTime;
        }
    }

    private static final Map<Integer, Integer>    lastHealth  = new HashMap<>();
    private static final Map<Integer, FloatEntry> damageFloat = new HashMap<>();
    private static final Map<Integer, FloatEntry> healFloat   = new HashMap<>();

    public static void clearEntity(int entityId) {
        lastHealth.remove(entityId);
        damageFloat.remove(entityId);
        healFloat.remove(entityId);
        EntityRenderCache.clear(entityId);
    }

    public static void render(
            EntityRenderCache.Snapshot snap,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraState
    ) {
        Minecraft mc = Minecraft.getInstance();
        ModConfig  cfg = ModConfig.get();

        if (mc.player == null || mc.level == null) return;

        LivingEntity entity = snap.entity;
        if (entity == mc.player) return;
        if (entity == mc.player.getVehicle()) return;

        if (!cfg.showWhenInvisible && snap.isInvisible) return;

        boolean shouldShow = cfg.showAlways;
        if (cfg.showWhenAggressive && snap.isAggressive)             shouldShow = true;
        if (cfg.showWhenDamaged    && snap.health < snap.maxHealth)  shouldShow = true;
        if (cfg.showWhenLookedAt   && isLookedAt(entity, mc, cfg.maxDistance)) shouldShow = true;
        if (!shouldShow) return;

        double dist = mc.player.distanceTo(entity);
        if (dist > cfg.maxDistance) return;
        if (!hasLineOfSight(mc, entity)) return;

        int  entityId      = snap.entityId;
        int  currentHealth = (int) Math.ceil(snap.health);
        long now           = System.currentTimeMillis();

        if (lastHealth.containsKey(entityId)) {
            int prev = lastHealth.get(entityId);
            if (currentHealth < prev) damageFloat.put(entityId, new FloatEntry("-" + (prev - currentHealth), true, now));
            else if (currentHealth > prev) healFloat.put(entityId, new FloatEntry("+" + (currentHealth - prev), false, now));
        }
        lastHealth.put(entityId, currentHealth);

        List<ClientEffectCache.CachedEffect> effects = snap.effects;
        long gameTick = mc.level.getGameTime();

        Vec3 attachPoint = entity.getAttachments().getNullable(
                EntityAttachment.NAME_TAG, 0, entity.getViewYRot(snap.partialTick)
        );
        if (attachPoint == null) return;

        float distScale = (float)(1.0 - (dist / cfg.maxDistance) * 0.5);
        float scale     = cfg.scale * distScale;

        poseStack.pushPose();
        poseStack.translate(attachPoint.x, attachPoint.y + 0.5 + cfg.yOffset * 0.1, attachPoint.z);
        poseStack.mulPose(mc.getEntityRenderDispatcher().camera.rotation());
        poseStack.scale(0.025f * scale, -0.025f * scale, 0.025f * scale);

        boolean hardcore    = mc.level.getLevelData().isHardcore();
        int     armorValue  = snap.armorValue;
        int     airSupply   = snap.airSupply;
        int     maxAir      = snap.maxAir;
        boolean hasArmor    = snap.wearingArmor || (cfg.showArmorAtZero && snap.canWearArmor);
        boolean hasAir      = airSupply < maxAir;
        int     lightCoords = snap.lightCoords;

        TextureAtlas guiAtlas = mc.getAtlasManager().getAtlasOrThrow(GUI_ATLAS);

        if (!effects.isEmpty()) {
            int effectsRowWidth = computeEffectsRowWidth(mc, effects, gameTick);
            int ex = -effectsRowWidth / 2;

            for (ClientEffectCache.CachedEffect cached : effects) {
                int remainingSecs = cached.getRemainingSeconds(gameTick);
                if (remainingSecs == 0) continue;
                Identifier spriteId = Gui.getMobEffectSprite(cached.instance.getEffect());
                submitSprite(collector, poseStack, guiAtlas, spriteId, ex, -ROW_SPACING, lightCoords);
                ex += ICON_SIZE + GAP;

                String durationStr = String.valueOf(remainingSecs);
                submitText(collector, poseStack, mc, durationStr, ex, -ROW_SPACING + 1, 0xFFFFFFFF, lightCoords, cfg.showTextShadow);
                ex += mc.font.width(durationStr) + GAP * 2;
            }
        }

        int rowWidth = computeRowWidth(mc, snap.health, armorValue, airSupply, hasArmor, hasAir);
        int cursorX  = -rowWidth / 2;
        int heartX   = cursorX;

        submitSprite(collector, poseStack, guiAtlas, hardcore ? HEART_CONTAINER_HC : HEART_CONTAINER,
                cursorX, 0, lightCoords);
        submitSprite(collector, poseStack, guiAtlas, resolveHeartTexture(snap, hardcore),
                cursorX, 0, lightCoords);
        cursorX += ICON_SIZE + GAP;

        String healthStr = String.valueOf(currentHealth);
        submitText(collector, poseStack, mc, healthStr, cursorX, 1, 0xFFFFFFFF, lightCoords, cfg.showTextShadow);
        cursorX += mc.font.width(healthStr) + GAP * 2;

        if (hasArmor) {
            submitSprite(collector, poseStack, guiAtlas, armorValue > 0 ? ARMOR_FULL : ARMOR_EMPTY,
                    cursorX, 0, lightCoords);
            cursorX += ICON_SIZE + GAP;
            String armorStr = String.valueOf(armorValue);
            submitText(collector, poseStack, mc, armorStr, cursorX, 1, 0xFFFFFFFF, lightCoords, cfg.showTextShadow);
            cursorX += mc.font.width(armorStr) + GAP * 2;
        }

        if (hasAir) {
            submitSprite(collector, poseStack, guiAtlas, airSupply <= 0 ? AIR_EMPTY : AIR,
                    cursorX, 0, lightCoords);
            cursorX += ICON_SIZE + GAP;
            String airStr = String.valueOf(Math.max(0, airSupply / 20));
            submitText(collector, poseStack, mc, airStr, cursorX, 1, 0xFFFFFFFF, lightCoords, cfg.showTextShadow);
        }

        FloatEntry dmg = damageFloat.get(entityId);
        if (dmg != null) {
            float elapsed = (now - dmg.startTime) / (float) FLOAT_DURATION_MS;
            if (elapsed >= 1f) {
                damageFloat.remove(entityId);
            } else {
                float floatY = FLOAT_TRAVEL * (1f - (float) Math.pow(1f - elapsed, 5));
                int   textW  = mc.font.width(dmg.text);
                submitText(collector, poseStack, mc, dmg.text, heartX - GAP - textW, (int) floatY,
                        0xFFFF5555, lightCoords, cfg.showTextShadow);
            }
        }

        FloatEntry heal = healFloat.get(entityId);
        if (heal != null) {
            float elapsed = (now - heal.startTime) / (float) FLOAT_DURATION_MS;
            if (elapsed >= 1f) {
                healFloat.remove(entityId);
            } else {
                float floatY = -(FLOAT_TRAVEL * (1f - (float) Math.pow(1f - elapsed, 5)));
                int   textW  = mc.font.width(heal.text);
                submitText(collector, poseStack, mc, heal.text, heartX - GAP - textW, (int) floatY,
                        0xFF55FF55, lightCoords, cfg.showTextShadow);
            }
        }

        poseStack.popPose();
    }

    private static void submitSprite(
            SubmitNodeCollector collector, PoseStack poseStack, TextureAtlas atlas,
            Identifier sprite, int x, int y, int lightCoords
    ) {
        TextureAtlasSprite tex = atlas.getSprite(sprite);
        int lightU = lightCoords & 0xFFFF;
        int lightV = (lightCoords >> 16) & 0xFFFF;
        collector.submitCustomGeometry(poseStack, GlyphRenderTypes.createForColorTexture(tex.atlasLocation()).normal(),
                (pose, buf) -> {
                    Matrix4f mat = pose.pose();
                    buf.addVertex(mat, x,             y,             0).setColor(0xFFFFFFFF).setUv(tex.getU0(), tex.getV0()).setUv2(lightU, lightV);
                    buf.addVertex(mat, x,             y + ICON_SIZE, 0).setColor(0xFFFFFFFF).setUv(tex.getU0(), tex.getV1()).setUv2(lightU, lightV);
                    buf.addVertex(mat, x + ICON_SIZE, y + ICON_SIZE, 0).setColor(0xFFFFFFFF).setUv(tex.getU1(), tex.getV1()).setUv2(lightU, lightV);
                    buf.addVertex(mat, x + ICON_SIZE, y,             0).setColor(0xFFFFFFFF).setUv(tex.getU1(), tex.getV0()).setUv2(lightU, lightV);
                });
    }

    private static void submitText(
            SubmitNodeCollector collector, PoseStack poseStack, Minecraft mc,
            String text, int x, int y, int color, int lightCoords, boolean shadow
    ) {
        FormattedCharSequence seq = FormattedCharSequence.forward(text, net.minecraft.network.chat.Style.EMPTY);
        collector.submitText(poseStack, x, y, seq, shadow, Font.DisplayMode.NORMAL, lightCoords, color, 0, 0);
    }

    private static int computeRowWidth(Minecraft mc, float health, int armor, int airSupply,
                                       boolean hasArmor, boolean hasAir) {
        int w = ICON_SIZE + GAP + mc.font.width(String.valueOf((int) Math.ceil(health))) + GAP * 2;
        if (hasArmor) w += ICON_SIZE + GAP + mc.font.width(String.valueOf(armor)) + GAP * 2;
        if (hasAir)   w += ICON_SIZE + GAP + mc.font.width(String.valueOf(Math.max(0, airSupply / 20)));
        return w;
    }

    private static int computeEffectsRowWidth(Minecraft mc, List<ClientEffectCache.CachedEffect> effects, long gameTick) {
        int w = 0;
        for (ClientEffectCache.CachedEffect cached : effects) {
                int remainingSecs = cached.getRemainingSeconds(gameTick);
                if (remainingSecs == 0) continue;
            int secs = cached.getRemainingSeconds(gameTick);
            if (secs > 0) w += ICON_SIZE + GAP + mc.font.width(String.valueOf(secs)) + GAP * 2;
        }
        return w;
    }

    private static Identifier resolveHeartTexture(EntityRenderCache.Snapshot snap, boolean hardcore) {
        if (snap.hasAbsorption) return hardcore ? HEART_ABSORBING_HC : HEART_ABSORBING;
        if (snap.hasWither)     return hardcore ? HEART_WITHERED_HC  : HEART_WITHERED;
        if (snap.hasPoison)     return hardcore ? HEART_POISONED_HC  : HEART_POISONED;
        if (snap.isFullyFrozen) return hardcore ? HEART_FROZEN_HC    : HEART_FROZEN;
        if (hardcore)           return HEART_FULL_HC;
        return HEART_FULL;
    }

    private static boolean isLookedAt(LivingEntity entity, Minecraft mc, int maxDistance) {
        if (mc.player == null) return false;
        Vec3 eyePos  = mc.player.getEyePosition();
        Vec3 lookVec = mc.player.getLookAngle();
        Vec3 reach   = eyePos.add(lookVec.scale(maxDistance));
        AABB box     = entity.getBoundingBox().inflate(entity.getPickRadius());
        return box.clip(eyePos, reach).isPresent();
    }

    private static boolean hasLineOfSight(Minecraft mc, LivingEntity entity) {
        Vec3 from = mc.getEntityRenderDispatcher().camera.position();
        Vec3 to   = entity.position().add(0, entity.getBbHeight() / 2.0, 0);
        BlockHitResult result = mc.level.clip(new ClipContext(
                from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity
        ));
        return result.getType() == HitResult.Type.MISS;
    }
}