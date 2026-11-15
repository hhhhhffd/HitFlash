package dev.hitflash.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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

    private static final class Flash {
        final long time;
        final long duration;
        final int id;

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
            if (now - flash.time < flash.duration) return false;
            if (QA) LOGGER.info("[hitflash-qa] expire target={}", flash.id);
            return true;
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

    public static int outlineFor(LivingEntity entity) {
        if (!config.enabled() || FLASHES.isEmpty() || entity.isInvisible() || entity.isSpectator()) return 0;
        Flash flash = FLASHES.get(entity.getUUID());
        if (flash == null || flash.id != entity.getId() || System.nanoTime() - flash.time >= flash.duration) return 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !isClearlyVisible(mc, entity)) return 0;
        // The stock outline pass expects a solid silhouette mask and softens the
        // edges itself. It ignores per-vertex alpha; don't dim the selected RGB.
        return 0xFF000000 | config.rgb();
    }

    private static boolean isClearlyVisible(Minecraft mc, LivingEntity target) {
        Vec3 eye = mc.gameRenderer.getMainCamera().position();
        double targetX = target.getX(), targetZ = target.getZ();
        double bottomY = target.getBoundingBox().minY;
        double height = target.getBbHeight();
        double deltaX = targetX - eye.x, deltaZ = targetZ - eye.z;
        double horizontal = Math.hypot(deltaX, deltaZ);
        double sideX = 0, sideZ = 0;
        if (horizontal > 1.0e-4) {
            double widthFactor = target.getBbWidth() * 0.42 / horizontal;
            sideX = -deltaZ * widthFactor;
            sideZ = deltaX * widthFactor;
        }
        double shouldersY = bottomY + 0.66 * height;
        // The stock outline composites over the scene. Suppress our mask if any
        // of the center/shoulder samples are blocked rather than reveal a fully
        // hidden or plainly partially hidden target.
        return clearRay(mc, eye, targetX, bottomY + height * 0.88, targetZ)
            && clearRay(mc, eye, targetX, bottomY + height * 0.16, targetZ)
            && clearRay(mc, eye, targetX + sideX, shouldersY, targetZ + sideZ)
            && clearRay(mc, eye, targetX - sideX, shouldersY, targetZ - sideZ);
    }

    private static boolean clearRay(Minecraft mc, Vec3 eye, double x, double y, double z) {
        return mc.level.clip(new ClipContext(eye, new Vec3(x, y, z),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.getCameraEntity())).getType() == HitResult.Type.MISS;
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
