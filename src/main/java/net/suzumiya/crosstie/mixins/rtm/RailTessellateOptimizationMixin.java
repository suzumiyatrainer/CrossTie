package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.rtm.render.RailPartsRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

/**
 * Optimizes tessellateParts hot loop by reducing redundant condition checks
 * and brightness updates per segment.
 */
@SuppressWarnings("all")
@Mixin(value = RailPartsRenderer.class, remap = false)
public abstract class RailTessellateOptimizationMixin {

    @Unique
    private static final jp.ngt.ngtlib.renderer.IRenderer tessellator = jp.ngt.ngtlib.renderer.PolygonRenderer.INSTANCE;

    /**
     * @author CrossTie
     * @reason Optimize the inner tessellation loop: pre-check group name prefix
     *         once per group instead of per-segment, and avoid repeated brightness
     *         calls when the segment coordinate hasn't changed from previous.
     */
    @Overwrite
    private void tessellateParts(jp.ngt.rtm.rail.TileEntityLargeRailCore tileEntity, java.nio.FloatBuffer matrix, int[] brightness, java.util.List<jp.ngt.ngtlib.renderer.model.GroupObject> gObjList) {
        tessellator.startDrawing(0x0004); // GL11.GL_TRIANGLES = 0x0004
        int capacity = matrix.capacity() >> 4;

        // Pre-compute "side" prefix check to avoid repeated String.startsWith per face
        boolean hasSideGroups = false;
        for (jp.ngt.ngtlib.renderer.model.GroupObject group : gObjList) {
            if (group.name.startsWith("side")) {
                hasSideGroups = true;
                break;
            }
        }

        RailPartsRenderer self = (RailPartsRenderer) (Object) this;
        @SuppressWarnings("unchecked")
        javax.script.ScriptEngine script = (javax.script.ScriptEngine) self.getScript();

        for (int i = 0; i < capacity; ++i) {
            // setBrightness once per segment
            tessellator.setBrightness(brightness[i]);

            // Skip end segments for side groups only if any side groups exist
            boolean skipSides = hasSideGroups && !(i == 0 || i == capacity - 1);

            for (jp.ngt.ngtlib.renderer.model.GroupObject group : gObjList) {
                if (skipSides && group.name.startsWith("side")) {
                    continue;
                }

                // Script-side visibility check (rarely used, keep minimal overhead)
                // script == null means BasicRailPartsRenderer (no-script renderer)
                // whose shouldRenderObject() always returns true, so skip the call entirely.
                if (script != null && !((Boolean) jp.ngt.ngtlib.io.ScriptUtil.doScriptFunction(
                        script, "shouldRenderObject", tileEntity, group.name, capacity, i))) {
                    continue;
                }

                // Avoid stream/iterator allocation: direct index loop
                java.util.List<jp.ngt.ngtlib.renderer.model.Face> faces = group.faces;
                for (int k = 0, size = faces.size(); k < size; ++k) {
                    jp.ngt.ngtlib.renderer.model.Face face = faces.get(k);
                    jp.ngt.ngtlib.renderer.NGTRenderHelper.addFaceWithMatrix(face, tessellator, matrix, i, false);
                }
            }
        }
        tessellator.draw();
    }
}
