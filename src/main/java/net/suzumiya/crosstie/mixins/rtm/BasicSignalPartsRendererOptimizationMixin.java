package net.suzumiya.crosstie.mixins.rtm;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jp.ngt.rtm.electric.SignalLevel;
import jp.ngt.rtm.modelpack.cfg.SignalConfig;
import jp.ngt.rtm.modelpack.modelset.ModelSetSignal.LightParts;
import jp.ngt.rtm.modelpack.modelset.ModelSetSignalClient;
import jp.ngt.rtm.render.BasicSignalPartsRenderer;
import jp.ngt.rtm.render.ModelObject;
import jp.ngt.rtm.render.Parts;
import jp.ngt.rtm.render.SignalPartsRenderer;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 3-4: {@link BasicSignalPartsRenderer} の DisplayList 未使用イミディエイト描画と 毎フレーム
 * {@code Arrays.stream} / {@code LinkedList.contains} を解消する Mixin。
 *
 * <ul>
 * <li>{@code init()} 時に fixture / body / 各ライトパーツを
 * {@link Parts}（{@code GLHelper} DisplayList 管理） として事前登録し、Angelica
 * ({@code DisplayListVBOBuilder}) / OptiFine 双方で高速描画する。</li>
 * <li>消灯時の {@code glColor4f(0.0625F, ...)} は DisplayList
 * 外側に維持し、終了時に白へ復元する。</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
@Mixin(value = BasicSignalPartsRenderer.class, remap = false)
public abstract class BasicSignalPartsRendererOptimizationMixin extends SignalPartsRenderer {

    @Shadow
    @Final
    public LightParts[] lightParts;

    @Unique
    private Parts crosstie$fixtureParts;

    @Unique
    private Parts crosstie$bodyParts;

    @Unique
    private Map<String, Parts> crosstie$lightPartsMap;

    @Unique
    private final Set<String> crosstie$litPartsSet = new HashSet<String>();

    @SuppressWarnings("deprecation")
    @Inject(method = "init(Ljp/ngt/rtm/modelpack/modelset/ModelSetSignalClient;Ljp/ngt/rtm/render/ModelObject;)V", at = @At("HEAD"), remap = false)
    private void crosstie$registerSignalDisplayLists(ModelSetSignalClient par1, ModelObject par2, CallbackInfo ci) {
        SignalConfig cfg = par1.getConfig();
        if (cfg.modelPartsFixture != null && cfg.modelPartsFixture.objects != null
                && cfg.modelPartsFixture.objects.length > 0) {
            this.crosstie$fixtureParts = this.registerParts(new Parts(cfg.modelPartsFixture.objects));
        }
        if (cfg.modelPartsBody != null && cfg.modelPartsBody.objects != null && cfg.modelPartsBody.objects.length > 0) {
            this.crosstie$bodyParts = this.registerParts(new Parts(cfg.modelPartsBody.objects));
        }
        this.crosstie$lightPartsMap = new HashMap<String, Parts>();
        if (this.lightParts != null) {
            for (int i = 0; i < this.lightParts.length; i++) {
                LightParts lp = this.lightParts[i];
                if (lp == null || lp.parts == null) {
                    continue;
                }
                for (int k = 0; k < lp.parts.length; k++) {
                    String partName = lp.parts[k];
                    if (partName != null && !this.crosstie$lightPartsMap.containsKey(partName)) {
                        this.crosstie$lightPartsMap.put(partName, this.registerParts(new Parts(partName)));
                    }
                }
            }
        }
    }

    /**
     * @author CrossTie
     * @reason イミディエイト描画 (renderOnly/renderPart) を Parts(DisplayList) 描画へ置換し、
     *         Arrays.stream + LinkedList.contains を HashSet + for ループ化する。
     */
    @Overwrite
    public void render(TileEntity entity, int pass, float par3) {
        GL11.glPushMatrix();

        this.crosstie$litPartsSet.clear();
        SignalConfig cfg = this.modelSet.getConfig();

        if (pass == 0 && this.crosstie$fixtureParts != null) {
            this.crosstie$fixtureParts.render(this);
        }

        if (cfg.rotateBody) {
            float yaw = this.getRotation(entity) - this.getBlockDirection(entity);
            @SuppressWarnings("deprecation")
            float[] fa1 = cfg.modelPartsBody.pos;
            GL11.glTranslatef(fa1[0], fa1[1], fa1[2]);
            GL11.glRotatef(yaw, 0.0F, 1.0F, 0.0F);
            GL11.glTranslatef(-fa1[0], -fa1[1], -fa1[2]);
        }

        if (pass == 0) {
            if (this.crosstie$bodyParts != null) {
                this.crosstie$bodyParts.render(this);
            }
        } else if (pass == 2 && this.lightParts != null && this.crosstie$lightPartsMap != null) {
            int signal = this.getSignal(entity);
            if (signal > SignalLevel.HIGH_SPEED_PROCEED.level) {
                signal = SignalLevel.HIGH_SPEED_PROCEED.level;
            }

            boolean finish = false;
            // Pass A: 点灯ライト
            for (int i = 0; i < this.lightParts.length; i++) {
                LightParts lightPart = this.lightParts[i];
                if (lightPart == null || lightPart.parts == null) {
                    continue;
                }
                boolean render = false;
                if (!finish && signal > 0 && signal <= lightPart.signalLevel) {
                    finish = true;
                    render = true;
                    int itv = lightPart.interval;
                    if (itv > 0) {
                        render = ((this.getTick(entity) / itv) % 2) == 0;
                    }
                }
                if (render) {
                    String[] names = lightPart.parts;
                    for (int k = 0; k < names.length; k++) {
                        String s = names[k];
                        Parts p = this.crosstie$lightPartsMap.get(s);
                        if (p != null) {
                            p.render(this);
                        }
                        this.crosstie$litPartsSet.add(s);
                    }
                }
            }

            // Pass B: 消灯ライト（glColor4f は DisplayList 外側で設定）
            GL11.glColor4f(0.0625F, 0.0625F, 0.0625F, 1.0F);
            for (int i = 0; i < this.lightParts.length; i++) {
                LightParts lightPart = this.lightParts[i];
                if (lightPart == null || lightPart.parts == null) {
                    continue;
                }
                String[] names = lightPart.parts;
                for (int k = 0; k < names.length; k++) {
                    String s = names[k];
                    if (!this.crosstie$litPartsSet.contains(s)) {
                        Parts p = this.crosstie$lightPartsMap.get(s);
                        if (p != null) {
                            p.render(this);
                        }
                    }
                }
            }
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }

        GL11.glPopMatrix();
    }
}
