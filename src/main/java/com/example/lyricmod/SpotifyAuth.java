package com.example.lyricmod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * Spotify login using the Authorization Code + PKCE flow, so no client secret is needed.
 * Create an app at https://developer.spotify.com/dashboard and add this redirect URI exactly:
 *   http://127.0.0.1:8888/callback
 */
public class SpotifyAuth {
	public static final int PORT = 8888;
	public static final String REDIRECT = "http://127.0.0.1:" + PORT + "/callback";
	private static final String SCOPE = "user-read-currently-playing user-read-playback-state";
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

	private static volatile String accessToken = "";
	private static volatile long expiresAtMs = 0;
	public static volatile String status = "Not connected";
	private static volatile boolean loginRunning = false;

	public static boolean isConnected() {
		return !LyricConfig.get().refreshToken.isEmpty();
	}

	/** A valid access token, refreshing if needed. Returns null if not logged in. */
	public static synchronized String token() {
		LyricConfig cfg = LyricConfig.get();
		if (cfg.refreshToken.isEmpty() || cfg.spotifyClientId.isEmpty()) return null;
		if (System.currentTimeMillis() < expiresAtMs - 30_000 && !accessToken.isEmpty()) return accessToken;
		try {
			JsonObject o = postForm("grant_type=refresh_token&refresh_token=" + enc(cfg.refreshToken)
				+ "&client_id=" + enc(cfg.spotifyClientId));
			if (o == null || !o.has("access_token")) {
				status = "Session expired - reconnect";
				cfg.refreshToken = "";
				cfg.save();
				return null;
			}
			store(o);
			return accessToken;
		} catch (Exception e) {
			LyricMod.LOGGER.warn("Token refresh failed", e);
			return null;
		}
	}

	public static void logout() {
		LyricConfig cfg = LyricConfig.get();
		cfg.refreshToken = "";
		cfg.save();
		accessToken = "";
		status = "Not connected";
	}

	/** Opens the browser and waits for the redirect on a background thread. */
	public static void startLogin() {
		LyricConfig cfg = LyricConfig.get();
		if (cfg.spotifyClientId.isBlank()) {
			status = "Enter your Client ID first";
			return;
		}
		if (loginRunning) return;
		loginRunning = true;
		status = "Waiting for browser login...";
		Thread t = new Thread(() -> {
			try (ServerSocket server = new ServerSocket(PORT, 1, InetAddress.getByName("127.0.0.1"))) {
				server.setSoTimeout(180_000);
				String verifier = randomString(64);
				String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
					MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
				String state = randomString(16);
				String url = "https://accounts.spotify.com/authorize?response_type=code"
					+ "&client_id=" + enc(cfg.spotifyClientId)
					+ "&scope=" + enc(SCOPE)
					+ "&redirect_uri=" + enc(REDIRECT)
					+ "&state=" + state
					+ "&code_challenge_method=S256&code_challenge=" + challenge;
				openBrowser(url);

				String code = null, returnedState = null, error = null;
				try (Socket s = server.accept()) {
					BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
					String line = in.readLine(); // GET /callback?code=...&state=... HTTP/1.1
					if (line != null && line.contains("?")) {
						String query = line.substring(line.indexOf('?') + 1, line.lastIndexOf(' '));
						for (String kv : query.split("&")) {
							String[] p = kv.split("=", 2);
							String v = p.length > 1 ? URLDecoder.decode(p[1], StandardCharsets.UTF_8) : "";
							switch (p[0]) {
								case "code" -> code = v;
								case "state" -> returnedState = v;
								case "error" -> error = v;
							}
						}
					}
					boolean ok = code != null && state.equals(returnedState);
					String html = "<html><body style='font-family:sans-serif;text-align:center;margin-top:20vh'><h2>"
						+ (ok ? "Connected! You can close this tab and return to Minecraft." : "Login failed. Return to Minecraft and try again.")
						+ "</h2></body></html>";
					byte[] body = html.getBytes(StandardCharsets.UTF_8);
					s.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "
						+ body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
					s.getOutputStream().write(body);
					s.getOutputStream().flush();
					if (!ok) code = null;
				}

				if (code == null) {
					status = error != null ? "Login denied" : "Login failed";
					return;
				}
				JsonObject o = postForm("grant_type=authorization_code&code=" + enc(code)
					+ "&redirect_uri=" + enc(REDIRECT) + "&client_id=" + enc(cfg.spotifyClientId)
					+ "&code_verifier=" + enc(verifier));
				if (o == null || !o.has("access_token")) {
					status = "Token exchange failed (check Client ID / redirect URI)";
					return;
				}
				store(o);
				cfg.refreshToken = o.get("refresh_token").getAsString();
				cfg.save();
				status = "Connected";
			} catch (java.net.BindException e) {
				status = "Port " + PORT + " is in use";
			} catch (java.net.SocketTimeoutException e) {
				status = "Login timed out";
			} catch (Exception e) {
				LyricMod.LOGGER.warn("Spotify login failed", e);
				status = "Login error: " + e.getClass().getSimpleName();
			} finally {
				loginRunning = false;
			}
		}, "LyricMod-SpotifyLogin");
		t.setDaemon(true);
		t.start();
	}

	private static void store(JsonObject o) {
		accessToken = o.get("access_token").getAsString();
		expiresAtMs = System.currentTimeMillis() + o.get("expires_in").getAsLong() * 1000;
		if (o.has("refresh_token")) {
			LyricConfig cfg = LyricConfig.get();
			cfg.refreshToken = o.get("refresh_token").getAsString();
			cfg.save();
		}
		if (isConnected()) status = "Connected";
	}

	private static JsonObject postForm(String body) throws Exception {
		HttpRequest req = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.timeout(Duration.ofSeconds(10))
			.POST(HttpRequest.BodyPublishers.ofString(body)).build();
		HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() != 200) return null;
		return JsonParser.parseString(res.body()).getAsJsonObject();
	}

	private static void openBrowser(String url) {
		try {
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				Desktop.getDesktop().browse(URI.create(url));
				return;
			}
		} catch (Throwable ignored) {}
		// Fallback where AWT is headless (Minecraft often runs that way)
		try {
			String os = System.getProperty("os.name", "").toLowerCase();
			ProcessBuilder pb = os.contains("win") ? new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url)
				: os.contains("mac") ? new ProcessBuilder("open", url)
				: new ProcessBuilder("xdg-open", url);
			pb.start();
		} catch (Exception e) {
			LyricMod.LOGGER.warn("Could not open browser. Open this URL manually: {}", url);
			status = "Could not open browser (see log)";
		}
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static String randomString(int len) {
		String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
		SecureRandom r = new SecureRandom();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < len; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
		return sb.toString();
	}
}
