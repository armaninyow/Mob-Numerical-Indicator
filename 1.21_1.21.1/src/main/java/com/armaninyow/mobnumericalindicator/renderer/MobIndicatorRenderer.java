package com.armaninyow.mobnumericalindicator.renderer;

import com.armaninyow.mobnumericalindicator.config.ModConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
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
	private static final ResourceLocation AIR_BURSTING       = ResourceLocation.withDefaultNamespace("hud/air_bursting");

	private static final int ICON_SIZE      = 9;
	private static final int GAP            = 2;
	private static final long FLOAT_DURATION_MS = 1000L;
	private static final float FLOAT_TRAVEL = 7f;

	// -------------------------------------------------------------------------
	// Floating indicator state
	// -------------------------------------------------------------------------

	private static class FloatEntry {
		final String text;
		final boolean isDamage; // true = damage (red, moves down), false = heal (green, moves up)
		final long startTime;

		FloatEntry(String text, boolean isDamage, long startTime) {
			this.text = text;
			this.isDamage = isDamage;
			this.startTime = startTime;
		}
	}

	// Per-entity last known health (rounded up) for delta detection
	private static final Map<Integer, Integer> lastHealth   = new HashMap<>();
	// Per-entity active floating damage/heal indicators
	private static final Map<Integer, FloatEntry> damageFloat = new HashMap<>();
	private static final Map<Integer, FloatEntry> healFloat   = new HashMap<>();

	// Called when an entity is unloaded from the client world (death, despawn, chunk unload)
	public static void clearEntity(int entityId) {
		lastHealth.remove(entityId);
		damageFloat.remove(entityId);
		healFloat.remove(entityId);
	}

	// -------------------------------------------------------------------------
	// Main render entry point
	// -------------------------------------------------------------------------

	public static void render(
			LivingEntity entity,
			float partialTick,
			PoseStack poseStack,
			MultiBufferSource bufferSource,
			int packedLight
	) {
		Minecraft mc = Minecraft.getInstance();
		ModConfig cfg = ModConfig.get();

		if (mc.player == null || mc.level == null) return;
		if (entity == mc.player) return;
		if (entity == mc.player.getVehicle()) return;

		// --- Invisibility is a hard filter; check before anything else ---
		if (!cfg.showWhenInvisible && entity.isInvisible()) return;

		// --- Visibility conditions (OR logic) ---
		// If all three are disabled, show always
		boolean anyConditionEnabled = cfg.showWhenAggressive || cfg.showWhenDamaged || cfg.showWhenLookedAt;
		boolean shouldShow = !anyConditionEnabled;
		if (cfg.showWhenAggressive && isAggressive(entity)) shouldShow = true;
		if (cfg.showWhenDamaged && entity.getHealth() < entity.getMaxHealth()) shouldShow = true;
		if (cfg.showWhenLookedAt && isLookedAt(entity, mc, cfg.maxDistance)) shouldShow = true;
		if (!shouldShow) return;

		// --- Distance check ---
		double dist = mc.player.distanceTo(entity);
		if (dist > cfg.maxDistance) return;

		// --- Line of sight check ---
		if (!hasLineOfSight(mc, entity)) return;

		// --- Health delta detection (always run, regardless of visibility) ---
		int entityId = entity.getId();
		int currentHealth = (int) Math.ceil(entity.getHealth());
		long now = System.currentTimeMillis();

		if (lastHealth.containsKey(entityId)) {
			int prev = lastHealth.get(entityId);
			if (currentHealth < prev) {
				// Damaged — replace any existing damage float
				int delta = prev - currentHealth;
				damageFloat.put(entityId, new FloatEntry("-" + delta, true, now));
			} else if (currentHealth > prev) {
				// Healed — replace any existing heal float
				int delta = currentHealth - prev;
				healFloat.put(entityId, new FloatEntry("+" + delta, false, now));
			}
		}
		lastHealth.put(entityId, currentHealth);

		// --- Resolve NAME_TAG attachment point, exactly like vanilla renderNameTag ---
		Vec3 attachPoint = entity.getAttachments().getNullable(
				EntityAttachment.NAME_TAG, 0, entity.getViewYRot(partialTick)
		);
		if (attachPoint == null) return;

		// --- Distance-based scale: shrinks to 50% at max distance ---
		float distScale = (float) (1.0 - (dist / cfg.maxDistance) * 0.5);
		float scale = cfg.scale * distScale;

		poseStack.pushPose();

		// Translate to attachment point + 0.5 above, matching vanilla name tag exactly
		poseStack.translate(attachPoint.x, attachPoint.y + 0.5, attachPoint.z);

		// Billboard using cameraOrientation(), matching vanilla bytecode
		poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());

		// Vanilla name tag scale is 0.025 / -0.025; user scale applied on top
		poseStack.scale(0.025f * scale, -0.025f * scale, 0.025f * scale);

		// --- Gather values ---
		boolean hardcore = mc.level.getLevelData().isHardcore();
		float health     = entity.getHealth();
		int armorValue   = entity.getArmorValue();
		int airSupply    = entity.getAirSupply();
		int maxAir       = entity.getMaxAirSupply();
		boolean hasArmor = shouldShowArmor(entity, cfg);
		boolean hasAir   = airSupply < maxAir;

		// --- Compute row width to center the row ---
		int rowWidth = computeRowWidth(mc, health, armorValue, airSupply, hasArmor, hasAir);
		int cursorX  = -rowWidth / 2;
		int cursorY  = 0;

		// X position of the heart icon — needed to center floating indicators on it
		int heartX = cursorX;

		// --- Health: container drawn first, filled heart drawn on top ---
		ResourceLocation containerTex = hardcore ? HEART_CONTAINER_HC : HEART_CONTAINER;
		drawSprite(poseStack, bufferSource, packedLight, containerTex, heartX, cursorY, ICON_SIZE, ICON_SIZE);
		ResourceLocation heartTex = resolveHeartTexture(entity, hardcore);
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
			ResourceLocation airTex = bursting ? AIR_BURSTING : AIR;
			drawSprite(poseStack, bufferSource, packedLight, airTex, cursorX, cursorY, ICON_SIZE, ICON_SIZE);
			cursorX += ICON_SIZE + GAP;

			String airStr = String.valueOf(Math.max(0, airSupply / 20));
			drawText(mc, poseStack, bufferSource, packedLight, airStr, cursorX, cursorY + 1);
		}

		// --- Floating damage/heal indicators ---
		// Positioned to the left of the heart icon, right-aligned to heartX
		FloatEntry dmg = damageFloat.get(entityId);
		if (dmg != null) {
			float elapsed = (now - dmg.startTime) / (float) FLOAT_DURATION_MS;
			if (elapsed >= 1f) {
				damageFloat.remove(entityId);
			} else {
				// Quint ease-out: 1 - (1-t)^5
				float eased = 1f - (float) Math.pow(1f - elapsed, 5);
				// Moves downward (positive Y in flipped space = down on screen)
				float floatY = FLOAT_TRAVEL * eased;
				int textW = mc.font.width(dmg.text);
				// Right-align to the left edge of the heart icon with a small gap
				int textX = heartX - GAP - textW;
				drawColoredText(mc, poseStack, bufferSource, packedLight, dmg.text, textX, (int) floatY, 0xFF5555, 0x3F1515, cfg.showTextShadow);
			}
		}

		FloatEntry heal = healFloat.get(entityId);
		if (heal != null) {
			float elapsed = (now - heal.startTime) / (float) FLOAT_DURATION_MS;
			if (elapsed >= 1f) {
				healFloat.remove(entityId);
			} else {
				float eased = 1f - (float) Math.pow(1f - elapsed, 5);
				// Moves upward (negative Y in flipped space = up on screen)
				float floatY = -(FLOAT_TRAVEL * eased);
				int textW = mc.font.width(heal.text);
				int textX = heartX - GAP - textW;
				drawColoredText(mc, poseStack, bufferSource, packedLight, heal.text, textX, (int) floatY, 0x55FF55, 0x153F15, cfg.showTextShadow);
			}
		}

		poseStack.popPose();
	}

	// -------------------------------------------------------------------------
	// Rendering
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
			mc.font.drawInBatch(
					text,
					x + 1, y + 1,
					0x3F3F3F,
					false,
					poseStack.last().pose(),
					bufferSource,
					Font.DisplayMode.NORMAL,
					0,
					packedLight
			);
			poseStack.popPose();
		}
		mc.font.drawInBatch(
				text,
				x, y,
				0xFFFFFF,
				false,
				poseStack.last().pose(),
				bufferSource,
				Font.DisplayMode.NORMAL,
				0,
				packedLight
		);
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
			mc.font.drawInBatch(
					text,
					x + 1, y + 1,
					shadowColor,
					false,
					poseStack.last().pose(),
					bufferSource,
					Font.DisplayMode.NORMAL,
					0,
					packedLight
			);
			poseStack.popPose();
		}
		mc.font.drawInBatch(
				text,
				x, y,
				color,
				false,
				poseStack.last().pose(),
				bufferSource,
				Font.DisplayMode.NORMAL,
				0,
				packedLight
		);
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
	// Heart texture
	// -------------------------------------------------------------------------

	private static ResourceLocation resolveHeartTexture(LivingEntity entity, boolean hardcore) {
		if (entity.hasEffect(MobEffects.ABSORPTION)) return hardcore ? HEART_ABSORBING_HC : HEART_ABSORBING;
		if (entity.hasEffect(MobEffects.WITHER))     return hardcore ? HEART_WITHERED_HC  : HEART_WITHERED;
		if (entity.hasEffect(MobEffects.POISON))     return hardcore ? HEART_POISONED_HC  : HEART_POISONED;
		if (entity.isFullyFrozen())                  return hardcore ? HEART_FROZEN_HC    : HEART_FROZEN;
		if (hardcore)                                return HEART_FULL_HC;
		return HEART_FULL;
	}

	// -------------------------------------------------------------------------
	// Armor check
	// -------------------------------------------------------------------------

	private static boolean shouldShowArmor(LivingEntity entity, ModConfig cfg) {
		boolean wearingArmor = false;
		for (EquipmentSlot slot : new EquipmentSlot[]{
				EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
		}) {
			ItemStack stack = entity.getItemBySlot(slot);
			if (!stack.isEmpty() && stack.getItem() instanceof ArmorItem) {
				wearingArmor = true;
				break;
			}
		}
		if (wearingArmor) return true;
		return cfg.showArmorAtZero;
	}

	// -------------------------------------------------------------------------
	// Visibility helpers
	// -------------------------------------------------------------------------

	private static boolean isAggressive(LivingEntity entity) {
		if (entity instanceof net.minecraft.world.entity.Mob mob) {
			return mob.getTarget() != null;
		}
		return false;
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