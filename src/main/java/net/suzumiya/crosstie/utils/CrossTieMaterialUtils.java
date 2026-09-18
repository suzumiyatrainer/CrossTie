package net.suzumiya.crosstie.utils;

import net.suzumiya.crosstie.utils.texture.CrossTieTextureOverrideManager;

/**
 * JS-facing API for dynamic texture replacement. Exposed to scripts via:
 * importPackage(Packages.net.suzumiya.crosstie.utils);
 */
public class CrossTieMaterialUtils {

    /**
     * Dynamically replaces the texture for a specific material on a given object
     * (Entity or TileEntity).
     * 
     * @param entity    The target object.
     * @param targetMat The material ID (0-255).
     * @param path      The texture path (supports .gif).
     */
    public static void setTexture(Object entity, int targetMat, String path) {
        CrossTieTextureOverrideManager.setTexture(entity, targetMat, path);
    }

    /**
     * Reverts the texture for a specific material on a given object
     * back to the default texture specified in the JSON model.
     * 
     * @param entity    The target object.
     * @param targetMat The material ID (0-255).
     */
    public static void resetTexture(Object entity, int targetMat) {
        CrossTieTextureOverrideManager.resetTexture(entity, targetMat);
    }

    /**
     * Binds the texture for a specific material on a given object.
     * If an override is set, it binds the overridden texture.
     * Otherwise, it binds the provided default texture path.
     * Use this before manually rendering script Parts.
     *
     * @param entity      The target object.
     * @param targetMat   The material ID (0-255).
     * @param defaultPath The default texture path to fallback to.
     */
    public static void bindTexture(Object entity, int targetMat, String defaultPath) {
        CrossTieTextureOverrideManager.bindTexture(entity, targetMat, defaultPath);
    }
}
