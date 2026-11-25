package io.github.togar2.pvp.test;

import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.attack.SweepingFeature;
import io.github.togar2.pvp.feature.attack.VanillaSweepingFeature;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SweepingDamageTest {
	
	@BeforeAll
	static void setup() {
		TestEnvironment.init();
	}
	
	@ParameterizedTest(name = "ratio={0}, damage={1} -> expected sweeping damage={2}")
	@CsvSource({
			"0.5, 10.0, 6.0",
			"0.25, 8.0, 3.0",
			"0.8, 10.0, 9.0",
			"1.0, 10.0, 11.0",
			"0.1, 20.0, 3.0"
	})
	@DisplayName("Attribute.SWEEPING_DAMAGE_RATIO is respected when calculating sweeping damage")
	void testSweepingDamageRatioAttribute(double ratio, float baseDamage, float expectedSweepingDamage) {
		SweepingFeature feature = CombatFeatures.modernVanilla().get(FeatureType.SWEEPING);
		assertTrue(feature instanceof VanillaSweepingFeature);
		
		LivingEntity attacker = new LivingEntity(EntityType.ZOMBIE);
		attacker.getAttribute(Attribute.SWEEPING_DAMAGE_RATIO).setBaseValue(ratio);
		
		float sweepingDamage = feature.getSweepingDamage(attacker, baseDamage);
		assertEquals(expectedSweepingDamage, sweepingDamage, 1e-5f,
				"Sweeping damage must equal 1.0 + ratio * damage");
	}
	
	@Test
	@DisplayName("Attribute.SWEEPING_DAMAGE_RATIO takes priority over Sweeping Edge enchantment")
	void testSweepingDamageRatioOverridesEnchantment() {
		SweepingFeature feature = CombatFeatures.modernVanilla().get(FeatureType.SWEEPING);
		
		LivingEntity attacker = new LivingEntity(EntityType.ZOMBIE);
		// Equip diamond sword with Sweeping Edge III (ratio would normally be 0.75)
		ItemStack sword = ItemStack.of(Material.DIAMOND_SWORD)
				.with(DataComponents.ENCHANTMENTS, new EnchantmentList(Map.of(Enchantment.SWEEPING_EDGE, 3)));
		attacker.setEquipment(EquipmentSlot.MAIN_HAND, sword);
		
		// When SWEEPING_DAMAGE_RATIO is 0, sweeping edge III formula gives 1 + 0.75 * 10 = 8.5
		attacker.getAttribute(Attribute.SWEEPING_DAMAGE_RATIO).setBaseValue(0.0);
		assertEquals(8.5f, feature.getSweepingDamage(attacker, 10.0f), 1e-5f);
		
		// When SWEEPING_DAMAGE_RATIO is set to 0.5, it MUST take precedence over Sweeping Edge III
		attacker.getAttribute(Attribute.SWEEPING_DAMAGE_RATIO).setBaseValue(0.5);
		float sweepingDamage = feature.getSweepingDamage(attacker, 10.0f);
		assertEquals(6.0f, sweepingDamage, 1e-5f,
				"Sweeping damage must respect SWEEPING_DAMAGE_RATIO (1.0 + 0.5 * 10 = 6.0) over enchantment level");
	}
	
	@Test
	@DisplayName("Fallback to default 1.0 sweeping damage when ratio is 0 and no enchantment is present")
	void testSweepingDamageZeroRatioNoEnchantment() {
		SweepingFeature feature = CombatFeatures.modernVanilla().get(FeatureType.SWEEPING);
		
		LivingEntity attacker = new LivingEntity(EntityType.ZOMBIE);
		attacker.getAttribute(Attribute.SWEEPING_DAMAGE_RATIO).setBaseValue(0.0);
		attacker.setEquipment(EquipmentSlot.MAIN_HAND, ItemStack.of(Material.DIAMOND_SWORD));
		
		float sweepingDamage = feature.getSweepingDamage(attacker, 10.0f);
		assertEquals(1.0f, sweepingDamage, 1e-5f, "Default sweeping damage with no ratio and no enchantment must be 1.0");
	}
}
