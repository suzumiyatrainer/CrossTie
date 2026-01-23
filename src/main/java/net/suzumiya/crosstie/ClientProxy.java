package net.suzumiya.crosstie;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * クライアント専用プロキシ
 */
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        // クライアント固有の初期化処理
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        // クライアント固有の初期化処理
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
        // クライアント固有の初期化処理
    }
}
