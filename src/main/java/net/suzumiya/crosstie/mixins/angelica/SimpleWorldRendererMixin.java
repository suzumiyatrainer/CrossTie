package net.suzumiya.crosstie.mixins.angelica;

import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "org.embeddedt.embeddium.impl.render.terrain.SimpleWorldRenderer", remap = false)
public abstract class SimpleWorldRendererMixin<W, S, L, E, C> {

    @Shadow
    private List<E> globalBlockEntities;

    /**
     * @author CrossTie
     * @reason Replace high-overhead Stream API with an allocation-free for-loop.
     */
    @Overwrite
    public void renderGlobalBlockEntities(C context) {
        if (this.globalBlockEntities == null) {
            return;
        }
        int size = this.globalBlockEntities.size();
        for (int i = 0; i < size; i++) {
            E entity = this.globalBlockEntities.get(i);
            if (entity != null) {
                this.renderTE(entity, context);
            }
        }
    }

    @Shadow
    protected abstract void renderTE(E entity, C context);
}
