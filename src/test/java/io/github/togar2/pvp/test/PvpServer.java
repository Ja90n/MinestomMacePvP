package io.github.togar2.pvp.test;

import io.github.togar2.pvp.MinestomPvP;
import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.damage.VanillaDamageFeature;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.*;
import net.minestom.server.entity.ai.EntityAIGroup;
import net.minestom.server.entity.ai.goal.MeleeAttackGoal;
import net.minestom.server.entity.ai.target.ClosestEntityTarget;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.entity.metadata.animal.AbstractHorseMeta;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.entity.EntityDamageEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.player.*;
import net.minestom.server.extras.lan.OpenToLAN;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.block.Block;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.CustomData;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.network.packet.client.play.ClientInputPacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.tag.Tag;
import net.minestom.server.world.DimensionType;
import net.minestom.server.world.attribute.EnvironmentAttribute;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interactive test server for Vanilla 1.21 / 26.x PvP (Mace, Spear, Knockback, Shields, Crits).
 * Players can choose weapon loadouts, armor, spawn dummies/hostile NPCs, or fight other players.
 */
public class PvpServer {
	private static final Pos SPAWN_POS = new Pos(0.5, 61, 0.5, 0, 0);
	private static final Pos JUMP_PLATFORM_POS = new Pos(0.5, 80, 15.5, 0, 0);
	
	private static final Tag<Boolean> IS_NPC = Tag.Boolean("isNpc");
	private static final Tag<Boolean> IS_PASSIVE_DUMMY = Tag.Boolean("isPassiveDummy");
	
	private static InstanceContainer arenaInstance;
	private static final Set<Entity> activeNpcs = ConcurrentHashMap.newKeySet();
	
	public static void main(String[] args) {
		MinecraftServer server = MinecraftServer.init();
		MinestomPvP.init();
		
		// Fullbright arena dimension with skylight enabled
		DimensionType dimension = DimensionType.builder()
				.ambientLight(1.0f)
				.skylight(true)
				.fixedTime(true)
				.setAttribute(EnvironmentAttribute.RESPAWN_ANCHOR_WORKS, true)
				.build();
		RegistryKey<DimensionType> dimKey = MinecraftServer.getDimensionTypeRegistry().register(Key.key("pvp:arena"), dimension);
		
		arenaInstance = MinecraftServer.getInstanceManager().createInstanceContainer(dimKey);
		arenaInstance.setChunkSupplier(LightingChunk::new);
		setupArenaTerrain(arenaInstance);
		arenaInstance.enableAutoChunkLoad(true);
		arenaInstance.setTime(6000L); // Bright midday sun
		
		GlobalEventHandler eventHandler = MinecraftServer.getGlobalEventHandler();
		
		// Attach full Modern Vanilla PvP features
		eventHandler.addChild(CombatFeatures.modernVanilla().createNode());
		
		setupPlayerEvents(eventHandler);
		setupDamageFeedback(eventHandler);
		setupCommands();
		
		OpenToLAN.open();
		
		int port = Integer.getInteger("server.port", args.length > 0 ? Integer.parseInt(args[0]) : 25565);
		server.start("0.0.0.0", port);
		System.out.println("======================================================");
		System.out.println("  Minestom 26.x / 1.21 PvP Test Server gestart op port " + port);
		System.out.println("  Lighting Engine (LightingChunk) actief: Daglicht & Helder!");
		System.out.println("  Join via localhost:" + port + " om Mace & Spear PvP te testen!");
		System.out.println("======================================================");
	}
	
