package net.suzumiya.crosstie.mixins.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import net.suzumiya.crosstie.CrossTieConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

@Mixin(GuiMainMenu.class)
public class GuiMainMenuPanoramaMixin {

    @Unique
    private static int selectedSet = -1;

    @Unique
    private static final ResourceLocation[] customPanoramaPaths = new ResourceLocation[6];

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        if (!CrossTieConfig.enableCustomTitleScreen) return;

        if (selectedSet == -1) {
            Minecraft mc = Minecraft.getMinecraft();
            int maxSets = 0;
            
            // Check how many sets exist by looking for panorama_0.png in each set directory
            for (int i = 1; i <= 100; i++) {
                ResourceLocation testLoc = new ResourceLocation("crosstie", "textures/gui/title/background/set" + i + "/panorama_0.png");
                try {
                    mc.getResourceManager().getResource(testLoc);
                    maxSets = i;
                } catch (Exception e) {
                    break; // Stop at the first missing set
                }
            }
            
            if (maxSets > 0) {
                selectedSet = new Random().nextInt(maxSets) + 1;
                for (int i = 0; i < 6; i++) {
                    customPanoramaPaths[i] = new ResourceLocation(
                            "crosstie", "textures/gui/title/background/set" + selectedSet + "/panorama_" + i + ".png"
                    );
                }
            } else {
                selectedSet = 0; // 0 means no custom sets found, fallback to vanilla
            }
        }
    }

    @Redirect(
        method = "renderSkybox",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureManager;bindTexture(Lnet/minecraft/util/ResourceLocation;)V")
    )
    private void redirectBindTexture(TextureManager manager, ResourceLocation originalLocation) {
        if (CrossTieConfig.enableCustomTitleScreen && selectedSet > 0) {
            String path = originalLocation.getResourcePath();
            if (path.startsWith("textures/gui/title/background/panorama_")) {
                try {
                    int index = Integer.parseInt(path.substring(39, 40));
                    if (index >= 0 && index < 6) {
                        manager.bindTexture(customPanoramaPaths[index]);
                        return;
                    }
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        manager.bindTexture(originalLocation);
    }
}
