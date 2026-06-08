package com.armaninyow.mobnumericalindicator.renderer;

import com.armaninyow.mobnumericalindicator.config.ModConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

public class MobIndicatorRenderer {

	// Vanilla HUD sprite paths — no .png extension, accessed via sprite atlas
	private static final ResourceLocation HEART_CONTAINER    = ResourceLocation.withDefaultNamespace("hud/heart/container");
	private static final ResourceLocation HEART_CONTAINER_HC = ResourceLocation.withDefaultNamespace("hud/heart/container_hardcore");
	private static final ResourceLocation HEART_FULL         = ResourceLocation.withDefaultNamespace("hud/heart/full");
	private static final ResourceLocation HEART_FULL_HC      = ResourceLocation.withDefaultNamespace("hud/heart/hardcore_full");
	private static final ResourceLocation HEART_ABSORBING    = ResourceLocation.withDefaultNamespace("hud/heart/absorbing_full");
	private static final ResourceLocation HEART_FROZEN       = ResourceLocation.withDefaultNamespace("hud/heart/frozen_full");
	private static final ResourceLocation HEART_FROZEN_HC    = ResourceLocation.withDefaultNamespace("hud/heart/frozen_hardcore_full");
	private static final ResourceLocation HEART_WITHERED     = ResourceLocation.withDefaultNamespace("hud/heart/withered_full");
	private static final ResourceLocation HEART_WITHERED_HC  = ResourceLocation.withDefaultNamespace("hud/heart/withered_hardcore_full");
	private static final ResourceLocation HEART_POISONED     = ResourceLocation.withDefaultNamespace("hud/heart/poisoned_full");
	private static final ResourceLocation HEART_POISONED_HC  = ResourceLocation.withDefaultNamespace("hud/heart/poisoned_hardcore_full");
	private static final ResourceLocation HEART_ABSORBING_HC = ResourceLocation.withDefaultNamespace("hud/heart/absorbing_hardcore_full");
	private static final ResourceLocation ARMOR_FULL         = ResourceLocation.withDefaultNamespace("hud/armor_full");
	private static final ResourceLocation ARMOR_EMPTY        = ResourceLocation.withDefaultNamespace("hud/armor_empty");
	private static final ResourceLocation AIR                = ResourceLocation.withDefaultNamespace("hud/air");
	private static final ResourceLocation AIR_EMPTY       = ResourceLocation.withDefaultNamespace("hud/air_empty");

	private static final int   ICON_SIZE         = 9;
	private static final int   GAP               = 2;
	private static final long  FLOAT_DURATION_MS = 1000L;
	private static final float FLOAT_TRAVEL      = 7f;

	// -------------------------------------------------------------------------
	// Floating indicator state
	// -------------------------------------------------------------------------

	private static class FloatEntry {
		final String text;
		final boolean isDamage;
		final long startTime;

		FloatEntry(String text, boolean isDamage, long startTime) {
			this.text      = text;
			this.isDamage  = isDamage;
			this.startTime = startTime;
		}
	}

	private static final Map<Integer, Integer>    lastHealth   = new HashMap<>();
	private static final Map<Integer, FloatEntry> damageFloat  = new HashMap<>();
	private static final Map<Integer, FloatEntry> healFloat    = new HashMap<>();

	public static void clearEntity(int entityId) {
		lastHealth.remove(entityId);
		damageFloat.remove(entityId);
		healFloat.remove(entityId);
		EntityRenderCache.clear(entityId);
	}

	// -------------------------------------------------------------------------
	// Main render entry point (1.21.2+) — receives a Snapshot instead of entity
	// -------------------------------------------------------------------------

