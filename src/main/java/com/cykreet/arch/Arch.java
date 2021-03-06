package com.cykreet.arch;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.cykreet.arch.listeners.PlayerChatListener;
import com.cykreet.arch.listeners.PlayerDeathListener;
import com.cykreet.arch.listeners.PlayerJoinListener;
import com.cykreet.arch.listeners.PlayerPreLoginListener;
import com.cykreet.arch.listeners.PlayerQuitListener;
import com.cykreet.arch.managers.CacheManager;
import com.cykreet.arch.managers.ConfigManager;
import com.cykreet.arch.managers.DiscordManager;
import com.cykreet.arch.managers.Manager;
import com.cykreet.arch.managers.PersistManager;
import com.cykreet.arch.util.ConfigPath;
import com.cykreet.arch.util.ConfigUtil;
import com.cykreet.arch.util.LoggerUtil;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import net.dv8tion.jda.api.entities.SelfUser;

public class Arch extends JavaPlugin {
	private static Map<Class<Manager>, Manager> managers = new HashMap<>();
	private DiscordManager discordManager;
	private ConfigManager configManager;
	private CacheManager cacheManager;
	private PersistManager database;
	private static Arch INSTANCE;

	public Arch() {
		INSTANCE = this;
	}

	@Override
	public void onEnable() {
		this.saveDefaultConfig();
		this.database = getManager(PersistManager.class);
		this.discordManager = getManager(DiscordManager.class);
		this.configManager = getManager(ConfigManager.class);
		this.configManager.setup(this.getConfig());
		
		this.registerListener(new PlayerPreLoginListener());
		this.registerListener(new PlayerDeathListener());
		this.registerListener(new PlayerChatListener());
		this.registerListener(new PlayerJoinListener());
		this.registerListener(new PlayerQuitListener());
		
		ConfigUtil.ensureAuthenticationEnabled();
		String configPlayer = ConfigUtil.getString(ConfigPath.DEFAULT_PLAYER);
		if (configPlayer != null) {
			UUID defaultPlayer = UUID.fromString(configPlayer);
			this.configManager.setPapiPlayer(Bukkit.getOfflinePlayer(defaultPlayer));
		}
		
		String botToken = ConfigUtil.getString(ConfigPath.BOT_TOKEN);
		// handled by config util
		if (botToken == null) return;
		
		String activity = ConfigUtil.getString(ConfigPath.BOT_STATUS);
		this.discordManager.login(botToken, activity);
		
		// disable if the bot hasn't been invited to the relevant guild
		// by checking if we have access to the provided guild
		if (this.discordManager.getGuild() == null) {
			SelfUser selfUser = this.discordManager.getSelfUser();
			String inviteLink = String.format("https://discord.com/oauth2/authorize?client_id=%s&scope=bot&permissions=805325824", selfUser.getId());
			String message = String.format("Discord bot is not in the relevant server, please invite the bot through the following link: %s", inviteLink);
			LoggerUtil.errorAndExit(message);
			return;
		}
		
		this.database.connect(this.getDataFolder(), "linked-users.db");
		int codeExpiry = ConfigUtil.getInt(ConfigPath.AUTH_CODE_EXPIRE);
		this.cacheManager = getManager(CacheManager.class);
		this.cacheManager.createCache(codeExpiry);
	}

	@Override
	public void onDisable() {
		this.database.close();
		this.discordManager.logout();
		this.cacheManager.getCache().invalidateAll();
		Bukkit.getScheduler().cancelTasks(this);
	}

	public void reloadPlugin() {
		// to be used with future reload command
		// todo: doesn't reload listeners and database and shit
		this.reloadConfig();
		this.configManager.reload();
	}

	private void registerListener(Listener listener) {
		this.getServer().getPluginManager().registerEvents(listener, this);
	}
	
	@SuppressWarnings("unchecked")
	public static <Type extends Manager> Type getManager(Class<Type> managerClass) {
		if (managers.containsKey(managerClass)) return (Type) managers.get(managerClass);
		try {
			Constructor<?> constructor = managerClass.getConstructors()[0];
			Type manager = (Type) constructor.newInstance();
			managers.putIfAbsent((Class<Manager>) managerClass, manager);
			return manager;
		} catch (Exception exception) {
			String message = String.format("Failed to instantiate manager \"%s\".", managerClass.getSimpleName());
			LoggerUtil.errorAndExit(message);
			return null;
		}
	}

	public static Arch getInstance() {
		return Arch.INSTANCE;
	}
}
