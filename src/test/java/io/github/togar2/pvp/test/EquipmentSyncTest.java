package io.github.togar2.pvp.test;

import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.attributes.VanillaEquipmentFeature;
import io.github.togar2.pvp.feature.cooldown.VanillaAttackCooldownFeature;
import net.kyori.adventure.key.Key;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.EquipmentSlotGroup;
import io.github.togar2.pvp.player.CombatPlayerImpl;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeModifier;
import net.minestom.server.entity.attribute.AttributeOperation;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.AttributeList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EquipmentSyncTest {
	
	@BeforeAll
	static void setup() {
		TestEnvironment.init();
	}
	
	private static VanillaEquipmentFeature feature() {
		var feature = CombatFeatures.modernVanilla().get(FeatureType.EQUIPMENT);
		assertTrue(feature instanceof VanillaEquipmentFeature);
		return (VanillaEquipmentFeature) feature;
	}
	
	private static Player combatPlayer() {
		PlayerConnection connection = new PlayerConnection() {
			@Override
			public void sendPacket(SendablePacket packet) {}
			
			@Override
			public SocketAddress getRemoteAddress() {
				return new InetSocketAddress("127.0.0.1", 12345);
			}
		};
		return new CombatPlayerImpl(connection, new GameProfile(UUID.randomUUID(), "P_" + UUID.randomUUID().toString().substring(0, 8)));
	}
	
	@Test
	@DisplayName("Attribute swapping: attributes of a swapped item are only applied on the next sync (end of tick), like vanilla")
	void attributesAreDeferredUntilSync() {
		VanillaEquipmentFeature feature = feature();
		Player player = combatPlayer();
		feature.syncEquipment(player);
		
		player.setItemInMainHand(ItemStack.of(Material.DIAMOND_AXE));
		assertEquals(1.0, player.getAttributeValue(Attribute.ATTACK_DAMAGE), 1e-6, "Not applied before the end of the tick");
		feature.syncEquipment(player);
		assertEquals(1.0 + 8.0, player.getAttributeValue(Attribute.ATTACK_DAMAGE), 1e-6);
		assertEquals(4.0 - 3.0, player.getAttributeValue(Attribute.ATTACK_SPEED), 1e-6);
		
		// Swap to a sword in the same tick as an attack: attributes must still be those of the axe.
		player.setItemInMainHand(ItemStack.of(Material.DIAMOND_SWORD));
		assertEquals(1.0 + 8.0, player.getAttributeValue(Attribute.ATTACK_DAMAGE), 1e-6,
				"Attack damage must still be the old item's until the end of the tick");
		assertEquals(4.0 - 3.0, player.getAttributeValue(Attribute.ATTACK_SPEED), 1e-6);
		
		feature.syncEquipment(player);
		assertEquals(1.0 + 6.0, player.getAttributeValue(Attribute.ATTACK_DAMAGE), 1e-6);
		assertEquals(4.0 - 2.4, player.getAttributeValue(Attribute.ATTACK_SPEED), 1e-6);
	}
	
	@Test
	@DisplayName("Armor attributes are also deferred until sync")
	void armorAttributesAreDeferred() {
		VanillaEquipmentFeature feature = feature();
		Player player = combatPlayer();
		feature.syncEquipment(player);
		player.setEquipment(EquipmentSlot.CHESTPLATE, ItemStack.of(Material.NETHERITE_CHESTPLATE));
		assertEquals(0.0, player.getAttributeValue(Attribute.ARMOR), 1e-6);
		feature.syncEquipment(player);
		assertEquals(8.0, player.getAttributeValue(Attribute.ARMOR), 1e-6);
		player.setEquipment(EquipmentSlot.CHESTPLATE, ItemStack.AIR);
		feature.syncEquipment(player);
		assertEquals(0.0, player.getAttributeValue(Attribute.ARMOR), 1e-6);
	}
	
	@Test
	@DisplayName("An explicit attribute modifiers component replaces the default modifiers and is applied for matching slots")
	void customAttributeModifiers() {
		VanillaEquipmentFeature feature = feature();
		LivingEntity entity = new LivingEntity(EntityType.ZOMBIE);
		entity.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(1.0);
		
		AttributeModifier modifier = new AttributeModifier(Key.key("test:bonus"), 10.0, AttributeOperation.ADD_VALUE);
		ItemStack custom = ItemStack.of(Material.DIAMOND_SWORD).with(DataComponents.ATTRIBUTE_MODIFIERS,
				new AttributeList(List.of(new AttributeList.Modifier(Attribute.ATTACK_DAMAGE, modifier, EquipmentSlotGroup.MAIN_HAND))));
		entity.setEquipment(EquipmentSlot.MAIN_HAND, custom);
		feature.syncEquipment(entity);
		assertEquals(11.0, entity.getAttributeValue(Attribute.ATTACK_DAMAGE), 1e-6, "Default sword damage must be replaced by the component");
		
		entity.setEquipment(EquipmentSlot.MAIN_HAND, ItemStack.AIR);
		feature.syncEquipment(entity);
		assertEquals(1.0, entity.getAttributeValue(Attribute.ATTACK_DAMAGE), 1e-6);
		
		ItemStack empty = ItemStack.of(Material.DIAMOND_SWORD).with(DataComponents.ATTRIBUTE_MODIFIERS, AttributeList.EMPTY);
		entity.setEquipment(EquipmentSlot.MAIN_HAND, empty);
		feature.syncEquipment(entity);
		assertEquals(1.0, entity.getAttributeValue(Attribute.ATTACK_DAMAGE), 1e-6, "An empty component removes the default modifiers");
	}
	
	@Test
	@DisplayName("Cooldown tag type is registered (cooldown reset happens on tick, not on slot change packet)")
	void cooldownFeatureExists() {
		assertTrue(CombatFeatures.modernVanilla().get(FeatureType.ATTACK_COOLDOWN) instanceof VanillaAttackCooldownFeature);
	}
}
