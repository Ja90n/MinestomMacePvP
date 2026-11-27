package io.github.togar2.pvp.test;

import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.attack.AttackFeature;
import io.github.togar2.pvp.feature.fall.FallFeature;
import io.github.togar2.pvp.feature.fall.VanillaFallFeature;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class WindBurstTest {
	private static InstanceContainer instance;
	private static AttackFeature attackFeature;
	private static FallFeature fallFeature;
	
	@BeforeAll
	static void setup() {
		TestEnvironment.init();
		instance = MinecraftServer.getInstanceManager().createInstanceContainer();
		var modernVanilla = CombatFeatures.modernVanilla();
		instance.eventNode().addChild(modernVanilla.createNode());
		attackFeature = modernVanilla.get(FeatureType.ATTACK);
		fallFeature = modernVanilla.get(FeatureType.FALL);
	}
	
	@Test
	@DisplayName("Mace Smash Attack with Wind Burst III launches player upward by (0.01 + 2.2) b/t and resets fall distance")
	void testWindBurstUpwardLaunch() {
		Player attacker = TestEnvironment.createPlayer(instance, new Pos(0, 50, 0));
		LivingEntity target = new LivingEntity(EntityType.ZOMBIE);
		target.getAttribute(Attribute.MAX_HEALTH).setBaseValue(100.0);
		target.heal();
		target.setInstance(instance, new Pos(0, 50, 2)).join();
		
		// Wind Burst III Mace
		ItemStack godMace = ItemStack.of(Material.MACE).with(DataComponents.ENCHANTMENTS, new EnchantmentList(Map.of(
				Enchantment.WIND_BURST, 3
		)));
		attacker.setItemInMainHand(godMace);
		
		// Set falling state (> 1.5 blocks fall distance)
		attacker.setTag(VanillaFallFeature.FALL_DISTANCE, 5.0);
		
		boolean hit = attackFeature.performAttack(attacker, target);
		assertTrue(hit, "Attack should succeed");
		
		// Vanilla: the mace sets y to 0.01 b/t, then the wind burst explosion adds multiplier * exposure (centered on the attacker)
		double expectedUpwardVel = (0.01 + 2.2) * ServerFlag.SERVER_TICKS_PER_SECOND;
		assertEquals(expectedUpwardVel, attacker.getVelocity().y(), 0.5, "Wind Burst III should launch upward with 44 blocks/s");
		
		// Fall distance should be reset
		assertEquals(0.0, fallFeature.getFallDistance(attacker), 0.001, "Fall distance must be reset after Mace smash hit");
	}
	
	@Test
	@DisplayName("Mace Smash Attack with Wind Burst I launches player upward by (0.01 + 1.2) b/t")
	void testWindBurstLevel1() {
		Player attacker = TestEnvironment.createPlayer(instance, new Pos(10, 50, 10));
		LivingEntity target = new LivingEntity(EntityType.ZOMBIE);
		target.getAttribute(Attribute.MAX_HEALTH).setBaseValue(100.0);
		target.heal();
		target.setInstance(instance, new Pos(10, 50, 12)).join();
		
		// Wind Burst I Mace
		ItemStack mace = ItemStack.of(Material.MACE).with(DataComponents.ENCHANTMENTS, new EnchantmentList(Map.of(
				Enchantment.WIND_BURST, 1
		)));
		attacker.setItemInMainHand(mace);
		
		// Set falling state (> 1.5 blocks fall distance)
		attacker.setTag(VanillaFallFeature.FALL_DISTANCE, 3.0);
		
		boolean hit = attackFeature.performAttack(attacker, target);
		assertTrue(hit, "Attack should succeed");
		
		double expectedUpwardVel = (0.01 + 1.2) * ServerFlag.SERVER_TICKS_PER_SECOND;
		assertEquals(expectedUpwardVel, attacker.getVelocity().y(), 0.5, "Wind Burst I should launch upward with 24 blocks/s");
	}
}
