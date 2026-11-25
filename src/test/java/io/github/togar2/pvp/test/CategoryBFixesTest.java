package io.github.togar2.pvp.test;

import io.github.togar2.pvp.damage.DamageTypeInfo;
import io.github.togar2.pvp.entity.explosion.CrystalEntity;
import io.github.togar2.pvp.entity.projectile.WindCharge;
import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.attack.AttackFeature;
import io.github.togar2.pvp.feature.block.BlockFeature;
import io.github.togar2.pvp.feature.block.VanillaBlockFeature;
import io.github.togar2.pvp.feature.fall.FallFeature;
import io.github.togar2.pvp.feature.fall.VanillaFallFeature;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.entity.metadata.LivingEntityMeta;
import net.minestom.server.instance.Explosion;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class CategoryBFixesTest {

	@BeforeAll
	static void setup() {
		TestEnvironment.init();
	}

	private static void setItemUseTime(Player player, PlayerHand hand, long ticks) {
		player.refreshItemUse(hand, 72000L);
		try {
			Field field = Player.class.getDeclaredField("startItemUseTime");
			field.setAccessible(true);
			field.set(player, player.getAliveTicks() - ticks);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	@DisplayName("Shield blocks correctly regardless of defender pitch (looking down or up)")
	void testShieldPitchIndependence() {
		BlockFeature blockFeature = CombatFeatures.modernVanilla().get(FeatureType.BLOCK);
		assertTrue(blockFeature instanceof VanillaBlockFeature);

		InstanceContainer instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer();
		
		// Defender at (0, 50, 0), Attacker at (0, 50, -5). Defender faces North towards attacker (yaw 180 or direction (0, 0, -1)).
		Player defender = TestEnvironment.createPlayer(instance, new Pos(0, 50, 0, 180.0f, 0.0f));
		LivingEntity attacker = new LivingEntity(net.minestom.server.entity.EntityType.ZOMBIE);
		attacker.setInstance(instance, new Pos(0, 50, -5)).join();

		// Equip and activate shield in offhand
		defender.setItemInOffHand(ItemStack.of(Material.SHIELD));
		LivingEntityMeta meta = (LivingEntityMeta) defender.getEntityMeta();
		meta.setHandActive(true);
		meta.setActiveHand(PlayerHand.OFF);

		// Simulate >= 5 ticks use time
		setItemUseTime(defender, PlayerHand.OFF, 5L);

		Damage damage = new Damage(DamageType.PLAYER_ATTACK, attacker, attacker, null, 5.0f);

		// Case 1: Looking straight ahead (pitch 0) -> BLOCKED
		defender.teleport(new Pos(0, 50, 0, 180.0f, 0.0f)).join();
		assertTrue(blockFeature.isDamageBlocked(defender, damage), "Shield should block when looking straight ahead");

		// Case 2: Looking straight down at feet (pitch 85.0f) -> MUST STILL BE BLOCKED
		defender.teleport(new Pos(0, 50, 0, 180.0f, 85.0f)).join();
		assertTrue(blockFeature.isDamageBlocked(defender, damage), "Shield must block when looking down at feet");

		// Case 3: Looking straight up at sky (pitch -85.0f) -> MUST STILL BE BLOCKED
		defender.teleport(new Pos(0, 50, 0, 180.0f, -85.0f)).join();
		assertTrue(blockFeature.isDamageBlocked(defender, damage), "Shield must block when looking up at sky");

		// Case 4: Facing away from attacker (yaw 0.0f) -> NOT BLOCKED
		defender.teleport(new Pos(0, 50, 0, 0.0f, 0.0f)).join();
		assertFalse(blockFeature.isDamageBlocked(defender, damage), "Shield must NOT block when facing away");
	}

	@Test
	@DisplayName("Shield requires 5-tick warm-up delay before blocking (vanilla parity)")
	void testShieldWarmupDelay() {
		BlockFeature blockFeature = CombatFeatures.modernVanilla().get(FeatureType.BLOCK);
		InstanceContainer instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer();

		Player defender = TestEnvironment.createPlayer(instance, new Pos(0, 50, 0, 180.0f, 0.0f));
		LivingEntity attacker = new LivingEntity(net.minestom.server.entity.EntityType.ZOMBIE);
		attacker.setInstance(instance, new Pos(0, 50, -5)).join();

		defender.setItemInOffHand(ItemStack.of(Material.SHIELD));
		LivingEntityMeta meta = (LivingEntityMeta) defender.getEntityMeta();
		meta.setHandActive(true);
		meta.setActiveHand(PlayerHand.OFF);

		Damage damage = new Damage(DamageType.PLAYER_ATTACK, attacker, attacker, null, 5.0f);

		// Tick 0: Should NOT block
		setItemUseTime(defender, PlayerHand.OFF, 0L);
		assertFalse(blockFeature.isDamageBlocked(defender, damage), "Shield must not block on tick 0");

		// Tick 4: Should NOT block
		setItemUseTime(defender, PlayerHand.OFF, 4L);
		assertFalse(blockFeature.isDamageBlocked(defender, damage), "Shield must not block on tick 4");

		// Tick 5: Should block!
		setItemUseTime(defender, PlayerHand.OFF, 5L);
		assertTrue(blockFeature.isDamageBlocked(defender, damage), "Shield must block on tick 5 (after 0.25s warm-up)");

		// Tick 20: Should block
		setItemUseTime(defender, PlayerHand.OFF, 20L);
		assertTrue(blockFeature.isDamageBlocked(defender, damage), "Shield must block on tick 20");
	}

	@Test
	@DisplayName("Armor durability degrades on damage (vanilla parity)")
	void testArmorDurabilityDegradation() {
		var combatFeatures = CombatFeatures.modernVanilla();
		InstanceContainer instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer();
		instance.eventNode().addChild(combatFeatures.createNode());

		Player player = TestEnvironment.createPlayer(instance, new Pos(0, 50, 0));
		
		// Equip diamond armor with max durability and 0 initial damage
		ItemStack helmet = ItemStack.of(Material.DIAMOND_HELMET);
		ItemStack chestplate = ItemStack.of(Material.DIAMOND_CHESTPLATE);
		ItemStack leggings = ItemStack.of(Material.DIAMOND_LEGGINGS);
		ItemStack boots = ItemStack.of(Material.DIAMOND_BOOTS);

		player.setEquipment(EquipmentSlot.HELMET, helmet);
		player.setEquipment(EquipmentSlot.CHESTPLATE, chestplate);
		player.setEquipment(EquipmentSlot.LEGGINGS, leggings);
		player.setEquipment(EquipmentSlot.BOOTS, boots);

		assertEquals(0, player.getEquipment(EquipmentSlot.HELMET).get(DataComponents.DAMAGE, 0));
		assertEquals(0, player.getEquipment(EquipmentSlot.CHESTPLATE).get(DataComponents.DAMAGE, 0));

		// Deal 8.0 damage -> damage / 4 = 2 durability per piece
		Damage damage = new Damage(DamageType.MOB_ATTACK, null, null, null, 8.0f);
		player.damage(damage);

		int helmetDamage = player.getEquipment(EquipmentSlot.HELMET).get(DataComponents.DAMAGE, 0);
		int chestDamage = player.getEquipment(EquipmentSlot.CHESTPLATE).get(DataComponents.DAMAGE, 0);
		int legsDamage = player.getEquipment(EquipmentSlot.LEGGINGS).get(DataComponents.DAMAGE, 0);
		int bootsDamage = player.getEquipment(EquipmentSlot.BOOTS).get(DataComponents.DAMAGE, 0);

		assertEquals(2, helmetDamage, "Helmet must take 2 durability damage (floor(8/4))");
		assertEquals(2, chestDamage, "Chestplate must take 2 durability damage");
		assertEquals(2, legsDamage, "Leggings must take 2 durability damage");
		assertEquals(2, bootsDamage, "Boots must take 2 durability damage");
	}

	@Test
	@DisplayName("Mace smash resets fall distance even when attack is blocked by shield")
	void testMaceFallDamageResetOnShieldBlock() {
		var features = CombatFeatures.modernVanilla();
		AttackFeature attackFeature = features.get(FeatureType.ATTACK);
		FallFeature fallFeature = features.get(FeatureType.FALL);
		assertNotNull(fallFeature);

		InstanceContainer instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer();
		instance.eventNode().addChild(features.createNode());

		Player attacker = TestEnvironment.createPlayer(instance, new Pos(0, 50, 0, 180.0f, 0.0f));
		Player defender = TestEnvironment.createPlayer(instance, new Pos(0, 50, -2, 0.0f, 0.0f));

		// Attacker holds mace and is falling from 10 blocks
		attacker.setItemInMainHand(ItemStack.of(Material.MACE));
		attacker.refreshOnGround(false);
		attacker.setVelocity(new Vec(0, -0.8, 0));
		attacker.setTag(VanillaFallFeature.FALL_DISTANCE, 10.0);
		assertEquals(10.0, fallFeature.getFallDistance(attacker), 0.01);

		// Defender raises shield facing attacker
		defender.setItemInOffHand(ItemStack.of(Material.SHIELD));
		LivingEntityMeta meta = (LivingEntityMeta) defender.getEntityMeta();
		meta.setHandActive(true);
		meta.setActiveHand(PlayerHand.OFF);
		setItemUseTime(defender, PlayerHand.OFF, 10L);

		// Attack defender
		attackFeature.performAttack(attacker, defender);

		// Even though damage was fully blocked by defender's shield, attacker's fall distance must be reset to 0!
		assertEquals(0.0, fallFeature.getFallDistance(attacker), 0.01,
				"Mace smash must negate attacker fall distance even when blocked by a shield");
	}

	@Test
	@DisplayName("Wind Charge knockback factors in Attribute.KNOCKBACK_RESISTANCE")
	void testWindChargeKnockbackResistance() {
		InstanceContainer instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer();

		// Target 1: No knockback resistance (0.0)
		LivingEntity normalTarget = new LivingEntity(net.minestom.server.entity.EntityType.ZOMBIE);
		normalTarget.setInstance(instance, new Pos(1.0, 50, 0)).join();
		normalTarget.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(0.0);

		// Target 2: 100% knockback resistance (1.0) - e.g. netherite armor
		LivingEntity resistantTarget = new LivingEntity(net.minestom.server.entity.EntityType.ZOMBIE);
		resistantTarget.setInstance(instance, new Pos(1.0, 50, 0)).join();
		resistantTarget.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1.0);

		WindCharge charge1 = new WindCharge(null, null);
		charge1.setInstance(instance, new Pos(0, 50, 0)).join();
		charge1.explode(new Pos(0, 50, 0), null);

		double normalHorizVel = normalTarget.getVelocity().x();
		assertTrue(normalHorizVel > 1.0, "Normal target should receive horizontal knockback");

		WindCharge charge2 = new WindCharge(null, null);
		charge2.setInstance(instance, new Pos(0, 50, 0)).join();
		charge2.explode(new Pos(0, 50, 0), null);

		double resistantHorizVel = resistantTarget.getVelocity().x();
		assertEquals(0.0, resistantHorizVel, 0.01,
				"100% knockback resistant target should receive zero horizontal knockback");
	}

	@Test
	@DisplayName("End crystal detonates from explosion damage and passes causing player entity")
	void testEndCrystalDaisyChainAndAttribution() {
		InstanceContainer instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer();

		AtomicBoolean exploded = new AtomicBoolean(false);
		AtomicReference<CompoundBinaryTag> capturedData = new AtomicReference<>();

		instance.setExplosionSupplier((centerX, centerY, centerZ, strength, additionalData) -> {
			exploded.set(true);
			capturedData.set(additionalData);
			return new Explosion(centerX, centerY, centerZ, strength) {
				@Override
				protected java.util.List<net.minestom.server.coordinate.Point> prepare(net.minestom.server.instance.Instance instance) {
					return java.util.List.of();
				}
				@Override
				public void apply(net.minestom.server.instance.Instance instance) {}
			};
		});

		CrystalEntity crystal = new CrystalEntity(true, false);
		crystal.setInstance(instance, new Pos(0, 50, 0)).join();

		Player player = TestEnvironment.createPlayer(instance, new Pos(5, 50, 0));

		// Hit crystal with explosion damage caused by player
		Damage explosionDamage = new Damage(DamageType.PLAYER_EXPLOSION, player, player, null, 20.0f);
		assertTrue(DamageTypeInfo.of(explosionDamage.getType()).explosive());

		boolean damaged = crystal.damage(explosionDamage);
		assertTrue(damaged, "Crystal must accept explosive damage");
		assertTrue(crystal.isRemoved(), "Crystal must be removed");
		assertTrue(exploded.get(), "Crystal must explode even when damaged by explosion (daisy chain)");

		assertNotNull(capturedData.get(), "Explosion data should be passed");
		assertEquals(player.getUuid().toString(), capturedData.get().getString("causingEntity"),
				"Player UUID must be passed as causingEntity for kill attribution");
		assertTrue(capturedData.get().getBoolean("fire"), "Fire tag must be passed");
	}

	@Test
	@DisplayName("Elytra firework rocket usage boosts player when flying with elytra")
	void testElytraFireworkRocketBoost() {
		var features = CombatFeatures.modernVanilla();
		InstanceContainer instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer();
		instance.eventNode().addChild(features.createNode());

		Player player = TestEnvironment.createPlayer(instance, new Pos(0, 50, 0, 0.0f, 0.0f)); // facing South (z+)
		player.setItemInMainHand(ItemStack.of(Material.FIREWORK_ROCKET, 10));

		// Not flying with elytra: PlayerUseItemEvent should not boost
		net.minestom.server.event.EventDispatcher.call(new net.minestom.server.event.player.PlayerUseItemEvent(
				player, PlayerHand.MAIN, player.getItemInMainHand(), 0L
		));
		assertEquals(0.0, player.getVelocity().z(), 0.01, "Should not boost when not flying with elytra");

		// Now enable elytra flight
		player.setFlyingWithElytra(true);
		assertTrue(player.isFlyingWithElytra());

		// Use firework rocket while flying
		net.minestom.server.event.EventDispatcher.call(new net.minestom.server.event.player.PlayerUseItemEvent(
				player, PlayerHand.MAIN, player.getItemInMainHand(), 0L
		));

		// Facing South (yaw 0): direction z is 1.0. Velocity.z() should be heavily positive (~30.0 b/s)
		assertTrue(player.getVelocity().z() > 20.0,
				"Using firework rocket while flying with elytra must propel player forward: " + player.getVelocity());
	}
}
