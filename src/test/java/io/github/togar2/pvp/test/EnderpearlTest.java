package io.github.togar2.pvp.test;

import io.github.togar2.pvp.entity.projectile.ThrownEnderpearl;
import io.github.togar2.pvp.entity.projectile.WindCharge;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.fall.FallFeature;
import io.github.togar2.pvp.feature.fall.VanillaFallFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.instance.InstanceContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EnderpearlTest {
	private static InstanceContainer instance;
	private static FallFeature fallFeature;
	
	@BeforeAll
	static void setup() {
		TestEnvironment.init();
		instance = MinecraftServer.getInstanceManager().createInstanceContainer();
		fallFeature = new VanillaFallFeature(new FeatureConfiguration());
	}
	
	@Test
	@DisplayName("ThrownEnderpearl teleports owner to impact position and deals 5.0 fall damage")
	void testEnderpearlTeleportAndDamage() {
		Pos startPos = new Pos(0, 50, 0);
		Pos landingPos = new Pos(25, 50, 25);
		
		Player player = TestEnvironment.createPlayer(instance, startPos);
		player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
		player.heal();
		player.setTag(VanillaFallFeature.FALL_DISTANCE, 10.0);
		
		assertEquals(20.0f, player.getHealth(), 0.01f);
		assertEquals(10.0, fallFeature.getFallDistance(player), 0.001);
		
		ThrownEnderpearl pearl = new ThrownEnderpearl(player, fallFeature);
		pearl.setInstance(instance, landingPos).join();
		
		// Simulate ticking pearl to set prevPos
		pearl.tick(0);
		
		// Impact block
		pearl.onStuck();
		
		// Player should be teleported near landing position
		assertEquals(landingPos.x(), player.getPosition().x(), 0.1, "Player should be teleported to pearl landing X");
		assertEquals(landingPos.z(), player.getPosition().z(), 0.1, "Player should be teleported to pearl landing Z");
		
		// Player should take 5.0 damage (20.0 - 5.0 = 15.0 HP)
		assertEquals(15.0f, player.getHealth(), 0.01f, "Player must take 5.0 fall damage from enderpearl landing");
		
		// Fall distance should be reset to 0
		assertEquals(0.0, fallFeature.getFallDistance(player), 0.001, "Fall distance must be reset after enderpearl teleport");
	}

	@Test
	@DisplayName("Wind Charge exploding near or hitting an Ender Pearl triggers mid-air Instant Pearl Catch (IPC)")
	void testInstantPearlCatch() {
		Pos playerPos = new Pos(0, 50, 0);
		Pos midAirPos = new Pos(0, 80, 0);

		Player player = TestEnvironment.createPlayer(instance, playerPos);
		player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
		player.heal();

		ThrownEnderpearl pearl = new ThrownEnderpearl(player, fallFeature);
		pearl.setInstance(instance, midAirPos).join();
		pearl.tick(0);

		assertFalse(pearl.isRemoved());
		assertEquals(50.0, player.getPosition().y(), 0.1);

		// Wind charge explodes at mid-air position
		io.github.togar2.pvp.entity.projectile.WindCharge windCharge = new io.github.togar2.pvp.entity.projectile.WindCharge(player, fallFeature);
		windCharge.setInstance(instance, midAirPos).join();

		// Hit pearl directly
		windCharge.onHit(pearl);

		// Pearl should be triggered and removed
		assertTrue(pearl.isRemoved(), "Pearl should be removed after being caught");

		// Player must be teleported mid-air to the catch position (Y: 80)!
		assertEquals(80.0, player.getPosition().y(), 0.5, "Player must be instantly teleported to mid-air catch location (Y: 80)");
		assertEquals(0.0, player.getPosition().x(), 0.1);
		assertEquals(0.0, player.getPosition().z(), 0.1);
	}

	@Test
	@DisplayName("Wind Charge moving in flight intercepts a thrown Ender Pearl and teleports owner mid-air")
	void testInFlightProjectileInterception() {
		Pos playerPos = new Pos(10, 50, 10);
		Player player = TestEnvironment.createPlayer(instance, playerPos);
		player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
		player.heal();

		// Pearl flying straight up at Y: 70
		ThrownEnderpearl pearl = new ThrownEnderpearl(player, fallFeature);
		pearl.setInstance(instance, new Pos(10, 70, 10)).join();
		pearl.setVelocity(new Vec(0, 20, 0)); // moving up at 1 block/tick
		pearl.tick(0);

		// Wind charge flying straight up right behind the pearl at Y: 68
		WindCharge windCharge = new WindCharge(player, fallFeature);
		windCharge.setInstance(instance, new Pos(10, 68, 10)).join();
		windCharge.setVelocity(new Vec(0, 50, 0)); // moving up faster at 2.5 blocks/tick

		// Tick wind charge so it catches up to the pearl
		windCharge.tick(50);

		// The wind charge should have intercepted the pearl during its movement tick!
		assertTrue(pearl.isRemoved(), "Pearl should be caught and removed by the in-flight Wind Charge");
		// Player should be teleported up to mid-air catch location (~70)
		assertTrue(player.getPosition().y() > 65.0, "Player must be teleported up to the mid-air interception point (got " + player.getPosition().y() + ")");
		// Player must have received upward velocity launch from the wind burst
		assertTrue(player.getVelocity().y() > 10.0, "Player must be launched upward by the wind charge blast (got " + player.getVelocity().y() + ")");
	}
}
