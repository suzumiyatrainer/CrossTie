package net.suzumiya.crosstie.mixins.worldedit;

import com.sk89q.jnbt.ByteArrayTag;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.NBTOutputStream;
import com.sk89q.jnbt.Tag;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.registry.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Mixin(targets = "com.sk89q.worldedit.extent.clipboard.io.SchematicWriter", remap = false)
public class MixinSchematicWriter {
    
    @Redirect(method = "write", at = @At(value = "INVOKE", target = "Lcom/sk89q/jnbt/NBTOutputStream;writeNamedTag(Ljava/lang/String;Lcom/sk89q/jnbt/Tag;)V"))
    private void onWriteNamedTag(NBTOutputStream outputStream, String name, Tag rootTag, Clipboard clipboard, WorldData data) throws IOException {
        if (rootTag instanceof CompoundTag && name.equals("Schematic")) {
            Map<String, Tag> schematic = new HashMap<>(((CompoundTag) rootTag).getValue());
            
            Region region = clipboard.getRegion();
            Vector min = region.getMinimumPoint();
            int width = region.getWidth();
            int length = region.getLength();
            int height = region.getHeight();
            
            // Check if there are block IDs > 255
            boolean hasExtendedIds = false;
            for (Object point : region) {
                BaseBlock block = clipboard.getBlock((Vector) point);
                if (block.getType() > 255) {
                    hasExtendedIds = true;
                    break;
                }
            }
            
            if (hasExtendedIds) {
                // Allocate Blocks16 byte array (2 bytes per block)
                byte[] blocks16 = new byte[width * height * length * 2];
                for (Object point : region) {
                    Vector vector = ((Vector) point).subtract(min);
                    int x = vector.getBlockX();
                    int y = vector.getBlockY();
                    int z = vector.getBlockZ();
                    int index = y * width * length + z * width + x;
                    
                    BaseBlock block = clipboard.getBlock((Vector) point);
                    int id = block.getType();
                    
                    // Little-endian format for short values
                    blocks16[index * 2] = (byte) (id & 0xFF);
                    blocks16[index * 2 + 1] = (byte) ((id >> 8) & 0xFF);
                }
                // Put it into the schematic NBT mapping
                schematic.put("Blocks16", new ByteArrayTag(blocks16));
            }
            
            rootTag = new CompoundTag(schematic);
        }
        
        outputStream.writeNamedTag(name, rootTag);
    }
}
