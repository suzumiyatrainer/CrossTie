package net.suzumiya.crosstie.gui;

import cpw.mods.fml.relauncher.Side;
import jp.ngt.rtm.modelpack.ModelPackLoadThread;
import jp.ngt.rtm.modelpack.ModelPackManager;
import jp.ngt.rtm.modelpack.texture.TextureManager;
import net.suzumiya.crosstie.CrossTie;

import java.lang.reflect.Field;
import java.util.Map;

public class RTMReloadPacksLogic {

    public static volatile String currentCacheMessage = null;

    public static Thread reloadPacks() {
        Thread wrapperThread = new Thread(() -> {
            try {
                currentCacheMessage = "crosstie.gui.reloadPacks.clearing_caches";
                // 1. Clear caches in ModelPackManager
                clearNestedMapField(ModelPackManager.INSTANCE, "allModelSetMap");
                clearNestedMapField(ModelPackManager.INSTANCE, "smpModelSetMap");
                clearMapField(ModelPackManager.INSTANCE, "modelFileMap");
                clearMapField(ModelPackManager.INSTANCE, "modelFileLocks");
                clearMapField(ModelPackManager.INSTANCE, "resourceMap");
                clearMapField(ModelPackManager.INSTANCE, "scriptCache");

                // 2. Clear caches in TextureManager
                clearNestedMapField(TextureManager.INSTANCE, "allTextureMap");

                // 3. Clear caches in KaizPatchX CachedPolygonModel (Kotlin object)
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
                // 3.5 Reload FIXFileLoader so it detects new or deleted packs
                reloadFIXFileLoader();

                currentCacheMessage = "crosstie.gui.reloadPacks.recreating_cachedpolygonmodel";
                reloadCachedPolygonModelCaches();

                currentCacheMessage = null;

                // Speed up model parsing by forcing max concurrency (Fastest)
                int oldLoadSpeed = jp.ngt.rtm.RTMConfig.loadSpeed;
                jp.ngt.rtm.RTMConfig.loadSpeed = 3;

                // 5. Re-initialize ModelPackLoadThread
                ModelPackLoadThread thread = new ModelPackLoadThread(Side.CLIENT);

                // Restore previous load speed after initializing the thread (it grabs the value in runThread)
                // Actually it grabs the value when it runs, so we must restore it AFTER join.

                // Force disable AWT window to prevent massive thread lock contention
                try {
                    Field displayWindowField = ModelPackLoadThread.class.getDeclaredField("displayWindow");
                    displayWindowField.setAccessible(true);
                    displayWindowField.setBoolean(thread, false);
                } catch (Exception e) {
                    CrossTie.LOGGER.warn("Could not disable displayWindow for ModelPackLoadThread", e);
                }

                thread.start();
                thread.join();
                
                // Restore loadSpeed
                jp.ngt.rtm.RTMConfig.loadSpeed = oldLoadSpeed;

                CrossTie.LOGGER.info("Successfully triggered RTM model pack reload.");
            } catch (Exception e) {
                CrossTie.LOGGER.error("Failed to reload RTM model packs dynamically.", e);
            }
        });
        wrapperThread.setName("RTMReloadPacksWrapper");
        wrapperThread.start();
        return wrapperThread;
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

            // 1. Close old ZipFiles to prevent file handle locks
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
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Exception e) {
                CrossTie.LOGGER.warn("Could not close old FIXFileLoader zip files", e);
            }

            // 2. Load new packs
            java.lang.reflect.Method getFilesMethod = fixFileLoaderClass.getMethod("getFiles");
            java.util.List<java.io.File> files = (java.util.List<java.io.File>) getFilesMethod.invoke(instance);

            java.lang.reflect.Method loadModelPackMethod = fixFileLoaderClass.getDeclaredMethod("loadModelPack",
                    java.io.File.class);
            loadModelPackMethod.setAccessible(true);

            Map<String, java.util.Set<Object>> newPacks = new java.util.HashMap<>();
            java.util.Set<Object> newAllModelPacks = new java.util.HashSet<>();

