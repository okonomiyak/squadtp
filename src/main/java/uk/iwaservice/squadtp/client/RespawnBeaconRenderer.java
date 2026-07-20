package uk.iwaservice.squadtp.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import uk.iwaservice.squadtp.ModRegistry;
import uk.iwaservice.squadtp.entity.RespawnBeaconEntity;

/**
 * No 3D model - just a slowly spinning, bobbing render of the beacon item
 * itself (the same technique vanilla uses for dropped items), plus the
 * standard nametag from {@link EntityRenderer}.
 */
public class RespawnBeaconRenderer extends EntityRenderer<RespawnBeaconEntity> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("minecraft", "textures/misc/particles.png");

    public RespawnBeaconRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.4f;
    }

    @Override
    public ResourceLocation getTextureLocation(RespawnBeaconEntity entity) {
        return TEXTURE; // never sampled: render() below draws an item stack instead of a textured quad
    }

    @Override
    public void render(RespawnBeaconEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        double bob = Math.sin((entity.tickCount + partialTicks) / 10.0) * 0.1;
        poseStack.translate(0.0, 0.9 + bob, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTicks) * 2.0f));
        poseStack.scale(1.5f, 1.5f, 1.5f);

        ItemStack stack = new ItemStack(ModRegistry.RESPAWN_BEACON_ITEM.get());
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.GROUND,
                packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), entity.getId());
        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }
}
