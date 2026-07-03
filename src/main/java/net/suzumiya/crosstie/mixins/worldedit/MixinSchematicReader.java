package net.suzumiya.crosstie.mixins.worldedit;

import com.sk89q.jnbt.ByteArrayTag;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.NBTInputStream;
import com.sk89q.jnbt.NamedTag;
import com.sk89q.jnbt.Tag;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.registry.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.util.Map;

@Mixin(targets = "com.sk89q.worldedit.extent.clipboard.io.SchematicReader", remap = false)
public class MixinSchematicReader {

    private static final ThreadLocal<byte[]> CURRENT_BLOCKS16 = new ThreadLocal<>();

    @Redirect(method = "read", at = @At(value = "INVOKE", target = "Lcom/sk89q/jnbt/NBTInputStream;readNamedTag()Lcom/sk89q/jnbt/NamedTag;"))
    private NamedTag onReadNamedTag(NBTInputStream inputStream) throws IOException {
        NamedTag rootTag = inputStream.readNamedTag();
        CURRENT_BLOCKS16.set(null); // Reset the thread local

        if (rootTag != null && rootTag.getName().equals("Schematic") && rootTag.getTag() instanceof CompoundTag) {
            Map<String, Tag> schematic = ((CompoundTag) rootTag.getTag()).getValue();
            if (schematic.containsKey("Blocks16")) {
                Tag blocks16Tag = schematic.get("Blocks16");
                if (blocks16Tag instanceof ByteArrayTag) {
                    CURRENT_BLOCKS16.set(((ByteArrayTag) blocks16Tag).getValue());
                }
            }
        }
        return rootTag;
    }

    @Inject(method = "read", at = @At("RETURN"))
    private void onReadReturn(WorldData data, CallbackInfoReturnable<Clipboard> cir) {
        byte[] blocks16 = CURRENT_BLOCKS16.get();
        Clipboard clipboard = cir.getReturnValue();

        if (blocks16 != null && clipboard != null) {
            Region region = clipboard.getRegion();
            Vector min = region.getMinimumPoint();
            int width = region.getWidth();
            int length = region.getLength();
            int height = region.getHeight();

            // Verify the Blocks16 array length matches the volume * 2
            if (blocks16.length >= width * height * length * 2) {
                for (int y = 0; y < height; y++) {
                    for (int z = 0; z < length; z++) {
                        for (int x = 0; x < width; x++) {
                            int index = y * width * length + z * width + x;
                            // Read 16-bit little-endian ID
                            int id = (blocks16[index * 2] & 0xFF) | ((blocks16[index * 2 + 1] & 0xFF) << 8);

                            Vector pt = min.add(x, y, z);
                            BaseBlock block = clipboard.getBlock(pt);

                            // The metadata (data) and NBT are already correct from standard read
                            BaseBlock newBlock = new BaseBlock(id, block.getData());
                            if (block.hasNbtData()) {
                                newBlock.setNbtData(block.getNbtData());
                            }

                            try {
                                clipboard.setBlock(pt, newBlock);
                            } catch (WorldEditException e) {
                                // Ignore set block failures just like the original code
                            }
                        }
                    }
                }
            }
        }
    }
}
