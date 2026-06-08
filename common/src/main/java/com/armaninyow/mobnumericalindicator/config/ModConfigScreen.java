package com.armaninyow.mobnumericalindicator.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ModConfigScreen {

	public static Screen create(Screen parent) {
		ModConfig cfg = ModConfig.get();

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.translatable("mobnumericalindicator.config.title"))
				.setSavingRunnable(ModConfig::save);

		ConfigEntryBuilder entries = builder.entryBuilder();
		ConfigCategory general = builder.getOrCreateCategory(
				Component.translatable("mobnumericalindicator.config.category.general")
		);

		general.addEntry(entries
				.startBooleanToggle(
						Component.translatable("mobnumericalindicator.config.show_when_aggressive"),
						cfg.showWhenAggressive
				)
				.setDefaultValue(true)
				.setTooltip(Component.translatable("mobnumericalindicator.config.show_when_aggressive.tooltip"))
				.setSaveConsumer(val -> cfg.showWhenAggressive = val)
				.build()
		);

		general.addEntry(entries
				.startBooleanToggle(
						Component.translatable("mobnumericalindicator.config.show_when_damaged"),
						cfg.showWhenDamaged
				)
				.setDefaultValue(true)
				.setTooltip(Component.translatable("mobnumericalindicator.config.show_when_damaged.tooltip"))
				.setSaveConsumer(val -> cfg.showWhenDamaged = val)
				.build()
		);

		general.addEntry(entries
				.startBooleanToggle(
						Component.translatable("mobnumericalindicator.config.show_when_looked_at"),
						cfg.showWhenLookedAt
				)
				.setDefaultValue(true)
				.setTooltip(Component.translatable("mobnumericalindicator.config.show_when_looked_at.tooltip"))
				.setSaveConsumer(val -> cfg.showWhenLookedAt = val)
				.build()
		);

		general.addEntry(entries
				.startTextDescription(
						Component.translatable("mobnumericalindicator.config.show_always_notice")
				)
				.build()
		);

		general.addEntry(entries
				.startBooleanToggle(
						Component.translatable("mobnumericalindicator.config.show_when_invisible"),
						cfg.showWhenInvisible
				)
				.setDefaultValue(false)
				.setTooltip(Component.translatable("mobnumericalindicator.config.show_when_invisible.tooltip"))
				.setSaveConsumer(val -> cfg.showWhenInvisible = val)
				.build()
		);

		general.addEntry(entries
				.startBooleanToggle(
						Component.translatable("mobnumericalindicator.config.show_armor_at_zero"),
						cfg.showArmorAtZero
				)
				.setDefaultValue(false)
				.setTooltip(Component.translatable("mobnumericalindicator.config.show_armor_at_zero.tooltip"))
				.setSaveConsumer(val -> cfg.showArmorAtZero = val)
				.build()
		);

		general.addEntry(entries
				.startBooleanToggle(
						Component.translatable("mobnumericalindicator.config.show_text_shadow"),
						cfg.showTextShadow
				)
				.setDefaultValue(true)
				.setTooltip(Component.translatable("mobnumericalindicator.config.show_text_shadow.tooltip"))
				.setSaveConsumer(val -> cfg.showTextShadow = val)
				.build()
		);

		general.addEntry(entries
				.startIntSlider(
						Component.translatable("mobnumericalindicator.config.scale"),
						Math.round(cfg.scale * 100),
						25,
						200
				)
				.setDefaultValue(100)
				.setTooltip(Component.translatable("mobnumericalindicator.config.scale.tooltip"))
				.setTextGetter(val -> Component.literal(val + "%"))
				.setSaveConsumer(val -> cfg.scale = val / 100.0f)
				.build()
		);

		general.addEntry(entries
				.startIntSlider(
						Component.translatable("mobnumericalindicator.config.max_distance"),
						Math.min(cfg.maxDistance, 64),
						4,
						64
				)
				.setDefaultValue(16)
				.setTooltip(Component.translatable("mobnumericalindicator.config.max_distance.tooltip"))
				.setTextGetter(val -> Component.literal(val + " blocks"))
				.setSaveConsumer(val -> cfg.maxDistance = val)
				.build()
		);

		return builder.build();
	}
}