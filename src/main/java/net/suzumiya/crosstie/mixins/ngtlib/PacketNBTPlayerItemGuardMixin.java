package net.suzumiya.crosstie.mixins.ngtlib;

import jp.ngt.ngtlib.network.PacketNBT;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CrossTie: PacketNBT における PlayerItem NBT不整合・誤上書き防止 Mixin
 *
 * <h3>問題の背景</h3>
 * <p>PacketNBT(Type_PlayerItem = 2) はサーバー側受信時に、現在プレイヤーが手に持っているスロット
 * (inventory.getCurrentItem()) の NBT を無条件に上書きする構造になっている。
 * クライアント側でパケットが送信された直後にスロット移動が起こる等のタイミングのズレが生じると、
 * 新しく持った全く関係のないアイテムの NBT が古いパケットデータで誤上書き複製されてしまう。</p>
 *
 * <h3>修正内容</h3>
 * <ol>
 *   <li>PacketNBT(EntityPlayer, ItemStack) 送信時に、対象 ItemStack の識別情報（Itemレジストリ名・Damage）をメタデータとして同梱する。</li>
 *   <li>サーバー側 onGetPacket 受信時、現在手持ちの ItemStack とパケットのターゲット情報が一致しない場合は
 *       NBT の上書き処理を抑止・ブロックする。</li>
 * </ol>
 */
@Mixin(value = PacketNBT.class, remap = false)
public abstract class PacketNBTPlayerItemGuardMixin {

    @Shadow
    public NBTTagCompound nbtData;

    @Inject(
        method = "<init>(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/item/ItemStack;)V",
        at = @At("RETURN"),
        remap = false
    )
    private void crosstie$onInitPlayerItemPacket(EntityPlayer player, ItemStack stack, CallbackInfo ci) {
        if (this.nbtData != null && stack != null && stack.getItem() != null) {
            String itemName = Item.itemRegistry.getNameForObject(stack.getItem());
            if (itemName != null) {
                this.nbtData.setString("CrosstieTargetItem", itemName);
                this.nbtData.setInteger("CrosstieTargetDamage", stack.getItemDamage());
            }
        }
    }

    @Inject(
        method = "onGetPacket",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void crosstie$guardPlayerItemPacket(World world, CallbackInfo ci) {
        if (this.nbtData == null || world == null) {
            return;
        }

        byte type = this.nbtData.getByte("DataType");
        if (type == 2) { // Type_PlayerItem = 2
            int id = this.nbtData.getInteger("EntityId");
            Entity entity = world.getEntityByID(id);
            if (entity instanceof EntityPlayer) {
                ItemStack currentStack = ((EntityPlayer) entity).inventory.getCurrentItem();
                if (this.nbtData.hasKey("CrosstieTargetItem")) {
                    String targetItem = this.nbtData.getString("CrosstieTargetItem");
                    int targetDamage = this.nbtData.getInteger("CrosstieTargetDamage");

                    if (currentStack == null || currentStack.getItem() == null) {
                        ci.cancel();
                        return;
                    }

                    String currentItemName = Item.itemRegistry.getNameForObject(currentStack.getItem());
                    if (!targetItem.equals(currentItemName) || currentStack.getItemDamage() != targetDamage) {
                        ci.cancel();
                        return;
                    }
                }
            }
        }
    }
}
