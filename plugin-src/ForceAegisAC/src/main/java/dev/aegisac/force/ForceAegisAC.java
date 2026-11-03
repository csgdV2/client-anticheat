package dev.aegisac.force;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ForceAegisAC extends JavaPlugin implements Listener, PluginMessageListener {

    public static final String CHANNEL = HandshakeProtocol.CHANNEL;

    private final Map<UUID, BukkitTask> pending = new ConcurrentHashMap<>();

    private long gracePeriodTicks;
    private Component kickMessage;
    private boolean logChecks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("Force Client Anticheat enabled — Client Anticheat mod is required to play.");
    }

    @Override
    public void onDisable() {
        pending.values().forEach(BukkitTask::cancel);
        pending.clear();
    }

    private void loadSettings() {
        reloadConfig();
        long seconds = Math.max(1, getConfig().getLong("grace-period-seconds", 5));
        gracePeriodTicks = seconds * 20L;
        logChecks = getConfig().getBoolean("log-checks", true);
        String raw = getConfig().getString("kick-message",
                "&cThis server requires Client Anticheat 2.2 or newer. Update your client mod.");
        kickMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission("forceaegisac.bypass")) {
            if (logChecks) {
                getLogger().info(player.getName() + " bypassed the Client Anticheat check (permission).");
            }
            return;
        }

        UUID id = player.getUniqueId();
        BukkitTask task = getServer().getScheduler().runTaskLater(this, () -> {
            pending.remove(id);
            if (player.isOnline()) {
                if (logChecks) {
                    getLogger().info(player.getName() + " kicked — no Client Anticheat handshake received.");
                }
                player.kick(kickMessage);
            }
        }, gracePeriodTicks);

        pending.put(id, task);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        BukkitTask task = pending.remove(event.getPlayer().getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!CHANNEL.equals(channel)) {
            return;
        }
        String version = HandshakeProtocol.readSupportedVersion(message);
        if (version == null) {
            // Invalid or outdated messages must not cancel the join deadline.
            return;
        }
        BukkitTask task = pending.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            if (logChecks) {
                getLogger().info(player.getName() + " verified — Client Anticheat v" + version + ".");
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            loadSettings();
            sender.sendMessage(Component.text("Force Client Anticheat config reloaded."));
            return true;
        }
        sender.sendMessage(Component.text("Usage: /" + label + " reload"));
        return true;
    }
}
