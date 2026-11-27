package io.github.togar2.pvp.test;

import io.github.togar2.pvp.entity.projectile.HurtingProjectile;
import io.github.togar2.pvp.entity.projectile.LargeFireball;
import io.github.togar2.pvp.entity.projectile.SmallFireball;
import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.attack.AttackFeature;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.instance.Explosion;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class FireballTest {
	private static InstanceContainer instance;
	private static AttackFeature attackFeature;
	private static final AtomicReference<float[]> lastExplosion = new AtomicReference<>();
	private static final AtomicReference<CompoundBinaryTag> lastExplosionData = new AtomicReference<>();
	
	@BeforeAll
	static void setup() {
		TestEnvironment.init();
		instance = MinecraftServer.getInstanceManager().createInstanceContainer();
		attackFeature = CombatFeatures.modernVanilla().get(FeatureType.ATTACK);
		instance.setExplosionSupplier((x, y, z, strength, data) -> {
			lastExplosion.set(new float[]{x, y, z, strength});
			lastExplosionData.set(data);
			return new Explosion(x, y, z, strength) {
				@Override
				protected java.util.List<net.minestom.server.coordinate.Point> prepare(net.minestom.server.instance.Instance instance) {
					return java.util.List.of();
				}
			};
		});
	}
	
	private static LivingEntity zombie(Pos pos) {
		LivingEntity target = new LivingEntity(EntityType.ZOMBIE);
		target.getAttribute(Attribute.ARMOR).setBaseValue(0.0);
		target.getAttribute(Attribute.MAX_HEALTH).setBaseValue(100.0);
		target.heal();
		target.setInstance(instance, pos).join();
		return target;
	}
	
	@Test
	@DisplayName("Fireballs accelerate every tick: movement = (movement + normalize(movement) * 0.1) * 0.95")
	void testAcceleration() {
		LargeFireball fireball = new LargeFireball(null);
		fireball.launch(instance, new Pos(0, 100, 0), new Vec(0, 0, 5));
		double tps = ServerFlag.SERVER_TICKS_PER_SECOND;
		assertEquals(0.1 * tps, fireball.getVelocity().z(), 1e-6, "Initial movement is the direction scaled by the acceleration power");
		
		fireball.tick(0);
		double expected = (0.1 + 0.1) * 0.95;
		assertEquals(expected * tps, fireball.getVelocity().z(), 1e-6);
		fireball.tick(1);
		expected = (expected + 0.1) * 0.95;
		assertEquals(expected * tps, fireball.getVelocity().z(), 1e-6);
		fireball.remove();
	}
	
	@Test
	@DisplayName("Large fireball deals 6 damage and explodes with its power, attributing the owner")
	void testLargeFireballHit() {
		Player owner = TestEnvironment.createPlayer(instance, new Pos(20, 100, 20));
		LivingEntity target = zombie(new Pos(20, 100, 25));
		LargeFireball fireball = new LargeFireball(owner, 2);
		fireball.setInstance(instance, new Pos(20, 101, 24)).join();
		lastExplosion.set(null);
		
		assertTrue(fireball.onHit(target), "The fireball is removed after hitting");
		assertEquals(6.0f, 100.0f - target.getHealth(), 0.01f);
		assertNotNull(lastExplosion.get(), "The fireball explodes");
		assertEquals(2.0f, lastExplosion.get()[3]);
		assertTrue(lastExplosionData.get().getBoolean("fire"));
		assertEquals(owner.getUuid().toString(), lastExplosionData.get().getString("causingEntity"));
		fireball.remove();
	}
	
	@Test
	@DisplayName("Small fireball deals 5 damage and sets the target on fire for 5 seconds")
	void testSmallFireballHit() {
		LivingEntity target = zombie(new Pos(40, 100, 40));
		SmallFireball fireball = new SmallFireball(null);
		fireball.setInstance(instance, new Pos(40, 101, 39)).join();
		
		assertTrue(fireball.onHit(target));
		assertEquals(5.0f, 100.0f - target.getHealth(), 0.01f);
		assertEquals(5 * ServerFlag.SERVER_TICKS_PER_SECOND, target.getFireTicks());
		fireball.remove();
	}
	
	@Test
	@DisplayName("Hitting a fireball deflects it in the look direction and makes the attacker its owner")
	void testDeflectByAttack() {
		Player attacker = TestEnvironment.createPlayer(instance, new Pos(60, 100, 60, 90, 0)); // looking towards -x
		LargeFireball fireball = new LargeFireball(null);
		fireball.launch(instance, new Pos(58, 101, 60), new Vec(0, 0, 1));
		
		assertFalse(attackFeature.performAttack(attacker, fireball), "Deflecting is not a damaging attack");
		assertSame(attacker, fireball.getShooter());
		assertEquals(-ServerFlag.SERVER_TICKS_PER_SECOND, fireball.getVelocity().x(), 0.01);
		assertEquals(0.0, fireball.getVelocity().z(), 0.01);
		fireball.remove();
	}
	
	@Test
	@DisplayName("A small fireball hitting the ground places fire on top of it")
	void testSmallFireballPlacesFire() {
		for (int x = 78; x <= 82; x++) for (int z = 78; z <= 82; z++) instance.setBlock(x, 99, z, Block.STONE);
		SmallFireball fireball = new SmallFireball(null);
		fireball.launch(instance, new Pos(80.5, 102, 80.5), new Vec(0, -1, 0));
		
		for (int i = 0; i < 60 && !fireball.isRemoved(); i++) fireball.tick(i);
		MinecraftServer.process().scheduler().process();
		
		assertTrue(instance.getBlock(80, 100, 80).compare(Block.FIRE), "Fire should be placed above the stone");
	}
}
