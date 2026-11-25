package io.github.togar2.pvp.test;

import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.attack.AttackValues;
import io.github.togar2.pvp.feature.attack.CriticalFeature;
import io.github.togar2.pvp.feature.attack.VanillaCriticalFeature;
import io.github.togar2.pvp.utils.FluidUtil;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CriticalHitTest {
	
	@BeforeAll
	static void setup() {
		TestEnvironment.init();
	}
	
	@Test
	@DisplayName("shouldCrit checks PotionEffect.SLOW_FALLING properly")
	void testSlowFallingPreventsCrit() {
		CriticalFeature criticalFeature = CombatFeatures.modernVanilla().get(FeatureType.CRITICAL);
		assertTrue(criticalFeature instanceof VanillaCriticalFeature);
		
		InstanceContainer instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer();
		Player player = TestEnvironment.createPlayer(instance, new Pos(0, 50, 0));
		
		// Set up ideal crit conditions: strong hit, falling, in air, not sprinting, not in water
		AttackValues.PreCritical strongAttack = new AttackValues.PreCritical(5.0f, 0.0f, 1.0, true, false, 0, 0);
		player.refreshOnGround(false);
		player.setVelocity(new Vec(0, -0.5, 0)); // falling
		player.setSprinting(false);
		
		// Ensure not touching water
		assertFalse(FluidUtil.isTouchingWater(player));
		
		// 1. Without Slow Falling -> Crit should be TRUE
		assertTrue(criticalFeature.shouldCrit(player, strongAttack),
				"Player falling under normal conditions should land a critical hit");
		
		// 2. With Slow Falling -> Crit should be FALSE
		player.addEffect(new Potion(PotionEffect.SLOW_FALLING, (byte) 0, 100));
		assertTrue(player.hasEffect(PotionEffect.SLOW_FALLING));
		assertFalse(criticalFeature.shouldCrit(player, strongAttack),
				"Player with SLOW_FALLING potion effect must NOT land a critical hit");
		
		// 3. Remove Slow Falling -> Crit should be TRUE again
		player.clearEffects();
		assertFalse(player.hasEffect(PotionEffect.SLOW_FALLING));
		assertTrue(criticalFeature.shouldCrit(player, strongAttack),
				"Player after clearing SLOW_FALLING should be able to land a critical hit");
	}
	
	@Test
	@DisplayName("shouldCrit checks water properly (touching water prevents crit)")
	void testWaterPreventsCrit() {
		CriticalFeature criticalFeature = CombatFeatures.modernVanilla().get(FeatureType.CRITICAL);
		
		InstanceContainer instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer();
		Player player = TestEnvironment.createPlayer(instance, new Pos(10.5, 50.0, 10.5));
		
		AttackValues.PreCritical strongAttack = new AttackValues.PreCritical(5.0f, 0.0f, 1.0, true, false, 0, 0);
		player.refreshOnGround(false);
		player.setVelocity(new Vec(0, -0.5, 0));
		player.setSprinting(false);
		
		// In air without water -> shouldCrit = true
		assertFalse(FluidUtil.isTouchingWater(player), "Player should not initially be touching water");
		assertTrue(criticalFeature.shouldCrit(player, strongAttack),
				"Player falling in air should crit");
		
		// Place water at player position
		instance.setBlock(10, 50, 10, Block.WATER);
		assertTrue(FluidUtil.isTouchingWater(player), "Player should now be touching water");
		assertFalse(criticalFeature.shouldCrit(player, strongAttack),
				"Player touching water must NOT land a critical hit");
		
		// Remove water (set back to air)
		instance.setBlock(10, 50, 10, Block.AIR);
		assertFalse(FluidUtil.isTouchingWater(player), "Player should no longer be touching water");
		assertTrue(criticalFeature.shouldCrit(player, strongAttack),
				"Player should crit again once out of water");
	}
	
	@Test
	@DisplayName("Non-player LivingEntity also respects slow falling check in shouldCrit")
	void testLivingEntitySlowFallingCrit() {
		CriticalFeature criticalFeature = CombatFeatures.modernVanilla().get(FeatureType.CRITICAL);
		InstanceContainer instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer();
		LivingEntity mob = new LivingEntity(EntityType.ZOMBIE);
		mob.setInstance(instance, new Pos(0, 50, 0)).join();
		AttackValues.PreCritical strongAttack = new AttackValues.PreCritical(5.0f, 0.0f, 1.0, true, false, 0, 0);
		mob.setVelocity(new Vec(0, -0.5, 0));
		
		// Non-player falling with no slow falling -> true
		assertTrue(criticalFeature.shouldCrit(mob, strongAttack));
		
		// Non-player falling with slow falling -> false
		mob.addEffect(new Potion(PotionEffect.SLOW_FALLING, (byte) 0, 100));
		assertFalse(criticalFeature.shouldCrit(mob, strongAttack),
				"LivingEntity with SLOW_FALLING must NOT land a critical hit");
	}

	@Test
	@DisplayName("Flying with elytra prevents critical hits (vanilla parity)")
	void testElytraPreventsCrit() {
		CriticalFeature criticalFeature = CombatFeatures.modernVanilla().get(FeatureType.CRITICAL);
		InstanceContainer instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer();
		Player player = TestEnvironment.createPlayer(instance, new Pos(0, 50, 0));

		AttackValues.PreCritical strongAttack = new AttackValues.PreCritical(5.0f, 0.0f, 1.0, true, false, 0, 0);
		player.refreshOnGround(false);
		player.setVelocity(new Vec(0, -0.5, 0));
		player.setSprinting(false);

		// Without elytra flight -> crit should be true
		assertTrue(criticalFeature.shouldCrit(player, strongAttack), "Falling player without elytra should crit");

		// With elytra flight -> crit must be false
		player.setFlyingWithElytra(true);
		assertTrue(player.isFlyingWithElytra());
		assertFalse(criticalFeature.shouldCrit(player, strongAttack), "Player flying with elytra must NOT crit");

		// Stop elytra flight -> crit should be true again
		player.setFlyingWithElytra(false);
		assertTrue(criticalFeature.shouldCrit(player, strongAttack), "Player no longer flying with elytra should crit again");
	}
}
