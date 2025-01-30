package xfilioo.xfilioochat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.util.*;

public class XFiliooChat extends JavaPlugin implements Listener, TabExecutor {

    private boolean chatEnabled = true;
    private Set<String> blockedWords;
    private final Map<UUID, String> lastMessages = new HashMap<>();
    private final Map<UUID, Long> lastMessageTimes = new HashMap<>();
    private long spamCooldownMs;
    private long defaultGeneralCooldownMs;
    private String blockedWordMessage;
    private String spamMessage;
    private String cooldownMessage;
    private String chatDisabledMessage;
    private String noPermissionMessage;
    private String usageMessage;
    private String chatEnabledByMessage;
    private String chatDisabledByMessage;
    private String chatClearedByMessage;
    private String chatConfigReloadedMessage;
    private Map<String, Long> rankCooldowns = new HashMap<>();

    // Wlaczenie
    @Override
    public void onEnable() {
        saveDefaultConfigWithComments();
        loadConfigValues();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("chat").setExecutor(this);
        getCommand("chat").setTabCompleter(this);
        Bukkit.getServer().getConsoleSender().sendMessage("§6----------------------------");
        Bukkit.getServer().getConsoleSender().sendMessage("§aWlaczono xFiliooChat w wersji 1.0!");
        Bukkit.getServer().getConsoleSender().sendMessage("§ePlugin dla UtopiaCraft.pl");
        Bukkit.getServer().getConsoleSender().sendMessage("§6----------------------------");
    }

    // Wylaczenie
    @Override
    public void onDisable() {
        Bukkit.getServer().getConsoleSender().sendMessage("§6----------------------------");
        Bukkit.getServer().getConsoleSender().sendMessage("§cWylaczono xFiliooChat!");
        Bukkit.getServer().getConsoleSender().sendMessage("§6----------------------------");
    }

    private void saveDefaultConfigWithComments() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        } else {
            YamlConfiguration existingConfig = YamlConfiguration.loadConfiguration(configFile);
            FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(getResource("config.yml")));
            for (String key : defaultConfig.getKeys(true)) {
                if (!existingConfig.contains(key)) {
                    existingConfig.set(key, defaultConfig.get(key));
                }
            }
            try {
                existingConfig.save(configFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void loadConfigValues() {
        FileConfiguration config = this.getConfig();
        blockedWords = Set.copyOf(config.getStringList("blocked-words"));
        spamCooldownMs = config.getLong("spam-cooldown-ms");
        defaultGeneralCooldownMs = config.getLong("general-cooldown-ms");

        // Load rank cooldowns
        rankCooldowns.clear();
        for (String key : config.getConfigurationSection("rank-cooldowns").getKeys(false)) {
            rankCooldowns.put(key, Long.valueOf(config.getLong("rank-cooldowns." + key)));
        }

        blockedWordMessage = ChatColor.translateAlternateColorCodes('&', config.getString("messages.blocked-word"));
        spamMessage = ChatColor.translateAlternateColorCodes('&', config.getString("messages.spam"));
        cooldownMessage = ChatColor.translateAlternateColorCodes('&', config.getString("messages.cooldown"));
        chatDisabledMessage = ChatColor.translateAlternateColorCodes('&', config.getString("messages.chat-disabled"));
        noPermissionMessage = ChatColor.translateAlternateColorCodes('&', config.getString("messages.no-permission"));
        usageMessage = ChatColor.translateAlternateColorCodes('&', config.getString("messages.usage"));
        chatEnabledByMessage = ChatColor.translateAlternateColorCodes('&', config.getString("messages.chat-enabled-by"));
        chatDisabledByMessage = ChatColor.translateAlternateColorCodes('&', config.getString("messages.chat-disabled-by"));
        chatClearedByMessage = ChatColor.translateAlternateColorCodes('&', config.getString("messages.chat-cleared-by"));
        chatConfigReloadedMessage = ChatColor.translateAlternateColorCodes('&', config.getString("messages.chat-config-reloaded"));
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!chatEnabled) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(chatDisabledMessage);
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String message = event.getMessage().toLowerCase();
        long currentTime = System.currentTimeMillis();

        // Check for blocked words
        for (String word : blockedWords) {
            if (message.contains(word.toLowerCase())) {
                event.setCancelled(true);
                player.sendMessage(blockedWordMessage);
                return;
            }
        }

        // Check for spam (same message)
        if (lastMessages.containsKey(playerId) && lastMessages.get(playerId).equals(message)) {
            long lastTime = lastMessageTimes.getOrDefault(playerId, Long.valueOf(0L));
            if (currentTime - lastTime < spamCooldownMs) {
                event.setCancelled(true);
                player.sendMessage(spamMessage);
                return;
            }
        }

        // Check for general cooldown (different messages)
        long cooldown = getPlayerCooldown(player);
        long lastTime = lastMessageTimes.getOrDefault(playerId, Long.valueOf(0L));
        if (currentTime - lastTime < cooldown) {
            event.setCancelled(true);
            player.sendMessage(cooldownMessage);
            return;
        }

        // Update last message and time
        lastMessages.put(playerId, message);
        lastMessageTimes.put(playerId, Long.valueOf(currentTime));
    }

    private long getPlayerCooldown(Player player) {
        long minCooldown = defaultGeneralCooldownMs;
        for (Map.Entry<String, Long> entry : rankCooldowns.entrySet()) {
            if (player.hasPermission("chatmanager." + entry.getKey())) {
                minCooldown = Math.min(minCooldown, entry.getValue());
            }
        }
        return minCooldown;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("chat")) {
            if (!sender.hasPermission("chatmanager.use")) {
                sender.sendMessage(noPermissionMessage);
                return true;
            }

            if (args.length == 0) {
                sender.sendMessage(usageMessage);
                return true;
            }

            if (args[0].equalsIgnoreCase("clear")) {
                clearChat(sender);
                return true;
            }

            if (args[0].equalsIgnoreCase("off")) {
                disableChat(sender);
                return true;
            }

            if (args[0].equalsIgnoreCase("on")) {
                enableChat(sender);
                return true;
            }

            if (args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                loadConfigValues();
                sender.sendMessage(chatConfigReloadedMessage);
                return true;
            }

            sender.sendMessage(usageMessage);
            return true;
        }
        return false;
    }

    private void clearChat(CommandSender sender) {
        for (int i = 0; i < 100; i++) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage("");
            }
        }
        Bukkit.broadcastMessage(chatClearedByMessage.replace("{player}", sender.getName()));
    }

    private void disableChat(CommandSender sender) {
        chatEnabled = false;
        Bukkit.broadcastMessage(chatDisabledByMessage.replace("{player}", sender.getName()));
    }

    private void enableChat(CommandSender sender) {
        chatEnabled = true;
        Bukkit.broadcastMessage(chatEnabledByMessage.replace("{player}", sender.getName()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("chat")) {
            if (args.length == 1) {
                return List.of("clear", "on", "off", "reload");
            }
        }
        return Collections.emptyList();
    }
}

