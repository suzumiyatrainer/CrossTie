package net.suzumiya.crosstie.utils;

import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.Method;
import jp.ngt.rtm.RTMCore;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.modelpack.modelset.ModelSetBase;
import net.minecraft.world.World;

public class CrossTieTascUtils {
    
    private static final Map<Integer, DecelProfile> decelCache = new HashMap<>();

    private static class DecelProfile {
        final double[] decels;
        final int maxCommon;
        DecelProfile(double[] decels, int maxCommon) {
            this.decels = decels;
            this.maxCommon = maxCommon;
        }
    }

    public static void processTasc(Object entityObj, Object dataMapObj) {
        if (!(entityObj instanceof EntityTrainBase)) return;
        EntityTrainBase train = (EntityTrainBase) entityObj;
        World world = train.worldObj;
        if (world == null || world.isRemote) return;

        try {
            Method getBoolean = dataMapObj.getClass().getMethod("getBoolean", String.class);
            Method setBoolean = dataMapObj.getClass().getMethod("setBoolean", String.class, boolean.class, int.class);
            Method getDouble = dataMapObj.getClass().getMethod("getDouble", String.class);
            Method setDouble = dataMapObj.getClass().getMethod("setDouble", String.class, double.class, int.class);
            Method getInt = dataMapObj.getClass().getMethod("getInt", String.class);

            if (!(Boolean) getBoolean.invoke(dataMapObj, "suzuTascEnable")) return;

            int trainTick = train.ticksExisted;
            double speedMTick = Math.abs(train.getSpeed());
            double speedKMH = speedMTick * 72.0;
            int curNotch = train.getNotch();

            double lastX = (Double) getDouble.invoke(dataMapObj, "suzuTascLastX");
            double lastY = (Double) getDouble.invoke(dataMapObj, "suzuTascLastY");
            double lastZ = (Double) getDouble.invoke(dataMapObj, "suzuTascLastZ");
            double targetDist = (Double) getDouble.invoke(dataMapObj, "suzuTascTargetDist");

            double dx = train.posX - lastX;
            double dy = train.posY - lastY;
            double dz = train.posZ - lastZ;
            double moveDist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (Double.isNaN(moveDist) || moveDist > 10.0) moveDist = 0.0;

            double stopDist = targetDist - moveDist;
            if (stopDist < 0.0) stopDist = 0.0;

            int phase = trainTick % 5;
            boolean isSyncTick = (phase == 1 || phase == 3);

            if (isSyncTick) {
                setDouble.invoke(dataMapObj, "suzuTascLastX", train.posX, 2);
                setDouble.invoke(dataMapObj, "suzuTascLastY", train.posY, 2);
                setDouble.invoke(dataMapObj, "suzuTascTargetDist", stopDist, 2);
                
                if (RTMCore.VERSION.contains("1.7.10")) {
                    setDouble.invoke(dataMapObj, "suzuTascLastZ", train.posZ, 2);
                } else {
                    double lastZVal = train.posZ;
                    try {
                        lastZVal = train.getClass().getField("field_70162_v").getDouble(train);
                    } catch (Exception e) {}
                    setDouble.invoke(dataMapObj, "suzuTascLastZ", lastZVal, 2);
                }
            }

            ModelSetBase<?> modelSet = train.getModelSet();
            int msKey = System.identityHashCode(modelSet);
            DecelProfile profile = decelCache.get(msKey);
            
            if (profile == null) {
                double[] defaultDecels = {0.0, 0.00025, 0.00020832, 0.00048608, 0.00083328, 0.0013888, 0.00194432, 0.0024304, 0.00291456};
                int defaultMax = 8;
                if (modelSet != null) {
                    Object cfg = null;
                    try {
                        Method getConfig = modelSet.getClass().getMethod("getConfig");
                        cfg = getConfig.invoke(modelSet);
                    } catch (Exception e) {}
                    
                    if (cfg != null) {
                        try {
                            float[] configDecels = (float[]) cfg.getClass().getField("deccelerations").get(cfg);
                            if (configDecels != null && configDecels.length > 0) {
                                double[] newDecels = new double[configDecels.length];
                                for (int k = 0; k < configDecels.length; k++) {
                                    newDecels[k] = Math.abs(configDecels[k]);
                                }
                                defaultDecels = newDecels;
                                defaultMax = configDecels.length - 1;
                            }
                        } catch (Exception e) {}
                    }
                }
                profile = new DecelProfile(defaultDecels, defaultMax);
                decelCache.put(msKey, profile);
            }

            double[] decels = profile.decels;
            int maxCommon = profile.maxCommon;

            if (speedMTick <= 0.00001 || (speedKMH < 0.3 && stopDist <= 0.25) || (speedKMH < 1.0 && stopDist <= 0.45) || (speedKMH < 2.0 && stopDist <= 0.1)) {
                setTrainStateNotch(train, -maxCommon);
                setBoolean.invoke(dataMapObj, "suzuTascEnable", false, 2);
                setDouble.invoke(dataMapObj, "suzuTascTargetDist", 0.0, 2);
                setDouble.invoke(dataMapObj, "suzuLimitSpeed", 0.0, 2);
                setDouble.invoke(dataMapObj, "suzuLimitDist", 0.0, 2);
                setBoolean.invoke(dataMapObj, "suzuSpcsDone", false, 2);
                return;
            }

            double tascCalcDist = stopDist + (speedMTick * speedMTick) * speedMTick * 0.0108;
            if (speedKMH < 25.0) tascCalcDist -= (25.0 - speedKMH) * 0.022;
            if (tascCalcDist < 0.01) tascCalcDist = 0.01;

            double tascReqDecel = (speedMTick * speedMTick) / (2.0 * tascCalcDist);

            int PREFERRED_MAX = Math.min(5, maxCommon);
            double tascDecelProfile = decels[PREFERRED_MAX];
            double idealTascDist = (speedMTick * speedMTick) / (2.0 * tascDecelProfile);
            double tascTriggerDist = idealTascDist + (speedMTick * 8.0);

            double limitDist = (Double) getDouble.invoke(dataMapObj, "suzuLimitDist");
            double spcsReqDecel = 0.0;
            boolean spcsForceCoast = false;
            int spcsForcePowerNotch = 0;
            boolean isUrgent = false;

            if (limitDist > 0.0) {
                limitDist = Math.max(0.0, limitDist - moveDist);
                if (isSyncTick) {
                    setDouble.invoke(dataMapObj, "suzuLimitDist", limitDist, 2);
                }

                double limSpeed = (Double) getDouble.invoke(dataMapObj, "suzuLimitSpeed");
                if (limSpeed > 0.0) {
                    double realTargetSpeed = Math.max(0.0, limSpeed - 2.0);
                    double adjustedLimitDist = Math.max(0.0, limitDist - 10.0);
                    boolean isSpcsDone = (Boolean) getBoolean.invoke(dataMapObj, "suzuSpcsDone");

                    if (!isSpcsDone) {
                        if (speedKMH <= realTargetSpeed) {
                            setBoolean.invoke(dataMapObj, "suzuSpcsDone", true, 2);
                            spcsForceCoast = true;
                        } else {
                            double targetLimitDist = adjustedLimitDist > 0.01 ? adjustedLimitDist : 0.01;
                            double limMTick = realTargetSpeed / 72.0;

                            double idealSpcsDist = ((speedMTick * speedMTick) - (limMTick * limMTick)) / (2.0 * tascDecelProfile);
                            double spcsTriggerDist = idealSpcsDist + (speedMTick * 6.0);

                            if (targetLimitDist > spcsTriggerDist) {
                                spcsForceCoast = true;
                            } else {
                                spcsReqDecel = ((speedMTick * speedMTick) - (limMTick * limMTick)) / (2.0 * targetLimitDist);
                                if (spcsReqDecel < tascDecelProfile) spcsReqDecel = tascDecelProfile;
                                if (spcsReqDecel > decels[maxCommon]) isUrgent = true;
                            }
                        }
                    } else {
                        double speedDiff = speedKMH - realTargetSpeed;
                        if (speedDiff > 0.5) {
                            spcsReqDecel = decels[Math.min(1, maxCommon)];
                        } else if (speedDiff < -0.5) {
                            spcsForceCoast = false;
                            spcsForcePowerNotch = 1;
                        } else {
                            spcsForceCoast = true;
                        }

                        double accelTriggerSpeed = limSpeed - 7.0;
                        if (speedKMH < accelTriggerSpeed) {
                            int allowedMaxPower = 0;
                            if (stopDist > 150.0) allowedMaxPower = 5;
                            else if (stopDist > 100.0) allowedMaxPower = 3;
                            else if (stopDist > 60.0) allowedMaxPower = 2;
                            else if (stopDist > 30.0) allowedMaxPower = 1;

                            if (allowedMaxPower > 0) {
                                spcsForceCoast = false;
                                double speedDeficit = realTargetSpeed - speedKMH;
                                spcsForcePowerNotch = Math.min(allowedMaxPower, (int) Math.ceil(speedDeficit * 0.8));
                            }
                        }
                    }
                }
            } else {
                if ((Boolean) getBoolean.invoke(dataMapObj, "suzuSpcsDone")) {
                    setBoolean.invoke(dataMapObj, "suzuSpcsDone", false, 2);
                }
            }

            int playerPowerNotch = (Integer) getInt.invoke(dataMapObj, "suzu_pNotch");
            int playerBrakeNotch = (Integer) getInt.invoke(dataMapObj, "suzu_bNotch");
            int targetNotch = 0;

            if (spcsForcePowerNotch == 0) {
                double effectiveTascDecel = 0.0;
                if (stopDist <= tascTriggerDist || speedKMH <= 5.0 || tascReqDecel > decels[PREFERRED_MAX]) {
                    effectiveTascDecel = tascReqDecel;
                }

                double finalReqDecel = Math.max(effectiveTascDecel, spcsReqDecel);

                if (finalReqDecel > 0.000001) {
                    for (int n = 1; n <= maxCommon; n++) {
                        if (decels[n] >= finalReqDecel) {
                            targetNotch = n;
                            break;
                        }
                    }

                    if (targetNotch == 0) {
                        targetNotch = maxCommon;
                        isUrgent = true;
                    }

                    if (effectiveTascDecel > spcsReqDecel && speedKMH > 15.0) {
                        if (stopDist > (idealTascDist * 1.25 + 2.0)) {
                            targetNotch = Math.max(0, targetNotch - 2);
                        } else if (stopDist > (idealTascDist * 1.1 + 0.5)) {
                            targetNotch = Math.max(0, targetNotch - 1);
                        }
                    }
                } else if (spcsForceCoast) {
                    targetNotch = 0;
                }
            }

            targetNotch = Math.max(targetNotch, playerBrakeNotch);
            int finalOutputNotch = 0;

            if (targetNotch == 0 || (playerPowerNotch > 0 && targetNotch <= 1)) {
                if (playerPowerNotch > 0) {
                    finalOutputNotch = playerPowerNotch;
                } else if (spcsForcePowerNotch > 0) {
                    finalOutputNotch = spcsForcePowerNotch;
                } else {
                    finalOutputNotch = 0;
                }
            } else {
                int finalBrakeNotch = targetNotch;
                int absCurNotch = Math.abs(curNotch);
                double releaseLimitDist = Math.max(25.0, speedKMH * 0.22);

                if (!isUrgent && stopDist > releaseLimitDist) {
                    int notchDiff = finalBrakeNotch - absCurNotch;
                    if (notchDiff > 1) finalBrakeNotch = absCurNotch + 1;
                    else if (notchDiff < -1) finalBrakeNotch = absCurNotch - 1;
                }

                if (finalBrakeNotch > maxCommon) finalBrakeNotch = maxCommon;
                finalOutputNotch = -finalBrakeNotch;
            }

            if (curNotch != finalOutputNotch) {
                setTrainStateNotch(train, finalOutputNotch);
            }
        } catch (Exception e) {
            System.out.println("[Server-TASC-Error] Exception in suzu_server_tascSystem (Java): " + e);
        }
    }

    private static void setTrainStateNotch(EntityTrainBase train, int notch) {
        try {
            if (RTMCore.VERSION.contains("1.7.10")) {
                Method setTrainStateData = train.getClass().getMethod("setTrainStateData", int.class, byte.class);
                setTrainStateData.invoke(train, 1, (byte) notch);
            } else {
                Method setTrainStateData = train.getClass().getMethod("setTrainStateData", int.class, Integer.class);
                setTrainStateData.invoke(train, 1, Integer.valueOf(notch));
            }
        } catch (Exception e) {}
    }
}