	public static void render(
			EntityRenderCache.Snapshot snap,
			PoseStack poseStack,
			MultiBufferSource bufferSource,
			int packedLight
	) {
		Minecraft mc = Minecraft.getInstance();
		ModConfig  cfg = ModConfig.get();

		if (mc.player == null || mc.level == null) return;

		LivingEntity entity = snap.entity;
		if (entity == mc.player) return;
		if (entity == mc.player.getVehicle()) return;

		// --- Invisibility hard filter ---
		if (!cfg.showWhenInvisible && snap.isInvisible) return;

		// --- Visibility conditions (OR logic) ---
		boolean anyConditionEnabled = cfg.showWhenAggressive || cfg.showWhenDamaged || cfg.showWhenLookedAt;
		boolean shouldShow = !anyConditionEnabled;
		if (cfg.showWhenAggressive && snap.isAggressive) shouldShow = true;
		if (cfg.showWhenDamaged && snap.health < snap.maxHealth) shouldShow = true;
		if (cfg.showWhenLookedAt && isLookedAt(entity, mc, cfg.maxDistance)) shouldShow = true;
		if (!shouldShow) return;

		// --- Distance check ---
		double dist = mc.player.distanceTo(entity);
		if (dist > cfg.maxDistance) return;

		// --- Line of sight check ---
		if (!hasLineOfSight(mc, entity)) return;

		// --- Health delta detection ---
		int  entityId      = snap.entityId;
		int  currentHealth = (int) Math.ceil(snap.health);
		long now           = System.currentTimeMillis();

		if (lastHealth.containsKey(entityId)) {
			int prev = lastHealth.get(entityId);
			if (currentHealth < prev) {
				int delta = prev - currentHealth;
				damageFloat.put(entityId, new FloatEntry("-" + delta, true, now));
			} else if (currentHealth > prev) {
				int delta = currentHealth - prev;
				healFloat.put(entityId, new FloatEntry("+" + delta, false, now));
			}
		}
		lastHealth.put(entityId, currentHealth);

		// --- Resolve NAME_TAG attachment point ---
		Vec3 attachPoint = entity.getAttachments().getNullable(
				EntityAttachment.NAME_TAG, 0, entity.getViewYRot(snap.partialTick)
		);
		if (attachPoint == null) return;

		// --- Distance-based scale ---
		float distScale = (float) (1.0 - (dist / cfg.maxDistance) * 0.5);
		float scale     = cfg.scale * distScale;

		poseStack.pushPose();
		poseStack.translate(attachPoint.x, attachPoint.y + 0.5, attachPoint.z);
		poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
		poseStack.scale(0.025f * scale, -0.025f * scale, 0.025f * scale);

		// --- Gather values ---
		boolean hardcore  = mc.level.getLevelData().isHardcore();
		float   health    = snap.health;
		int     armorValue = snap.armorValue;
		int     airSupply  = snap.airSupply;
		int     maxAir     = snap.maxAir;
		boolean hasArmor   = snap.wearingArmor || cfg.showArmorAtZero;
		boolean hasAir     = airSupply < maxAir;

		// --- Compute row width to center ---
		int rowWidth = computeRowWidth(mc, health, armorValue, airSupply, hasArmor, hasAir);
		int cursorX  = -rowWidth / 2;
		int cursorY  = 0;
		int heartX   = cursorX;

		// --- Health ---
		ResourceLocation containerTex = hardcore ? HEART_CONTAINER_HC : HEART_CONTAINER;
		drawSprite(poseStack, bufferSource, packedLight, containerTex, heartX, cursorY, ICON_SIZE, ICON_SIZE);
		ResourceLocation heartTex = resolveHeartTexture(snap, hardcore);
		drawSprite(poseStack, bufferSource, packedLight, heartTex, heartX, cursorY, ICON_SIZE, ICON_SIZE);
		cursorX += ICON_SIZE + GAP;

		String healthStr = String.valueOf(currentHealth);
		drawText(mc, poseStack, bufferSource, packedLight, healthStr, cursorX, cursorY + 1);
		cursorX += mc.font.width(healthStr) + GAP * 2;

		// --- Armor ---
		if (hasArmor) {
			ResourceLocation armorTex = armorValue > 0 ? ARMOR_FULL : ARMOR_EMPTY;
			drawSprite(poseStack, bufferSource, packedLight, armorTex, cursorX, cursorY, ICON_SIZE, ICON_SIZE);
			cursorX += ICON_SIZE + GAP;

			String armorStr = String.valueOf(armorValue);
			drawText(mc, poseStack, bufferSource, packedLight, armorStr, cursorX, cursorY + 1);
			cursorX += mc.font.width(armorStr) + GAP * 2;
		}

		// --- Oxygen ---
		if (hasAir) {
			boolean bursting = airSupply <= 0;
			ResourceLocation airTex = bursting ? AIR_EMPTY : AIR;
			drawSprite(poseStack, bufferSource, packedLight, airTex, cursorX, cursorY, ICON_SIZE, ICON_SIZE);
			cursorX += ICON_SIZE + GAP;

			String airStr = String.valueOf(Math.max(0, airSupply / 20));
			drawText(mc, poseStack, bufferSource, packedLight, airStr, cursorX, cursorY + 1);
		}

		// --- Floating damage/heal indicators ---
		FloatEntry dmg = damageFloat.get(entityId);
		if (dmg != null) {
			float elapsed = (now - dmg.startTime) / (float) FLOAT_DURATION_MS;
			if (elapsed >= 1f) {
				damageFloat.remove(entityId);
			} else {
				float eased  = 1f - (float) Math.pow(1f - elapsed, 5);
				float floatY = FLOAT_TRAVEL * eased;
				int   textW  = mc.font.width(dmg.text);
				int   textX  = heartX - GAP - textW;
				drawColoredText(mc, poseStack, bufferSource, packedLight, dmg.text, textX, (int) floatY, 0xFF5555, 0x3F1515, cfg.showTextShadow);
			}
		}

		FloatEntry heal = healFloat.get(entityId);
		if (heal != null) {
			float elapsed = (now - heal.startTime) / (float) FLOAT_DURATION_MS;
			if (elapsed >= 1f) {
				healFloat.remove(entityId);
			} else {
				float eased  = 1f - (float) Math.pow(1f - elapsed, 5);
				float floatY = -(FLOAT_TRAVEL * eased);
				int   textW  = mc.font.width(heal.text);
				int   textX  = heartX - GAP - textW;
				drawColoredText(mc, poseStack, bufferSource, packedLight, heal.text, textX, (int) floatY, 0x55FF55, 0x153F15, cfg.showTextShadow);
			}
		}

		poseStack.popPose();
	}

