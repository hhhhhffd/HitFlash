package dev.hitflash.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

record HitFlashConfig(boolean enabled, int rgb, int durationMs, boolean playersOnly) {
    static final int MIN_MS = 50;
    static final int MAX_MS = 3000;
    static final HitFlashConfig DEFAULT = new HitFlashConfig(true, 0xFFFFFF, 450, false);
    static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("hitflash.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static HitFlashConfig load() {
        if (!Files.isRegularFile(FILE)) return DEFAULT;
        try (var reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
            return new HitFlashConfig(
                object.has("enabled") ? object.get("enabled").getAsBoolean() : DEFAULT.enabled,
                object.has("color") ? parseHex(object.get("color").getAsString()) : DEFAULT.rgb,
                object.has("durationMs") ? clampMs(object.get("durationMs").getAsInt()) : DEFAULT.durationMs,
                object.has("playersOnly") ? object.get("playersOnly").getAsBoolean() : DEFAULT.playersOnly
            );
        } catch (IOException | RuntimeException error) {
            HitFlashClient.LOGGER.warn("Could not read hitflash.json; using defaults", error);
            return DEFAULT;
        }
    }

    void save() throws IOException {
        Files.createDirectories(FILE.getParent());
        Path temp = Files.createTempFile(FILE.getParent(), ".hitflash-", ".tmp");
        try {
            JsonObject config = new JsonObject();
            config.addProperty("enabled", enabled);
            config.addProperty("color", hex());
            config.addProperty("durationMs", durationMs);
            config.addProperty("playersOnly", playersOnly);
            Files.writeString(temp, GSON.toJson(config) + "\n", StandardCharsets.UTF_8);
            try {
                Files.move(temp, FILE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    String hex() { return String.format(Locale.ROOT, "#%06X", rgb); }
    static int clampMs(int value) { return Math.clamp(value, MIN_MS, MAX_MS); }

    static int parseHex(String input) {
        String value = input.strip();
        if (value.startsWith("#")) value = value.substring(1);
        if (!value.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Expected RRGGBB");
        return Integer.parseInt(value, 16);
    }
}
