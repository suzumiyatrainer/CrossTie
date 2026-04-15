package net.suzumiya.crosstie;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.suzumiya.crosstie.config.CrossTieConfig;

/**
 * サーバー/クライアント共通プロキシ
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        // Use custom config file name "CrossTie.cfg" instead of default "crosstie.cfg"
        java.io.File configFile = new java.io.File(event.getModConfigurationDirectory(), "CrossTie.cfg");
        CrossTieConfig.init(configFile);
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
