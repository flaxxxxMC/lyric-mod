package com.example.lyricmod;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** Draws the lyric block. Used by the HUD and by the settings screen preview. */
public class LyricHud {
	private record Entry(String text, boolean current) {}

	/** HUD element callback. The settings screen draws its own preview, so skip here while it is open. */
	public static void extract(GuiGraphicsExtractor g, DeltaTracker tick) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || mc.player == null) return;
		if (mc.gui.screen() instanceof LyricConfigScreen) return;
		draw(g, false);
	}

	public static void draw(GuiGraphicsExtractor g, boolean preview) {
		Minecraft mc = Minecraft.getInstance();
		LyricConfig cfg = LyricConfig.get();
		if (!cfg.enabled) return;

		List<Entry> entries = new ArrayList<>();
		Lyrics lyrics = LyricManager.lyrics();

		if (LyricManager.isPlaying() && lyrics != null) {
			int idx = lyrics.indexAt(LyricManager.positionMs());
			if (cfg.showPrevious && idx > 0) entries.add(new Entry(lyrics.textAt(idx - 1), false));
			if (idx >= 0) {
				String cur = lyrics.textAt(idx);
				entries.add(new Entry(cur.isBlank() ? "♪" : cur, true));
			}
			if (cfg.showNext && idx + 1 < lyrics.lines.size()) entries.add(new Entry(lyrics.textAt(idx + 1), false));
		} else if (preview) {
			if (cfg.showPrevious) entries.add(new Entry("The previous line fades back", false));
			entries.add(new Entry("This is how your lyrics will look", true));
			if (cfg.showNext) entries.add(new Entry("And the next line waits here", false));
		}
		entries.removeIf(e -> e.text.isBlank());
		if (entries.isEmpty()) return;

		Font font = mc.font;
		int sw = g.guiWidth();
		int sh = g.guiHeight();

		Style style = Style.EMPTY
			.withFont(new FontDescription.Resource(Identifier.parse(cfg.font)))
			.withBold(cfg.bold)
			.withItalic(cfg.italic);

		int alpha = Math.max(8, Math.min(255, Math.round(cfg.opacity * 255)));
		int mainRgb = cfg.rainbow
			? Color.HSBtoRGB((System.currentTimeMillis() % 4000L) / 4000f, 0.65f, 1f) & 0xFFFFFF
			: cfg.color;
		int mainColor = (alpha << 24) | mainRgb;
		int dimAlpha = Math.max(8, Math.round(alpha * 0.65f));
		int dimColor = (dimAlpha << 24) | cfg.dimColor;

		List<List<FormattedCharSequence>> wrapped = new ArrayList<>();
		List<Float> scales = new ArrayList<>();
		float totalH = 0, maxW = 0;
		for (Entry e : entries) {
			float s = e.current ? cfg.scale : cfg.scale * 0.7f;
			List<FormattedCharSequence> lines = font.split(Component.literal(e.text).withStyle(style), (int) (sw * 0.85f / s));
			wrapped.add(lines);
			scales.add(s);
			totalH += lines.size() * 9 * s + 3;
			for (FormattedCharSequence l : lines) maxW = Math.max(maxW, font.width(l) * s);
		}

		float cx = sw * cfg.xPercent / 100f;
		float top = sh * cfg.yPercent / 100f;
		top = Math.max(2, Math.min(top, sh - totalH - 2));

		if (cfg.background) {
			g.fill((int) (cx - maxW / 2 - 4), (int) top - 3, (int) (cx + maxW / 2 + 4), (int) (top + totalH + 1),
				((int) (alpha * 0.4f) << 24));
		}

		float y = top;
		for (int i = 0; i < entries.size(); i++) {
			float s = scales.get(i);
			int color = entries.get(i).current ? mainColor : dimColor;
			g.pose().pushMatrix();
			g.pose().translate(cx, y);
			g.pose().scale(s, s);
			int ly = 0;
			for (FormattedCharSequence line : wrapped.get(i)) {
				g.text(font, line, -font.width(line) / 2, ly, color, cfg.shadow);
				ly += 9;
			}
			g.pose().popMatrix();
			y += wrapped.get(i).size() * 9 * s + 3;
		}
	}
}
