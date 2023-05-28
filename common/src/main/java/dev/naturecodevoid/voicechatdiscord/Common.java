package dev.naturecodevoid.voicechatdiscord;

import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import okhttp3.OkHttpClient;
import org.bspfsystems.yamlconfiguration.configuration.InvalidConfigurationException;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Common code between Paper and Fabric.
 */
public class Common {
    public static final String PLUGIN_ID = "voicechat-discord";
    public static final String RELOAD_CONFIG_PERMISSION = "voicechat-discord.reload-config";
    public static final String VOICECHAT_MIN_VERSION = "2.4.7";
    public static final List<String> configHeader = List.of(
            "To add a bot, just copy paste the following into bots:",
            "",
            "bots:",
            "- token: DISCORD_BOT_TOKEN_HERE",
            "  vc_id: VOICE_CHANNEL_ID_HERE",
            "",
            "Example for 2 bots:",
            "",
            "bots:",
            "- token: MyFirstBotsToken",
            "  vc_id: 1234567890123456789",
            "- token: MySecondBotsToken",
            "  vc_id: 9876543210987654321",
            "",
            "If you are only using 1 bot, just replace DISCORD_BOT_TOKEN_HERE with your bot's token and replace VOICE_CHANNEL_ID_HERE with the voice channel ID.",
            "",
            "For more information on getting everything setup: https://github.com/naturecodevoid/voicechat-discord#readme"
    );
    public static final ArrayList<SubCommands.SubCommand> SUB_COMMANDS = new ArrayList<>();
    public static ArrayList<DiscordBot> bots = new ArrayList<>();
    public static VoicechatServerApi api;
    public static Platform platform;
    public static YamlConfiguration config;

    public static void enable() {
        loadConfig();
        SubCommands.register();
    }

    @SuppressWarnings({"DataFlowIssue", "unchecked", "ResultOfMethodCallIgnored"})
    protected static void loadConfig() {
        File configFile = new File(platform.getConfigPath());

        if (!configFile.getParentFile().exists())
            configFile.getParentFile().mkdirs();

        config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException ignored) {
        } catch (InvalidConfigurationException e) {
            throw new RuntimeException(e);
        }

        LinkedHashMap<String, String> defaultBot = new LinkedHashMap<>();
        defaultBot.put("token", "DISCORD_BOT_TOKEN_HERE");
        defaultBot.put("vc_id", "VOICE_CHANNEL_ID_HERE");
        config.addDefault("bots", List.of(defaultBot));

        config.getOptions().setCopyDefaults(true);
        config.getOptions().setHeader(configHeader);
        try {
            config.save(configFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (!bots.isEmpty())
            bots = new ArrayList<>();

        for (LinkedHashMap<String, Object> bot : (List<LinkedHashMap<String, Object>>) config.getList("bots")) {
            try {
                bots.add(new DiscordBot((String) bot.get("token"), (Long) bot.get("vc_id")));
            } catch (ClassCastException e) {
                platform.error(
                        "Failed to load a bot. Please make sure that the vc_id property is a valid channel ID.");
            }
        }

        platform.info("Using " + bots.size() + " bot" + (bots.size() != 1 ? "s" : ""));
    }

    public static void disable() {
        platform.info("Shutting down " + bots.size() + " bot" + (bots.size() != 1 ? "s" : ""));

        stopBots();

        platform.info("Successfully shutdown " + bots.size() + " bot" + (bots.size() != 1 ? "s" : ""));
    }

    protected static void stopBots() {
        for (DiscordBot bot : bots) {
            bot.stop();
            if (bot.jda == null)
                continue;
            bot.jda.shutdownNow();
            OkHttpClient client = bot.jda.getHttpClient();
            client.connectionPool().evictAll();
            client.dispatcher().executorService().shutdownNow();
        }
    }

    public static void onPlayerLeave(UUID playerUuid) {
        DiscordBot bot = getBotForPlayer(playerUuid);
        if (bot != null) {
            platform.info("Stopping bot");
            bot.stop();
        }
    }

    public static void afterPlayerRespawn(ServerPlayer newPlayer) {
        DiscordBot bot = getBotForPlayer(newPlayer.getUuid());
        if (bot != null)
            bot.audioChannel.updateEntity(newPlayer);
    }

    public static DiscordBot getBotForPlayer(UUID playerUuid) {
        return getBotForPlayer(playerUuid, false);
    }

    public static DiscordBot getBotForPlayer(UUID playerUuid, boolean fallbackToAvailableBot) {
        for (DiscordBot bot : bots) {
            if (bot.player != null)
                if (bot.player.getUuid().compareTo(playerUuid) == 0)
                    return bot;
        }
        if (fallbackToAvailableBot)
            return getAvailableBot();
        return null;
    }

    public static DiscordBot getAvailableBot() {
        for (DiscordBot bot : bots) {
            if (bot.player == null)
                return bot;
        }
        return null;
    }

    private static int @Nullable [] splitVersion(String version) {
        try {
            return Arrays.stream(version.split("\\.")).mapToInt(Integer::parseInt).toArray();
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isSVCVersionSufficient(String version) {
        String[] splitVersion = version.split("-");
        int[] parsedVersion = splitVersion(splitVersion[splitVersion.length - 1]);
        int[] parsedMinVersion = Objects.requireNonNull(splitVersion(VOICECHAT_MIN_VERSION));
        if (parsedVersion != null) {
            for (int i = 0; i < parsedMinVersion.length; i++) {
                int part = parsedMinVersion[i];
                int testPart;
                if (parsedVersion.length > i) {
                    testPart = parsedVersion[i];
                } else {
                    testPart = 0;
                }
                if (testPart < part) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Returns true if the SVC version is not new enough
     */
    public static boolean checkSVCVersion(@Nullable String version) {
        if (version == null || !isSVCVersionSufficient(version)) {
            platform.error("Simple Voice Chat Discord Bridge requires Simple Voice Chat version " + VOICECHAT_MIN_VERSION + " or later");
            return false;
        }
        return true;
    }
}