	private static void setupArenaTerrain(InstanceContainer instance) {
		instance.setGenerator(unit -> {
			int minX = unit.absoluteStart().blockX();
			int maxX = unit.absoluteEnd().blockX();
			int minZ = unit.absoluteStart().blockZ();
			int maxZ = unit.absoluteEnd().blockZ();
			
			for (int x = minX; x < maxX; x++) {
				for (int z = minZ; z < maxZ; z++) {
					// Base foundation
					for (int y = 0; y < 60; y++) {
						unit.modifier().setBlock(x, y, z, Block.BEDROCK);
					}
					
					// Arena floor (radius 40 blocks)
					double dist = Math.sqrt(x * x + z * z);
					if (dist <= 40) {
						if (dist >= 39) {
							unit.modifier().setBlock(x, 60, z, Block.SEA_LANTERN);
						} else if ((Math.abs(x) == 10 && Math.abs(z) <= 10) || (Math.abs(z) == 10 && Math.abs(x) <= 10)) {
							unit.modifier().setBlock(x, 60, z, Block.SEA_LANTERN);
						} else if (x % 8 == 0 && z % 8 == 0) {
							unit.modifier().setBlock(x, 60, z, Block.SEA_LANTERN);
						} else {
							unit.modifier().setBlock(x, 60, z, Block.GRASS_BLOCK);
						}
					}
					
					// Elevated Jump Platform (X: -2..2, Z: 13..17, Y: 79) for Mace Smash Attack Testing
					if (x >= -2 && x <= 2 && z >= 13 && z <= 17) {
						unit.modifier().setBlock(x, 79, z, Block.SMOOTH_QUARTZ);
						if (x == -2 || x == 2 || z == 13 || z == 17) {
							unit.modifier().setBlock(x, 80, z, Block.GLASS);
						}
					}
					
					// Pillar supporting the jump platform
					if (x == 0 && z == 15) {
						for (int y = 61; y < 79; y++) {
							unit.modifier().setBlock(x, y, z, Block.CHISELED_QUARTZ_BLOCK);
						}
					}
				}
			}
		});
	}
	
