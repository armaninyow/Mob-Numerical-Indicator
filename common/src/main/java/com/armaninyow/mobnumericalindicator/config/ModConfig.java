package com.armaninyow.mobnumericalindicator.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("mobnumericalindicator.json");

	private static ModConfig INSTANCE;

	public boolean showAlways = false;
	public boolean showWhenAggressive = true;
	public boolean showWhenDamaged = true;
	public boolean showWhenLookedAt = true;
	public boolean showWhenInvisible = false;
	public boolean showArmorAtZero = false;
	public boolean showTextShadow = true;
	public float scale = 1.0f;
	public int maxDistance = 16;
	public int yOffset = 0;

	public static ModConfig get() {
		if (INSTANCE == null) {
			load();
		}
		return INSTANCE;
	}

	public static void load() {
		if (Files.exists(CONFIG_PATH)) {
			try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
				INSTANCE = GSON.fromJson(reader, ModConfig.class);
				if (INSTANCE == null) INSTANCE = new ModConfig();
				INSTANCE.scale = Math.max(0.25f, Math.min(2.0f, INSTANCE.scale));
				INSTANCE.maxDistance = Math.max(4, Math.min(64, INSTANCE.maxDistance));
				INSTANCE.yOffset = Math.max(-10, Math.min(10, INSTANCE.yOffset));
			} catch (IOException e) {
				INSTANCE = new ModConfig();
			}
		} else {
			INSTANCE = new ModConfig();
			save();
		}
	}

	public static void save() {
		try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
			GSON.toJson(INSTANCE, writer);
		} catch (IOException e) {
			// ignored
		}
	}
}