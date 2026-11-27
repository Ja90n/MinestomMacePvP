package io.github.togar2.pvp.utils;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.scoreboard.Team;
import org.jetbrains.annotations.Nullable;

/**
 * Team helpers matching vanilla's {@code Entity#isAlliedTo} and {@code Player#canHarmPlayer}.
 */
public final class TeamUtil {
	private TeamUtil() {}
	
	public static @Nullable Team getTeam(Entity entity) {
		if (entity instanceof Player player) return player.getTeam();
		String member = entity.getUuid().toString();
		for (Team team : MinecraftServer.getTeamManager().getTeams()) {
			if (team.getMembers().contains(member)) return team;
		}
		return null;
	}
	
	/** An entity is allied to itself and to every entity in the same team. */
	public static boolean isAllied(@Nullable Entity a, @Nullable Entity b) {
		if (a == null || b == null) return false;
		if (a == b) return true;
		Team team = getTeam(a);
		return team != null && team == getTeam(b);
	}
	
	/** Players in the same team cannot hurt each other unless friendly fire is enabled. */
	public static boolean canHarmPlayer(Player attacker, Player target) {
		Team team = attacker.getTeam();
		if (team == null) return true;
		return !isAllied(attacker, target) || team.isAllowFriendlyFire();
	}
}
