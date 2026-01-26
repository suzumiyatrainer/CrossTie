package net.suzumiya.crosstie;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * サーバー/クライアント共通プロキシ
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        // 共通初期化処理
    }

    public void init(FMLInitializationEvent event) {
        // 共通初期化処理
    }

    public void postInit(FMLPostInitializationEvent event) {
        // 共通初期化処理
    }

    public int getClientRenderDistance() {
        return 0;
    }

    public net.minecraft.entity.Entity getClientPlayer() {
        return null;
    }
}
