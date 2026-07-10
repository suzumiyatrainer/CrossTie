package net.suzumiya.crosstie.utils;

import net.minecraft.block.Block;

public class ATSAssistReflectionHelper {
    private static ClassLoader atsClassLoader;
    private static Class<?> trainControllerManagerClass;
    private static java.lang.reflect.Method getTrainControllerMethod;
    private static Class<?> trainControllerClass;
    private static java.lang.reflect.Field tascControllerField;
    private static Class<?> tascControllerClass;
    private static java.lang.reflect.Method getStopDistanceMethod;
    private static java.lang.reflect.Method isStopPositionMethod;
    private static java.lang.reflect.Method isBreakingMethod;
    private static java.lang.reflect.Method setBrakingMethod;
    private static java.lang.reflect.Method isEnableMethod;

    private static boolean initialized = false;
    private static boolean failed = false;

    public static synchronized void init() {
        if (initialized || failed) return;
        try {
            Block block = Block.getBlockFromName("ATSAssistMod:groundUnit");
            if (block == null) {
                block = Block.getBlockFromName("tile.ATSAssistMod:groundUnit");
            }
            if (block == null) {
                return;
            }
            atsClassLoader = block.getClass().getClassLoader();

            trainControllerManagerClass = Class.forName("jp.kaiz.atsassistmod.controller.TrainControllerManager", true, atsClassLoader);
            getTrainControllerMethod = trainControllerManagerClass.getMethod("getTrainController", jp.ngt.rtm.entity.train.EntityTrainBase.class);
            
            trainControllerClass = Class.forName("jp.kaiz.atsassistmod.controller.TrainController", true, atsClassLoader);
            tascControllerField = trainControllerClass.getField("tascController");
            
            tascControllerClass = Class.forName("jp.kaiz.atsassistmod.controller.TASCController", true, atsClassLoader);
            getStopDistanceMethod = tascControllerClass.getMethod("getStopDistance");
            isStopPositionMethod = tascControllerClass.getMethod("isStopPosition");
            isBreakingMethod = tascControllerClass.getMethod("isBreaking");
            setBrakingMethod = tascControllerClass.getMethod("setBraking", boolean.class);
            isEnableMethod = tascControllerClass.getMethod("isEnable");

            initialized = true;
        } catch (Exception e) {
            e.printStackTrace();
            failed = true;
        }
    }

    public static Object getTrainController(jp.ngt.rtm.entity.train.EntityTrainBase train) {
        init();
        if (!initialized) return null;
        try {
            return getTrainControllerMethod.invoke(null, train);
        } catch (Exception e) {
            return null;
        }
    }

    public static Object getTASCController(Object trainController) {
        if (trainController == null) return null;
        try {
            return tascControllerField.get(trainController);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isTASCEnabled(Object tascController) {
        if (tascController == null) return false;
        try {
            return (Boolean) isEnableMethod.invoke(tascController);
        } catch (Exception e) {
            return false;
        }
    }

    public static double getStopDistance(Object tascController) {
        if (tascController == null) return -1.0;
        try {
            return (Double) getStopDistanceMethod.invoke(tascController);
        } catch (Exception e) {
            return -1.0;
        }
    }

    public static boolean isStopPosition(Object tascController) {
        if (tascController == null) return false;
        try {
            return (Boolean) isStopPositionMethod.invoke(tascController);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isBreaking(Object tascController) {
        if (tascController == null) return false;
        try {
            return (Boolean) isBreakingMethod.invoke(tascController);
        } catch (Exception e) {
            return false;
        }
    }

    public static void setBraking(Object tascController, boolean braking) {
        if (tascController == null) return;
        try {
            setBrakingMethod.invoke(tascController, braking);
        } catch (Exception e) {
            // ignore
        }
    }
}