	private static void setupPlayerEvents(GlobalEventHandler eventHandler) {
		eventHandler.addListener(AsyncPlayerPreLoginEvent.class, event -> {
			event.setGameProfile(new GameProfile(UUID.randomUUID(), event.getGameProfile().name()));
		});
		
		eventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
			event.setSpawningInstance(arenaInstance);
			event.getPlayer().setRespawnPoint(SPAWN_POS);
		});
		
		eventHandler.addListener(PlayerSpawnEvent.class, event -> {
			Player player = event.getPlayer();
			player.setGameMode(GameMode.SURVIVAL);
			player.setPermissionLevel(4);
			player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
			player.heal();
			player.setFood(20);
			player.addEffect(new Potion(PotionEffect.NIGHT_VISION, 0, Potion.INFINITE_DURATION, (byte) 0));
			
			giveHotbarItems(player);
			sendWelcomeChat(player);
		});
		
		// Right-click hotbar selector items
		eventHandler.addListener(PlayerUseItemEvent.class, event -> {
			Player player = event.getPlayer();
			ItemStack item = event.getItemStack();
			Material mat = item.material();
			
			if (mat == Material.COMPASS) {
				openWeaponsMenu(player);
			} else if (mat == Material.NETHERITE_CHESTPLATE) {
				openArmorMenu(player);
			} else if (mat == Material.ZOMBIE_HEAD) {
				openNpcMenu(player);
			} else if (mat == Material.SADDLE) {
				spawnAndMountHorse(player);
			} else if (mat == Material.FEATHER) {
				player.teleport(JUMP_PLATFORM_POS);
				player.sendMessage(Component.text("Je bent geteleporteerd naar het Mace Smash platform! Spring en sla!", NamedTextColor.AQUA));
			}
		});
		
		// Inventory GUI click handling
		eventHandler.addListener(InventoryPreClickEvent.class, event -> {
			if (!(event.getInventory() instanceof Inventory inv)) return;
			String title = inv.getTitle().toString();
			if (title.contains("Wapen") || title.contains("Armor") || title.contains("NPC")) {
				event.setCancelled(true);
				ItemStack clicked = event.getClickedItem();
				if (clicked.isAir()) return;
				
				Player player = event.getPlayer();
				handleMenuClick(player, clicked);
			}
		});
		
		// Dismount when pressing shift while riding
		eventHandler.addListener(PlayerInputEvent.class, event -> {
			Player player = event.getPlayer();
			Entity vehicle = player.getVehicle();
			if (vehicle != null && (event.isHoldingShiftKey() || event.hasPressedShiftKey())) {
				vehicle.removePassenger(player);
				player.sendMessage(Component.text("Je bent van het paard afgestapt.", NamedTextColor.GRAY));
			}
		});
		
		eventHandler.addListener(PlayerPacketEvent.class, event -> {
			if (event.getPacket() instanceof ClientInputPacket inputPacket && inputPacket.shift()) {
				Player player = event.getPlayer();
				Entity vehicle = player.getVehicle();
				if (vehicle != null) {
					vehicle.removePassenger(player);
					player.sendMessage(Component.text("Je bent van het paard afgestapt.", NamedTextColor.GRAY));
				}
			}
		});
		
		eventHandler.addListener(PlayerRespawnEvent.class, event -> {
			event.setRespawnPosition(SPAWN_POS);
			Player player = event.getPlayer();
			player.addEffect(new Potion(PotionEffect.NIGHT_VISION, 0, Potion.INFINITE_DURATION, (byte) 0));
			giveHotbarItems(player);
		});
	}
	
	private static void sendWelcomeChat(Player player) {
		player.sendMessage(Component.text("--------------------------------------------------", NamedTextColor.DARK_GRAY));
		player.sendMessage(Component.text(" Welkom op de Minestom 1.21 / 26.x PvP Test Server!", NamedTextColor.GOLD, TextDecoration.BOLD));
		player.sendMessage(Component.text(" Test hier de nieuwe Mace, Spears, Shields, Crits en Knockback.", NamedTextColor.GRAY));
		player.sendMessage(Component.empty());
		
		Component weaponsBtn = Component.text("[🗡️ Kies Wapen]", NamedTextColor.YELLOW, TextDecoration.BOLD)
				.clickEvent(ClickEvent.runCommand("/weapons"))
				.hoverEvent(HoverEvent.showText(Component.text("Klik om het wapenmenu te openen!")));
		
		Component armorBtn = Component.text("[🛡️ Kies Armor]", NamedTextColor.AQUA, TextDecoration.BOLD)
				.clickEvent(ClickEvent.runCommand("/armor"))
				.hoverEvent(HoverEvent.showText(Component.text("Klik om het armormenu te openen!")));
		
		Component npcBtn = Component.text("[🧟 NPC Menu]", NamedTextColor.GREEN, TextDecoration.BOLD)
				.clickEvent(ClickEvent.runCommand("/npc"))
				.hoverEvent(HoverEvent.showText(Component.text("Spawn een Dummy of Agressieve NPC!")));
		
		Component jumpBtn = Component.text("[🪶 Spring Platform]", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
				.clickEvent(ClickEvent.runCommand("/jump"))
				.hoverEvent(HoverEvent.showText(Component.text("Teleporteer naar het platform voor Mace smash hits!")));
		
		player.sendMessage(Component.text(" Snelle acties: ")
				.append(weaponsBtn).append(Component.space())
				.append(armorBtn).append(Component.space())
				.append(npcBtn).append(Component.space())
				.append(jumpBtn));
		player.sendMessage(Component.text("--------------------------------------------------", NamedTextColor.DARK_GRAY));
	}
	
	private static void giveHotbarItems(Player player) {
		player.getInventory().setItemStack(0, ItemStack.of(Material.COMPASS)
				.with(DataComponents.CUSTOM_NAME, Component.text("Kies Wapen (Right-Click)", NamedTextColor.YELLOW, TextDecoration.BOLD)));
		player.getInventory().setItemStack(1, ItemStack.of(Material.NETHERITE_CHESTPLATE)
				.with(DataComponents.CUSTOM_NAME, Component.text("Kies Armor (Right-Click)", NamedTextColor.AQUA, TextDecoration.BOLD)));
		player.getInventory().setItemStack(2, ItemStack.of(Material.ZOMBIE_HEAD)
				.with(DataComponents.CUSTOM_NAME, Component.text("NPC Menu (Right-Click)", NamedTextColor.GREEN, TextDecoration.BOLD)));
		player.getInventory().setItemStack(5, ItemStack.of(Material.ENDER_PEARL, 16)
				.with(DataComponents.CUSTOM_NAME, Component.text("16x Ender Pearls (Right-Click to Throw)", NamedTextColor.DARK_AQUA, TextDecoration.BOLD)));
		player.getInventory().setItemStack(6, ItemStack.of(Material.WIND_CHARGE, 16)
				.with(DataComponents.CUSTOM_NAME, Component.text("Wind Charges (Right-Click to Throw)", NamedTextColor.WHITE, TextDecoration.BOLD)));
		player.getInventory().setItemStack(7, ItemStack.of(Material.SADDLE)
				.with(DataComponents.CUSTOM_NAME, Component.text("Paard Spawn (Cavalry Test)", NamedTextColor.GOLD, TextDecoration.BOLD)));
		player.getInventory().setItemStack(8, ItemStack.of(Material.FEATHER)
				.with(DataComponents.CUSTOM_NAME, Component.text("Mace Spring Platform (Right-Click)", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)));
	}
	
	private static void openWeaponsMenu(Player player) {
		Inventory inv = new Inventory(InventoryType.CHEST_3_ROW, Component.text("Kies een Wapen"));
		
		// 1. Vanilla Mace
		inv.setItemStack(10, ItemStack.of(Material.MACE)
				.with(DataComponents.CUSTOM_NAME, Component.text("Standaard Mace", NamedTextColor.GOLD, TextDecoration.BOLD)));
		
		// 2. God Mace (Density V, Wind Burst III, Breach IV)
		inv.setItemStack(11, ItemStack.of(Material.MACE)
				.with(DataComponents.CUSTOM_NAME, Component.text("God Mace (Density V + WindBurst III + Breach IV)", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
				.with(DataComponents.ENCHANTMENTS, new EnchantmentList(Map.of(
						Enchantment.DENSITY, 5,
						Enchantment.WIND_BURST, 3,
						Enchantment.BREACH, 4
				))));
		
		// 3. Netherite Spear (Lunge III)
		inv.setItemStack(12, ItemStack.of(Material.NETHERITE_SPEAR)
				.with(DataComponents.CUSTOM_NAME, Component.text("Netherite Spear (Lunge III + Kinetic)", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
				.with(DataComponents.ENCHANTMENTS, new EnchantmentList(Map.of(
						Enchantment.LUNGE, 3
				))));
		
		// 4. Diamond Spear
		inv.setItemStack(13, ItemStack.of(Material.DIAMOND_SPEAR)
				.with(DataComponents.CUSTOM_NAME, Component.text("Diamond Spear (Kinetic Weapon)", NamedTextColor.AQUA, TextDecoration.BOLD)));
		
		// 5. Copper Spear
		inv.setItemStack(14, ItemStack.of(Material.COPPER_SPEAR)
				.with(DataComponents.CUSTOM_NAME, Component.text("Copper Spear", NamedTextColor.RED, TextDecoration.BOLD)));
		
		// 6. Netherite Sword (Sharpness V, Sweeping Edge III)
		inv.setItemStack(15, ItemStack.of(Material.NETHERITE_SWORD)
				.with(DataComponents.CUSTOM_NAME, Component.text("Netherite Sword (Sharpness V + Sweeping III)", NamedTextColor.WHITE, TextDecoration.BOLD))
				.with(DataComponents.ENCHANTMENTS, new EnchantmentList(Map.of(
						Enchantment.SHARPNESS, 5,
						Enchantment.SWEEPING_EDGE, 3
				))));
		
		// 7. Netherite Axe
		inv.setItemStack(16, ItemStack.of(Material.NETHERITE_AXE)
				.with(DataComponents.CUSTOM_NAME, Component.text("Netherite Axe (Schild Breker)", NamedTextColor.DARK_GRAY, TextDecoration.BOLD)));
		
		// 8. Ender Pearls (x16)
		inv.setItemStack(19, ItemStack.of(Material.ENDER_PEARL, 16)
				.with(DataComponents.CUSTOM_NAME, Component.text("16x Ender Pearls (Teleportatie)", NamedTextColor.DARK_AQUA, TextDecoration.BOLD)));
		
		// 9. Wind Charges (x16)
		inv.setItemStack(20, ItemStack.of(Material.WIND_CHARGE, 16)
				.with(DataComponents.CUSTOM_NAME, Component.text("16x Wind Charges (Wind Burst Jump & Knockback)", NamedTextColor.WHITE, TextDecoration.BOLD)));
		
		// 10. Shield
		inv.setItemStack(22, ItemStack.of(Material.SHIELD)
				.with(DataComponents.CUSTOM_NAME, Component.text("Vanilla Schild", NamedTextColor.BLUE, TextDecoration.BOLD)));
		
		player.openInventory(inv);
	}
	
	private static void openArmorMenu(Player player) {
		Inventory inv = new Inventory(InventoryType.CHEST_3_ROW, Component.text("Kies Armor"));
		
		inv.setItemStack(10, ItemStack.of(Material.NETHERITE_CHESTPLATE)
				.with(DataComponents.CUSTOM_NAME, Component.text("Full Netherite (Protection IV)", NamedTextColor.DARK_GRAY, TextDecoration.BOLD)));
		
		inv.setItemStack(12, ItemStack.of(Material.DIAMOND_CHESTPLATE)
				.with(DataComponents.CUSTOM_NAME, Component.text("Full Diamond", NamedTextColor.AQUA, TextDecoration.BOLD)));
		
		inv.setItemStack(14, ItemStack.of(Material.IRON_CHESTPLATE)
				.with(DataComponents.CUSTOM_NAME, Component.text("Full Iron", NamedTextColor.WHITE, TextDecoration.BOLD)));
		
		inv.setItemStack(15, ItemStack.of(Material.ELYTRA)
				.with(DataComponents.CUSTOM_NAME, Component.text("Elytra Kit (Elytra + 64x Fireworks)", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)));
		
		inv.setItemStack(16, ItemStack.of(Material.BARRIER)
				.with(DataComponents.CUSTOM_NAME, Component.text("Verwijder alle Armor", NamedTextColor.RED, TextDecoration.BOLD)));
		
		player.openInventory(inv);
	}
	
	private static void openNpcMenu(Player player) {
		Inventory inv = new Inventory(InventoryType.CHEST_3_ROW, Component.text("NPC Gevechtsopties"));
		
		inv.setItemStack(11, ItemStack.of(Material.ARMOR_STAND)
				.with(DataComponents.CUSTOM_NAME, Component.text("Spawn Passive Dummy (Punching Bag)", NamedTextColor.GREEN, TextDecoration.BOLD)));
		
		inv.setItemStack(13, ItemStack.of(Material.ZOMBIE_HEAD)
				.with(DataComponents.CUSTOM_NAME, Component.text("Spawn Agressieve NPC (Vecht Terug)", NamedTextColor.RED, TextDecoration.BOLD)));
		
		inv.setItemStack(15, ItemStack.of(Material.LAVA_BUCKET)
				.with(DataComponents.CUSTOM_NAME, Component.text("Verwijder alle NPCs", NamedTextColor.YELLOW, TextDecoration.BOLD)));
		
		player.openInventory(inv);
	}
	
	private static void handleMenuClick(Player player, ItemStack clicked) {
		Material mat = clicked.material();
		Component name = clicked.get(DataComponents.CUSTOM_NAME);
		String nameStr = name != null ? name.toString() : "";
		
		// Weapons
		boolean isSpear = mat.name().toLowerCase().contains("spear");
		if (mat == Material.MACE || isSpear || mat == Material.NETHERITE_SWORD || mat == Material.NETHERITE_AXE) {
			player.setItemInMainHand(clicked);
			Component displayName = clicked.get(DataComponents.CUSTOM_NAME) != null
					? clicked.get(DataComponents.CUSTOM_NAME)
					: Component.text(mat.name());
			player.sendMessage(Component.text("Wapen gekozen: ", NamedTextColor.GREEN).append(displayName));
			player.closeInventory();
		} else if (mat == Material.ENDER_PEARL) {
			player.getInventory().addItemStack(ItemStack.of(Material.ENDER_PEARL, 16));
			player.sendMessage(Component.text("16x Ender Pearls toegevoegd!", NamedTextColor.DARK_AQUA));
			player.closeInventory();
		} else if (mat == Material.WIND_CHARGE) {
			player.getInventory().addItemStack(ItemStack.of(Material.WIND_CHARGE, 16));
			player.sendMessage(Component.text("16x Wind Charges toegevoegd!", NamedTextColor.WHITE));
			player.closeInventory();
		} else if (mat == Material.SHIELD) {
			player.setEquipment(EquipmentSlot.OFF_HAND, clicked);
			player.sendMessage(Component.text("Schild in off-hand geplaatst!", NamedTextColor.BLUE));
			player.closeInventory();
		}
		// Armor sets
		else if (mat == Material.NETHERITE_CHESTPLATE && nameStr.contains("Netherite")) {
			equipArmorSet(player, Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS, true);
			player.sendMessage(Component.text("Full Netherite Armor (Prot IV) aangetrokken!", NamedTextColor.DARK_GRAY));
			player.closeInventory();
		} else if (mat == Material.DIAMOND_CHESTPLATE) {
			equipArmorSet(player, Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS, false);
			player.sendMessage(Component.text("Full Diamond Armor aangetrokken!", NamedTextColor.AQUA));
			player.closeInventory();
		} else if (mat == Material.IRON_CHESTPLATE) {
			equipArmorSet(player, Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS, false);
			player.sendMessage(Component.text("Full Iron Armor aangetrokken!", NamedTextColor.WHITE));
			player.closeInventory();
		} else if (mat == Material.ELYTRA) {
			player.setEquipment(EquipmentSlot.CHESTPLATE, ItemStack.of(Material.ELYTRA));
			if (player.getEquipment(EquipmentSlot.HELMET).isAir()) {
				player.setEquipment(EquipmentSlot.HELMET, ItemStack.of(Material.NETHERITE_HELMET));
			}
			if (player.getEquipment(EquipmentSlot.LEGGINGS).isAir()) {
				player.setEquipment(EquipmentSlot.LEGGINGS, ItemStack.of(Material.NETHERITE_LEGGINGS));
			}
			if (player.getEquipment(EquipmentSlot.BOOTS).isAir()) {
				player.setEquipment(EquipmentSlot.BOOTS, ItemStack.of(Material.NETHERITE_BOOTS));
			}
			player.getInventory().addItemStack(ItemStack.of(Material.FIREWORK_ROCKET, 64));
			player.sendMessage(Component.text("Elytra aangetrokken & 64x Firework Rockets toegevoegd om te vliegen!", NamedTextColor.LIGHT_PURPLE));
			player.closeInventory();
		} else if (mat == Material.BARRIER) {
			player.setEquipment(EquipmentSlot.HELMET, ItemStack.AIR);
			player.setEquipment(EquipmentSlot.CHESTPLATE, ItemStack.AIR);
			player.setEquipment(EquipmentSlot.LEGGINGS, ItemStack.AIR);
			player.setEquipment(EquipmentSlot.BOOTS, ItemStack.AIR);
			player.sendMessage(Component.text("Armor verwijderd!", NamedTextColor.RED));
			player.closeInventory();
		}
		// NPC options
		else if (mat == Material.ARMOR_STAND) {
			spawnDummy(player.getPosition().add(player.getPosition().direction().mul(3)));
			player.sendMessage(Component.text("Passive Dummy gespawned!", NamedTextColor.GREEN));
			player.closeInventory();
		} else if (mat == Material.ZOMBIE_HEAD) {
			spawnHostileNpc(player.getPosition().add(player.getPosition().direction().mul(5)));
			player.sendMessage(Component.text("Agressieve Combat NPC gespawned!", NamedTextColor.RED));
			player.closeInventory();
		} else if (mat == Material.LAVA_BUCKET) {
			clearNpcs();
			player.sendMessage(Component.text("Alle NPCs verwijderd!", NamedTextColor.YELLOW));
			player.closeInventory();
		}
	}
	
	private static void equipArmorSet(Player player, Material helmet, Material chestplate, Material leggings, Material boots, boolean prot4) {
		EnchantmentList enchants = prot4 ? new EnchantmentList(Map.of(Enchantment.PROTECTION, 4)) : null;
		
		player.setEquipment(EquipmentSlot.HELMET, enchants != null ? ItemStack.of(helmet).with(DataComponents.ENCHANTMENTS, enchants) : ItemStack.of(helmet));
		player.setEquipment(EquipmentSlot.CHESTPLATE, enchants != null ? ItemStack.of(chestplate).with(DataComponents.ENCHANTMENTS, enchants) : ItemStack.of(chestplate));
		player.setEquipment(EquipmentSlot.LEGGINGS, enchants != null ? ItemStack.of(leggings).with(DataComponents.ENCHANTMENTS, enchants) : ItemStack.of(leggings));
		player.setEquipment(EquipmentSlot.BOOTS, enchants != null ? ItemStack.of(boots).with(DataComponents.ENCHANTMENTS, enchants) : ItemStack.of(boots));
	}
	
	private static void spawnDummy(Pos pos) {
		EntityCreature dummy = new EntityCreature(EntityType.ZOMBIE);
		dummy.setTag(IS_NPC, true);
		dummy.setTag(IS_PASSIVE_DUMMY, true);
		dummy.getAttribute(Attribute.MAX_HEALTH).setBaseValue(100.0);
		dummy.heal();
		dummy.setCustomName(Component.text("PvP Dummy [100.0 HP]", NamedTextColor.GREEN, TextDecoration.BOLD));
		dummy.setCustomNameVisible(true);
		
		dummy.setInstance(arenaInstance, pos.withY(61)).thenRun(() -> {
			// Equip with diamond chestplate so players can test armor damage & Breach
			dummy.setEquipment(EquipmentSlot.CHESTPLATE, ItemStack.of(Material.DIAMOND_CHESTPLATE));
		});
		activeNpcs.add(dummy);
	}
	
	private static void spawnHostileNpc(Pos pos) {
		EntityCreature npc = new EntityCreature(EntityType.ZOMBIE);
		npc.setTag(IS_NPC, true);
		npc.setTag(IS_PASSIVE_DUMMY, false);
		npc.getAttribute(Attribute.MAX_HEALTH).setBaseValue(40.0);
		npc.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.28);
		npc.heal();
		npc.setCustomName(Component.text("Combat NPC [40.0 HP]", NamedTextColor.RED, TextDecoration.BOLD));
		npc.setCustomNameVisible(true);
		
		// Add aggressive AI targeting players
		EntityAIGroup aiGroup = new EntityAIGroup();
		aiGroup.getGoalSelectors().add(new MeleeAttackGoal(npc, 1.2, Duration.ofMillis(1000)));
		aiGroup.getTargetSelectors().add(new ClosestEntityTarget(npc, 25.0f, Player.class));
		npc.addAIGroup(aiGroup);
		
		npc.setInstance(arenaInstance, pos.withY(61)).thenRun(() -> {
			// Give hostile NPC a spear and iron armor
			npc.setEquipment(EquipmentSlot.MAIN_HAND, ItemStack.of(Material.DIAMOND_SPEAR));
			npc.setEquipment(EquipmentSlot.HELMET, ItemStack.of(Material.IRON_HELMET));
			npc.setEquipment(EquipmentSlot.CHESTPLATE, ItemStack.of(Material.IRON_CHESTPLATE));
		});
		activeNpcs.add(npc);
	}
	
	private static void spawnAndMountHorse(Player player) {
		LivingEntity horse = new LivingEntity(EntityType.HORSE);
		horse.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.35); // Fast horse for spear kinetic testing
		horse.getAttribute(Attribute.MAX_HEALTH).setBaseValue(50.0);
		horse.heal();
		horse.setEquipment(EquipmentSlot.SADDLE, ItemStack.of(Material.SADDLE));
		if (horse.getEntityMeta() instanceof AbstractHorseMeta horseMeta) {
			horseMeta.setTamed(true);
		}
		
		Pos spawn = player.getPosition();
		horse.setInstance(arenaInstance, spawn).thenRun(() -> {
			horse.addPassenger(player);
			player.sendMessage(Component.text("Paard gespawned! Druk op Shift of typ /dismount om af te stappen.", NamedTextColor.GOLD));
		});
		activeNpcs.add(horse);
	}
	
	private static void clearNpcs() {
		for (Entity e : activeNpcs) {
			if (!e.isRemoved()) e.remove();
		}
		activeNpcs.clear();
	}
	
	private static void setupDamageFeedback(GlobalEventHandler eventHandler) {
		eventHandler.addListener(EntityDamageEvent.class, event -> {
			Entity victim = event.getEntity();
			Entity attacker = event.getDamage().getAttacker();
			float dmg = event.getDamage().getAmount();
			
			if (attacker instanceof Player player) {
				String typeName = event.getDamage().getType().name();
				Component feedback = Component.text("Hit! ", NamedTextColor.RED, TextDecoration.BOLD)
						.append(Component.text(String.format("%.1f", dmg) + " dmg", NamedTextColor.YELLOW, TextDecoration.BOLD))
						.append(Component.text(" (" + typeName + ")", NamedTextColor.GRAY));
				
				if (victim instanceof LivingEntity livingVictim) {
					float remainingHp = Math.max(0, livingVictim.getHealth() - dmg);
					feedback = feedback.append(Component.text(" | Target HP: " + String.format("%.1f", remainingHp), NamedTextColor.GREEN));
					
					// Update dummy nametag
					if (victim.hasTag(IS_NPC)) {
						NamedTextColor color = victim.hasTag(IS_PASSIVE_DUMMY) ? NamedTextColor.GREEN : NamedTextColor.RED;
						victim.setCustomName(Component.text((victim.hasTag(IS_PASSIVE_DUMMY) ? "PvP Dummy [" : "Combat NPC [") 
								+ String.format("%.1f", remainingHp) + " HP]", color, TextDecoration.BOLD));
						
						// Auto heal passive dummy after 3 seconds
						if (victim.hasTag(IS_PASSIVE_DUMMY) && remainingHp < 30) {
							MinecraftServer.getSchedulerManager().buildTask(() -> {
								if (!victim.isRemoved()) {
									livingVictim.heal();
									victim.setCustomName(Component.text("PvP Dummy [100.0 HP]", NamedTextColor.GREEN, TextDecoration.BOLD));
								}
							}).delay(Duration.ofSeconds(3)).schedule();
						}
					}
				}
				
				player.sendActionBar(feedback);
			}
		});
	}
	
	private static void setupCommands() {
		CommandManager cm = MinecraftServer.getCommandManager();
		
		cm.register(new Command("weapons", "kit") {{
			setDefaultExecutor((sender, args) -> {
				if (sender instanceof Player p) openWeaponsMenu(p);
			});
		}});
		
		cm.register(new Command("armor") {{
			setDefaultExecutor((sender, args) -> {
				if (sender instanceof Player p) openArmorMenu(p);
			});
		}});
		
		cm.register(new Command("npc") {{
			var actionArg = ArgumentType.Word("action").from("spawn", "aggressive", "clear");
			addSyntax((sender, args) -> {
				if (!(sender instanceof Player p)) return;
				String action = args.get(actionArg);
				if ("spawn".equals(action)) {
					spawnDummy(p.getPosition().add(p.getPosition().direction().mul(3)));
					p.sendMessage(Component.text("Passive Dummy gespawned!", NamedTextColor.GREEN));
				} else if ("aggressive".equals(action)) {
					spawnHostileNpc(p.getPosition().add(p.getPosition().direction().mul(5)));
					p.sendMessage(Component.text("Agressieve Combat NPC gespawned!", NamedTextColor.RED));
				} else if ("clear".equals(action)) {
					clearNpcs();
					p.sendMessage(Component.text("Alle NPCs verwijderd!", NamedTextColor.YELLOW));
				}
			}, actionArg);
			
			setDefaultExecutor((sender, args) -> {
				if (sender instanceof Player p) openNpcMenu(p);
			});
		}});
		
		cm.register(new Command("jump", "platform") {{
			setDefaultExecutor((sender, args) -> {
				if (sender instanceof Player p) {
					p.teleport(JUMP_PLATFORM_POS);
					p.sendMessage(Component.text("Geteleporteerd naar het Mace Smash platform!", NamedTextColor.AQUA));
				}
			});
		}});
		
		cm.register(new Command("horse") {{
			setDefaultExecutor((sender, args) -> {
				if (sender instanceof Player p) spawnAndMountHorse(p);
			});
		}});
		
		cm.register(new Command("dismount") {{
			setDefaultExecutor((sender, args) -> {
				if (sender instanceof Player p) {
					Entity vehicle = p.getVehicle();
					if (vehicle != null) {
						vehicle.removePassenger(p);
						p.sendMessage(Component.text("Je bent van het paard afgestapt.", NamedTextColor.GRAY));
					} else {
						p.sendMessage(Component.text("Je zit niet op een paard of voertuig.", NamedTextColor.RED));
					}
				}
			});
		}});
		
		cm.register(new Command("heal") {{
			setDefaultExecutor((sender, args) -> {
				if (sender instanceof Player p) {
					p.heal();
					p.setFood(20);
					p.sendMessage(Component.text("Je bent volledig genezen!", NamedTextColor.GREEN));
				}
			});
		}});
	}
}
