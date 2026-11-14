package com.example.lyricmod;

import java.util.List;

/** Fonts the user can pick. The lyricmod:* ones are TTFs bundled in the mod's resources. */
public class LyricFonts {
	public record Entry(String name, String id) {}

	public static final List<Entry> ALL = List.of(
		new Entry("Minecraft", "minecraft:default"),
		new Entry("Uniform", "minecraft:uniform"),
		new Entry("Sans", "lyricmod:sans"),
		new Entry("Serif", "lyricmod:serif"),
		new Entry("Mono", "lyricmod:mono"),
		new Entry("Rounded", "lyricmod:rounded"),
		new Entry("Enchanting Table", "minecraft:alt"),
		new Entry("Illager Runes", "minecraft:illageralt")
	);

	public static int indexOf(String id) {
		for (int i = 0; i < ALL.size(); i++) if (ALL.get(i).id.equals(id)) return i;
		return 0;
	}

	public static String nameOf(String id) {
		return ALL.get(indexOf(id)).name;
	}
}
