package net.suzumiya.crosstie.mixins.client;

import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.suzumiya.crosstie.client.TrainCameraController;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={EntityRenderer.class})
public abstract class CameraTiltMixin {
    @Shadow(remap = false, aliases = {"field_78491_C", "thirdPersonDistanceTemp"})
    private float thirdPersonDistanceTemp;
    @Shadow(remap = false, aliases = {"field_78490_B", "thirdPersonDistance"})
    private float thirdPersonDistance;

    @Inject(method={"orientCamera", "func_78467_g"}, at={@At(value="TAIL")}, require=1, remap=false)
    private void crosstie$applyCameraTilt(float partialTicks, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null || mc.thePlayer == null) {
            return;
        }
        EntityClientPlayerMP player = mc.thePlayer;
        EntityVehicleBase<?> vehicle = TrainCameraController.getMountedOrStandingVehicle((EntityPlayer)player);
        if (vehicle == null || vehicle.isDead) {
            return;
        }
        float pitch = vehicle.prevRotationPitch + (vehicle.rotationPitch - vehicle.prevRotationPitch) * partialTicks;
        float roll = vehicle.prevRotationRoll + (vehicle.rotationRoll - vehicle.prevRotationRoll) * partialTicks;
        roll *= TrainCameraController.getRollMultiplier((EntityPlayer)player);
        if (Math.abs(pitch) < 0.001f && Math.abs(roll) < 0.001f) {
            return;
        }
        float vehicleYaw = vehicle.prevRotationYaw + MathHelper.wrapAngleTo180_float((float)(vehicle.rotationYaw - vehicle.prevRotationYaw)) * partialTicks;
        float playerYaw = player.prevRotationYaw + MathHelper.wrapAngleTo180_float((float)(player.rotationYaw - player.prevRotationYaw)) * partialTicks;
        float relYaw = MathHelper.wrapAngleTo180_float((float)(playerYaw - vehicleYaw));
        double rad = Math.toRadians(relYaw);
        double cosR = Math.cos(rad);
        double sinR = Math.sin(rad);
        float camPitch = (float)((double)pitch * cosR + (double)roll * sinR);
        float camRoll = (float)((double)roll * cosR - (double)pitch * sinR);
        if (Math.abs(camPitch) < 0.001f && Math.abs(camRoll) < 0.001f) {
            return;
        }
        boolean isThirdPerson = mc.gameSettings.thirdPersonView > 0;
        float distance = 0.0f;
        float pivotHeight = TrainCameraController.getPivotHeight((EntityPlayer)player);

        if (isThirdPerson) {
            distance = this.thirdPersonDistanceTemp + (this.thirdPersonDistance - this.thirdPersonDistanceTemp) * partialTicks;
            GL11.glTranslatef(0.0f, 0.0f, distance);
        } else {
            // 一人称視点: 目の位置から足元/腰(ピボット)へ原点を下げる
            GL11.glTranslatef(0.0f, -pivotHeight, 0.0f);
        }

        if (Math.abs(camPitch) > 0.001f) {
            GL11.glRotatef(-camPitch, 1.0f, 0.0f, 0.0f);
        }
        if (Math.abs(camRoll) > 0.001f) {
            GL11.glRotatef(camRoll, 0.0f, 0.0f, 1.0f);
        }

        if (isThirdPerson) {
            GL11.glTranslatef(0.0f, 0.0f, -distance);
        } else {
            // 一人称視点: ピボットから目の位置へ原点を戻す
            GL11.glTranslatef(0.0f, pivotHeight, 0.0f);
        }
    }
}
