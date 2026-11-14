package com.example.lyricmod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Polls Spotify for the current track and keeps the matching synced lyrics. */
public class LyricManager {
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private static final ScheduledExecutorService POLLER = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "LyricMod-Poller");
		t.setDaemon(true);
		return t;
	});

	// Written by the poller thread, read by the render thread
	private static volatile String trackId = "";
	private static volatile String trackName = "";
	private static volatile String artistName = "";
	private static volatile boolean playing = false;
	private static volatile long progressMs = 0;
	private static volatile long polledAtNs = 0;
	private static volatile Lyrics lyrics = null;
	private static volatile boolean lyricsLoading = false;
	private static volatile String lyricsStatus = "";
	private static volatile long backoffUntilMs = 0;

	public static void start() {
		POLLER.scheduleWithFixedDelay(LyricManager::poll, 1, 1, TimeUnit.SECONDS);
	}

	private static void poll() {
		try {
			if (System.currentTimeMillis() < backoffUntilMs) return;
			LyricConfig cfg = LyricConfig.get();
			if (!cfg.enabled) return;
			String token = SpotifyAuth.token();
			if (token == null) {
				playing = false;
				return;
			}
			HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1/me/player/currently-playing"))
				.header("Authorization", "Bearer " + token)
				.timeout(Duration.ofSeconds(8)).GET().build();
			HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

			switch (res.statusCode()) {
				case 200 -> handle(JsonParser.parseString(res.body()).getAsJsonObject());
				case 204 -> playing = false;
				case 429 -> {
					long wait = res.headers().firstValue("Retry-After").map(Long::parseLong).orElse(5L);
					backoffUntilMs = System.currentTimeMillis() + wait * 1000;
				}
				default -> {} // 401 handled by token refresh on next poll
			}
		} catch (Exception e) {
			LyricMod.LOGGER.debug("Poll failed", e);
		}
	}

	private static void handle(JsonObject o) {
		if (!o.has("item") || o.get("item").isJsonNull()) {
			playing = false;
			return;
		}
		JsonObject item = o.getAsJsonObject("item");
		if (!"track".equals(item.has("type") ? item.get("type").getAsString() : "track")) {
			playing = false; // podcasts have no lyrics here
			return;
		}
		String id = item.get("id").getAsString();
		progressMs = o.get("progress_ms").getAsLong();
		polledAtNs = System.nanoTime();
		playing = o.get("is_playing").getAsBoolean();

		if (!id.equals(trackId)) {
			trackId = id;
			trackName = item.get("name").getAsString();
			artistName = item.getAsJsonArray("artists").get(0).getAsJsonObject().get("name").getAsString();
			String album = item.getAsJsonObject("album").get("name").getAsString();
			long duration = item.get("duration_ms").getAsLong();
			lyrics = null;
			lyricsLoading = true;
			lyricsStatus = "Searching lyrics...";
			final String forId = id;
			final String t = trackName, a = artistName;
			Thread th = new Thread(() -> {
				Lyrics l = LrcLib.fetch(t, a, album, duration);
				if (forId.equals(trackId)) {
					lyrics = l;
					lyricsLoading = false;
					lyricsStatus = l == null ? "No synced lyrics found" : "";
				}
			}, "LyricMod-Lyrics");
			th.setDaemon(true);
			th.start();
		}
	}

	/** Estimated playback position right now, in ms, including the user's sync offset. */
	public static long positionMs() {
		long p = progressMs;
		if (playing) p += (System.nanoTime() - polledAtNs) / 1_000_000L;
		return p + LyricConfig.get().syncOffsetMs;
	}

	public static boolean isPlaying() { return playing; }
	public static Lyrics lyrics() { return lyrics; }
	public static String trackName() { return trackName; }
	public static String artistName() { return artistName; }
	public static String lyricsStatus() { return lyricsStatus; }
}
