package io.github.togar2.pvp.test;

import io.github.togar2.pvp.enums.Tool;
import io.github.togar2.pvp.enums.ToolMaterial;
import io.github.togar2.pvp.utils.CombatVersion;
import io.github.togar2.pvp.utils.ModifierId;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeModifier;
import net.minestom.server.entity.attribute.AttributeOperation;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class ToolAttributesTest {
	
	@BeforeAll
	static void setup() {
		TestEnvironment.init();
	}
	
	@Test
	@DisplayName("Tool.MACE has correct attack damage (+5.0), attack speed (-3.4), and isMace() is true")
	void testMaceAttributes() {
		Tool mace = Tool.MACE;
		assertNotNull(mace, "Tool.MACE must exist");
		assertTrue(mace.isMace(), "Tool.MACE isMace() must be true");
		assertFalse(mace.isSword(), "Tool.MACE isSword() must be false");
		assertFalse(mace.isAxe(), "Tool.MACE isAxe() must be false");
		assertFalse(mace.isSpear(), "Tool.MACE isSpear() must be false");
		
		assertEquals(Tool.MACE, Tool.fromMaterial(Material.MACE), "Material.MACE must map to Tool.MACE");
		
		Map<Attribute, AttributeModifier> modifiers = mace.getAttributeModifiers();
		
		AttributeModifier damageMod = modifiers.get(Attribute.ATTACK_DAMAGE);
		assertNotNull(damageMod, "Mace must have ATTACK_DAMAGE modifier");
		assertEquals(5.0, damageMod.amount(), 1e-6, "Mace attack damage modifier must be +5.0");
		assertEquals(ModifierId.ATTACK_DAMAGE_MODIFIER_ID, damageMod.id());
		assertEquals(AttributeOperation.ADD_VALUE, damageMod.operation());
		
		AttributeModifier speedMod = modifiers.get(Attribute.ATTACK_SPEED);
		assertNotNull(speedMod, "Mace must have ATTACK_SPEED modifier");
		assertEquals(-3.4, speedMod.amount(), 1e-6, "Mace attack speed modifier must be -3.4");
		assertEquals(ModifierId.ATTACK_SPEED_MODIFIER_ID, speedMod.id());
		assertEquals(AttributeOperation.ADD_VALUE, speedMod.operation());
		
		// Also test updating equipment attributes on LivingEntity
		LivingEntity entity = new LivingEntity(EntityType.ZOMBIE);
		Tool.updateEquipmentAttributes(entity, ItemStack.AIR, ItemStack.of(Material.MACE), EquipmentSlot.MAIN_HAND, CombatVersion.MODERN);
		
		AttributeModifier appliedDamage = findModifier(entity.getAttribute(Attribute.ATTACK_DAMAGE), ModifierId.ATTACK_DAMAGE_MODIFIER_ID);
		assertNotNull(appliedDamage, "Applied attack damage modifier must not be null");
		assertEquals(5.0, appliedDamage.amount(), 1e-6);
		
		AttributeModifier appliedSpeed = findModifier(entity.getAttribute(Attribute.ATTACK_SPEED), ModifierId.ATTACK_SPEED_MODIFIER_ID);
		assertNotNull(appliedSpeed, "Applied attack speed modifier must not be null");
		assertEquals(-3.4, appliedSpeed.amount(), 1e-6);
	}
	
	@ParameterizedTest(name = "{0}: damage={2}, speed={3}")
	@MethodSource("spearData")
	@DisplayName("All spears have isSpear() = true, correct damages/speeds, and ENTITY_INTERACTION_RANGE modifier (+1.5)")
	void testSpearAttributes(Tool spear, Material material, float expectedDamage, float expectedSpeed) {
		assertNotNull(spear, "Spear tool must exist");
		assertTrue(spear.isSpear(), spear.name() + " isSpear() must be true");
		assertFalse(spear.isMace(), spear.name() + " isMace() must be false");
		assertFalse(spear.isSword(), spear.name() + " isSword() must be false");
		assertFalse(spear.isAxe(), spear.name() + " isAxe() must be false");
		
		assertEquals(spear, Tool.fromMaterial(material), material + " must map to " + spear.name());
		
		Map<Attribute, AttributeModifier> modifiers = spear.getAttributeModifiers();
		
		// Attack damage
		AttributeModifier damageMod = modifiers.get(Attribute.ATTACK_DAMAGE);
		assertNotNull(damageMod, spear.name() + " must have ATTACK_DAMAGE modifier");
		assertEquals(expectedDamage, (float) damageMod.amount(), 1e-4f, spear.name() + " attack damage mismatch");
		assertEquals(ModifierId.ATTACK_DAMAGE_MODIFIER_ID, damageMod.id());
		assertEquals(AttributeOperation.ADD_VALUE, damageMod.operation());
		
		// Attack speed
		AttributeModifier speedMod = modifiers.get(Attribute.ATTACK_SPEED);
		assertNotNull(speedMod, spear.name() + " must have ATTACK_SPEED modifier");
		assertEquals(expectedSpeed, (float) speedMod.amount(), 1e-4f, spear.name() + " attack speed mismatch");
		assertEquals(ModifierId.ATTACK_SPEED_MODIFIER_ID, speedMod.id());
		assertEquals(AttributeOperation.ADD_VALUE, speedMod.operation());
		
		// Entity interaction range
		AttributeModifier rangeMod = modifiers.get(Attribute.ENTITY_INTERACTION_RANGE);
		assertNotNull(rangeMod, spear.name() + " must have ENTITY_INTERACTION_RANGE modifier");
		assertEquals(1.5, rangeMod.amount(), 1e-6, spear.name() + " interaction range modifier must be +1.5");
		assertEquals(ModifierId.ENTITY_INTERACTION_RANGE_MODIFIER_ID, rangeMod.id());
		assertEquals(AttributeOperation.ADD_VALUE, rangeMod.operation());
		
		// Test applying to living entity
		LivingEntity entity = new LivingEntity(EntityType.ZOMBIE);
		Tool.updateEquipmentAttributes(entity, ItemStack.AIR, ItemStack.of(material), EquipmentSlot.MAIN_HAND, CombatVersion.MODERN);
		
		AttributeModifier appliedRange = findModifier(entity.getAttribute(Attribute.ENTITY_INTERACTION_RANGE), ModifierId.ENTITY_INTERACTION_RANGE_MODIFIER_ID);
		assertNotNull(appliedRange, "Applied range modifier must not be null on entity");
		assertEquals(1.5, appliedRange.amount(), 1e-6);
		
		// Test removing equipment
		Tool.updateEquipmentAttributes(entity, ItemStack.of(material), ItemStack.AIR, EquipmentSlot.MAIN_HAND, CombatVersion.MODERN);
		assertNull(findModifier(entity.getAttribute(Attribute.ENTITY_INTERACTION_RANGE), ModifierId.ENTITY_INTERACTION_RANGE_MODIFIER_ID),
				"Range modifier must be removed when spear is unequipped");
	}
	
	private static AttributeModifier findModifier(net.minestom.server.entity.attribute.AttributeInstance instance, net.kyori.adventure.key.Key id) {
		if (instance == null) return null;
		return instance.modifiers().stream().filter(m -> m.id().equals(id)).findFirst().orElse(null);
	}
	
	static Stream<Arguments> spearData() {
		return Stream.of(
				Arguments.of(Tool.WOODEN_SPEAR, Material.WOODEN_SPEAR, 0.0F, -2.4615F),
				Arguments.of(Tool.STONE_SPEAR, Material.STONE_SPEAR, 1.0F, -2.6667F),
				Arguments.of(Tool.COPPER_SPEAR, Material.COPPER_SPEAR, 1.0F, -2.8235F),
				Arguments.of(Tool.IRON_SPEAR, Material.IRON_SPEAR, 2.0F, -2.9474F),
				Arguments.of(Tool.GOLDEN_SPEAR, Material.GOLDEN_SPEAR, 0.0F, -2.9474F),
				Arguments.of(Tool.DIAMOND_SPEAR, Material.DIAMOND_SPEAR, 3.0F, -3.0476F),
				Arguments.of(Tool.NETHERITE_SPEAR, Material.NETHERITE_SPEAR, 4.0F, -3.1304F)
		);
	}
	
	@Test
	@DisplayName("ToolMaterial.COPPER exists and is linked properly")
	void testCopperToolMaterial() {
		ToolMaterial copper = ToolMaterial.COPPER;
		assertNotNull(copper, "ToolMaterial.COPPER must exist");
		assertEquals("COPPER", copper.name());
		
		assertEquals(1, copper.getMiningLevel(), "COPPER mining level must be 1");
		assertEquals(5.0F, copper.getMiningSpeed(), 1e-6f, "COPPER mining speed must be 5.0");
		assertEquals(1.0F, copper.getAttackDamage(), 1e-6f, "COPPER attack damage must be 1.0");
		
		assertEquals(ToolMaterial.COPPER, ToolMaterial.fromMaterial(Material.COPPER_SPEAR),
				"Material.COPPER_SPEAR must map to ToolMaterial.COPPER");
		
		// Verify Tool.COPPER_SPEAR has damage matching COPPER attack damage
		AttributeModifier copperSpearDamage = Tool.COPPER_SPEAR.getAttributeModifiers().get(Attribute.ATTACK_DAMAGE);
		assertEquals(copper.getAttackDamage(), (float) copperSpearDamage.amount(), 1e-6f);
	}
}
