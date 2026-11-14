package com.example.lyricmod;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsed time-synced lyrics (LRC format). */
public class Lyrics {
	public record Line(long timeMs, String text) {}

	private static final Pattern TS = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");

	public final List<Line> lines;

	public Lyrics(List<Line> lines) {
		this.lines = lines;
	}

	public static Lyrics parseLrc(String lrc) {
		List<Line> out = new ArrayList<>();
		for (String raw : lrc.split("\\r?\\n")) {
			Matcher m = TS.matcher(raw);
			List<Long> times = new ArrayList<>();
			int end = 0;
			while (m.find() && m.start() == end) {
				long min = Long.parseLong(m.group(1));
				long sec = Long.parseLong(m.group(2));
				long ms = 0;
				if (m.group(3) != null) {
					String f = m.group(3);
					ms = Long.parseLong(f) * (f.length() == 1 ? 100 : f.length() == 2 ? 10 : 1);
				}
				times.add(min * 60_000 + sec * 1000 + ms);
				end = m.end();
			}
			if (times.isEmpty()) continue;
			String text = raw.substring(end).trim();
			for (long t : times) out.add(new Line(t, text));
		}
		out.sort((a, b) -> Long.compare(a.timeMs, b.timeMs));
		return new Lyrics(out);
	}

	/** Index of the line active at the given time, or -1 before the first line. */
	public int indexAt(long posMs) {
		int lo = 0, hi = lines.size() - 1, ans = -1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (lines.get(mid).timeMs <= posMs) { ans = mid; lo = mid + 1; } else hi = mid - 1;
		}
		return ans;
	}

	public String textAt(int index) {
		if (index < 0 || index >= lines.size()) return "";
		return lines.get(index).text;
	}
}
