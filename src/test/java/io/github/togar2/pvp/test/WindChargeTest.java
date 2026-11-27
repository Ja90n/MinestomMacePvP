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
	
	@Test
	@DisplayName("Explosion knockback follows vanilla: (1 - dist / 2r) * exposure * multiplier, and solid blocks shield entities")
	public void testExplosionKnockbackFormulaAndExposure() {
		Pos center = new Pos(60, 10, 60);
		
		// Open air: entity directly above the center, 0.6 blocks away (dist = 0.6 / 2.4)
		LivingEntity open = new LivingEntity(EntityType.ZOMBIE);
		open.setInstance(instance, new Pos(60, 10.6, 60)).join();
		io.github.togar2.pvp.utils.ExplosionKnockback.apply(instance, center, 1.2f, 1.22f, null, null, null);
		double expected = (1.0 - 0.6 / 2.4) * 1.22 * net.minestom.server.ServerFlag.SERVER_TICKS_PER_SECOND;
		assertEquals(expected, open.getVelocity().y(), 0.01, "Knockback power must follow the vanilla formula");
		
		// A solid wall between the center and the entity blocks the knockback
		for (int y = 8; y <= 14; y++) for (int x = 69; x <= 71; x++) for (int z = 59; z <= 61; z++)
			instance.setBlock(x, y, z, net.minestom.server.instance.block.Block.STONE);
		LivingEntity shielded = new LivingEntity(EntityType.ZOMBIE);
		shielded.setInstance(instance, new Pos(72.5, 10, 60.5)).join();
		io.github.togar2.pvp.utils.ExplosionKnockback.apply(instance, new Pos(68.5, 10.5, 60.5), 3.5f, 2.2f, null, null, null);
		assertEquals(0.0, shielded.getVelocity().length(), 1e-6, "Entities behind solid blocks must not be pushed");
	}
	
	@Test
	@DisplayName("Hitting a wind charge deflects it in the look direction and changes its owner, but not in its first 5 ticks")
	public void testWindChargeDeflection() {
		Player deflector = TestEnvironment.createPlayer(instance, new Pos(90, 10, 90, 90, 0)); // looking towards -x
		Player shooter = TestEnvironment.createPlayer(instance, new Pos(95, 10, 95));
		WindCharge windCharge = new WindCharge(shooter, fallFeature);
		windCharge.setInstance(instance, new Pos(88, 11, 90)).join();
		
		assertFalse(windCharge.deflect(deflector), "A fresh wind charge cannot be deflected");
		
		try {
			var field = net.minestom.server.entity.Entity.class.getDeclaredField("ticks");
			field.setAccessible(true);
			field.setLong(windCharge, 10);
		} catch (ReflectiveOperationException e) {
			// Fall back to the real tick counter below
			for (int i = 0; i < 6; i++) windCharge.tick(i);
		}
		if (windCharge.getAliveTicks() < 5) for (int i = 0; i < 6; i++) windCharge.update(i);
		
		assertTrue(windCharge.deflect(deflector));
		assertSame(deflector, windCharge.getShooter(), "The deflector becomes the owner");
		assertEquals(-20.0, windCharge.getVelocity().x(), 0.01, "Velocity is the look direction at 1 block per tick");
	}
	
	@Test
	@DisplayName("Players in the same team cannot hurt each other unless friendly fire is enabled")
	public void testFriendlyFire() {
		var team = net.minestom.server.MinecraftServer.getTeamManager().createTeam("pvp-test-team");
		Player a = TestEnvironment.createPlayer(instance, new Pos(100, 10, 100));
		Player b = TestEnvironment.createPlayer(instance, new Pos(101, 10, 100));
		a.setTeam(team);
		b.setTeam(team);
		assertFalse(io.github.togar2.pvp.utils.TeamUtil.canHarmPlayer(a, b));
		team.setAllowFriendlyFire(true);
		assertTrue(io.github.togar2.pvp.utils.TeamUtil.canHarmPlayer(a, b));
		team.setAllowFriendlyFire(false);
		Player outsider = TestEnvironment.createPlayer(instance, new Pos(102, 10, 100));
		assertTrue(io.github.togar2.pvp.utils.TeamUtil.canHarmPlayer(a, outsider));
	}
}
