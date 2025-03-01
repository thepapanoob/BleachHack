/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDrinker420/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package bleach.hack.module.mods;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;

import bleach.hack.eventbus.BleachSubscribe;
import com.google.gson.JsonElement;

import bleach.hack.module.ModuleCategory;
import bleach.hack.BleachHack;
import bleach.hack.event.events.EventTick;
import bleach.hack.module.Module;
import bleach.hack.setting.base.SettingMode;
import bleach.hack.setting.base.SettingToggle;
import bleach.hack.util.BleachLogger;
import bleach.hack.util.io.BleachFileHelper;
import bleach.hack.util.rpc.DiscordEventHandlers;
import bleach.hack.util.rpc.DiscordRPCManager;
import bleach.hack.util.rpc.DiscordRichPresence;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;

public class DiscordRPCMod extends Module {

	private String customText1 = "top text";
	private String customText2 = "bottom text";

	private int tick = 0;

	private boolean silent;

	public DiscordRPCMod() {
		super("DiscordRPC", KEY_UNBOUND, ModuleCategory.MISC, true, "Dicord RPC, use the \"rpc\" command to set a custom status",
				new SettingMode("Text 1", "Playing %server%", "%server%", "%type%", "%username% ontop", "Minecraft %mcver%", "%username%", "<- bad client", "%custom%").withDesc("Line 1"),
				new SettingMode("Text 2", "%hp% hp - Holding %item%", "%username% - %hp% hp", "Holding %item%", "%hp% hp - At %coords%", "At %coords%", "%custom%").withDesc("Line 2"),
				new SettingMode("Elapsed", "Normal", "Random", "Backwards", "None").withDesc("How to show elapsed time"),
				new SettingToggle("Silent", false).withDesc("Use a generic Minecraft title and image"));

		JsonElement t1 = BleachFileHelper.readMiscSetting("discordRPCTopText");
		JsonElement t2 = BleachFileHelper.readMiscSetting("discordRPCBottomText");

		if (t1 != null) {
			customText1 = t1.getAsString();
		}

		if (t2 != null) {
			customText2 = t2.getAsString();
		}
	}

	public void onEnable() {
		silent = getSetting(3).asToggle().state;

		tick = 0;

		BleachLogger.logger.info("Initing Discord RPC...");
		DiscordRPCManager.initialize(silent ? "727434331089272903" : "740928841433743370",
				new DiscordEventHandlers.Builder()
				.withReadyEventHandler(user -> BleachLogger.logger.info(user.username + "#" + user.discriminator + " is big gay"))
				.build());

		super.onEnable();
	}

	public void onDisable() {
		DiscordRPCManager.shutdown();

		super.onDisable();
	}

	@BleachSubscribe
	public void onTick(EventTick event) {
		if (silent != getSetting(3).asToggle().state) {
			onDisable();
			onEnable();
		}

		if (tick % 40 == 0) {
			String text1 = customText1;
			String text2 = customText2;
			long start = 0;

			switch (getSetting(0).asMode().mode) {
				case 0:
					text1 = "Playing " + (mc.getCurrentServerEntry() == null ? "Singleplayer" : mc.getCurrentServerEntry().address);
					break;
				case 1:
					text1 = mc.getCurrentServerEntry() == null ? "Singleplayer" : mc.getCurrentServerEntry().address;
					break;
				case 2:
					text1 = mc.getCurrentServerEntry() == null ? "Singleplayer" : "Multiplayer";
					break;
				case 3:
					text1 = mc.player.getEntityName() + " Ontop!";
					break;
				case 4:
					text1 = "Minecraft " + SharedConstants.getGameVersion().getName();
					break;
				case 5:
					text1 = mc.player.getEntityName();
					break;
				case 6:
					text1 = "<- bad client";
					break;
			}

			ItemStack currentItem = mc.player.getInventory().getMainHandStack();

			String customName = StringUtils.strip(currentItem.getName().getString());
			if (customName.length() > 25) {
				customName = customName.substring(0, 23) + "..";
			}

			String name = currentItem.getItem().getName().getString();
			String itemName = currentItem.isEmpty() ? "Nothing"
					: (currentItem.getCount() > 1 ? currentItem.getCount() + " " : "")
					+ (currentItem.hasCustomName() ? "\"" + customName + "\" (" + name + ")" : name);

			switch (getSetting(1).asMode().mode) {
				case 0:
					text2 = (int) mc.player.getHealth() + " hp - Holding " + itemName;
					break;
				case 1:
					text2 = mc.player.getEntityName() + " - " + (int) mc.player.getHealth() + " hp";
					break;
				case 2:
					text2 = "Holding " + itemName;
					break;
				case 3:
					text2 = (int) mc.player.getHealth() + " hp - At " + mc.player.getBlockPos().toShortString();
					break;
				case 4:
					text2 = "At " + mc.player.getBlockPos().toShortString();
					break;
			}

			switch (getSetting(2).asMode().mode) {
				case 0:
					start = System.currentTimeMillis() - tick * 50;
					break;
				case 1:
					start = System.currentTimeMillis() - RandomUtils.nextInt(0, 86400000);
					break;
				case 2:
					start = 1590000000000l + tick * 100;
					break;
			}

			DiscordRPCManager.updatePresence(
					new DiscordRichPresence.Builder(text2)
					.setBigImage(silent ? "mc" : "bh", silent ? "Minecraft " + SharedConstants.getGameVersion().getName() : "BleachHack " + BleachHack.VERSION)
					.setDetails(text1).setStartTimestamps(start).build());
		}

		if (tick % 200 == 0) {
			DiscordRPCManager.runCallbacks();
		}

		tick++;
	}

	public void setTopText(String text) {
		customText1 = text;
	}

	public void setBottomText(String text) {
		customText2 = text;
	}

	public String getTopText() {
		return customText1;
	}

	public String getBottomText() {
		return customText2;
	}
}