            for (java.io.File file : files) {
                try {
                    Object pack = loadModelPackMethod.invoke(instance, file);
                    if (pack != null) {
                        java.lang.reflect.Method getDomainsMethod = pack.getClass().getMethod("getDomains");
                        getDomainsMethod.setAccessible(true); // Required for private Kotlin classes
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

            // 3. Clear and repopulate the existing maps/sets to avoid JVM static final
            // inlining issues
            boolean updatedPacks = false;
            for (Field field : fixFileLoaderClass.getDeclaredFields()) {
                if (field.getName().equals("packs") && Map.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Map<Object, Object> existingPacks = (Map<Object, Object>) field.get(null);
                    existingPacks.clear();
                    existingPacks.putAll(newPacks);
                    updatedPacks = true;
                } else if (field.getName().equals("allModelPacks")
                        && java.util.Set.class.isAssignableFrom(field.getType())) {
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
                            } catch (Exception ignored) {
                            }
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
            // KaizPatchX not installed or version changed
        } catch (Exception e) {
            CrossTie.LOGGER.warn("Failed to reload FIXFileLoader.", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void reloadCachedPolygonModelCaches() {
        try {
            // Re-instantiate caches using Reflection
            Class<?> cacheClass = Class.forName("jp.kaiz.kaizpatch.fixrtm.caching.ModelPackBasedCache");

            // Requires constructor ModelPackBasedCache(File, Pair<Int,
            // TaggedFileManager.Serializer<*>>[])
            java.io.File cacheDir = new java.io.File(net.minecraft.client.Minecraft.getMinecraft().mcDataDir,
                    "fixrtm-cache/PolygonModel");
            java.io.File scriptedCacheDir = new java.io.File(net.minecraft.client.Minecraft.getMinecraft().mcDataDir,
                    "fixrtm-cache/ScriptedPolygonModel");

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

            // This instantiates new Cache managers and properly clears unused disk caches
            Object newCache = cacheConstructor.newInstance(cacheDir, pairArray);
            Object newScriptedCache = cacheConstructor.newInstance(scriptedCacheDir, pairArray);

            Class<?> cachedPolyClass = Class.forName("jp.kaiz.kaizpatch.fixrtm.model.CachedPolygonModel");

            // Extract the internally generated 'caches' Maps from the newly created caches
            Field cachesMapField = cacheClass.getDeclaredField("caches");
            cachesMapField.setAccessible(true);
            // Instead of replacing the static final fields, mutate the existing Maps to
            // avoid JIT inlining issues
            for (Field field : cachedPolyClass.getDeclaredFields()) {
                if (field.getName().equals("cache")) {
                    field.setAccessible(true);
                    Object oldCache = field.get(null);
                    Map<Object, Object> oldCachesMap = (Map<Object, Object>) cachesMapField.get(oldCache);
                    Map<Object, Object> newMap = (Map<Object, Object>) cachesMapField.get(newCache);
                    CrossTie.LOGGER.info("Reloading CachedPolygonModel.cache.caches. Old size: " + oldCachesMap.size()
                            + ", New size: " + newMap.size());
                    oldCachesMap.clear();
                    oldCachesMap.putAll(newMap);
                } else if (field.getName().equals("scriptedCache")) {
                    field.setAccessible(true);
                    Object oldScriptedCache = field.get(null);
                    Map<Object, Object> oldScriptedCachesMap = (Map<Object, Object>) cachesMapField
                            .get(oldScriptedCache);
                    Map<Object, Object> newScriptedMap = (Map<Object, Object>) cachesMapField.get(newScriptedCache);
                    CrossTie.LOGGER.info("Reloading CachedPolygonModel.scriptedCache.caches. Old size: "
                            + oldScriptedCachesMap.size() + ", New size: " + newScriptedMap.size());
                    oldScriptedCachesMap.clear();
                    oldScriptedCachesMap.putAll(newScriptedMap);
                }
            }

            // Also clear tracked caches
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
            } catch (Exception ignored) {
            }

            CrossTie.LOGGER.info("Successfully recreated CachedPolygonModel caches.");
        } catch (Exception e) {
            CrossTie.LOGGER.warn("Failed to recreate CachedPolygonModel caches.", e);
        }
    }

}
