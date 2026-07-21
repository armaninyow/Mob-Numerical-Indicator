package com.armaninyow.mobnumericalindicator.config;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ModConfigScreen {

    public static Screen create(Screen parent) {
        ModConfig cfg = ModConfig.get();

        Option<Boolean>[] showAlwaysRef = new Option[1];

        Option<Boolean> showWhenAggressive = Option.<Boolean>createBuilder()
                .name(Component.translatable("mobnumericalindicator.config.show_when_aggressive"))
                .description(OptionDescription.of(Component.translatable("mobnumericalindicator.config.show_when_aggressive.tooltip")))
                .binding(true, () -> cfg.showWhenAggressive, val -> {
                    cfg.showWhenAggressive = val;
                    if (!val && !cfg.showWhenDamaged && !cfg.showWhenLookedAt && !cfg.showAlways) {
                        cfg.showAlways = true;
                        showAlwaysRef[0].requestSet(true);
                    }
                })
                .controller(BooleanControllerBuilder::create)
                .build();

        Option<Boolean> showWhenDamaged = Option.<Boolean>createBuilder()
                .name(Component.translatable("mobnumericalindicator.config.show_when_damaged"))
                .description(OptionDescription.of(Component.translatable("mobnumericalindicator.config.show_when_damaged.tooltip")))
                .binding(true, () -> cfg.showWhenDamaged, val -> {
                    cfg.showWhenDamaged = val;
                    if (!val && !cfg.showWhenAggressive && !cfg.showWhenLookedAt && !cfg.showAlways) {
                        cfg.showAlways = true;
                        showAlwaysRef[0].requestSet(true);
                    }
                })
                .controller(BooleanControllerBuilder::create)
                .build();

        Option<Boolean> showWhenLookedAt = Option.<Boolean>createBuilder()
                .name(Component.translatable("mobnumericalindicator.config.show_when_looked_at"))
                .description(OptionDescription.of(Component.translatable("mobnumericalindicator.config.show_when_looked_at.tooltip")))
                .binding(true, () -> cfg.showWhenLookedAt, val -> {
                    cfg.showWhenLookedAt = val;
                    if (!val && !cfg.showWhenAggressive && !cfg.showWhenDamaged && !cfg.showAlways) {
                        cfg.showAlways = true;
                        showAlwaysRef[0].requestSet(true);
                    }
                })
                .controller(BooleanControllerBuilder::create)
                .build();

        Option<Boolean> showAlways = Option.<Boolean>createBuilder()
                .name(Component.translatable("mobnumericalindicator.config.show_always"))
                .description(OptionDescription.of(Component.translatable("mobnumericalindicator.config.show_always.tooltip")))
                .binding(false, () -> cfg.showAlways, val -> {
                    cfg.showAlways = val;
                    showWhenAggressive.setAvailable(!val);
                    showWhenDamaged.setAvailable(!val);
                    showWhenLookedAt.setAvailable(!val);
                })
                .controller(BooleanControllerBuilder::create)
                .listener((opt, val) -> {
                    showWhenAggressive.setAvailable(!val);
                    showWhenDamaged.setAvailable(!val);
                    showWhenLookedAt.setAvailable(!val);
                })
                .build();
        showAlwaysRef[0] = showAlways;

        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("mobnumericalindicator.config.title"))
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("mobnumericalindicator.config.category.display"))
                        .option(showAlways)
                        .option(showWhenAggressive)
                        .option(showWhenDamaged)
                        .option(showWhenLookedAt)
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("mobnumericalindicator.config.show_when_invisible"))
                                .description(OptionDescription.of(Component.translatable("mobnumericalindicator.config.show_when_invisible.tooltip")))
                                .binding(false, () -> cfg.showWhenInvisible, val -> cfg.showWhenInvisible = val)
                                .controller(BooleanControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("mobnumericalindicator.config.show_armor_at_zero"))
                                .description(OptionDescription.of(Component.translatable("mobnumericalindicator.config.show_armor_at_zero.tooltip")))
                                .binding(false, () -> cfg.showArmorAtZero, val -> cfg.showArmorAtZero = val)
                                .controller(BooleanControllerBuilder::create)
                                .build())
                        .build())
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("mobnumericalindicator.config.category.appearance"))
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("mobnumericalindicator.config.show_text_shadow"))
                                .description(OptionDescription.of(Component.translatable("mobnumericalindicator.config.show_text_shadow.tooltip")))
                                .binding(true, () -> cfg.showTextShadow, val -> cfg.showTextShadow = val)
                                .controller(BooleanControllerBuilder::create)
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Component.translatable("mobnumericalindicator.config.y_offset"))
                                .description(OptionDescription.of(Component.translatable("mobnumericalindicator.config.y_offset.tooltip")))
                                .binding(0, () -> cfg.yOffset, val -> cfg.yOffset = val)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                        .range(-10, 10)
                                        .step(1)
                                        .formatValue(val -> Component.literal(val > 0 ? "+" + val : String.valueOf(val))))
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Component.translatable("mobnumericalindicator.config.scale"))
                                .description(OptionDescription.of(Component.translatable("mobnumericalindicator.config.scale.tooltip")))
                                .binding(100, () -> Math.round(cfg.scale * 100), val -> cfg.scale = val / 100.0f)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                        .range(25, 200)
                                        .step(1)
                                        .formatValue(val -> Component.literal(val + "%")))
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Component.translatable("mobnumericalindicator.config.max_distance"))
                                .description(OptionDescription.of(Component.translatable("mobnumericalindicator.config.max_distance.tooltip")))
                                .binding(16, () -> Math.min(cfg.maxDistance, 64), val -> cfg.maxDistance = val)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                        .range(4, 64)
                                        .step(1)
                                        .formatValue(val -> Component.literal(val + " blocks")))
                                .build())
                        .build())
                .save(ModConfig::save)
                .build()
                .generateScreen(parent);
    }
}