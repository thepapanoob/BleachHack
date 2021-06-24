/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDrinker420/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package bleach.hack.module.mods;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import org.lwjgl.glfw.GLFW;

import com.google.common.collect.Streams;
import bleach.hack.eventbus.BleachSubscribe;

import bleach.hack.BleachHack;
import bleach.hack.event.events.EventTick;
import bleach.hack.module.ModuleCategory;
import bleach.hack.module.Module;
import bleach.hack.setting.base.SettingMode;
import bleach.hack.setting.base.SettingSlider;
import bleach.hack.setting.base.SettingToggle;
import bleach.hack.setting.other.SettingRotate;
import bleach.hack.util.world.EntityUtils;
import bleach.hack.util.world.WorldUtils;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class Killaura extends Module {

	private int delay = 0;

	public Killaura() {
		super("Killaura", GLFW.GLFW_KEY_K, ModuleCategory.COMBAT, "Automatically attacks entities",
				new SettingMode("Sort", "Angle", "Distance").withDesc("How to sort targets"),
				new SettingToggle("Players", true).withDesc("Attack players"),
				new SettingToggle("Mobs", true).withDesc("Attack mobs"),
				new SettingToggle("Animals", false).withDesc("Attack animals"),
				new SettingToggle("Trajectories", false).withDesc("Attack Shulker Bullets & Fireballs"),
				new SettingToggle("Armor Stands", false).withDesc("Attack armor stands"),
				new SettingToggle("Triggerbot", false).withDesc("Only attacks the entity you are looking at"),
				new SettingToggle("MultiAura", false).withDesc("Atacks multiple entities at once").withChildren(
						new SettingSlider("Targets", 1, 20, 3, 0).withDesc("How many targets to attack at once")),
				new SettingRotate(true),
				new SettingToggle("Thru Walls", false).withDesc("Attack through walls"),
				new SettingToggle("1.9 Delay", false).withDesc("Uses the 1.9+ delay between hits"),
				new SettingSlider("Range", 0, 6, 4.25, 2).withDesc("Attack range"),
				new SettingSlider("CPS", 0, 20, 8, 0).withDesc("Attack CPS"));
	}

	@BleachSubscribe
	public void onTick(EventTick event) {
		if (!mc.player.isAlive()) {
			return;
		}

		delay++;
		int reqDelay = (int) Math.round(20 / getSetting(12).asSlider().getValue());

		boolean cooldownDone = getSetting(10).asToggle().state
				? mc.player.getAttackCooldownProgress(mc.getTickDelta()) == 1.0f
				: (delay > reqDelay || reqDelay == 0);

		if (cooldownDone) {
			for (Entity e: getEntities()) {
				boolean shouldRotate = getSetting(8).asRotate().state && DebugRenderer.getTargetedEntity(mc.player, 7).orElse(null) != e;

				if (shouldRotate) {
					WorldUtils.facePosAuto(e.getX(), e.getY() + e.getHeight() / 2, e.getZ(), getSetting(8).asRotate());
				}

				boolean wasSprinting = mc.player.isSprinting();

				if (wasSprinting)
					mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, Mode.STOP_SPRINTING));

				mc.interactionManager.attackEntity(mc.player, e);
				mc.player.swingHand(Hand.MAIN_HAND);

				if (wasSprinting)
					mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, Mode.START_SPRINTING));

				delay = 0;
			}
		}
	}

	private List<Entity> getEntities() {
		Stream<Entity> targets;

		if (getSetting(6).asToggle().state) {
			Optional<Entity> entity = DebugRenderer.getTargetedEntity(mc.player, 7);

			if (!entity.isPresent()) {
				return Collections.emptyList();
			}

			targets = Stream.of(entity.get());
		} else {
			targets = Streams.stream(mc.world.getEntities());
		}

		Comparator<Entity> comparator;

		if (getSetting(0).asMode().mode == 0) {
			comparator = Comparator.comparing(e -> {
				Vec3d center = e.getBoundingBox().getCenter();

				double diffX = center.x - mc.player.getX();
				double diffY = center.y - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
				double diffZ = center.z - mc.player.getZ();

				double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

				float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90F;
				float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

				return Math.abs(MathHelper.wrapDegrees(yaw - mc.player.getYaw())) + Math.abs(MathHelper.wrapDegrees(pitch - mc.player.getPitch()));
			});
		} else {
			comparator = Comparator.comparing(mc.player::distanceTo);
		}

		return targets
				.filter(e -> !(e instanceof PlayerEntity && BleachHack.friendMang.has(e.getName().getString()))
						&& e.isAlive()
						&& e != mc.player.getVehicle()
						&& !e.getEntityName().equals(mc.getSession().getUsername())
						&& mc.player.distanceTo(e) <= getSetting(11).asSlider().getValue()
						&& (mc.player.canSee(e) || getSetting(9).asToggle().state))
				.filter(e -> (e instanceof PlayerEntity && getSetting(1).asToggle().state)
						|| (e instanceof Monster && getSetting(2).asToggle().state)
						|| (EntityUtils.isAnimal(e) && getSetting(3).asToggle().state)
						|| ((e instanceof ShulkerBulletEntity || e instanceof FireballEntity)&& getSetting(4).asToggle().state)
						|| (e instanceof ArmorStandEntity && getSetting(5).asToggle().state))
				.sorted(comparator)
				.limit(getSetting(7).asToggle().state ? getSetting(7).asToggle().getChild(0).asSlider().getValueLong() : 1L)
				.collect(Collectors.toList());
	}
}
