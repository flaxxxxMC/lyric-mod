package com.example.lyricmod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Settings UI. The lyrics are previewed live at their real position on top of it. */
public class LyricConfigScreen extends Screen {
	private record Preset(String name, int rgb) {}

	private static final Preset[] PRESETS = {
		new Preset("White", 0xFFFFFF), new Preset("Spotify Green", 0x1DB954), new Preset("Gold", 0xFFD700),
		new Preset("Aqua", 0x55FFFF), new Preset("Pink", 0xFF69B4), new Preset("Red", 0xFF5555),
		new Preset("Orange", 0xFFA500), new Preset("Purple", 0xB266FF), new Preset("Blue", 0x5588FF),
		new Preset("Gray", 0xAAAAAA), new Preset("Dark Gray", 0x777777)
	};

	private static final int H = 18, GAP = 19, COL_W = 150, TOP = 24;

	private final Screen parent;
	private Button connectButton;
	private EditBox hexField;

	public LyricConfigScreen(Screen parent) {
		super(Component.literal("Lyric Mod"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		LyricConfig cfg = LyricConfig.get();
		int lx = 10, rx = 10 + COL_W + 10, half = (COL_W - 4) / 2;
		int row = 0;

		// ---- Left column: appearance ----
		addRenderableWidget(Button.builder(Component.literal("Font: " + LyricFonts.nameOf(cfg.font)), b -> {
			int next = (LyricFonts.indexOf(cfg.font) + 1) % LyricFonts.ALL.size();
			cfg.font = LyricFonts.ALL.get(next).id();
			b.setMessage(Component.literal("Font: " + LyricFonts.nameOf(cfg.font)));
		}).bounds(lx, y(row++), COL_W, H).build());

		addRenderableWidget(Button.builder(colorLabel("Color", cfg.color), b -> {
			cfg.color = nextPreset(cfg.color);
			b.setMessage(colorLabel("Color", cfg.color));
			hexField.setValue(String.format("#%06X", cfg.color));
		}).bounds(lx, y(row++), COL_W, H).build());

		hexField = new EditBox(this.font, lx, y(row++), COL_W, H, Component.literal("Hex"));
		hexField.setMaxLength(7);
		hexField.setValue(String.format("#%06X", cfg.color));
		hexField.setResponder(s -> {
			String t = s.startsWith("#") ? s.substring(1) : s;
			if (t.matches("[0-9a-fA-F]{6}")) cfg.color = Integer.parseInt(t, 16);
		});
		addRenderableWidget(hexField);

		addRenderableWidget(Button.builder(colorLabel("Other lines", cfg.dimColor), b -> {
			cfg.dimColor = nextPreset(cfg.dimColor);
			b.setMessage(colorLabel("Other lines", cfg.dimColor));
		}).bounds(lx, y(row++), COL_W, H).build());

		toggle(lx, y(row), half, "Rainbow", () -> cfg.rainbow, v -> cfg.rainbow = v);
		toggle(lx + half + 4, y(row++), half, "Enabled", () -> cfg.enabled, v -> cfg.enabled = v);
		toggle(lx, y(row), half, "Bold", () -> cfg.bold, v -> cfg.bold = v);
		toggle(lx + half + 4, y(row++), half, "Italic", () -> cfg.italic, v -> cfg.italic = v);
		toggle(lx, y(row), half, "Shadow", () -> cfg.shadow, v -> cfg.shadow = v);
		toggle(lx + half + 4, y(row++), half, "Backdrop", () -> cfg.background, v -> cfg.background = v);
		toggle(lx, y(row), half, "Prev line", () -> cfg.showPrevious, v -> cfg.showPrevious = v);
		toggle(lx + half + 4, y(row++), half, "Next line", () -> cfg.showNext, v -> cfg.showNext = v);

		// ---- Right column: layout + Spotify ----
		row = 0;
		addRenderableWidget(new FloatSlider(rx, y(row++), COL_W, H, "Size", 0.5f, 4f, cfg.scale, "%.1fx", 1f, v -> cfg.scale = v));
		addRenderableWidget(new FloatSlider(rx, y(row++), COL_W, H, "Opacity", 0.1f, 1f, cfg.opacity, "%.0f%%", 100f, v -> cfg.opacity = v));
		addRenderableWidget(new FloatSlider(rx, y(row++), COL_W, H, "Horizontal", 0f, 100f, cfg.xPercent, "%.0f%%", 1f, v -> cfg.xPercent = v));
		addRenderableWidget(new FloatSlider(rx, y(row++), COL_W, H, "Vertical", 0f, 100f, cfg.yPercent, "%.0f%%", 1f, v -> cfg.yPercent = v));
		addRenderableWidget(new FloatSlider(rx, y(row++), COL_W, H, "Sync (ms)", -3000f, 3000f, cfg.syncOffsetMs, "%+.0f", 1f, v -> cfg.syncOffsetMs = Math.round(v)));

		EditBox idField = new EditBox(this.font, rx, y(row++), COL_W, H, Component.literal("Client ID"));
		idField.setMaxLength(64);
		idField.setHint(Component.literal("Spotify Client ID"));
		idField.setValue(cfg.spotifyClientId);
		idField.setResponder(s -> cfg.spotifyClientId = s.trim());
		addRenderableWidget(idField);

		connectButton = Button.builder(connectLabel(), b -> {
			if (SpotifyAuth.isConnected()) SpotifyAuth.logout(); else SpotifyAuth.startLogin();
			b.setMessage(connectLabel());
		}).bounds(rx, y(row++), COL_W, H).build();
		addRenderableWidget(connectButton);

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
			.bounds(rx, y(row++), COL_W, H).build());
	}

	private static int y(int row) {
		return TOP + row * GAP;
	}

	private Component connectLabel() {
		return Component.literal(SpotifyAuth.isConnected() ? "Disconnect Spotify" : "Connect Spotify");
	}

	private static Component colorLabel(String label, int rgb) {
		String name = "Custom";
		for (Preset p : PRESETS) if (p.rgb == rgb) name = p.name;
		return Component.literal(label + ": " + name).withStyle(s -> s.withColor(rgb));
	}

	private static int nextPreset(int current) {
		for (int i = 0; i < PRESETS.length; i++) if (PRESETS[i].rgb == current) return PRESETS[(i + 1) % PRESETS.length].rgb;
		return PRESETS[0].rgb;
	}

	private void toggle(int x, int y, int w, String label, Supplier<Boolean> get, Consumer<Boolean> set) {
		addRenderableWidget(Button.builder(toggleLabel(label, get.get()), b -> {
			set.accept(!get.get());
			b.setMessage(toggleLabel(label, get.get()));
		}).bounds(x, y, w, H).build());
	}

	private static Component toggleLabel(String label, boolean on) {
		return Component.literal(label + ": " + (on ? "On" : "Off"));
	}

	@Override
	public void tick() {
		if (connectButton != null) connectButton.setMessage(connectLabel());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.text(this.font, "Lyric Mod", 10, 8, 0xFFFFFFFF, true);
		int rx = 10 + COL_W + 10;
		g.text(this.font, "Spotify: " + SpotifyAuth.status, rx, y(8) + 3, 0xFFBBBBBB, true);
		String track = LyricManager.trackName();
		if (!track.isEmpty()) {
			String info = "♪ " + track + " - " + LyricManager.artistName();
			g.text(this.font, this.font.plainSubstrByWidth(info, COL_W), rx, y(9) + 3, 0xFF88DD88, true);
			String ls = LyricManager.lyricsStatus();
			if (!ls.isEmpty()) g.text(this.font, ls, rx, y(10) + 3, 0xFFDDAA55, true);
		}
		// Live preview on top, at the real HUD position
		LyricHud.draw(g, true);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public void removed() {
		LyricConfig.get().save();
	}

	/** Slider that maps 0..1 onto [min, max] and reports the value through a setter. */
	private static class FloatSlider extends AbstractSliderButton {
		private final String label, fmt;
		private final float min, max, displayMul;
		private final Consumer<Float> setter;

		FloatSlider(int x, int y, int w, int h, String label, float min, float max, float cur, String fmt, float displayMul, Consumer<Float> setter) {
			super(x, y, w, h, Component.empty(), (cur - min) / (max - min));
			this.label = label;
			this.fmt = fmt;
			this.min = min;
			this.max = max;
			this.displayMul = displayMul;
			this.setter = setter;
			updateMessage();
		}

		private float val() {
			return min + (float) value * (max - min);
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal(label + ": " + String.format(fmt, val() * displayMul)));
		}

		@Override
		protected void applyValue() {
			setter.accept(val());
		}
	}
}
