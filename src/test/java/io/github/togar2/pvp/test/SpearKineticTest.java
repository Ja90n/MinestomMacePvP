package io.github.togar2.pvp.test;

import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.attack.VanillaAttackFeature;
import io.github.togar2.pvp.utils.KnownMovement;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.player.PlayerStabEvent;
import net.minestom.server.event.player.PlayerTickEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Vanilla 26.2 spear mechanics: jab (stab action), charge (kinetic weapon while using the item) and lunge.
 */
public class SpearKineticTest {
	private static InstanceContainer instance;
	
	@BeforeAll
	static void setup() {
		TestEnvironment.init();
		instance = MinecraftServer.getInstanceManager().createInstanceContainer();
		instance.eventNode().addChild(CombatFeatures.modernVanilla().createNode());
	}
	
	private static void setItemUseTime(Player player, long ticks) {
		player.refreshItemUse(PlayerHand.MAIN, 72000L);
		try {
			Field field = Player.class.getDeclaredField("startItemUseTime");
			field.setAccessible(true);
			field.set(player, player.getAliveTicks() - ticks);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private static LivingEntity dummy(Pos pos) {
		LivingEntity target = new LivingEntity(EntityType.ZOMBIE);
		target.getAttribute(Attribute.ARMOR).setBaseValue(0.0);
		target.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200.0);
		target.heal();
		target.setInstance(instance, pos).join();
		return target;
	}
	
	@Test
	@DisplayName("Jab hits every entity in the attack range (2.0 - 4.5 blocks) and deals the attack damage attribute")
	void testJabPiercesEntitiesInRange() {
		Player attacker = TestEnvironment.createPlayer(instance, new Pos(0, 42, 0, 0, 0)); // looking towards z+
		attacker.setItemInMainHand(ItemStack.of(Material.DIAMOND_SPEAR));
		LivingEntity near = dummy(new Pos(0, 42, 2.8));
		LivingEntity far = dummy(new Pos(0, 42, 4.0));
		LivingEntity tooClose = dummy(new Pos(0, 42, 1.0));
		LivingEntity tooFar = dummy(new Pos(0, 42, 7.0));
		
		double expected = attacker.getAttributeValue(Attribute.ATTACK_DAMAGE);
		EventDispatcher.call(new PlayerStabEvent(attacker));
		
		assertEquals(expected, 200.0 - near.getHealth(), 0.01, "First entity in range is hit");
		assertEquals(expected, 200.0 - far.getHealth(), 0.01, "The jab pierces and also hits the second entity");
		assertEquals(200.0, tooClose.getHealth(), 0.01, "Closer than the minimum reach of 2.0 is not hit");
		assertEquals(200.0, tooFar.getHealth(), 0.01, "Further than the maximum reach of 4.5 is not hit");
	}
	
	@Test
	@DisplayName("A normal attack click with a spear does nothing (vanilla ignores attack packets for piercing weapons)")
	void testNormalAttackWithSpearIsIgnored() {
		Player attacker = TestEnvironment.createPlayer(instance, new Pos(20, 42, 20, 0, 0));
		attacker.setItemInMainHand(ItemStack.of(Material.DIAMOND_SPEAR));
		LivingEntity target = dummy(new Pos(20, 42, 21.5));
		EventDispatcher.call(new EntityAttackEvent(attacker, target));
		assertEquals(200.0, target.getHealth(), 0.01);
	}
	
	@Test
	@DisplayName("Jab with Lunge III propels the attacker horizontally (look direction * (1,0,1) * 0.458 * level)")
	void testJabLunge() {
		Player attacker = TestEnvironment.createPlayer(instance, new Pos(40, 42, 40, 0, 45)); // pitched down
		attacker.setFood(20);
		attacker.setVelocity(Vec.ZERO);
		attacker.setItemInMainHand(ItemStack.of(Material.NETHERITE_SPEAR)
				.with(DataComponents.ENCHANTMENTS, new EnchantmentList(Map.of(Enchantment.LUNGE, 3))));
		
		EventDispatcher.call(new PlayerStabEvent(attacker));
		
		double expectedZ = 0.458 * 3 * 20 * Math.cos(Math.toRadians(45));
		assertEquals(expectedZ, attacker.getVelocity().z(), 0.05, "Lunge impulse uses the horizontal part of the look direction");
		assertEquals(0.0, attacker.getVelocity().y(), 1e-6, "Lunge has no vertical component");
	}
	
	@Test
	@DisplayName("Lunge needs enough food (>= 7) unless in creative")
	void testLungeNeedsFood() {
		Player attacker = TestEnvironment.createPlayer(instance, new Pos(60, 42, 60, 0, 0));
		attacker.setFood(5);
		attacker.setVelocity(Vec.ZERO);
		attacker.setItemInMainHand(ItemStack.of(Material.NETHERITE_SPEAR)
				.with(DataComponents.ENCHANTMENTS, new EnchantmentList(Map.of(Enchantment.LUNGE, 1))));
		EventDispatcher.call(new PlayerStabEvent(attacker));
		assertEquals(0.0, attacker.getVelocity().z(), 1e-6);
		
		attacker.setGameMode(GameMode.CREATIVE);
		attacker.setFood(5);
		EventDispatcher.call(new PlayerStabEvent(attacker));
		assertTrue(attacker.getVelocity().z() > 5.0, "Creative players lunge regardless of food");
	}
	
	@Test
	@DisplayName("Charging: damage is the base attack damage + floor(relative speed * multiplier) once the damage condition is met")
	void testChargeDamage() {
		Player attacker = TestEnvironment.createPlayer(instance, new Pos(80, 42, 80, 0, 0));
		attacker.setItemInMainHand(ItemStack.of(Material.DIAMOND_SPEAR));
		LivingEntity target = dummy(new Pos(80, 42, 83));
		
		setItemUseTime(attacker, 40);
		KnownMovement.set(attacker, new Vec(0, 0, 0.5)); // 10 blocks per second along the look direction
		KnownMovement.set(target, Vec.ZERO);
		
		EventDispatcher.call(new PlayerTickEvent(attacker));
		
		double base = attacker.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue();
		assertEquals(base + Math.floor(10.0 * 1.075), 200.0 - target.getHealth(), 0.01);
	}
	
	@Test
	@DisplayName("Charging below the minimum relative speed deals no damage, and a contact only hits once per 10 ticks")
	void testChargeConditionsAndContactCooldown() {
		Player attacker = TestEnvironment.createPlayer(instance, new Pos(100, 42, 100, 0, 0));
		attacker.setItemInMainHand(ItemStack.of(Material.DIAMOND_SPEAR));
		LivingEntity slowTarget = dummy(new Pos(100, 42, 103));
		
		setItemUseTime(attacker, 40);
		KnownMovement.set(attacker, new Vec(0, 0, 0.1)); // 2 blocks per second: below the minimum of 4.6
		EventDispatcher.call(new PlayerTickEvent(attacker));
		assertEquals(200.0, slowTarget.getHealth(), 0.01, "Too slow to deal damage");
		
		KnownMovement.set(attacker, new Vec(0, 0, 0.5));
		EventDispatcher.call(new PlayerTickEvent(attacker));
		assertEquals(200.0, slowTarget.getHealth(), 0.01, "Still on contact cooldown from the previous contact");
	}
	
	@Test
	@DisplayName("Charging fast enough on a horse dismounts a mounted target")
	void testChargeDismounts() {
		Player attacker = TestEnvironment.createPlayer(instance, new Pos(120, 42, 120, 0, 0));
		attacker.setItemInMainHand(ItemStack.of(Material.NETHERITE_SPEAR));
		LivingEntity target = new LivingEntity(EntityType.ZOMBIE);
		LivingEntity horse = new LivingEntity(EntityType.HORSE);
		horse.setInstance(instance, new Pos(120, 42, 123)).join();
		target.setInstance(instance, new Pos(120, 42, 123)).join();
		horse.addPassenger(target);
		assertNotNull(target.getVehicle());
		
		setItemUseTime(attacker, 30);
		KnownMovement.set(attacker, new Vec(0, 0, 0.6)); // 12 blocks per second, netherite dismount speed is 9
		KnownMovement.set(horse, Vec.ZERO);
		EventDispatcher.call(new PlayerTickEvent(attacker));
		
		assertNull(target.getVehicle(), "Target should be dismounted");
	}
	
	@Test
	@DisplayName("Spear jab respects the minimum attack charge (needs a full cooldown)")
	void testJabNeedsFullCharge() {
		Player attacker = TestEnvironment.createPlayer(instance, new Pos(140, 42, 140, 0, 0));
		attacker.setItemInMainHand(ItemStack.of(Material.DIAMOND_SPEAR));
		LivingEntity target = dummy(new Pos(140, 42, 143));
		
		EventDispatcher.call(new PlayerStabEvent(attacker));
		double afterFirst = target.getHealth();
		assertTrue(afterFirst < 200.0);
		
		// Immediately jabbing again is still on cooldown
		target.heal();
		EventDispatcher.call(new PlayerStabEvent(attacker));
		assertEquals(200.0, target.getHealth(), 0.01, "The second jab happens before the attack charge has recovered");
	}
}
