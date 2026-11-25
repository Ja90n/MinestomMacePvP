package io.github.togar2.pvp.test;

import io.github.togar2.pvp.entity.projectile.WindCharge;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.fall.FallFeature;
import io.github.togar2.pvp.feature.fall.VanillaFallFeature;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.instance.InstanceContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WindChargeTest {
	private static InstanceContainer instance;
	private static FallFeature fallFeature;
	
	@BeforeAll
	public static void setup() {
		TestEnvironment.init();
		instance = MinecraftServer.getInstanceManager().createInstanceContainer();
		fallFeature = new VanillaFallFeature(new FeatureConfiguration());
	}
	
	@Test
	@DisplayName("Wind charge has no gravity and flies straight")
	public void testWindChargeAerodynamics() {
		Player player = TestEnvironment.createPlayer(instance, new Pos(0, 10, 0));
		WindCharge windCharge = new WindCharge(player, fallFeature);
		
		assertTrue(windCharge.hasNoGravity(), "WindCharge should have no gravity");
		assertEquals(0.0, windCharge.getAerodynamics().gravity(), 0.001, "WindCharge gravity should be 0.0");
	}
	
	@Test
	@DisplayName("Wind charge explosion launches shooter upward (Wind Charge Jump) and resets fall distance")
	public void testWindChargeJump() {
		Player player = TestEnvironment.createPlayer(instance, new Pos(10, 10, 10));
		player.setTag(VanillaFallFeature.FALL_DISTANCE, 15.0);
		assertEquals(15.0, fallFeature.getFallDistance(player), 0.001);
		
		WindCharge windCharge = new WindCharge(player, fallFeature);
		windCharge.setInstance(instance, new Pos(10, 9.5, 10)).join();
		
		// Explode directly underneath shooter
		windCharge.explode(new Pos(10, 9.5, 10), null);
		
		// Upward launch velocity should be high (~20+ blocks/s)
		assertTrue(player.getVelocity().y() > 15.0, "Player should be launched upward with high velocity: " + player.getVelocity().y());
		// Fall distance should be reset
		assertEquals(0.0, fallFeature.getFallDistance(player), 0.001, "Fall distance must be reset after wind charge jump");
	}
	
	@Test
	@DisplayName("Wind charge explosion pushes nearby enemy entities away radially and damages direct hit")
	public void testWindChargeRadialKnockbackAndDamage() {
		Player shooter = TestEnvironment.createPlayer(instance, new Pos(30, 10, 30));
		
		LivingEntity target = new LivingEntity(EntityType.ZOMBIE);
		target.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
		target.heal();
		target.setInstance(instance, new Pos(32, 10, 30)).join();
		
		WindCharge windCharge = new WindCharge(shooter, fallFeature);
		windCharge.setInstance(instance, new Pos(32, 10, 30)).join();
		
		// Direct hit on target at (32, 10, 30)
		windCharge.explode(new Pos(32, 10, 30), target);
		
		// Direct damage: 1.0f -> target should have 19.0 HP
		assertEquals(19.0f, target.getHealth(), 0.01f, "Direct hit should deal 1.0 damage");
		
		// Check that target received knockback velocity
		Vec vel = target.getVelocity();
		assertTrue(vel.x() != 0 || vel.y() > 0 || vel.z() != 0, "Target should receive knockback velocity: " + vel);
	}
}
