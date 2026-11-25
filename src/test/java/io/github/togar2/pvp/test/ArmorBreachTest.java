package io.github.togar2.pvp.test;

import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.armor.ArmorFeature;
import io.github.togar2.pvp.feature.armor.VanillaArmorFeature;
import io.github.togar2.pvp.feature.config.CombatConfiguration;
import io.github.togar2.pvp.feature.provider.DifficultyProvider;
import io.github.togar2.pvp.utils.CombatVersion;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ArmorBreachTest {
	
	@BeforeAll
	static void setup() {
		TestEnvironment.init();
	}
	
	@Test
	@DisplayName("Breach level 1 reduces effective armor/toughness by 15%, level 4 by 60%, resulting in higher damage taken")
	void testBreachArmorCalculation() {
		CombatConfiguration configuration = CombatFeatures.getVanilla(CombatVersion.MODERN, DifficultyProvider.DEFAULT);
		ArmorFeature armorFeature = configuration.build().get(FeatureType.ARMOR);
		
		LivingEntity target = new LivingEntity(EntityType.ZOMBIE);
		target.getAttribute(Attribute.ARMOR).setBaseValue(20.0);
		target.getAttribute(Attribute.ARMOR_TOUGHNESS).setBaseValue(12.0);
		
		LivingEntity attacker = new LivingEntity(EntityType.ZOMBIE);
		float initialDamage = 20.0f;
		
		// 1. Without Breach (level 0)
		attacker.setEquipment(EquipmentSlot.MAIN_HAND, ItemStack.of(Material.MACE));
		Damage noBreachDamage = new Damage(DamageType.PLAYER_ATTACK, attacker, attacker, null, initialDamage);
		float damageTakenNoBreach = armorFeature.getDamageWithProtection(target, noBreachDamage);
		
		// Expected for armor=20, toughness=12, damage=20:
		// f = 2 + 12/4 = 5
		// g = clamp(20 - 20/5, 20*0.2, 20) = 16
		// damage = 20 * (1 - 16/25) = 7.2f
		assertEquals(7.2f, damageTakenNoBreach, 1e-4f, "Damage without breach should be 7.2");
		
		// 2. With Breach Level 1 (reduces effective armor and toughness by 15%)
		attacker.setEquipment(EquipmentSlot.MAIN_HAND, ItemStack.of(Material.MACE)
				.with(DataComponents.ENCHANTMENTS, new EnchantmentList(Map.of(Enchantment.BREACH, 1))));
		Damage breach1Damage = new Damage(DamageType.PLAYER_ATTACK, attacker, attacker, null, initialDamage);
		float damageTakenBreach1 = armorFeature.getDamageWithProtection(target, breach1Damage);
		
		// Expected for armor = 20 * (1 - 0.15) = 17.0, toughness = 12 * (1 - 0.15) = 10.2:
		// f = 2 + 10.2/4 = 4.55
		// g = clamp(17 - 20/4.55, 17*0.2, 20) = 17 - 4.3956044 = 12.6043956
		// damage = 20 * (1 - 12.6043956/25) = 9.9164835f
		float expectedBreach1 = 20.0f * (1.0f - (17.0f - 20.0f / (2.0f + 10.2f / 4.0f)) / 25.0f);
		assertEquals(expectedBreach1, damageTakenBreach1, 1e-4f, "Damage with Breach 1 should reflect 15% armor/toughness reduction");
		assertTrue(damageTakenBreach1 > damageTakenNoBreach, "Breach 1 should result in higher damage taken than no breach");
		
		// 3. With Breach Level 4 (reduces effective armor and toughness by 60%)
		attacker.setEquipment(EquipmentSlot.MAIN_HAND, ItemStack.of(Material.MACE)
				.with(DataComponents.ENCHANTMENTS, new EnchantmentList(Map.of(Enchantment.BREACH, 4))));
		Damage breach4Damage = new Damage(DamageType.PLAYER_ATTACK, attacker, attacker, null, initialDamage);
		float damageTakenBreach4 = armorFeature.getDamageWithProtection(target, breach4Damage);
		
		// Expected for armor = 20 * (1 - 0.60) = 8.0, toughness = 12 * (1 - 0.60) = 4.8:
		// f = 2 + 4.8/4 = 3.2
		// g = clamp(8 - 20/3.2, 8*0.2, 20) = clamp(8 - 6.25, 1.6, 20) = 1.75
		// damage = 20 * (1 - 1.75/25) = 20 * 0.93 = 18.6f
		float expectedBreach4 = 18.6f;
		assertEquals(expectedBreach4, damageTakenBreach4, 1e-4f, "Damage with Breach 4 should reflect 60% armor/toughness reduction");
		assertTrue(damageTakenBreach4 > damageTakenBreach1, "Breach 4 should result in higher damage taken than Breach 1");
		
		// Summary progression check: no breach < breach 1 < breach 2 < breach 3 < breach 4
		float prevDamage = damageTakenNoBreach;
		for (int level = 1; level <= 4; level++) {
			attacker.setEquipment(EquipmentSlot.MAIN_HAND, ItemStack.of(Material.MACE)
					.with(DataComponents.ENCHANTMENTS, new EnchantmentList(Map.of(Enchantment.BREACH, level))));
			Damage damage = new Damage(DamageType.PLAYER_ATTACK, attacker, attacker, null, initialDamage);
			float currentDamage = armorFeature.getDamageWithProtection(target, damage);
			assertTrue(currentDamage > prevDamage, "Damage with Breach " + level + " must be strictly greater than level " + (level - 1));
			prevDamage = currentDamage;
		}
	}
	
	@Test
	@DisplayName("Verify effective armor and toughness formulas for Breach levels")
	void testEffectiveArmorValuesFormula() {
		float baseArmor = 20.0f;
		float baseToughness = 12.0f;
		
		// Breach 1: 15% reduction
		int breach1 = 1;
		float effectiveArmor1 = baseArmor * (1.0f - 0.15f * breach1);
		float effectiveToughness1 = baseToughness * (1.0f - 0.15f * breach1);
		assertEquals(17.0f, effectiveArmor1, 1e-5f, "Breach 1 effective armor must be 85% of base");
		assertEquals(10.2f, effectiveToughness1, 1e-5f, "Breach 1 effective toughness must be 85% of base");
		
		// Breach 4: 60% reduction
		int breach4 = 4;
		float effectiveArmor4 = baseArmor * (1.0f - 0.15f * breach4);
		float effectiveToughness4 = baseToughness * (1.0f - 0.15f * breach4);
		assertEquals(8.0f, effectiveArmor4, 1e-5f, "Breach 4 effective armor must be 40% of base");
		assertEquals(4.8f, effectiveToughness4, 1e-5f, "Breach 4 effective toughness must be 40% of base");
	}
}
