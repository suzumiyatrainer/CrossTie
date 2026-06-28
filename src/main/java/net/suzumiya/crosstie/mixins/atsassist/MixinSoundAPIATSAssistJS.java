package net.suzumiya.crosstie.mixins.atsassist;

import net.suzumiya.crosstie.api.sound.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.script.ScriptEngine;

@Mixin(targets = "jp.kaiz.atsassistmod.ifttt.IFTTTContainer$That$ATSAssist$JavaScript", remap = false)
public abstract class MixinSoundAPIATSAssistJS {

    @Shadow private transient ScriptEngine scriptEngine;

    @Inject(
        method = "doThat",
        at = @At(
            value = "INVOKE",
            target = "Ljp/ngt/ngtlib/io/ScriptUtil;doScript(Ljava/lang/String;)Ljavax/script/ScriptEngine;",
            shift = At.Shift.AFTER
        )
    )
    private void crosstie$bindSoundAPI(CallbackInfo ci) {
        if (this.scriptEngine != null) {
            this.scriptEngine.put("SoundAPI", SoundManager.getInstance());
        }
    }
}
