package io.github.togar2.pvp.feature.attributes;

import io.github.togar2.pvp.enums.ArmorMaterial;
import io.github.togar2.pvp.enums.Tool;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.utils.CombatVersion;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityTickEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.attribute.AttributeInstance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.AttributeList;
import net.minestom.server.tag.Tag;

/**
 * Vanilla implementation of {@link EquipmentFeature}
 */
public class VanillaEquipmentFeature implements EquipmentFeature, RegistrableFeature {
	public static final DefinedFeature<VanillaEquipmentFeature> DEFINED = new DefinedFeature<>(
			FeatureType.EQUIPMENT, VanillaEquipmentFeature::new,
			FeatureType.VERSION
	);
	
	private final FeatureConfiguration configuration;
	
	//TODO this probably shouldn't work this way
	// We probably want to store all the tools & armor separately per DataFeature
	private CombatVersion version;
	
	public VanillaEquipmentFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
	}
	
	@Override
	public void initDependencies() {
		this.version = configuration.get(FeatureType.VERSION);
	}
	
	/**
	 * Vanilla only applies equipment attribute modifiers at the end of the tick in which the item changed
	 * (LivingEntity#detectEquipmentUpdates), never while processing a packet. This means a hotbar swap and an
	 * attack in the same tick still use the attributes (damage, attack speed, ...) of the previously held item,
	 * while the newly held item determines everything else (enchantments, mace smash, shield disabling, ...).
	 * This is what makes attribute swapping possible, so the modifiers must be synchronised on tick.
	 */
	private static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.MAIN_HAND, EquipmentSlot.OFF_HAND,
			EquipmentSlot.HELMET, EquipmentSlot.CHESTPLATE, EquipmentSlot.LEGGINGS, EquipmentSlot.BOOTS
	};
	private static final Tag<ItemStack>[] APPLIED = createTags();
	
	/**
	 * Set on entities of which the equipment attributes are managed by this feature. Minestom applies them
	 * directly when the equipment changes, {@link io.github.togar2.pvp.player.CombatPlayerImpl} stops doing so
	 * for entities with this tag, because vanilla only applies them at the end of the tick.
	 */
	public static final Tag<Boolean> SYNCED = Tag.Transient("minestompvp:equipment_synced");
	
	@SuppressWarnings("unchecked")
	private static Tag<ItemStack>[] createTags() {
		Tag<ItemStack>[] tags = new Tag[SLOTS.length];
		for (int i = 0; i < SLOTS.length; i++) {
			tags[i] = Tag.Transient("minestompvp:applied_equipment_" + SLOTS[i].name().toLowerCase());
		}
		return tags;
	}
	
	@Override
	public void init(EventNode<EntityInstanceEvent> node) {
		node.addListener(EntityTickEvent.class, event -> {
			if (event.getEntity() instanceof LivingEntity entity) syncEquipment(entity);
		});
	}
	
	/**
	 * Applies the attribute modifiers of every equipment slot that changed since the last synchronisation.
	 */
	public void syncEquipment(LivingEntity entity) {
		for (int i = 0; i < SLOTS.length; i++) {
			EquipmentSlot slot = SLOTS[i];
			ItemStack current = entity.getEquipment(slot);
			ItemStack applied = entity.hasTag(APPLIED[i]) ? entity.getTag(APPLIED[i]) : ItemStack.AIR;
			if (applied.isSimilar(current)) continue;
			entity.setTag(APPLIED[i], current);
			
			if (version.legacy()) {
				// Legacy values are not part of the default item components
				if (slot.isArmor()) {
					ArmorMaterial.updateEquipmentAttributes(entity, applied, current, slot, version);
				} else if (slot == EquipmentSlot.MAIN_HAND) {
					Tool.updateEquipmentAttributes(entity, applied, current, slot, version);
				}
				updateComponentAttributes(entity, applied, current, slot, true);
			} else {
				// Modern: the item components (which include the default modifiers of an item) are the source of truth
				updateComponentAttributes(entity, applied, current, slot, false);
				if (slot.isArmor()) ArmorMaterial.playEquipSound(entity, current);
			}
		}
		entity.setTag(SYNCED, true);
	}
	
	/**
	 * Applies modifiers from the {@link DataComponents#ATTRIBUTE_MODIFIERS} component of an item.
	 * In vanilla an explicit component replaces the default modifiers of an item.
	 *
	 * @param explicitOnly only use the component if it was explicitly set on the stack (not the item defaults)
	 */
	public static void updateComponentAttributes(LivingEntity entity, ItemStack oldStack, ItemStack newStack,
	                                             EquipmentSlot slot, boolean explicitOnly) {
		if (!explicitOnly || oldStack.componentPatch().has(DataComponents.ATTRIBUTE_MODIFIERS)) {
			AttributeList list = oldStack.get(DataComponents.ATTRIBUTE_MODIFIERS);
			if (list != null) for (AttributeList.Modifier modifier : list.modifiers()) {
				if (!modifier.slot().contains(slot)) continue;
				AttributeInstance instance = entity.getAttribute(modifier.attribute());
				if (instance != null) instance.removeModifier(modifier.modifier().id());
			}
		}
		if (!explicitOnly || newStack.componentPatch().has(DataComponents.ATTRIBUTE_MODIFIERS)) {
			AttributeList list = newStack.get(DataComponents.ATTRIBUTE_MODIFIERS);
			if (list != null) for (AttributeList.Modifier modifier : list.modifiers()) {
				if (!modifier.slot().contains(slot)) continue;
				AttributeInstance instance = entity.getAttribute(modifier.attribute());
				if (instance != null) instance.addModifier(modifier.modifier());
			}
		}
	}
}
