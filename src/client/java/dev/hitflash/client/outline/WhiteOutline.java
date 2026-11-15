package dev.hitflash.client.outline;

import com.mojang.blaze3d.pipeline.BlendFunction;
import dev.hitflash.client.mixin.OutlineRenderTypeInvoker;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;

/** A depth-tested inverted hull around the six primary player cuboids. */
public final class WhiteOutline {
    private static final float GROWTH_PIXELS = 0.28f;

    private WhiteOutline() {}

    /**
     * Call at the vanilla base-model submission, with the same pose stack.
     * The caller decides which hit states activate this; this method never shows hidden players.
     * @param argb a packed AARRGGBB stroke color (e.g. 0xffffffff)
     */
    public static void submit(AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector, int argb) {
        if ((argb >>> 24) == 0 || state.skin == null || state.isSpectator || state.isInvisible || state.isInvisibleToPlayer) {
            return;
        }
        Hull model = state.skin.model() == PlayerModelType.SLIM ? Resources.SLIM : Resources.WIDE;
        collector.order(1).submitModel(model, state, pose, Resources.TYPE, state.lightCoords,
            OverlayTexture.NO_OVERLAY, argb, null, 0, null);
    }

    private static final class Hull extends PlayerModel {
        private Hull(boolean slim) {
            super(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(GROWTH_PIXELS), slim), 64, 64)
                .bakeRoot(), slim);
        }

        @Override
        public void setupAnim(AvatarRenderState state) {
            super.setupAnim(state);
            hat.visible = false;
            jacket.visible = false;
            leftSleeve.visible = false;
            rightSleeve.visible = false;
            leftPants.visible = false;
            rightPants.visible = false;
        }
    }

    private static final class Resources {
        private static final Hull WIDE = new Hull(false);
        private static final Hull SLIM = new Hull(true);
        private static final Identifier SHADER = Identifier.fromNamespaceAndPath("hitflash", "core/white_outline");
        private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("hitflash", "pipeline/white_outline"))
            .withVertexShader(SHADER)
            .withFragmentShader(SHADER)
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)
            .withCull(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .build();

        private static final RenderType TYPE = OutlineRenderTypeInvoker.hitflash$create("hitflash_white_outline",
            RenderSetup.builder(PIPELINE)
                .setOutputTarget(OutputTarget.MAIN_TARGET)
                .setOutline(RenderSetup.OutlineProperty.NONE)
                .createRenderSetup());
    }
}
