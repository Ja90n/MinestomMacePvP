package io.github.togar2.pvp.test;

import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.knockback.KnockbackFeature;
import io.github.togar2.pvp.feature.knockback.VanillaKnockbackFeature;
import io.github.togar2.pvp.utils.CombatVersion;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class KnockbackTest {
	
	@BeforeAll
	static void setup() {
		TestEnvironment.init();
	}
	
	static class GroundedEntity extends LivingEntity {
		public GroundedEntity(EntityType type) {
			super(type);
			this.onGround = true;
		}
		
		public void setOnGround(boolean onGround) {
			this.onGround = onGround;
		}
	}
	
	@Test
	@DisplayName("With kbResistance = 1.0 (full netherite), vertical knockback is NOT reduced to 0 (vertical > 0) while horizontal is 0")
	void testKnockbackWithFullResistance() {
		KnockbackFeature kbFeature = CombatFeatures.modernVanilla().get(FeatureType.KNOCKBACK);
		assertTrue(kbFeature instanceof VanillaKnockbackFeature);
		
		net.minestom.server.instance.InstanceContainer instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer();
		
		LivingEntity attacker = new GroundedEntity(EntityType.ZOMBIE);
		attacker.setInstance(instance, new Pos(0, 0, 0, 0, 0)).join();
		
		GroundedEntity targetWithRes = new GroundedEntity(EntityType.ZOMBIE);
		targetWithRes.setInstance(instance, new Pos(1, 0, 0)).join();
		targetWithRes.setOnGround(true);
		targetWithRes.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1.0); // full resistance (e.g. netherite)
		targetWithRes.setVelocity(Vec.ZERO);
		
		Damage damage = new Damage(DamageType.PLAYER_ATTACK, attacker, attacker, null, 5.0f);
		boolean appliedWithRes = kbFeature.applyDamageKnockback(damage, targetWithRes);
		
		assertTrue(appliedWithRes, "Knockback should still apply when vertical knockback is present");
		
		Vec velWithRes = targetWithRes.getVelocity();
		double horizontalMagWithRes = Math.sqrt(velWithRes.x() * velWithRes.x() + velWithRes.z() * velWithRes.z());
		
		// Horizontal knockback should be completely 0
		assertEquals(0.0, horizontalMagWithRes, 1e-6, "Horizontal knockback must be 0 with kbResistance = 1.0");
		assertEquals(0.0, velWithRes.x(), 1e-6, "Velocity X must be 0 with kbResistance = 1.0");
		assertEquals(0.0, velWithRes.z(), 1e-6, "Velocity Z must be 0 with kbResistance = 1.0");
		
		// Vertical knockback should NOT be reduced to 0
		assertTrue(velWithRes.y() > 0.0, "Vertical knockback must NOT be reduced to 0 (must be > 0), actual: " + velWithRes.y());
		assertEquals(io.github.togar2.pvp.feature.knockback.KnockbackSettings.DEFAULT.vertical(), velWithRes.y(), 1e-4,
				"Vertical knockback should match settings vertical");
		
		// Compare with target having kbResistance = 0.0
		GroundedEntity targetNoRes = new GroundedEntity(EntityType.ZOMBIE);
		targetNoRes.setInstance(instance, new Pos(1, 0, 0)).join();
		targetNoRes.setOnGround(true);
		targetNoRes.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(0.0);
		targetNoRes.setVelocity(Vec.ZERO);
		
		boolean appliedNoRes = kbFeature.applyDamageKnockback(damage, targetNoRes);
		assertTrue(appliedNoRes);
		
		Vec velNoRes = targetNoRes.getVelocity();
		double horizontalMagNoRes = Math.sqrt(velNoRes.x() * velNoRes.x() + velNoRes.z() * velNoRes.z());
		
		assertTrue(horizontalMagNoRes > 0.0, "Target with no resistance must have horizontal knockback > 0");
		assertEquals(velNoRes.y(), velWithRes.y(), 1e-6, "Vertical knockback must be identical regardless of knockback resistance");
	}
	
	@Test
	@DisplayName("Attack knockback and sweeping knockback also preserve vertical knockback when kbResistance = 1.0")
	void testAttackAndSweepingKnockbackWithFullResistance() {
		KnockbackFeature kbFeature = CombatFeatures.modernVanilla().get(FeatureType.KNOCKBACK);
		net.minestom.server.instance.InstanceContainer instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer();
		
		LivingEntity attacker = new GroundedEntity(EntityType.ZOMBIE);
		attacker.setInstance(instance, new Pos(0, 0, 0, 0, 0)).join(); // yaw = 0
		
		// Test attack knockback
		GroundedEntity targetAttack = new GroundedEntity(EntityType.ZOMBIE);
		targetAttack.setInstance(instance, new Pos(0, 0, 1)).join();
		targetAttack.setOnGround(true);
		targetAttack.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		targetAttack.setVelocity(Vec.ZERO);
		
		boolean attackApplied = kbFeature.applyAttackKnockback(attacker, targetAttack, 1);
		assertTrue(attackApplied);
		Vec attackVel = targetAttack.getVelocity();
		assertEquals(0.0, attackVel.x(), 1e-6, "Attack knockback horizontal X must be 0 with kbResistance = 1.0");
		assertEquals(0.0, attackVel.z(), 1e-6, "Attack knockback horizontal Z must be 0 with kbResistance = 1.0");
		assertTrue(attackVel.y() > 0.0, "Attack knockback vertical must be > 0 with kbResistance = 1.0");
		
		// Test sweeping knockback
		GroundedEntity targetSweep = new GroundedEntity(EntityType.ZOMBIE);
		targetSweep.setInstance(instance, new Pos(0, 0, 1)).join();
		targetSweep.setOnGround(true);
		targetSweep.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		targetSweep.setVelocity(Vec.ZERO);
		
		boolean sweepApplied = kbFeature.applySweepingKnockback(attacker, targetSweep);
		assertTrue(sweepApplied);
		Vec sweepVel = targetSweep.getVelocity();
		assertEquals(0.0, sweepVel.x(), 1e-6, "Sweeping knockback horizontal X must be 0 with kbResistance = 1.0");
		assertEquals(0.0, sweepVel.z(), 1e-6, "Sweeping knockback horizontal Z must be 0 with kbResistance = 1.0");
		assertTrue(sweepVel.y() > 0.0, "Sweeping knockback vertical must be > 0 with kbResistance = 1.0");
	}
}
