package net.suzumiya.crosstie.mixins.customnpc;

import noppes.npcs.config.ConfigScript;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.script.ScriptEngineManager;

/**
 * CustomNPC+ の ScriptController コンストラクタ内の
 * {@code new ScriptEngineManager()} をインターセプトし、
 * Java 8 環境でのクラッシュを防ぐ。
 *
 * <h3>問題</h3>
 * {@code ScriptController.<init>} は L76 の {@code isScriptingEnabled()} チェックより
 * 前の L75 で {@code new ScriptEngineManager()} を実行する。
 * このコンストラクタは Java SPI (ServiceLoader) 経由で全 ScriptEngineFactory を
 * 即座にロードするため、CustomNPC+ JAR に同梱された Java 11+ コンパイル済みの
 * {@code org.openjdk.nashorn.NashornScriptEngineFactory} が
 * {@link UnsupportedClassVersionError} を引き起こしてサーバーをクラッシュさせる。
 *
 * <h3>修正</h3>
 * CustomNPC+ 自身の {@link ConfigScript#ScriptingEnabled} が {@code false}（デフォルト）の場合、
 * {@code null} ClassLoader を指定した {@link ScriptEngineManager} を返す。
 * これにより SPI スキャンがブートストラップ CL に限定され、
 * アプリケーション CL 上のサードパーティ SPI 実装（Java 11+ 版 Nashorn）はロードされない。
 *
 * {@code Enable Scripting = true} に明示設定した場合のみ通常の完全初期化を実行する。
 */
@Mixin(targets = "noppes.npcs.controllers.ScriptController", remap = false)
public class ScriptControllerInitMixin {

    /**
     * {@code ScriptController} コンストラクタ内の {@code new ScriptEngineManager()} を
     * 条件付きで安全な初期化に差し替える。
     *
     * <ul>
     *   <li>{@link ConfigScript#ScriptingEnabled} が {@code false}（デフォルト）:<br>
     *       {@code new ScriptEngineManager(null)} を返す。
     *       {@code null} ClassLoader を渡すと ServiceLoader はブートストラップ CL のみを使うため、
     *       アプリケーション CL 上の Java 11+ 版 {@code NashornScriptEngineFactory} がロードされない。
     *   </li>
     *   <li>{@link ConfigScript#ScriptingEnabled} が {@code true}:<br>
     *       通常の {@code new ScriptEngineManager()} を返す（Java 11+ 環境向け）。
     *   </li>
     * </ul>
     *
     * @return 条件に応じた {@link ScriptEngineManager} インスタンス
     */
    @Redirect(
        method = "<init>",
        at = @At(
            value = "NEW",
            target = "javax/script/ScriptEngineManager"
        ),
        remap = false
    )
    private ScriptEngineManager crosstie$safeScriptEngineManagerInit() {
        if (!ConfigScript.ScriptingEnabled) {
            // Enable Scripting = false の場合は SPI スキャンを回避する。
            // ClassLoader = null を渡すと ServiceLoader はブートストラップ CL のみを使うため、
            // アプリケーション CL 上の Java 11+ 版 NashornScriptEngineFactory がロードされない。
            return new ScriptEngineManager(null);
        }
        // Enable Scripting = true の場合のみ通常の ScriptEngineManager を使用する。
        return new ScriptEngineManager();
    }
}