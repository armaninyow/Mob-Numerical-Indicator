package com.armaninyow.mobnumericalindicator;

import com.armaninyow.mobnumericalindicator.renderer.MobIndicatorRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.minecraft.world.entity.LivingEntity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MobNumericalIndicator implements ClientModInitializer {
	public static final String MOD_ID = "mobnumericalindicator";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if (entity instanceof LivingEntity) {
				MobIndicatorRenderer.clearEntity(entity.getId());
			}
		});

		LOGGER.info("Mob Numerical Indicator initialized.");
	}
}