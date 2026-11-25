package io.github.togar2.pvp.test;

import io.github.togar2.pvp.damage.DamageTypeInfo;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DamageTypeInfoTest {
	
	@BeforeAll
	static void setup() {
		TestEnvironment.init();
	}
	
	@Test
	@DisplayName("Verify SONIC_BOOM is unblockable")
	void testSonicBoomUnblockable() {
		assertTrue(DamageTypeInfo.INFO_MAP.containsKey(DamageType.SONIC_BOOM),
				"SONIC_BOOM must be present in DamageTypeInfo.INFO_MAP");
		DamageTypeInfo info = DamageTypeInfo.of(DamageType.SONIC_BOOM);
		assertNotNull(info);
		assertTrue(info.unblockable(), "SONIC_BOOM damage type must be unblockable");
		assertTrue(info.bypassesArmor(), "SONIC_BOOM damage type must bypass armor");
	}
	
	@Test
	@DisplayName("Verify MACE_SMASH and SPEAR are registered and scale with difficulty")
	void testMaceSmashAndSpearScaleWithDifficulty() {
		// Verify registration in INFO_MAP
		assertTrue(DamageTypeInfo.INFO_MAP.containsKey(DamageType.MACE_SMASH),
				"MACE_SMASH must be registered in DamageTypeInfo.INFO_MAP");
		assertTrue(DamageTypeInfo.INFO_MAP.containsKey(DamageType.SPEAR),
				"SPEAR must be registered in DamageTypeInfo.INFO_MAP");
		
		DamageTypeInfo maceSmash = DamageTypeInfo.of(DamageType.MACE_SMASH);
		DamageTypeInfo spear = DamageTypeInfo.of(DamageType.SPEAR);
		
		// Verify scaleWithDifficulty enum setting
		assertEquals(DamageTypeInfo.ScaleWithDifficulty.WHEN_CAUSED_BY_LIVING_NON_PLAYER, maceSmash.scaleWithDifficulty(),
				"MACE_SMASH must scale with difficulty when caused by living non-player");
		assertEquals(DamageTypeInfo.ScaleWithDifficulty.WHEN_CAUSED_BY_LIVING_NON_PLAYER, spear.scaleWithDifficulty(),
				"SPEAR must scale with difficulty when caused by living non-player");
		
		// Verify shouldScaleWithDifficulty behavior with different attackers
		LivingEntity mobAttacker = new LivingEntity(EntityType.ZOMBIE);
		Damage maceMobDamage = new Damage(DamageType.MACE_SMASH, mobAttacker, mobAttacker, null, 10.0f);
		Damage spearMobDamage = new Damage(DamageType.SPEAR, mobAttacker, mobAttacker, null, 10.0f);
		
		assertTrue(maceSmash.shouldScaleWithDifficulty(maceMobDamage),
				"MACE_SMASH from living non-player mob should scale with difficulty");
		assertTrue(spear.shouldScaleWithDifficulty(spearMobDamage),
				"SPEAR from living non-player mob should scale with difficulty");
		
		Player playerAttacker = TestEnvironment.createPlayer(null, new Pos(0, 0, 0));
		Damage macePlayerDamage = new Damage(DamageType.MACE_SMASH, playerAttacker, playerAttacker, null, 10.0f);
		Damage spearPlayerDamage = new Damage(DamageType.SPEAR, playerAttacker, playerAttacker, null, 10.0f);
		
		assertFalse(maceSmash.shouldScaleWithDifficulty(macePlayerDamage),
				"MACE_SMASH from player should not scale with difficulty");
		assertFalse(spear.shouldScaleWithDifficulty(spearPlayerDamage),
				"SPEAR from player should not scale with difficulty");
	}
	
	@Test
	@DisplayName("Verify WIND_CHARGE is unblockable")
	void testWindChargeUnblockable() {
		assertTrue(DamageTypeInfo.INFO_MAP.containsKey(DamageType.WIND_CHARGE),
				"WIND_CHARGE must be registered in DamageTypeInfo.INFO_MAP");
		DamageTypeInfo info = DamageTypeInfo.of(DamageType.WIND_CHARGE);
		assertNotNull(info);
		assertTrue(info.unblockable(), "WIND_CHARGE damage type must be unblockable");
	}

	@Test
	@DisplayName("Verify LIGHTNING_BOLT, CAMPFIRE, and OUTSIDE_BORDER are unblockable")
	void testUnblockableTypes() {
		DamageTypeInfo lightning = DamageTypeInfo.of(DamageType.LIGHTNING_BOLT);
		assertNotNull(lightning);
		assertTrue(lightning.unblockable(), "LIGHTNING_BOLT must be unblockable");
		assertTrue(lightning.fire(), "LIGHTNING_BOLT must be fire damage");

		DamageTypeInfo campfire = DamageTypeInfo.of(DamageType.CAMPFIRE);
		assertNotNull(campfire);
		assertTrue(campfire.unblockable(), "CAMPFIRE must be unblockable");
		assertTrue(campfire.fire(), "CAMPFIRE must be fire damage");
		assertTrue(campfire.bypassesArmor(), "CAMPFIRE must bypass armor");

		DamageTypeInfo border = DamageTypeInfo.of(DamageType.OUTSIDE_BORDER);
		assertNotNull(border);
		assertTrue(border.unblockable(), "OUTSIDE_BORDER must be unblockable");
		assertTrue(border.bypassesArmor(), "OUTSIDE_BORDER must bypass armor");
	}
}
