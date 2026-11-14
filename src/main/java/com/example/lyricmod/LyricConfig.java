package com.example.lyricmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** User settings, saved to config/lyricmod.json. */
public class LyricConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("lyricmod.json");
	private static LyricConfig instance;

	// Appearance
	public boolean enabled = true;
	public String font = "minecraft:default";
	public int color = 0xFFFFFF;          // current line (RGB)
	public int dimColor = 0xAAAAAA;       // previous/next lines (RGB)
	public boolean rainbow = false;
	public boolean bold = false;
	public boolean italic = false;
	public boolean shadow = true;
	public boolean background = false;
	public boolean showPrevious = true;
	public boolean showNext = true;
	public float scale = 1.5f;            // 0.5 - 4
	public float opacity = 1.0f;          // 0.1 - 1
	public float xPercent = 50f;          // 0 - 100, horizontal center of text
	public float yPercent = 80f;          // 0 - 100, top of lyric block
	public int syncOffsetMs = 0;          // positive = lyrics appear later

	// Spotify (PKCE flow - no client secret needed)
	public String spotifyClientId = "";
	public String refreshToken = "";

	public static LyricConfig get() {
		if (instance == null) load();
		return instance;
	}

	public static void load() {
		try {
			if (Files.exists(FILE)) {
				instance = GSON.fromJson(Files.readString(FILE), LyricConfig.class);
			}
		} catch (Exception e) {
			LyricMod.LOGGER.warn("Could not read config, using defaults", e);
		}
		if (instance == null) instance = new LyricConfig();
	}

	public void save() {
		try {
			Files.writeString(FILE, GSON.toJson(this));
		} catch (Exception e) {
			LyricMod.LOGGER.warn("Could not save config", e);
		}
	}
}
