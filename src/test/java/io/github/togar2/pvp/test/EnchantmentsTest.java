package io.github.togar2.pvp.test;

import io.github.togar2.pvp.enchantment.CombatEnchantment;
import io.github.togar2.pvp.enchantment.CombatEnchantments;
import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.enchantment.EnchantmentFeature;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class EnchantmentsTest {
	
	@BeforeAll
	static void setup() {
		TestEnvironment.init();
	}
	
	@Test
	@DisplayName("DENSITY, BREACH, WIND_BURST, LUNGE are registered in CombatEnchantments")
	void testCombatEnchantmentsRegistration() {
		CombatEnchantment density = CombatEnchantments.get(Enchantment.DENSITY);
		assertNotNull(density, "DENSITY must be registered in CombatEnchantments");
		assertEquals(Enchantment.DENSITY, density.getEnchantment());
		
		CombatEnchantment breach = CombatEnchantments.get(Enchantment.BREACH);
		assertNotNull(breach, "BREACH must be registered in CombatEnchantments");
		assertEquals(Enchantment.BREACH, breach.getEnchantment());
		
		CombatEnchantment windBurst = CombatEnchantments.get(Enchantment.WIND_BURST);
		assertNotNull(windBurst, "WIND_BURST must be registered in CombatEnchantments");
		assertEquals(Enchantment.WIND_BURST, windBurst.getEnchantment());
		
		CombatEnchantment lunge = CombatEnchantments.get(Enchantment.LUNGE);
		assertNotNull(lunge, "LUNGE must be registered in CombatEnchantments");
		assertEquals(Enchantment.LUNGE, lunge.getEnchantment());
	}
	
	@Test
	@DisplayName("EnchantmentFeature gets the correct levels for DENSITY, BREACH, WIND_BURST, LUNGE")
	void testEnchantmentFeatureLevels() {
		EnchantmentFeature feature = CombatFeatures.modernVanilla().get(FeatureType.ENCHANTMENT);
		assertNotNull(feature, "EnchantmentFeature must be present in modernVanilla");
		
		LivingEntity entity = new LivingEntity(EntityType.ZOMBIE);
		
		// Initially unenchanted
		assertEquals(0, feature.getDensity(entity));
		assertEquals(0, feature.getBreach(entity));
		assertEquals(0, feature.getWindBurst(entity));
		assertEquals(0, feature.getLunge(entity));
		
		// Test Density levels
		for (int level = 1; level <= 5; level++) {
			ItemStack item = ItemStack.of(Material.MACE)
					.with(net.minestom.server.component.DataComponents.ENCHANTMENTS, new EnchantmentList(Map.of(Enchantment.DENSITY, level)));
			entity.setEquipment(EquipmentSlot.MAIN_HAND, item);
			assertEquals(level, feature.getDensity(entity), "Density level must match " + level);
		}
		
		// Test Breach levels
		for (int level = 1; level <= 4; level++) {
			ItemStack item = ItemStack.of(Material.MACE)
					.with(net.minestom.server.component.DataComponents.ENCHANTMENTS, new EnchantmentList(Map.of(Enchantment.BREACH, level)));
			entity.setEquipment(EquipmentSlot.MAIN_HAND, item);
			assertEquals(level, feature.getBreach(entity), "Breach level must match " + level);
		}
		
		// Test Wind Burst levels
		for (int level = 1; level <= 3; level++) {
			ItemStack item = ItemStack.of(Material.MACE)
					.with(net.minestom.server.component.DataComponents.ENCHANTMENTS, new EnchantmentList(Map.of(Enchantment.WIND_BURST, level)));
			entity.setEquipment(EquipmentSlot.MAIN_HAND, item);
			assertEquals(level, feature.getWindBurst(entity), "Wind Burst level must match " + level);
		}
		
		// Test Lunge levels
		for (int level = 1; level <= 3; level++) {
			ItemStack item = ItemStack.of(Material.DIAMOND_SPEAR)
					.with(net.minestom.server.component.DataComponents.ENCHANTMENTS, new EnchantmentList(Map.of(Enchantment.LUNGE, level)));
			entity.setEquipment(EquipmentSlot.MAIN_HAND, item);
			assertEquals(level, feature.getLunge(entity), "Lunge level must match " + level);
		}
		
		// Test combined enchantments on single item
		ItemStack combo = ItemStack.of(Material.MACE)
				.with(net.minestom.server.component.DataComponents.ENCHANTMENTS, new EnchantmentList(Map.of(
						Enchantment.DENSITY, 5,
						Enchantment.BREACH, 4,
						Enchantment.WIND_BURST, 3,
						Enchantment.LUNGE, 2
				)));
		entity.setEquipment(EquipmentSlot.MAIN_HAND, combo);
		
		assertEquals(5, feature.getDensity(entity));
		assertEquals(4, feature.getBreach(entity));
		assertEquals(3, feature.getWindBurst(entity));
		assertEquals(2, feature.getLunge(entity));
		
		// Unequip item and verify reset to 0
		entity.setEquipment(EquipmentSlot.MAIN_HAND, ItemStack.AIR);
		assertEquals(0, feature.getDensity(entity));
		assertEquals(0, feature.getBreach(entity));
		assertEquals(0, feature.getWindBurst(entity));
		assertEquals(0, feature.getLunge(entity));
	}
}
