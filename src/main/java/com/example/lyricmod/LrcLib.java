package com.example.lyricmod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Fetches time-synced lyrics from LRCLIB (https://lrclib.net), a free open lyrics database. */
public class LrcLib {
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static String get(String url) throws Exception {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
			.header("User-Agent", "LyricMod/1.0 (Minecraft Fabric mod)")
			.timeout(Duration.ofSeconds(10)).GET().build();
		HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
		return res.statusCode() == 200 ? res.body() : null;
	}

	/** Returns synced lyrics, or null if none were found. */
	public static Lyrics fetch(String track, String artist, String album, long durationMs) {
		try {
			String url = "https://lrclib.net/api/get?track_name=" + enc(track) + "&artist_name=" + enc(artist)
				+ "&album_name=" + enc(album) + "&duration=" + Math.round(durationMs / 1000.0);
			String body = get(url);
			Lyrics l = fromObject(body == null ? null : JsonParser.parseString(body).getAsJsonObject());
			if (l != null) return l;

			// Fallback: looser search
			body = get("https://lrclib.net/api/search?track_name=" + enc(track) + "&artist_name=" + enc(artist));
			if (body == null) return null;
			JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
			for (JsonElement e : arr) {
				Lyrics found = fromObject(e.getAsJsonObject());
				if (found != null) return found;
			}
		} catch (Exception e) {
			LyricMod.LOGGER.warn("Lyric lookup failed", e);
		}
		return null;
	}

	private static Lyrics fromObject(JsonObject o) {
		if (o == null || !o.has("syncedLyrics") || o.get("syncedLyrics").isJsonNull()) return null;
		Lyrics l = Lyrics.parseLrc(o.get("syncedLyrics").getAsString());
		return l.lines.isEmpty() ? null : l;
	}
}
