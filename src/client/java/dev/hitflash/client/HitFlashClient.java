package dev.hitflash.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class HitFlashClient implements ClientModInitializer {
    static final Logger LOGGER = LoggerFactory.getLogger("HitFlash");
    private static final boolean QA = Boolean.getBoolean("hitflash.qa");
    private static final Map<UUID, Flash> FLASHES = new HashMap<>();
    private static HitFlashConfig config = HitFlashConfig.DEFAULT;
    private static ClientLevel trackedLevel;
    private static final long HURT_TINT_NANOS = TimeUnit.MILLISECONDS.toNanos(600);

    private static final class Flash {
        final long time;
        final long duration;
        final int id;
        boolean expired;

        Flash(long time, long duration, int id) {
            this.time = time;
            this.duration = duration;
            this.id = id;
        }
    }

    @Override public void onInitializeClient() {
        config = HitFlashConfig.load();
        if (QA) LOGGER.info("[hitflash-qa] init color={} duration={} enabled={} playersOnly={}",
            config.hex(), config.durationMs(), config.enabled(), config.playersOnly());
        ClientTickEvents.END_CLIENT_TICK.register(HitFlashClient::tick);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
            literal("hitflash")
                .executes(ctx -> info(ctx.getSource()))
                .then(literal("color").then(argument("hex", StringArgumentType.greedyString())
                    .executes(ctx -> setColor(ctx.getSource(), StringArgumentType.getString(ctx, "hex")))))
                .then(literal("duration").then(argument("ms", IntegerArgumentType.integer(HitFlashConfig.MIN_MS, HitFlashConfig.MAX_MS))
                    .executes(ctx -> update(ctx.getSource(), new HitFlashConfig(config.enabled(), config.rgb(),
                        IntegerArgumentType.getInteger(ctx, "ms"), config.playersOnly())))))
                .then(literal("on").executes(ctx -> enable(ctx.getSource(), true)))
                .then(literal("off").executes(ctx -> enable(ctx.getSource(), false)))
                .then(literal("targets")
                    .then(literal("players").executes(ctx -> targets(ctx.getSource(), true)))
                    .then(literal("all").executes(ctx -> targets(ctx.getSource(), false))))
        ));
    }

    private static void syncWorld(ClientLevel level) {
        if (trackedLevel != level) {
            FLASHES.clear();
            trackedLevel = level;
        }
    }

    private static void tick(Minecraft client) {
        syncWorld(client.level);
        long now = System.nanoTime();
        FLASHES.values().removeIf(flash -> {
            long elapsed = now - flash.time;
            if (!flash.expired && elapsed >= flash.duration) {
                flash.expired = true;
                if (QA) LOGGER.info("[hitflash-qa] expire target={}", flash.id);
            }
            return elapsed >= Math.max(flash.duration, HURT_TINT_NANOS);
        });
    }

    public static void onDamage(ClientboundDamageEventPacket event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        syncWorld(level);
        if (!config.enabled() || level == null || mc.player == null
            || !(level.getEntity(event.entityId()) instanceof LivingEntity living)
            || living == mc.player || (config.playersOnly() && !(living instanceof Player))) return;

        int localId = mc.player.getId();
        var type = event.sourceType();
        boolean localMelee = event.sourceDirectId() == localId && event.sourceCauseId() == localId
            && (type.is(DamageTypes.PLAYER_ATTACK) || type.is(DamageTypes.MACE_SMASH) || type.is(DamageTypes.SPEAR));
        if (!localMelee) {
            // Keep the vanilla feedback for an unrelated hit on the same target.
            FLASHES.remove(living.getUUID());
            if (QA) LOGGER.info("[hitflash-qa] ignore target={} cause={} direct={} type={}",
                event.entityId(), event.sourceCauseId(), event.sourceDirectId(), event.sourceType().unwrapKey().orElse(null));
            return;
        }

        FLASHES.put(living.getUUID(), new Flash(System.nanoTime(),
            TimeUnit.MILLISECONDS.toNanos(config.durationMs()), event.entityId()));
        if (QA) LOGGER.info("[hitflash-qa] hit target={} type={} color={} duration={}",
            living.getId(), living.getType(), config.hex(), config.durationMs());
    }

    /** The lower word is an optional vanilla overlay override; upper word is custom outline ARGB. */
    public static long modelEffectFor(LivingEntity entity) {
        final long noEffect = 0xFFFFFFFFL;
        if (!config.enabled() || FLASHES.isEmpty() || entity.isInvisible()) return noEffect;
        Flash flash = FLASHES.get(entity.getUUID());
        if (flash == null || flash.id != entity.getId()) return noEffect;

        long elapsed = System.nanoTime() - flash.time;
        boolean avatar = entity instanceof Avatar;
        if (elapsed >= flash.duration) {
            // Don't switch back to vanilla red at the end of a short custom flash on non-player models.
            return !avatar && elapsed < HURT_TINT_NANOS ? (long) OverlayTexture.NO_OVERLAY : noEffect;
        }
        float remaining = 1.0f - (float) elapsed / (float) flash.duration;
        if (avatar) {
            // Keep the standard damage flash, but add an independent color outline to the skin only.
            int alpha = Math.clamp(Math.round(340.0f * remaining), 1, 255);
            int rgba = (alpha << 24) | config.rgb();
            return (Integer.toUnsignedLong(rgba) << 32) | noEffect;
        }
        Minecraft mc = Minecraft.getInstance();
        ((FlashPalette) mc.gameRenderer.overlayTexture()).hitflash$ensureColor(config.rgb());
        // Hold full intensity during the first 25%, then fade.
        int step = Math.clamp(Math.round(20.0f * remaining), 1, 15);
        return OverlayTexture.pack(step, 0);
    }

    private static int setColor(FabricClientCommandSource source, String text) {
        try {
            return update(source, new HitFlashConfig(config.enabled(), HitFlashConfig.parseHex(text), config.durationMs(), config.playersOnly()));
        } catch (IllegalArgumentException invalid) {
            source.sendError(Component.translatable("hitflash.bad_hex"));
            return 0;
        }
    }

    private static int enable(FabricClientCommandSource source, boolean enabled) {
        return update(source, new HitFlashConfig(enabled, config.rgb(), config.durationMs(), config.playersOnly()));
    }

    private static int targets(FabricClientCommandSource source, boolean playersOnly) {
        return update(source, new HitFlashConfig(config.enabled(), config.rgb(), config.durationMs(), playersOnly));
    }

    private static int update(FabricClientCommandSource source, HitFlashConfig settings) {
        try {
            settings.save();
        } catch (IOException error) {
            LOGGER.warn("Could not save client settings", error);
            source.sendError(Component.translatable("hitflash.save_failed"));
            return 0;
        }
        config = settings;
        FLASHES.clear();
        if (QA) LOGGER.info("[hitflash-qa] config color={} duration={} enabled={} playersOnly={}",
            config.hex(), config.durationMs(), config.enabled(), config.playersOnly());
        return info(source);
    }

    private static int info(FabricClientCommandSource source) {
        source.sendFeedback(Component.translatable("hitflash.status",
            Component.translatable(config.enabled() ? "hitflash.on" : "hitflash.off"),
            config.hex(), config.durationMs(),
            Component.translatable(config.playersOnly() ? "hitflash.players" : "hitflash.all")));
        return 1;
    }
}
