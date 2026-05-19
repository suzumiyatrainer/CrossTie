package net.suzumiya.crosstie.mixins.angelica;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.gtnewhorizons.angelica.glsm.GLStateManager", remap = false)
public abstract class AngelicaRenderGlobalDisplayListCrashMixin {

    /**
     * Opt-IN flag (default: disabled).
     *
     * <p>This workaround bypasses Angelica's GLStateManager for display lists created in
     * {@code RenderGlobal.<init>}. With Angelica 2.1.21+ the ImmediateModeRecorder switched to
     * attrib-based vertex reading; calling GL11 directly while Angelica's attrib state is live
     * corrupts the buffer position and causes an IndexOutOfBoundsException on the next
     * non-list draw call.</p>
     *
     * <p>Enable only if using an older Angelica that crashes without this workaround:
     * {@code -Dcrosstie.enableNativeRenderGlobalDisplayLists=true}</p>
     */
    @Unique
    private static final boolean crosstie$nativeRenderGlobalListsEnabled =
            Boolean.getBoolean("crosstie.enableNativeRenderGlobalDisplayLists");

    @Unique
    private static final Set<Integer> crosstie$nativeRenderGlobalLists =
            Collections.synchronizedSet(new HashSet<Integer>());

    @Unique
    private static final ThreadLocal<Boolean> crosstie$compilingNativeRenderGlobalList =
            new ThreadLocal<Boolean>();

    @Inject(method = "glNewList", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void crosstie$useNativeRenderGlobalList(int list, int mode, CallbackInfo ci) {
        if (!crosstie$nativeRenderGlobalListsEnabled || list <= 0 || !crosstie$isRenderGlobalConstructor()) {
            return;
        }
        crosstie$nativeRenderGlobalLists.add(Integer.valueOf(list));
        crosstie$compilingNativeRenderGlobalList.set(Boolean.TRUE);
        GL11.glNewList(list, mode);
        ci.cancel();
    }

    @Inject(method = "glEndList", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void crosstie$endNativeRenderGlobalList(CallbackInfo ci) {
        if (Boolean.TRUE.equals(crosstie$compilingNativeRenderGlobalList.get())) {
            crosstie$compilingNativeRenderGlobalList.remove();
            GL11.glEndList();
            ci.cancel();
        }
    }

    @Inject(method = "glCallList", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void crosstie$callNativeRenderGlobalList(int list, CallbackInfo ci) {
        if (crosstie$nativeRenderGlobalLists.contains(Integer.valueOf(list))) {
            GL11.glCallList(list);
            ci.cancel();
        }
    }

    @Inject(method = "glDrawArrays", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void crosstie$drawArraysInNativeRenderGlobalList(int mode, int first, int count, CallbackInfo ci) {
        if (Boolean.TRUE.equals(crosstie$compilingNativeRenderGlobalList.get())) {
            GL11.glDrawArrays(mode, first, count);
            ci.cancel();
        }
    }

    @Inject(method = "glDeleteLists", at = @At("HEAD"), require = 0, remap = false)
    private static void crosstie$deleteNativeRenderGlobalLists(int list, int range, CallbackInfo ci) {
        boolean removeNativeRange = false;
        for (int i = list; i < list + range; i++) {
            removeNativeRange |= crosstie$nativeRenderGlobalLists.remove(Integer.valueOf(i));
        }
        if (removeNativeRange) {
            GL11.glDeleteLists(list, range);
        }
    }

    @Unique
    private static boolean crosstie$isRenderGlobalConstructor() {
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : trace) {
            if ("net.minecraft.client.renderer.RenderGlobal".equals(element.getClassName())
                    && "<init>".equals(element.getMethodName())) {
                return true;
            }
        }
        return false;
    }
}
