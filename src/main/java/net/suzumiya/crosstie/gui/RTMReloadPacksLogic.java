package net.suzumiya.crosstie.gui;

import cpw.mods.fml.relauncher.Side;
import jp.ngt.rtm.modelpack.ModelPackLoadThread;
import jp.ngt.rtm.modelpack.ModelPackManager;
import jp.ngt.rtm.modelpack.texture.TextureManager;
import jp.ngt.rtm.modelpack.texture.TextureProperty;
import jp.ngt.ngtlib.renderer.model.IModelNGT;
import net.minecraft.client.Minecraft;
import net.suzumiya.crosstie.CrossTie;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RTMReloadPacksLogic {

    public static volatile String currentCacheMessage = null;

    public static Thread reloadPacks() {
        // Reset state
        currentCacheMessage = null;
        
        Thread wrapperThread = new Thread(() -> {
            try {
                currentCacheMessage = "crosstie.gui.reloadPacks.clearing_caches";
                
                // --- DOUBLE BUFFERING START ---
                // We use Reflection to access the Mixin injected fields and original private fields
                CrossTie.LOGGER.info("Preparing Double Buffering maps...");
                
                // ModelPackManager
                Map<?, ?> oldAllModelSetMap = cloneNestedMap(ModelPackManager.INSTANCE, "allModelSetMap");
                Map<?, ?> oldSmpModelSetMap = cloneNestedMap(ModelPackManager.INSTANCE, "smpModelSetMap");
                Map<?, ?> oldModelFileMap = cloneMap(ModelPackManager.INSTANCE, "modelFileMap");
                Map<?, ?> oldResourceMap = cloneNestedMap(ModelPackManager.INSTANCE, "resourceMap");
                Map<?, ?> oldScriptCache = cloneMap(ModelPackManager.INSTANCE, "scriptCache");
                
                setField(ModelPackManager.INSTANCE, "crosstie$oldAllModelSetMap", oldAllModelSetMap);
                setField(ModelPackManager.INSTANCE, "crosstie$oldSmpModelSetMap", oldSmpModelSetMap);
                setField(ModelPackManager.INSTANCE, "crosstie$oldModelFileMap", oldModelFileMap);
                setField(ModelPackManager.INSTANCE, "crosstie$oldResourceMap", oldResourceMap);
                setField(ModelPackManager.INSTANCE, "crosstie$oldScriptCache", oldScriptCache);
                
                setField(ModelPackManager.INSTANCE, "crosstie$isReloading", true);
                
                // TextureManager
                Map<?, ?> oldAllTextureMap = cloneNestedMap(TextureManager.INSTANCE, "allTextureMap");
                setField(TextureManager.INSTANCE, "crosstie$oldAllTextureMap", oldAllTextureMap);
                setField(TextureManager.INSTANCE, "crosstie$isReloading", true);

                // Now we can safely clear the original maps because the render thread reads from old maps
                clearNestedMapField(ModelPackManager.INSTANCE, "allModelSetMap");
                clearNestedMapField(ModelPackManager.INSTANCE, "smpModelSetMap");
                clearMapField(ModelPackManager.INSTANCE, "modelFileMap");
                clearMapField(ModelPackManager.INSTANCE, "modelFileLocks");
                clearMapField(ModelPackManager.INSTANCE, "resourceMap");
                clearMapField(ModelPackManager.INSTANCE, "scriptCache");
                clearNestedMapField(TextureManager.INSTANCE, "allTextureMap");

                // 3. Clear caches in KaizPatchX CachedPolygonModel
                try {
                    Class<?> cachedPolyClass = Class.forName("jp.kaiz.kaizpatch.fixrtm.model.CachedPolygonModel");
                    Object instance = cachedPolyClass.getField("INSTANCE").get(null);
                    clearMapField(instance, "trackedModels");

                    Class<?> lruClass = Class.forName("jp.kaiz.kaizpatch.fixrtm.model.CachedPolygonModel$LoadedModelLru");
                    Field lruInstanceField = lruClass.getDeclaredField("INSTANCE");
                    lruInstanceField.setAccessible(true);
                    Object lruInstance = lruInstanceField.get(null);
                    clearMapField(lruInstance, "lru");

                    Field totalWeightField = lruClass.getDeclaredField("totalWeight");
                    totalWeightField.setAccessible(true);
                    totalWeightField.set(lruInstance, 0L);
                } catch (Exception e) {
                    CrossTie.LOGGER.warn("Failed to clear KaizPatchX caches. Might not be installed or version changed.", e);
                }

                currentCacheMessage = "crosstie.gui.reloadPacks.reloading_fileloader";
                reloadFIXFileLoader();

                currentCacheMessage = "crosstie.gui.reloadPacks.recreating_cachedpolygonmodel";
                reloadCachedPolygonModelCaches();

                currentCacheMessage = null;

                // Speed up model parsing by forcing max concurrency
                int oldLoadSpeed = jp.ngt.rtm.RTMConfig.loadSpeed;
                jp.ngt.rtm.RTMConfig.loadSpeed = 3;

                // Re-initialize ModelPackLoadThread
                ModelPackLoadThread thread = new ModelPackLoadThread(Side.CLIENT);
                try {
                    Field displayWindowField = ModelPackLoadThread.class.getDeclaredField("displayWindow");
                    displayWindowField.setAccessible(true);
                    displayWindowField.setBoolean(thread, false);
                } catch (Exception e) {
                    CrossTie.LOGGER.warn("Could not disable displayWindow for ModelPackLoadThread", e);
                }

                thread.start();
                thread.join();
                
                jp.ngt.rtm.RTMConfig.loadSpeed = oldLoadSpeed;

                // Done loading. We don't swap maps here, we wait for the GUI to call finishReloadOnMainThread().
                CrossTie.LOGGER.info("Successfully loaded new RTM models into background caches.");
            } catch (Exception e) {
                CrossTie.LOGGER.error("Failed to reload RTM model packs dynamically.", e);
                // Recovery is handled by the main thread GUI anyway.
            }
        });
        wrapperThread.setName("RTMReloadPacksWrapper");
        wrapperThread.start();
        return wrapperThread;
    }

    public static void finishReloadOnMainThread() {
        try {
            // Turn off double buffering, letting the render thread read the NEW maps
            setField(ModelPackManager.INSTANCE, "crosstie$isReloading", false);
            setField(TextureManager.INSTANCE, "crosstie$isReloading", false);
            
            // Force redraw of entities
            forceUpdateAllEntities();
            
            CrossTie.LOGGER.info("Successfully completed atomic swap for RTM reload.");
        } catch (Exception e) {
            CrossTie.LOGGER.error("Failed to finish reload on main thread", e);
        }
    }



    private static void forceUpdateAllEntities() {
        if (Minecraft.getMinecraft().theWorld != null) {
            for (Object obj : Minecraft.getMinecraft().theWorld.loadedEntityList) {
                if (obj instanceof jp.ngt.rtm.entity.train.EntityTrainBase) {
                    try {
                        java.lang.reflect.Method m = jp.ngt.rtm.entity.train.EntityTrainBase.class.getDeclaredMethod("onModelChanged");
                        m.setAccessible(true);
                        m.invoke(obj);
                    } catch (Exception e) {}
                } else if (obj instanceof jp.ngt.rtm.entity.vehicle.EntityVehicleBase) {
                    try {
                        java.lang.reflect.Method m = jp.ngt.rtm.entity.vehicle.EntityVehicleBase.class.getDeclaredMethod("onModelChanged");
                        m.setAccessible(true);
                        m.invoke(obj);
                    } catch (Exception e) {}
                }
            }
        }
    }

    private static Map<Object, Object> cloneMap(Object instance, String fieldName) {
        try {
            Field field = instance.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(instance);
            if (value instanceof Map) {
                return new ConcurrentHashMap<>((Map<?, ?>) value);
            }
        } catch (Exception e) {
            CrossTie.LOGGER.warn("Could not clone map field: " + fieldName);
        }
        return new ConcurrentHashMap<>();
    }

    private static Map<Object, Map<Object, Object>> cloneNestedMap(Object instance, String fieldName) {
        Map<Object, Map<Object, Object>> result = new ConcurrentHashMap<>();
        try {
            Field field = instance.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(instance);
            if (value instanceof Map) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    if (entry.getValue() instanceof Map) {
                        result.put(entry.getKey(), new ConcurrentHashMap<>((Map<?, ?>) entry.getValue()));
                    }
                }
            }
        } catch (Exception e) {
            CrossTie.LOGGER.warn("Could not clone nested map field: " + fieldName);
        }
        return result;
    }

    private static void setField(Object instance, String fieldName, Object value) {
        try {
            Field field = instance.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(instance, value);
        } catch (Exception e) {
            CrossTie.LOGGER.warn("Could not set field: " + fieldName, e);
        }
    }

    private static Object getField(Object instance, String fieldName) {
        try {
            Field field = instance.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(instance);
        } catch (Exception e) {
            CrossTie.LOGGER.warn("Could not get field: " + fieldName, e);
            return null;
        }
    }

    private static void clearMapField(Object instance, String fieldName) {
        try {
            Field field = instance.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(instance);
            if (value instanceof Map) {
                ((Map<?, ?>) value).clear();
            } else if (value instanceof java.util.Collection) {
                ((java.util.Collection<?>) value).clear();
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            CrossTie.LOGGER.warn("Could not clear field: " + fieldName + " in " + instance.getClass().getName());
        }
    }

    private static void clearNestedMapField(Object instance, String fieldName) {
        try {
            Field field = instance.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(instance);
            if (value instanceof Map) {
                for (Object inner : ((Map<?, ?>) value).values()) {
                    if (inner instanceof Map) {
                        ((Map<?, ?>) inner).clear();
                    } else if (inner instanceof java.util.Collection) {
                        ((java.util.Collection<?>) inner).clear();
                    }
                }
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            CrossTie.LOGGER.warn("Could not clear nested field: " + fieldName + " in " + instance.getClass().getName());
        }
    }

    @SuppressWarnings("unchecked")
    private static void reloadFIXFileLoader() {
        try {
            Class<?> fixFileLoaderClass = Class.forName("jp.kaiz.kaizpatch.fixrtm.modelpack.FIXFileLoader");
            Object instance = fixFileLoaderClass.getField("INSTANCE").get(null);

            try {
                java.lang.reflect.Method getAllModelPacksMethod = fixFileLoaderClass.getMethod("getAllModelPacks");
                java.util.Set<?> oldPacks = (java.util.Set<?>) getAllModelPacksMethod.invoke(instance);
                for (Object pack : oldPacks) {
                    if (pack.getClass().getSimpleName().equals("ZipModelPack")) {
                        try {
                            Field zipFileField = pack.getClass().getDeclaredField("zipFile");
                            zipFileField.setAccessible(true);
                            Object zipFile = zipFileField.get(pack);
                            if (zipFile instanceof java.util.zip.ZipFile) {
                                ((java.util.zip.ZipFile) zipFile).close();
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                CrossTie.LOGGER.warn("Could not close old FIXFileLoader zip files", e);
            }

            java.lang.reflect.Method getFilesMethod = fixFileLoaderClass.getMethod("getFiles");
            java.util.List<java.io.File> files = (java.util.List<java.io.File>) getFilesMethod.invoke(instance);

            java.lang.reflect.Method loadModelPackMethod = fixFileLoaderClass.getDeclaredMethod("loadModelPack", java.io.File.class);
            loadModelPackMethod.setAccessible(true);

            Map<String, java.util.Set<Object>> newPacks = new java.util.HashMap<>();
            java.util.Set<Object> newAllModelPacks = new java.util.HashSet<>();

            // 差分ロードによる高速化は一旦省略し、安全なフルロードを行います
            // (ModelPackLoadThread側がフルスキャンするので)
            for (java.io.File file : files) {
                try {
                    Object pack = loadModelPackMethod.invoke(instance, file);
                    if (pack != null) {
                        java.lang.reflect.Method getDomainsMethod = pack.getClass().getMethod("getDomains");
                        getDomainsMethod.setAccessible(true);
                        java.util.Set<String> domains = (java.util.Set<String>) getDomainsMethod.invoke(pack);
                        for (String domain : domains) {
                            if (!newPacks.containsKey(domain)) {
                                newPacks.put(domain, new java.util.HashSet<>());
                            }
                            newPacks.get(domain).add(pack);
                        }
                        newAllModelPacks.add(pack);
                    }
                } catch (Exception e) {
                    CrossTie.LOGGER.warn("Failed to process FIXModelPack for file: " + file, e);
                }
            }

            boolean updatedPacks = false;
            for (Field field : fixFileLoaderClass.getDeclaredFields()) {
                if (field.getName().equals("packs") && Map.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Map<Object, Object> existingPacks = (Map<Object, Object>) field.get(null);
                    existingPacks.clear();
                    existingPacks.putAll(newPacks);
                    updatedPacks = true;
                } else if (field.getName().equals("allModelPacks") && java.util.Set.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    java.util.Set<Object> existingAllPacks = (java.util.Set<Object>) field.get(null);
                    existingAllPacks.clear();
                    existingAllPacks.addAll(newAllModelPacks);
                } else if (field.getName().equals("ignoreCaseMap") && Map.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Map<String, String> existingIgnoreCaseMap = (Map<String, String>) field.get(null);
                    existingIgnoreCaseMap.clear();
                    for (Object pack : newAllModelPacks) {
                        if (pack.getClass().getSimpleName().equals("ZipModelPack")) {
                            try {
                                Field zipFileField = pack.getClass().getDeclaredField("zipFile");
                                zipFileField.setAccessible(true);
                                Object zipFileObj = zipFileField.get(pack);
                                if (zipFileObj instanceof java.util.zip.ZipFile) {
                                    java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = ((java.util.zip.ZipFile) zipFileObj).entries();
                                    while (entries.hasMoreElements()) {
                                        java.util.zip.ZipEntry entry = entries.nextElement();
                                        existingIgnoreCaseMap.put(entry.getName().toLowerCase(), entry.getName());
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }

            if (updatedPacks) {
                CrossTie.LOGGER.info("Successfully reloaded FIXFileLoader caches.");
            } else {
                CrossTie.LOGGER.error("Failed to find 'packs' Map field in FIXFileLoader to overwrite!");
            }
        } catch (ClassNotFoundException e) {
            // Ignore
        } catch (Exception e) {
            CrossTie.LOGGER.warn("Failed to reload FIXFileLoader.", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void reloadCachedPolygonModelCaches() {
        try {
            Class<?> cacheClass = Class.forName("jp.kaiz.kaizpatch.fixrtm.caching.ModelPackBasedCache");
            java.io.File cacheDir = new java.io.File(Minecraft.getMinecraft().mcDataDir, "fixrtm-cache/PolygonModel");
            java.io.File scriptedCacheDir = new java.io.File(Minecraft.getMinecraft().mcDataDir, "fixrtm-cache/ScriptedPolygonModel");
            Class<?> pairClass = Class.forName("kotlin.Pair");
            java.lang.reflect.Constructor<?> cacheConstructor = cacheClass.getConstructors()[0];
            Object pairArray = java.lang.reflect.Array.newInstance(pairClass, 1);
            Class<?> serializerObjClass = Class.forName("jp.kaiz.kaizpatch.fixrtm.model.CachedPolygonModel$Serializer");
            Field serializerInstanceField = serializerObjClass.getDeclaredField("INSTANCE");
            serializerInstanceField.setAccessible(true);
            Object serializerInstance = serializerInstanceField.get(null);

            java.lang.reflect.Constructor<?> pairConstructor = pairClass.getConstructor(Object.class, Object.class);
            Object pairInstance = pairConstructor.newInstance(0, serializerInstance);
            java.lang.reflect.Array.set(pairArray, 0, pairInstance);

            Object newCache = cacheConstructor.newInstance(cacheDir, pairArray);
            Object newScriptedCache = cacheConstructor.newInstance(scriptedCacheDir, pairArray);

            Class<?> cachedPolyClass = Class.forName("jp.kaiz.kaizpatch.fixrtm.model.CachedPolygonModel");
            Field cachesMapField = cacheClass.getDeclaredField("caches");
            cachesMapField.setAccessible(true);

            for (Field field : cachedPolyClass.getDeclaredFields()) {
                if (field.getName().equals("cache")) {
                    field.setAccessible(true);
                    Object oldCache = field.get(null);
                    Map<Object, Object> oldCachesMap = (Map<Object, Object>) cachesMapField.get(oldCache);
                    Map<Object, Object> newMap = (Map<Object, Object>) cachesMapField.get(newCache);
                    oldCachesMap.clear();
                    oldCachesMap.putAll(newMap);
                } else if (field.getName().equals("scriptedCache")) {
                    field.setAccessible(true);
                    Object oldScriptedCache = field.get(null);
                    Map<Object, Object> oldScriptedCachesMap = (Map<Object, Object>) cachesMapField.get(oldScriptedCache);
                    Map<Object, Object> newScriptedMap = (Map<Object, Object>) cachesMapField.get(newScriptedCache);
                    oldScriptedCachesMap.clear();
                    oldScriptedCachesMap.putAll(newScriptedMap);
                }
            }

            Object instance = cachedPolyClass.getField("INSTANCE").get(null);
            for (Field field : cachedPolyClass.getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(instance);
                if (value instanceof Map) {
                    ((Map<?, ?>) value).clear();
                } else if (value instanceof java.util.Collection) {
                    ((java.util.Collection<?>) value).clear();
                }
            }

            try {
                Class<?> lruClass = Class.forName("jp.kaiz.kaizpatch.fixrtm.model.CachedPolygonModel$LoadedModelLru");
                Field lruInstanceField = lruClass.getDeclaredField("INSTANCE");
                lruInstanceField.setAccessible(true);
                Object lruInstance = lruInstanceField.get(null);

                for (Field field : lruClass.getDeclaredFields()) {
                    field.setAccessible(true);
                    Object value = field.get(lruInstance);
                    if (value instanceof Map) {
                        ((Map<?, ?>) value).clear();
                    } else if (value instanceof java.util.Collection) {
                        ((java.util.Collection<?>) value).clear();
                    }
                }
            } catch (Exception ignored) {}

            CrossTie.LOGGER.info("Successfully recreated CachedPolygonModel caches.");
        } catch (Exception e) {
            CrossTie.LOGGER.warn("Failed to recreate CachedPolygonModel caches.", e);
        }
    }
}
