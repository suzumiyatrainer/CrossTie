package net.suzumiya.crosstie.client.sound;

import net.minecraft.client.audio.ITickableSound;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.suzumiya.crosstie.api.sound.SoundManager;
import net.suzumiya.crosstie.api.sound.TrainSoundContext;

public class TrainLineSegmentSound extends PositionedSound implements ITickableSound {

    private final EntityTrainBase train;
    private final float length;
    private final float maxRadius;
    private final byte type; // 0: InCar, 1: Exterior
    private boolean donePlaying = false;
    private int crosstie$tickCounter = 0;

    public TrainLineSegmentSound(EntityTrainBase train, float length, float maxRadius, String soundName, byte type) {
        super(new ResourceLocation(soundName));
        this.train = train;
        this.length = length;
        this.maxRadius = maxRadius;
        this.type = type;
        this.volume = 0.0F; // 初期値は0、毎チック計算
        this.field_147663_c = 1.0F; // pitch
        this.repeat = false;
        
        // TrainSoundContext がない場合は作成（フェイルセーフ）
        if (SoundManager.getInstance().getContext(train) == null) {
             SoundManager.getInstance().onSpeedChanged(train, train.getSpeed());
        }
        
        update(); // 初回計算
    }

    @Override
    public boolean isDonePlaying() {
        return donePlaying || train.isDead;
    }

    @Override
    public void update() {
        if (this.train.isDead) {
            this.donePlaying = true;
            return;
        }

        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return;

        // 車両のYは常に +5.0
        this.yPosF = (float) (train.posY + 5.0);

        // 線分ABの計算 (長さlength, 向きはtrain.rotationYaw)
        // MCのYawは +Z が 0度, +X が -90度 などの独自仕様だが、ここでは標準的な三角関数を用いる
        double yawRad = Math.toRadians(-train.rotationYaw);
        double halfL = length / 2.0;
        
        double dirX = -Math.sin(yawRad);
        double dirZ = Math.cos(yawRad);

        double Ax = train.posX + dirX * halfL;
        double Az = train.posZ + dirZ * halfL;
        double Bx = train.posX - dirX * halfL;
        double Bz = train.posZ - dirZ * halfL;

        // プレイヤーから線分ABへの最短点 C を求める
        double[] closest = getClosestPointOnSegment(player.posX, player.posZ, Ax, Az, Bx, Bz);
        this.xPosF = (float) closest[0];
        this.zPosF = (float) closest[1];

        // プレイヤーと中心線との距離
        double dx = player.posX - this.xPosF;
        double dz = player.posZ - this.zPosF;
        double d_center = Math.sqrt(dx * dx + dz * dz);
        
        // 車体側面からの距離
        double d_side = Math.max(0, d_center - 1.5);

        // プレイヤーが車体の右側か左側かの判定
        // 車両の進行方向ベクトル (dirX, dirZ) と、車両中心からプレイヤーへのベクトル (-dx, -dz) の外積で判定
        double crossProduct = dirX * dz - dirZ * dx;
        boolean isLeft = crossProduct > 0; // 車両の向き定義により逆になる場合があるが、相対的左右として扱う

        TrainSoundContext ctx = SoundManager.getInstance().getContext(train);
        byte doorState = train.getTrainStateData(4); // 4 = TrainStateType.State_Door.id
        ctx.setDoorState(doorState); // クライアント側のコンテキスト状態も更新（これにより lastOpenSide も自動更新）
        
        boolean isDoorOpenLeft = (doorState & 1) == 1;
        boolean isDoorOpenRight = (doorState & 2) == 2;
        boolean areBothDoorsClosed = (!isDoorOpenLeft && !isDoorOpenRight);
        
        TrainSoundContext.DoorSide lastOpen = ctx.getLastOpenSide();

        if (type == 0) {
            // ----- IN CAR (車内放送) -----
            if (d_center <= 1.5) {
                // 車内（直径3ブロック = 半径1.5m）は常に 1.0F
                this.volume = 1.0F;
            } else if (areBothDoorsClosed) {
                // ドアが閉まっている時は完全遮音（車外に漏れない）
                this.volume = 0.0F;
            } else {
                // 開いている側の外側かどうか
                if ((isLeft && isDoorOpenLeft) || (!isLeft && isDoorOpenRight) || (isDoorOpenLeft && isDoorOpenRight)) {
                    // 開いている側は 0.8F 基準で maxRadius にかけて線形減衰 (若干速めの減衰など、JS側指定による)
                    this.volume = calculateVolumeFade(d_side, 0.8F, this.maxRadius);
                } else {
                    // 閉まっている側の外側は完全遮音
                    this.volume = 0.0F;
                }
            }
        } else if (type == 1) {
            // ----- EXTERIOR (車外スピーカー) -----
            if (d_center <= 1.5) {
                // 車内：ドアが開いているなら 0.5F、閉まっていても 0.1F で聞こえる
                this.volume = (isDoorOpenLeft || isDoorOpenRight) ? 0.5F : 0.1F;
            } else {
                // 車外：ドアの状態に関わらず常に聞こえる (最大可聴範囲 maxRadius は駅用と同一ロジック)
                this.volume = calculateVolumeFade(d_side, 0.8F, this.maxRadius);
            }
        }

        if (net.suzumiya.crosstie.CrossTieConfig.enableSoundDebug && this.crosstie$tickCounter++ % 20 == 0) {
            System.out.println("[CrossTie-Debug] SoundUpdate. trainId=" + train.getEntityId() + 
                ", type=" + type +
                ", d_center=" + d_center + 
                ", d_side=" + d_side + 
                ", isLeft=" + isLeft + 
                ", doorState=" + ctx.getDoorState() + 
                ", lastOpen=" + lastOpen + 
                ", volume=" + this.volume);
        }
    }

    private float calculateVolumeFade(double d_side, float baseVolume, float maxRadius) {
        if (d_side >= maxRadius) return 0.0F;
        return baseVolume * (float) (1.0 - (d_side / maxRadius));
    }

    private double[] getClosestPointOnSegment(double px, double pz, double ax, double az, double bx, double bz) {
        double abx = bx - ax;
        double abz = bz - az;
        double apx = px - ax;
        double apz = pz - az;

        double t = (apx * abx + apz * abz) / (abx * abx + abz * abz);
        t = Math.max(0, Math.min(1, t)); // 線分内にクランプ

        return new double[]{ ax + t * abx, az + t * abz };
    }
}