	// -------------------------------------------------------------------------
	// Rendering helpers
	// -------------------------------------------------------------------------

	private static void drawSprite(
			PoseStack poseStack,
			MultiBufferSource bufferSource,
			int packedLight,
			ResourceLocation sprite,
			int x, int y, int w, int h
	) {
		net.minecraft.client.gui.GuiSpriteManager sprites = Minecraft.getInstance().getGuiSprites();
		net.minecraft.client.renderer.texture.TextureAtlasSprite tex = sprites.getSprite(sprite);
		VertexConsumer buf = bufferSource.getBuffer(RenderType.text(tex.atlasLocation()));
		Matrix4f mat = poseStack.last().pose();
		buf.addVertex(mat, x,     y,     0).setColor(1f, 1f, 1f, 1f).setUv(tex.getU0(), tex.getV0()).setLight(packedLight);
		buf.addVertex(mat, x,     y + h, 0).setColor(1f, 1f, 1f, 1f).setUv(tex.getU0(), tex.getV1()).setLight(packedLight);
		buf.addVertex(mat, x + w, y + h, 0).setColor(1f, 1f, 1f, 1f).setUv(tex.getU1(), tex.getV1()).setLight(packedLight);
		buf.addVertex(mat, x + w, y,     0).setColor(1f, 1f, 1f, 1f).setUv(tex.getU1(), tex.getV0()).setLight(packedLight);
	}

