package io.github.togar2.pvp.feature.projectile;

import io.github.togar2.pvp.entity.projectile.FireworkRocketEntity;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.block.BlockFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.player.CombatPlayer;
import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.event.player.PlayerUseItemOnBlockEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Vanilla implementation of {@link FireworkRocketFeature}
 */
public class VanillaFireworkRocketFeature implements FireworkRocketFeature, RegistrableFeature {

    private final FeatureConfiguration configuration;
    private BlockFeature blockFeature;

    public static final DefinedFeature<VanillaFireworkRocketFeature> DEFINED = new DefinedFeature<>(
            FeatureType.FIREWORK, VanillaFireworkRocketFeature::new,
            FeatureType.BLOCK
    );

    public VanillaFireworkRocketFeature(FeatureConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void initDependencies() {
        this.blockFeature = configuration.get(FeatureType.BLOCK);
    }

    @Override
    public void init(EventNode<@NotNull EntityInstanceEvent> node) {
        node.addListener(PlayerUseItemOnBlockEvent.class, event -> {
            if (event.getItemStack().material() != Material.FIREWORK_ROCKET) return;

            Player player = event.getPlayer();
            ItemStack itemStack = event.getItemStack();

            var offsetX = event.getCursorPosition().x();
            var offsetZ = event.getCursorPosition().z();
            Point position = getBlockPlacement(
                    event.getPosition().add(offsetX, 0, offsetZ),
                    event.getBlockFace());

            FireworkRocketEntity fireworkRocketEntity = new FireworkRocketEntity(
                    player,
                    itemStack,
                    false,
                    blockFeature);

            fireworkRocketEntity.setInstance(event.getInstance(), position);

            if (player.getGameMode().equals(GameMode.CREATIVE)) return;
            player.setItemInHand(event.getHand(), itemStack.withAmount(itemStack.amount() - 1));
        });

        node.addListener(PlayerUseItemEvent.class, event -> {
            if (event.getItemStack().material() != Material.FIREWORK_ROCKET) return;
            Player player = event.getPlayer();
            if (!player.isFlyingWithElytra()) return;

            // Give an initial launch impulse in look direction
            Vec lookDir = player.getPosition().direction();
            player.setVelocity(player.getVelocity().add(lookDir.mul(1.5 * ServerFlag.SERVER_TICKS_PER_SECOND)));
            if (player instanceof CombatPlayer combatPlayer) {
                combatPlayer.sendImmediateVelocityUpdate();
            }

            ViewUtil.viewersAndSelf(player).playSound(Sound.sound(
                    SoundEvent.ENTITY_FIREWORK_ROCKET_LAUNCH,
                    Sound.Source.PLAYER,
                    3.0f,
                    1.0f
            ), player);

            FireworkRocketEntity fireworkRocketEntity = new FireworkRocketEntity(
                    player,
                    event.getItemStack(),
                    false,
                    blockFeature);
            fireworkRocketEntity.setInstance(player.getInstance(), player.getPosition());

            if (player.getGameMode().equals(GameMode.CREATIVE)) return;
            player.setItemInHand(event.getHand(), event.getItemStack().withAmount(event.getItemStack().amount() - 1));
        });
    }

    private static Point getBlockPlacement(@NotNull Point position, BlockFace face) {
        position = switch (face) {
            case BlockFace.TOP -> position.add(0, 1, 0);
            case BlockFace.BOTTOM -> position.add(0, -1, 0);
            case BlockFace.NORTH -> position.add(0, 0, -1);
            case BlockFace.SOUTH -> position.add(0, 0, 1);
            case BlockFace.WEST -> position.add(-1, 0, 0);
            case BlockFace.EAST -> position.add(1, 0, 0);
        };
        return position;
    }
}
