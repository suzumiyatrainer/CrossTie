package net.suzumiya.crosstie.mixins.rtm;

import net.minecraft.client.renderer.GLAllocation;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Mixin(targets = "jp.ngt.ngtlib.renderer.model.PolygonModel", remap = false)
public abstract class MixinPolygonModel {

    @Unique
    private final Map<String, Integer> crosstie$displayLists = new HashMap<>();
    
    @Unique
    private final ThreadLocal<Boolean> crosstie$isCompiling = ThreadLocal.withInitial(() -> false);

    @Inject(method = "renderAll(Z)V", at = @At("HEAD"), cancellable = true)
    private void renderAll_Head(boolean smoothing, CallbackInfo ci) {
        String key = "ALL_" + smoothing;
        Integer listId = crosstie$displayLists.get(key);
        
        if (listId != null) {
            GL11.glCallList(listId);
            ci.cancel(); // We found the VBO cache, do not execute the rest of the method
        } else {
            listId = GLAllocation.generateDisplayLists(1);
            crosstie$displayLists.put(key, listId);
            GL11.glNewList(listId, GL11.GL_COMPILE_AND_EXECUTE); // Record and execute at the same time
            crosstie$isCompiling.set(true);
        }
    }

    @Inject(method = "renderAll(Z)V", at = @At("RETURN"))
    private void renderAll_Return(boolean smoothing, CallbackInfo ci) {
        if (crosstie$isCompiling.get()) {
            GL11.glEndList(); // End the compilation recording
            crosstie$isCompiling.set(false);
        }
    }

    @Inject(method = "renderOnly(Z[Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void renderOnly_Head(boolean smoothing, String[] groupNames, CallbackInfo ci) {
        if (groupNames == null) return;
        String[] sortedNames = groupNames.clone();
        Arrays.sort(sortedNames);
        String key = "ONLY_" + smoothing + "_" + String.join(",", sortedNames);
        Integer listId = crosstie$displayLists.get(key);
        
        if (listId != null) {
            GL11.glCallList(listId);
            ci.cancel();
        } else {
            listId = GLAllocation.generateDisplayLists(1);
            crosstie$displayLists.put(key, listId);
            GL11.glNewList(listId, GL11.GL_COMPILE_AND_EXECUTE);
            crosstie$isCompiling.set(true);
        }
    }

    @Inject(method = "renderOnly(Z[Ljava/lang/String;)V", at = @At("RETURN"))
    private void renderOnly_Return(boolean smoothing, String[] groupNames, CallbackInfo ci) {
        if (crosstie$isCompiling.get()) {
            GL11.glEndList();
            crosstie$isCompiling.set(false);
        }
    }

    @Inject(method = "renderPart(ZLjava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void renderPart_Head(boolean smoothing, String partName, CallbackInfo ci) {
        if (partName == null) return;
        String key = "PART_" + smoothing + "_" + partName;
        Integer listId = crosstie$displayLists.get(key);
        
        if (listId != null) {
            GL11.glCallList(listId);
            ci.cancel();
        } else {
            listId = GLAllocation.generateDisplayLists(1);
            crosstie$displayLists.put(key, listId);
            GL11.glNewList(listId, GL11.GL_COMPILE_AND_EXECUTE);
            crosstie$isCompiling.set(true);
        }
    }

    @Inject(method = "renderPart(ZLjava/lang/String;)V", at = @At("RETURN"))
    private void renderPart_Return(boolean smoothing, String partName, CallbackInfo ci) {
        if (crosstie$isCompiling.get()) {
            GL11.glEndList();
            crosstie$isCompiling.set(false);
        }
    }
}
