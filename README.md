# Lyric Mod (Fabric, Minecraft 26.2)

Shows synced lyrics for whatever you're playing on Spotify on your HUD.
Press L in-game to open the settings UI (live preview included).

## Build
1. Install JDK 25 (e.g. Temurin 25 from adoptium.net).
2. Download the Fabric example mod (github.com/FabricMC/fabric-example-mod, branch 26.2) and copy
   `gradlew`, `gradlew.bat` and the `gradle` folder next to this build.gradle.
3. In a terminal in this folder run: gradlew build  (Windows)  or  ./gradlew build
4. Take build/libs/lyricmod-1.0.0.jar (NOT the -sources one) and put it in your mods folder with Fabric API 0.156.0+26.2.

## Spotify setup (one time)
1. https://developer.spotify.com/dashboard -> create an app.
2. Add redirect URI exactly: http://127.0.0.1:8888/callback
3. Copy the Client ID (no secret needed).
4. In Minecraft press L, paste the Client ID, click Connect Spotify, approve in the browser.

## Notes
- Lyrics come from LRCLIB (lrclib.net). Songs it doesn't have show nothing.
- Use the Sync slider if lyrics run early/late.
- Bundled fonts: Liberation Sans/Serif/Mono and Carlito (SIL OFL).
- Config: config/lyricmod.json