	private static void drawText(
			Minecraft mc,
			PoseStack poseStack,
			MultiBufferSource bufferSource,
			int packedLight,
			String text,
			int x, int y
	) {
		if (ModConfig.get().showTextShadow) {
			poseStack.pushPose();
			poseStack.translate(0, 0, -0.001f);
			mc.font.drawInBatch(text, x + 1, y + 1, 0x3F3F3F, false,
					poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
			poseStack.popPose();
		}
		mc.font.drawInBatch(text, x, y, 0xFFFFFF, false,
				poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
	}

	private static void drawColoredText(
			Minecraft mc,
			PoseStack poseStack,
			MultiBufferSource bufferSource,
			int packedLight,
			String text,
			int x, int y,
			int color,
			int shadowColor,
			boolean showShadow
	) {
		if (showShadow) {
			poseStack.pushPose();
			poseStack.translate(0, 0, -0.001f);
			mc.font.drawInBatch(text, x + 1, y + 1, shadowColor, false,
					poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
			poseStack.popPose();
		}
		mc.font.drawInBatch(text, x, y, color, false,
				poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
	}

	// -------------------------------------------------------------------------
	// Layout
	// -------------------------------------------------------------------------

	private static int computeRowWidth(
			Minecraft mc,
			float health,
			int armor,
			int airSupply,
			boolean hasArmor,
			boolean hasAir
	) {
		int w = ICON_SIZE + GAP + mc.font.width(String.valueOf((int) Math.ceil(health))) + GAP * 2;
		if (hasArmor) {
			w += ICON_SIZE + GAP + mc.font.width(String.valueOf(armor)) + GAP * 2;
		}
		if (hasAir) {
			w += ICON_SIZE + GAP + mc.font.width(String.valueOf(Math.max(0, airSupply / 20)));
		}
		return w;
	}

	// -------------------------------------------------------------------------
	// Heart texture (reads from Snapshot, no entity needed)
	// -------------------------------------------------------------------------

	private static ResourceLocation resolveHeartTexture(EntityRenderCache.Snapshot snap, boolean hardcore) {
		if (snap.hasAbsorption) return hardcore ? HEART_ABSORBING_HC : HEART_ABSORBING;
		if (snap.hasWither)     return hardcore ? HEART_WITHERED_HC  : HEART_WITHERED;
		if (snap.hasPoison)     return hardcore ? HEART_POISONED_HC  : HEART_POISONED;
		if (snap.isFullyFrozen) return hardcore ? HEART_FROZEN_HC    : HEART_FROZEN;
		if (hardcore)           return HEART_FULL_HC;
		return HEART_FULL;
	}

	// -------------------------------------------------------------------------
	// Visibility helpers (still use entity directly — called before render phase)
	// -------------------------------------------------------------------------

	private static boolean isLookedAt(LivingEntity entity, Minecraft mc, int maxDistance) {
		if (mc.player == null) return false;
		Vec3 eyePos  = mc.player.getEyePosition();
		Vec3 lookVec = mc.player.getLookAngle();
		Vec3 reach   = eyePos.add(lookVec.scale(maxDistance));
		AABB box     = entity.getBoundingBox().inflate(entity.getPickRadius());
		return box.clip(eyePos, reach).isPresent();
	}

	private static boolean hasLineOfSight(Minecraft mc, LivingEntity entity) {
		Vec3 from = mc.getEntityRenderDispatcher().camera.getPosition();
		Vec3 to   = entity.position().add(0, entity.getBbHeight() / 2.0, 0);
		BlockHitResult result = mc.level.clip(new ClipContext(
				from, to,
				ClipContext.Block.VISUAL,
				ClipContext.Fluid.NONE,
				entity
		));
		return result.getType() == HitResult.Type.MISS;
	}
}