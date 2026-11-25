package io.github.togar2.pvp.test;

import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.attack.AttackFeature;
import io.github.togar2.pvp.feature.attack.VanillaAttackFeature;
import io.github.togar2.pvp.feature.config.CombatConfiguration;
import io.github.togar2.pvp.feature.provider.DifficultyProvider;
import io.github.togar2.pvp.utils.CombatVersion;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.player.PlayerHandAnimationEvent;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SpearKineticTest {
	private static InstanceContainer instance;
	private static AttackFeature attackFeature;
	
	@BeforeAll
	static void setup() {
		TestEnvironment.init();
		instance = MinecraftServer.getInstanceManager().createInstanceContainer();
		instance.eventNode().addChild(CombatFeatures.modernVanilla().createNode());
		attackFeature = CombatFeatures.modernVanilla().get(FeatureType.ATTACK);
	}
	
	@Test
	void testSpearKineticDamageScaling() {
		Player attacker = TestEnvironment.createPlayer(instance, new Pos(0, 42, 0, 0, 0)); // facing South (z+)
		LivingEntity target = new LivingEntity(EntityType.PIG);
		target.getAttribute(Attribute.MAX_HEALTH).setBaseValue(100.0);
		target.heal();
		target.setInstance(instance, new Pos(0, 42, 3)).join();
		
		ItemStack spear = ItemStack.of(Material.DIAMOND_SPEAR);
		attacker.setItemInMainHand(spear);
		io.github.togar2.pvp.enums.Tool.updateEquipmentAttributes(attacker, ItemStack.AIR, spear, net.minestom.server.entity.EquipmentSlot.MAIN_HAND, CombatVersion.MODERN);
		
		// High speed charging (10 blocks/sec along look direction z+)
		// In Minestom velocity is per tick: 10 blocks/sec = 0.5 blocks/tick
		attacker.setVelocity(new Vec(0, 0, 0.5));
		target.setVelocity(Vec.ZERO);
		
		// In Diamond Spear: minRelativeSpeed = 4.6, multiplier = 1.075
		// speed = 10.0, relativeSpeed = 10.0 >= 4.6
		// kinetic damage = floor(10.0 * 1.075) = 10.0
		// base attack damage = 4.0
		// total expected damage = 14.0
		float targetHealthBefore = target.getHealth();
		boolean success = attackFeature.performAttack(attacker, target);
		assertTrue(success);
		
		float damageDone = targetHealthBefore - target.getHealth();
		assertEquals(14.0f, damageDone, 0.5f);
	}
	
	@Test
	void testSpearMountedCavalryChargingAndDismount() {
		Player attacker = TestEnvironment.createPlayer(instance, new Pos(10, 42, 10, 0, 0));
		LivingEntity attackerHorse = new LivingEntity(EntityType.HORSE);
		attackerHorse.setInstance(instance, new Pos(10, 42, 10)).join();
		attackerHorse.addPassenger(attacker);
		
		LivingEntity target = new LivingEntity(EntityType.ZOMBIE);
		LivingEntity targetHorse = new LivingEntity(EntityType.HORSE);
		targetHorse.setInstance(instance, new Pos(10, 42, 13)).join();
		target.setInstance(instance, new Pos(10, 42, 13)).join();
		targetHorse.addPassenger(target);
		
		attacker.setItemInMainHand(ItemStack.of(Material.NETHERITE_SPEAR));
		
		// Horse charging forward at 12 blocks/sec (0.6 blocks/tick)
		// Netherite spear minDismountSpeed = 9.0
		// Speed 12.0 >= 9.0 -> target should be dismounted!
		attackerHorse.setVelocity(new Vec(0, 0, 0.6));
		targetHorse.setVelocity(Vec.ZERO);
		
		assertTrue(target.getVehicle() != null, "Target should be mounted before attack");
		
		boolean success = attackFeature.performAttack(attacker, target);
		assertTrue(success);
		
		assertNull(target.getVehicle(), "Target should be dismounted after being charged by spear!");
	}

	@Test
	void testSpearLungeSwingInAir() {
		Player player = TestEnvironment.createPlayer(instance, new Pos(20, 42, 20, 0, 0)); // Facing South (z+)
		player.setFood(20);
		player.setVelocity(Vec.ZERO);

		ItemStack lungeSpear = ItemStack.of(Material.NETHERITE_SPEAR)
				.with(DataComponents.ENCHANTMENTS, new EnchantmentList(Map.of(Enchantment.LUNGE, 3)));
		player.setItemInMainHand(lungeSpear);
		io.github.togar2.pvp.enums.Tool.updateEquipmentAttributes(player, ItemStack.AIR, lungeSpear, net.minestom.server.entity.EquipmentSlot.MAIN_HAND, CombatVersion.MODERN);

		// Swing in the air
		EventDispatcher.call(new PlayerHandAnimationEvent(player, PlayerHand.MAIN));

		// Should have been propelled forward (positive z)
		assertTrue(player.getVelocity().z() > 20.0, "Player should be propelled forward with Lunge III (expected > 20 blocks/s, got " + player.getVelocity().z() + ")");
	}

	@Test
	void testSpearLungeOnEntityAttack() {
		Player attacker = TestEnvironment.createPlayer(instance, new Pos(30, 42, 30, 0, 0)); // Facing South (z+)
		attacker.setFood(20);
		attacker.setVelocity(Vec.ZERO);

		LivingEntity target = new LivingEntity(EntityType.ZOMBIE);
		target.setInstance(instance, new Pos(30, 42, 33)).join();

		ItemStack lungeSpear = ItemStack.of(Material.NETHERITE_SPEAR)
				.with(DataComponents.ENCHANTMENTS, new EnchantmentList(Map.of(Enchantment.LUNGE, 3)));
		attacker.setItemInMainHand(lungeSpear);
		io.github.togar2.pvp.enums.Tool.updateEquipmentAttributes(attacker, ItemStack.AIR, lungeSpear, net.minestom.server.entity.EquipmentSlot.MAIN_HAND, CombatVersion.MODERN);

		boolean success = attackFeature.performAttack(attacker, target);
		assertTrue(success, "Attack with Lunge spear should succeed");
		assertTrue(attacker.getVelocity().z() > 20.0, "Attacker should lunge forward towards target on attack");
	}
}
