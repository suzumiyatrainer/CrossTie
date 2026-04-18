package net.suzumiya.crosstie.mixins.rtm;

import jp.ngt.ngtlib.renderer.NGTTessellator;
import jp.ngt.ngtlib.renderer.model.GroupObject;
import jp.ngt.ngtlib.renderer.model.PolygonModel;
import net.minecraft.client.renderer.GLAllocation;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(value = PolygonModel.class, remap = false)
public abstract class MixinPolygonModel {

    @Shadow public abstract void tessellateAll(jp.ngt.ngtlib.renderer.IRenderer tessellator, boolean smoothing);
    @Shadow public List<GroupObject> groupObjects;
    @Shadow protected int drawMode;

    @Unique
    private final Map<String, Integer> crosstie$displayLists = new HashMap<>();

    /**
     * @author CrossTie
     * @reason Overwrite to compile into DisplayList/VBO (via Angelica hook)
     */
    @Overwrite
    public void renderAll(boolean smoothing) {
        String key = "ALL_" + smoothing;
        Integer listId = crosstie$displayLists.get(key);
        
        if (listId == null) {
            listId = GLAllocation.generateDisplayLists(1);
            GL11.glNewList(listId, GL11.GL_COMPILE);
            
            if (smoothing) GL11.glShadeModel(GL11.GL_SMOOTH);
            NGTTessellator tessellator = NGTTessellator.instance;
            tessellator.startDrawing(this.drawMode);
            this.tessellateAll(tessellator, smoothing);
            tessellator.draw();
            if (smoothing) GL11.glShadeModel(GL11.GL_FLAT);
            
            GL11.glEndList();
            crosstie$displayLists.put(key, listId);
        }
        
        GL11.glCallList(listId);
    }

    /**
     * @author CrossTie
     * @reason Overwrite to compile into DisplayList/VBO (via Angelica hook)
     */
    @Overwrite
    public void renderOnly(boolean smoothing, String... groupNames) {
        // Sort to ensure cache hit irrespective of array order
        String[] sortedNames = groupNames.clone();
        Arrays.sort(sortedNames);
        String key = "ONLY_" + smoothing + "_" + String.join(",", sortedNames);
        Integer listId = crosstie$displayLists.get(key);
        
        if (listId == null) {
            listId = GLAllocation.generateDisplayLists(1);
            GL11.glNewList(listId, GL11.GL_COMPILE);
            
            if (smoothing) GL11.glShadeModel(GL11.GL_SMOOTH);
            this.groupObjects.forEach(groupObject -> 
                Arrays.stream(groupNames)
                      .filter(groupName -> groupName.equalsIgnoreCase(groupObject.name))
                      .forEach(groupName -> groupObject.render(smoothing))
            );
            if (smoothing) GL11.glShadeModel(GL11.GL_FLAT);

            GL11.glEndList();
            crosstie$displayLists.put(key, listId);
        }
        
        GL11.glCallList(listId);
    }

    /**
     * @author CrossTie
     * @reason Overwrite to compile into DisplayList/VBO (via Angelica hook)
     */
    @Overwrite
    public void renderPart(boolean smoothing, String partName) {
        String key = "PART_" + smoothing + "_" + partName;
        Integer listId = crosstie$displayLists.get(key);
        
        if (listId == null) {
            listId = GLAllocation.generateDisplayLists(1);
            GL11.glNewList(listId, GL11.GL_COMPILE);
            
            if (smoothing) GL11.glShadeModel(GL11.GL_SMOOTH);
            for (GroupObject groupObject : this.groupObjects) {
                if (partName.equalsIgnoreCase(groupObject.name)) {
                    groupObject.render(smoothing);
                    break;
                }
            }
            if (smoothing) GL11.glShadeModel(GL11.GL_FLAT);
            
            GL11.glEndList();
            crosstie$displayLists.put(key, listId);
        }
        
        GL11.glCallList(listId);
    }
}
