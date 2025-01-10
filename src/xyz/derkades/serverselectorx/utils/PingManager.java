package xyz.derkades.serverselectorx.utils;

import com.google.common.collect.Iterables;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.derkades.serverselectorx.Main;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class PingManager implements PluginMessageListener {
    private BukkitTask runningPingTask;
    private final Map<String, ServerPinger> pingers = new HashMap<>();

    public PingManager() {}

    public void start() {
        Bukkit.getServer().getMessenger().registerIncomingPluginChannel(Main.getPlugin(), "BungeeCord", this);

        this.loadServers();

        PingTask pingTask = new PingTask(this);
        this.runningPingTask = Bukkit.getScheduler().runTaskTimerAsynchronously(Main.getPlugin(), pingTask, 10, 10);
    }

    public void stop() {
        if (runningPingTask != null) {
            // Unfortunately not possible to wait until ping task is cancelled,
            // isCancelled() is missing in 1.8
            runningPingTask.cancel();
            try {
                Thread.sleep(200);
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void loadServers() {
        for (final FileConfiguration config : Main.getConfigurationManager().allFiles()) {
            // Ignore if config failed to load
            if (config == null || config.getConfigurationSection("menu") == null) {
                return;
            }

            for (final String key : config.getConfigurationSection("menu").getKeys(false)) {
                final ConfigurationSection section = config.getConfigurationSection("menu." + key);

                if (section.isBoolean("ping-server")) {
                    // Legacy server pinging with configured ip and port
                    if (!section.getBoolean("ping-server", false)) {
                        continue;
                    }

                    final String ip = section.getString("ip");
                    final int port = section.getInt("port");
                    final String fakeServerName = ip + ":" + port;
                    this.pingers.put(fakeServerName, ServerPinger.create(fakeServerName, ip, port));
                } else if (section.isString("ping-server")) {
                    // New server pinging with automatic ip and port
                    final String serverName = section.getString("ping-server");
                    this.pingers.put(serverName, null);
                }
            }
        }
    }

    @Nullable
    public ServerPinger getServerPinger(String serverName) {
        if (!pingers.containsKey(serverName)) {
            Main.getPlugin().getLogger().warning("Requested pinger for unknown server " + serverName);
            return null;
        }

        ServerPinger cachedPinger = pingers.get(serverName);
        if (cachedPinger != null) {
            return cachedPinger;
        }

        // Attempt to request server IP and port from BungeeCord

        Player player = Iterables.getFirst(Bukkit.getOnlinePlayers(), null);
        if (player == null) {
            return null;
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("ServerIP");
        out.writeUTF(serverName);
        player.sendPluginMessage(Main.getPlugin(), "BungeeCord", out.toByteArray());
        return null;
    }

    public Collection<String> getServerNames() {
        return this.pingers.keySet();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("BungeeCord")) {
            return;
        }
        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subchannel = in.readUTF();
        if (!subchannel.equals("ServerIP")) {
            return;
        }

        String serverName = in.readUTF();
        String ip = in.readUTF();
        int port = in.readUnsignedShort();
        ServerPinger serverPinger = ServerPinger.create(serverName, ip, port);
        this.pingers.put(serverName, serverPinger);
    }

}
