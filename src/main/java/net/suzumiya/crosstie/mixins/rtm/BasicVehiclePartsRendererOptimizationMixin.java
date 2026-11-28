package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.modelpack.cfg.VehicleBaseConfig;
import jp.ngt.rtm.modelpack.cfg.VehicleBaseConfig.VehicleParts;
import jp.ngt.rtm.render.BasicVehiclePartsRenderer;
import jp.ngt.rtm.render.Parts;
import jp.ngt.rtm.render.PartsWithChildren;
import jp.ngt.rtm.render.VehiclePartsRenderer;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Phase 4-4: {@link BasicVehiclePartsRenderer} の毎フレーム {@code IntStream} / {@code Arrays.stream} を廃止し、
 * {@code sigmoid(move)} をループ外へ巻き上げる。
 */
@Mixin(value = BasicVehiclePartsRenderer.class, remap = false)
public abstract class BasicVehiclePartsRendererOptimizationMixin extends VehiclePartsRenderer {

    @Shadow
    private Parts body;

    @Shadow
    private PartsWithChildren[] doorLeft;

    @Shadow
    private PartsWithChildren[] doorRight;

    @Shadow
    private PartsWithChildren[] pantographFront;

    @Shadow
    private PartsWithChildren[] pantographBack;

    public BasicVehiclePartsRendererOptimizationMixin(String... par1) {
        super(par1);
    }

    /**
     * @author CrossTie
     * @reason IntStream 廃止と sigmoid 計算のループ外巻き上げ
     */
    @Overwrite(remap = false)
    public void render(Entity entity, int pass, float par3) {
        this.body.render(this);
        VehicleBaseConfig cfg = this.modelSet.getConfig();

        if (cfg.door_left != null) {
            float move = this.sigmoid(this.getDoorMovementL(entity));
            VehicleParts[] parts = cfg.door_left;
            for (int j = 0; j < parts.length; ++j) {
                this.renderParts(move, parts[j], this.doorLeft[j]);
            }
        }

        if (cfg.door_right != null) {
            float move = this.sigmoid(this.getDoorMovementR(entity));
            VehicleParts[] parts = cfg.door_right;
            for (int j = 0; j < parts.length; ++j) {
                this.renderParts(move, parts[j], this.doorRight[j]);
            }
        }

        if (cfg.pantograph_front != null) {
            float move = this.sigmoid(this.getPantographMovementFront(entity));
            VehicleParts[] parts = cfg.pantograph_front;
            for (int j = 0; j < parts.length; ++j) {
                this.renderParts(move, parts[j], this.pantographFront[j]);
            }
        }

        if (cfg.pantograph_back != null) {
            float move = this.sigmoid(this.getPantographMovementBack(entity));
            VehicleParts[] parts = cfg.pantograph_back;
            for (int j = 0; j < parts.length; ++j) {
                this.renderParts(move, parts[j], this.pantographBack[j]);
            }
        }
    }

    /**
     * @author CrossTie
     * @reason Arrays.stream / IntStream の毎フレーム生成を排除
     */
    @Overwrite(remap = false)
    private void renderParts(float move, VehicleParts parts, PartsWithChildren parts2) {
        GL11.glPushMatrix();
        GL11.glTranslatef(parts.pos[0], parts.pos[1], parts.pos[2]);
        float[][] transforms = parts.transform;
        for (int i = 0; i < transforms.length; ++i) {
            float[] fa = transforms[i];
            if (fa.length == 3) {
                GL11.glTranslatef(fa[0] * move, fa[1] * move, fa[2] * move);
            } else if (fa.length == 4) {
                GL11.glRotatef(fa[0] * move, fa[1], fa[2], fa[3]);
            }
        }
        GL11.glTranslatef(-parts.pos[0], -parts.pos[1], -parts.pos[2]);
        parts2.render(this);

        if (parts.childParts != null) {
            VehicleParts[] children = parts.childParts;
            for (int i = 0; i < children.length; ++i) {
                this.renderParts(move, children[i], (PartsWithChildren) parts2.childParts.get(i));
            }
        }

        GL11.glPopMatrix();
    }
}
