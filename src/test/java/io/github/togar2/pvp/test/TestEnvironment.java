package io.github.togar2.pvp.test;

import io.github.togar2.pvp.MinestomPvP;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;

public class TestEnvironment {
	private static volatile boolean initialized = false;
	
	public static synchronized void init() {
		if (!initialized) {
			MinecraftServer.init();
			MinestomPvP.init();
			initialized = true;
		}
	}
	
	public static Player createPlayer(InstanceContainer instance, Pos pos) {
		init();
		PlayerConnection connection = new PlayerConnection() {
			@Override
			public void sendPacket(SendablePacket packet) {}
			
			@Override
			public SocketAddress getRemoteAddress() {
				return new InetSocketAddress("127.0.0.1", 12345);
			}
		};
		Player player = new Player(connection, new GameProfile(UUID.randomUUID(), "P_" + UUID.randomUUID().toString().substring(0, 8)));
		if (instance != null) {
			player.setInstance(instance, pos).join();
		}
		return player;
	}
}
