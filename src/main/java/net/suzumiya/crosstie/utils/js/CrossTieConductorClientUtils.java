package net.suzumiya.crosstie.utils.js;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;

/**
 * 車掌システムのクライアント側（キー入力・Render）処理。 Minecraft クライアント専用クラス（Minecraft,
 * Keyboard等）への参照を サーバー側クラス（CrossTieArudenUtils）から完全に隔離し、 専用サーバーでの
 * NoClassDefFoundError を防止する。
 */
public final class CrossTieConductorClientUtils {

    private CrossTieConductorClientUtils() {
    }

    // 車両ID + キー名でローカル送信状態を管理（車両間の干渉を完全排除）
    private static final Map<String, Boolean> lastSentKeys = new HashMap<>();

    // isLeftの状態保持用（毎フレームパケット送信防止）
    private static final Map<Integer, Boolean> lastIsLeftMap = new HashMap<>();

    /**
     * クライアントサイドのキー入力処理。onRender() から毎フレームで呼ぶ。
     */
    public static void processConductorRender(Object entityObj, Object dataMapObj, int buzzer, int bell, int door,
            int next, int prev, int ann, int eb, int melody, int tojime, int fd) {
        if (!(entityObj instanceof EntityTrainBase))
            return;
        EntityTrainBase train = (EntityTrainBase) entityObj;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null)
            return;

        EntityPlayer player = mc.thePlayer;
        if (player == null)
            return;

