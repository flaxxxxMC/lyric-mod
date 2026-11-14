package com.example.lyricmod;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LyricMod implements ClientModInitializer {
	public static final String MOD_ID = "lyricmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static KeyMapping openSettings;

	@Override
	public void onInitializeClient() {
		LyricConfig.load();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		openSettings = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.lyricmod.open", InputConstants.Type.KEYSYM, InputConstants.KEY_L, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openSettings.consumeClick()) {
				Minecraft mc = Minecraft.getInstance();
				if (mc.gui.screen() == null) mc.gui.setScreen(new LyricConfigScreen(null));
			}
		});

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(MOD_ID, "lyrics"), LyricHud::extract);

		LyricManager.start();
	}
}
