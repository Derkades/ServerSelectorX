package xyz.derkades.serverselectorx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.tr7zw.changeme.nbtapi.utils.MinecraftVersion;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import xyz.derkades.derkutils.bukkit.ItemBuilder;
import xyz.derkades.derkutils.bukkit.NbtItemBuilder;
import xyz.derkades.derkutils.bukkit.PlaceholderUtil;
import xyz.derkades.serverselectorx.configuration.ConfigSync;
import xyz.derkades.serverselectorx.configuration.ConfigurationManager;
import xyz.derkades.serverselectorx.placeholders.PapiExpansionRegistrar;
import xyz.derkades.serverselectorx.placeholders.Server;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class Main extends JavaPlugin {

	static {
		// Disable NBT API update checker
		MinecraftVersion.disableUpdateCheck();
	}

	// When set to true, debug information related to giving items on join is printed to the
	// console. This boolean is enabled using /ssx lagdebug
	static boolean ITEM_DEBUG = false;

	private static ConfigurationManager configurationManager;
	private static ConfigSync configSync;

	private static Main plugin;

	private static BukkitAudiences adventure;

	public static WebServer server;

	public static final Gson GSON = new GsonBuilder().registerTypeAdapter(Server.class, Server.SERIALIZER).create();

	private final HotbarItemManager hotbarItemManager = new HotbarItemManager(this);
	public HotbarItemManager getHotbarItemManager() { return this.hotbarItemManager; }

	private Heads heads;

	@SuppressWarnings("null")
	@NotNull
	public static Main getPlugin(){
		return plugin;
	}

	@Override
	public void onEnable() {
		plugin = this;

		MinecraftVersion.replaceLogger(this.getLogger());

		configurationManager = new ConfigurationManager();
		try {
			configurationManager.reload();
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}

		heads = new Heads(this);

		this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

		PluginCommand command = Objects.requireNonNull(this.getCommand("serverselectorx"),
				"Command missing from plugin.yml");
		command.setExecutor(new ServerSelectorXCommand());
		command.setTabCompleter(new ServerSelectorXCommandCompleter());

		// Register custom selector commands
		Commands.registerCustomCommands();

		adventure = BukkitAudiences.create(this);

		server = new WebServer();
		server.start();

		configSync = new ConfigSync();
		new Stats();
		new ItemMoveDropCancelListener();
		this.hotbarItemManager.enable();

		Bukkit.getPluginManager().registerEvents(new ItemClickListener(), this);
		Bukkit.getPluginManager().registerEvents(new BetaMessageJoinListener(), this);
		Bukkit.getPluginManager().registerEvents(new ActionsOnJoinListener(), this);

		if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
			PapiExpansionRegistrar.register();
		}
	}

	@Override
	public void onDisable() {
		if (adventure != null) {
			adventure.close();
			adventure = null;
		}

		if (server != null) {
			server.stop();
		}
	}

	public static ConfigurationManager getConfigurationManager() {
		return configurationManager;
	}

	static ConfigSync getConfigSync() {
		return configSync;
	}

	public static BukkitAudiences adventure() {
		return adventure;
	}

    public static void getItemBuilderFromMaterialString(final Player player, @Nullable String materialString, Consumer<NbtItemBuilder> builderConsumer) throws InvalidConfigurationException {
		if (materialString == null || materialString.isEmpty()) {
			return;
		}

		if (materialString.charAt(0) == '!') {
			materialString = PlaceholderUtil.parsePapiPlaceholders(player, materialString.substring(1));
		}

		if (materialString.startsWith("head:")) {
			String headValue = materialString.substring(5);
			if (headValue.equals("self") || headValue.equals("auto")) {
				if (getConfigurationManager().getMiscConfiguration().getBoolean("mojang-api-head-auto", false)) {
					headValue = "uuid:" + player.getUniqueId();
				} else {
					// Bypass head system, just return player's own head. No need to get a texture, because the server caches it for online players
					builderConsumer.accept(new NbtItemBuilder(Material.SKULL_ITEM).damage(3).skullOwner(player.getName()));
					return;
				}
			}

			CompletableFuture<@Nullable String> headTextureFuture = plugin.heads.getHead(headValue);

			Futures.whenCompleteOnMainThread(plugin, headTextureFuture, (headTexture, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
				}
				
				// Try native method first
				try {
					if (headTexture != null) {
						ItemStack itemStack = new ItemBuilder(Material.PLAYER_HEAD).skullTexture(headTexture).create();
						builderConsumer.accept(new NbtItemBuilder(itemStack));
					}
				} catch (UnsupportedOperationException e) {
					if (getConfigurationManager().getMiscConfiguration().getBoolean("head-api-debug", false)) {
						e.printStackTrace();
					}
				}

				if (headTexture != null) {
					builderConsumer.accept(new NbtItemBuilder(Material.SKULL_ITEM).damage(3).skullTexture(headTexture));
				} else {
					builderConsumer.accept(new NbtItemBuilder(Material.SKULL_ITEM).damage(3));
				}
			});
			return;
		}

		final String[] materialsToTry = materialString.split("\\|");
		Material material = null;
		for (String materialString2 : materialsToTry) {
			try {
				material = Material.valueOf(materialString2);
				break;
			} catch (final IllegalArgumentException ignored) {
			}
		}

		if (material == null) {
			player.sendMessage("Invalid item name '" + materialString + "'");
			player.sendMessage("https://github.com/ServerSelectorX/ServerSelectorX/wiki/Item-names");
			return;
		}

		if (material != Material.AIR) {
			builderConsumer.accept(new NbtItemBuilder(material));
		}
	}

	private static final LegacyComponentSerializer LEGACY_COMPONENT_SERIALIZER = LegacyComponentSerializer.builder()
			.character(ChatColor.COLOR_CHAR)
			.hexColors()
			.useUnusualXRepeatedCharacterHexFormat()
			.build();

	public static String miniMessageToLegacy(String miniMessage) {
		return LEGACY_COMPONENT_SERIALIZER.serialize(MiniMessage.miniMessage().deserialize(miniMessage));
	}

}