        try {
            Method getBoolean = dataMapObj.getClass().getMethod("getBoolean", String.class);
            Method setBoolean = dataMapObj.getClass().getMethod("setBoolean", String.class, boolean.class, int.class);
            Method getString = dataMapObj.getClass().getMethod("getString", String.class);

            String playerID = String.valueOf(player.getEntityId());
            String cabPlayers = (String) getString.invoke(dataMapObj, "suzu_cabPlayers");

            boolean inCab = false;
            if (cabPlayers != null && !cabPlayers.isEmpty()) {
                List<String> pList = Arrays.asList(cabPlayers.split(","));
                inCab = pList.contains(playerID);
            }

            int trainId = train.getEntityId();

            if (!inCab) {
                // 乗務員室外にいる場合は押下中だったキーを解放して処理を抜ける
                releaseKeysIfHeld(trainId, dataMapObj, getBoolean, setBoolean);
                return;
            }

            double dx = player.posX - train.posX;
            double dy = player.posY - train.posY;
            double dz = player.posZ - train.posZ;
            float yaw = -train.rotationYaw;

            Vec3 targetVec = Vec3.createVectorHelper(dx, dy, dz);
            targetVec.rotateAroundY((float) Math.toRadians(yaw));
            boolean isLeft = targetVec.xCoord > 0;

            Boolean lastIsLeft = lastIsLeftMap.get(trainId);
            if (lastIsLeft == null || lastIsLeft != isLeft) {
                setBoolean.invoke(dataMapObj, "suzu_keyPlayerIsLeft", isLeft, 3);
                lastIsLeftMap.put(trainId, isLeft);
            }

            sendKey(trainId, dataMapObj, getBoolean, setBoolean, "BuzzerKey", buzzer);
            sendKey(trainId, dataMapObj, getBoolean, setBoolean, "BellKey", bell);
            sendKey(trainId, dataMapObj, getBoolean, setBoolean, "DoorControlKey", door);
            sendKey(trainId, dataMapObj, getBoolean, setBoolean, "NextAnnounceKey", next);
            sendKey(trainId, dataMapObj, getBoolean, setBoolean, "PrevAnnounceKey", prev);
            sendKey(trainId, dataMapObj, getBoolean, setBoolean, "AnnounceKey", ann);
            sendKey(trainId, dataMapObj, getBoolean, setBoolean, "EBKey", eb);
            sendKey(trainId, dataMapObj, getBoolean, setBoolean, "MelodyKey", melody);
            sendKey(trainId, dataMapObj, getBoolean, setBoolean, "TojimeKey", tojime);
            sendKey(trainId, dataMapObj, getBoolean, setBoolean, "FDCtrlKey", fd);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sendKey(int trainId, Object dataMap, Method getBoolean, Method setBoolean, String keyName,
            int keyCode) throws Exception {
        String fullKey = "suzu_keys_" + keyName;
        boolean currentInput = isKeyActuallyDown(keyCode);
        
        String mapKey = trainId + "_" + fullKey;
        Boolean lastSent = lastSentKeys.get(mapKey);

        if (currentInput) {
            if (lastSent == null || !lastSent) {
                // 押しはじめのみサーバーへ送信
                setBoolean.invoke(dataMap, fullKey, true, 3);
                lastSentKeys.put(mapKey, true);
            }
        } else if (lastSent != null && lastSent) {
            // 離したとき一度だけ送信
            setBoolean.invoke(dataMap, fullKey, false, 3);
            lastSentKeys.put(mapKey, false);
        }
    }

    private static void releaseKeysIfHeld(int trainId, Object dataMap, Method getBoolean, Method setBoolean) {
        String[] keyNames = { "BuzzerKey", "BellKey", "DoorControlKey", "NextAnnounceKey", "PrevAnnounceKey",
                "AnnounceKey", "EBKey", "MelodyKey", "TojimeKey", "FDCtrlKey" };
        for (String keyName : keyNames) {
            String fullKey = "suzu_keys_" + keyName;
            String mapKey = trainId + "_" + fullKey;
            Boolean lastSent = lastSentKeys.get(mapKey);
            if (lastSent != null && lastSent) {
                try {
                    setBoolean.invoke(dataMap, fullKey, false, 3);
                } catch (Exception e) {
                }
                lastSentKeys.put(mapKey, false);
            }
        }
    }

    /**
     * JS側から呼び出される、特定キー押下時のDataMap同期処理。
     */
    public static void syncDataOnKeyPress(int entityId, Object dataMap, int keyCode, String[] sourceKeys, String[] targetKeys) {
        try {
            boolean currentInput = isKeyActuallyDown(keyCode);
            
            String mapKey = entityId + "_syncKey_" + keyCode;
            Boolean lastSent = lastSentKeys.get(mapKey);
            
            if (currentInput) {
                if (lastSent == null || !lastSent) {
                    Method getInt = dataMap.getClass().getMethod("getInt", String.class);
                    Method setInt = dataMap.getClass().getMethod("setInt", String.class, int.class, int.class);
                    
                    for (int i = 0; i < sourceKeys.length && i < targetKeys.length; i++) {
                        int val = (Integer) getInt.invoke(dataMap, sourceKeys[i]);
                        CrossTieDMUtils.setThrottledInt(dataMap, getInt, setInt, targetKeys[i], val, 3);
                    }
                    
                    lastSentKeys.put(mapKey, true);
                }
            } else if (lastSent != null && lastSent) {
                lastSentKeys.put(mapKey, false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 指定されたkeyCodeに対応する物理キーが実際に押されているかを、
     * Vanilla環境とlwjgl3ify(JISキーボード等)環境の両方で正確に判定する。
     */
    private static boolean isKeyActuallyDown(int expectedKeyCode) {
        String[] expectedNames = getExpectedNamesForLwjgl2(expectedKeyCode);
        if (expectedNames == null) {
            return Keyboard.isKeyDown(expectedKeyCode);
        }

        // Vanilla判定: 現在の環境で keyCode そのままのキーが期待通りの名前なら、そのキーを判定する
        String currentName = Keyboard.getKeyName(expectedKeyCode);
        boolean isExpectedName = false;
        if (currentName != null) {
            for (String ex : expectedNames) {
                if (currentName.equalsIgnoreCase(ex)) {
                    isExpectedName = true;
                    break;
                }
            }
        }
        
        boolean isDown = false;
        if (isExpectedName) {
            isDown = Keyboard.isKeyDown(expectedKeyCode);
        }

        // lwjgl3ify環境用: 現在押されている全てのキーについて、その名前が期待する名前と一致すればtrue
        if (!isDown) {
            for (int i = 1; i < 256; i++) {
                if (Keyboard.isKeyDown(i)) {
                    String name = Keyboard.getKeyName(i);
                    if (name != null) {
                        for (String ex : expectedNames) {
                            if (name.equalsIgnoreCase(ex)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        
        return isDown;
    }

    /**
     * JS側で指定されるVanilla(LWJGL2)のキーコードに対応する、本来のキー名を返す。
     * lwjgl3ify環境でのJIS配列ズレを吸収するため、記号キーのみ定義。
     */
    private static String[] getExpectedNamesForLwjgl2(int keyCode) {
        switch (keyCode) {
            case Keyboard.KEY_LBRACKET: return new String[]{"LBRACKET", "["};
            case Keyboard.KEY_RBRACKET: return new String[]{"RBRACKET", "]"};
            case Keyboard.KEY_AT: return new String[]{"AT", "@"};
            case Keyboard.KEY_COLON: return new String[]{"COLON", ":"};
            case Keyboard.KEY_SEMICOLON: return new String[]{"SEMICOLON", ";"};
            case Keyboard.KEY_APOSTROPHE: return new String[]{"APOSTROPHE", "'"};
            case Keyboard.KEY_BACKSLASH: return new String[]{"BACKSLASH", "\\"};
            case Keyboard.KEY_MINUS: return new String[]{"MINUS", "-"};
            case Keyboard.KEY_EQUALS: return new String[]{"EQUALS", "=", "^"};
            case Keyboard.KEY_SLASH: return new String[]{"SLASH", "/"};
            case Keyboard.KEY_PERIOD: return new String[]{"PERIOD", "."};
            case Keyboard.KEY_COMMA: return new String[]{"COMMA", ","};
            case Keyboard.KEY_GRAVE: return new String[]{"GRAVE", "`"};
            // その他のキー（アルファベット等）はズレにくいため null を返す
            default: return null;
        }
    }
}
