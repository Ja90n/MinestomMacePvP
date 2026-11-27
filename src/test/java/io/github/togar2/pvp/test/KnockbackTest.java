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
	
	private static GroundedEntity grounded(net.minestom.server.instance.InstanceContainer instance, Pos pos, double resistance) {
		GroundedEntity entity = new GroundedEntity(EntityType.ZOMBIE);
		entity.setInstance(instance, pos).join();
		entity.setOnGround(true);
		entity.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(resistance);
		entity.setVelocity(Vec.ZERO);
		return entity;
	}
	
	@Test
	@DisplayName("Vanilla: kbResistance = 1.0 removes ALL knockback, horizontal and vertical (LivingEntity#knockback returns early)")
	void testKnockbackWithFullResistance() {
		KnockbackFeature kbFeature = CombatFeatures.modernVanilla().get(FeatureType.KNOCKBACK);
		assertTrue(kbFeature instanceof VanillaKnockbackFeature);
		var instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer();
		
		LivingEntity attacker = new GroundedEntity(EntityType.ZOMBIE);
		attacker.setInstance(instance, new Pos(0, 0, 0, 0, 0)).join();
		Damage damage = new Damage(DamageType.PLAYER_ATTACK, attacker, attacker, null, 5.0f);
		
		GroundedEntity full = grounded(instance, new Pos(1, 0, 0), 1.0);
		assertFalse(kbFeature.applyDamageKnockback(damage, full), "No knockback must be applied at full resistance");
		assertEquals(Vec.ZERO, full.getVelocity());
		
		GroundedEntity attackTarget = grounded(instance, new Pos(0, 0, 1), 1.0);
		assertFalse(kbFeature.applyAttackKnockback(attacker, attackTarget, 1));
		assertEquals(Vec.ZERO, attackTarget.getVelocity());
		
		GroundedEntity sweepTarget = grounded(instance, new Pos(0, 0, 1), 1.0);
		assertFalse(kbFeature.applySweepingKnockback(attacker, sweepTarget));
		assertEquals(Vec.ZERO, sweepTarget.getVelocity());
	}
	
	@Test
	@DisplayName("Vanilla: partial kbResistance scales both horizontal and vertical knockback")
	void testKnockbackWithPartialResistance() {
		KnockbackFeature kbFeature = CombatFeatures.modernVanilla().get(FeatureType.KNOCKBACK);
		var instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer();
		
		LivingEntity attacker = new GroundedEntity(EntityType.ZOMBIE);
		attacker.setInstance(instance, new Pos(0, 0, 0, 0, 0)).join();
		Damage damage = new Damage(DamageType.PLAYER_ATTACK, attacker, attacker, null, 5.0f);
		
		GroundedEntity none = grounded(instance, new Pos(1, 0, 0), 0.0);
		GroundedEntity half = grounded(instance, new Pos(1, 0, 0), 0.5);
		assertTrue(kbFeature.applyDamageKnockback(damage, none));
		assertTrue(kbFeature.applyDamageKnockback(damage, half));
		
		assertEquals(none.getVelocity().y() * 0.5, half.getVelocity().y(), 1e-6, "Vertical knockback must be scaled by 1 - resistance");
		assertEquals(Math.abs(none.getVelocity().x()) * 0.5, Math.abs(half.getVelocity().x()), 1e-6, "Horizontal knockback must be scaled by 1 - resistance");
	}
}
