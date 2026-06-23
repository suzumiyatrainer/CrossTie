package net.suzumiya.crosstie.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class CrossTieClassTransformer implements IClassTransformer {

    /**
     * GTNHLib 0.9.x で使用されていた {@code MixinBlock_IconWrapper} のクラス名。
     *
     * <p>
     * GTNHLib 0.10.0 ではこの Mixin が廃止され、{@code BlockIconTransformer} が
     * {@code Block} クラスに直接 ASM でフックを注入する方式に変わりました。
     * パッチ対象の {@code nhlib$getParticleIcon} メソッドはもはや存在しないため、
     * この定数は互換性チェック用に残しています。
     *
     * @deprecated GTNHLib 0.10.0 以降は不要
     */
    @Deprecated
    private static final String GTNHLIB_BLOCK_ICON_MIXIN = "com.gtnewhorizon.gtnhlib.mixins.early.models.MixinBlock_IconWrapper";
    private static final String ANGELICA_CTM_RENDER_BLOCKS_MIXIN = "com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.ctm.MixinRenderBlocks";
    private static final String ANGELICA_CTM_CC_RENDER_BLOCKS_MIXIN = "com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.ctm_cc.MixinRenderBlocks";
    private static final String ANGELICA_CTM_CC_RENDER_BLOCKS_NO_CC = "com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.ctm_cc.MixinRenderBlocksNoCC";
    private static final String ANGELICA_CTM_CC_RENDER_BLOCKS_NO_CTM = "com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.ctm_cc.MixinRenderBlocksNoCTM";
    private static final String MCPATCHER_GLASS_PANE_RENDERER = "com.prupe.mcpatcher.ctm.GlassPaneRenderer";

    /**
     * SplashProgress$3 (スプラッシュ画面の描画ループ) のクラス名。
     * enableFontRenderer=false 時に Angelica の GL リダイレクターが GL11.glEnable() を
     * キャッシュ更新のみの GLStateManager.glEnable() に書き換えてしまい、
     * テクスチャが描画されず画面が真っ黒になる問題を修正する。
     *
     * <p>
     * このクラスは Angelica より先に実行される CorePlugin ASM トランスフォーマで
     * 直接パッチするため、Angelica のバイトコードリダイレクターの影響を受けない。
     * </p>
     */
    private static final String SPLASH_PROGRESS_3 = "cpw.mods.fml.client.SplashProgress$3";

    /**
     * Hodgepodge の GuavaPooler クラス名。
     *
     * <p>
     * Hodgepodge の {@code NBTTagCompoundHashMapTransformer} が
     * {@code NBTTagCompound} を
     * 早期変換する際に {@code StringPooler$GuavaPooler} がロードされます。
     * これにより Mixin フェーズより前にクラスがロード済みとなり、
     * {@code MixinTargetAlreadyLoadedException} が発生します。
     * そのため Mixin ではなく ASM トランスフォーマーで対処します。
     */
    private static final String HODGEPODGE_GUAVA_POOLER = "com.mitchej123.hodgepodge.util.StringPooler$GuavaPooler";

    /**
     * NGTLib/RTM の {@code ScriptUtil} クラス名。
     * NashornScriptEngineFactory が利用不可な環境で {@code ScriptUtil.doScript(String)}
     * を {@link net.suzumiya.crosstie.compat.ScriptUtilFallback} にリダイレクトする。
     */
    private static final String SCRIPT_UTIL_CLASS = "jp.ngt.ngtlib.io.ScriptUtil";

    /**
     * Macro/Keybind Mod の {@code MacroModPermissions} クラス名。
     * 各メソッドから tamperCheck() 呼び出しを削除して、
     * パーミッションシステムを正常に動作させつつクラッシュを回避する。
     */
    private static final String MACRO_MOD_PERMISSIONS = "net.eq2online.macros.permissions.MacroModPermissions";

    /**
     * LiteLoader の {@code PermissionsManagerClient} クラス名。
     * tamperCheck() を no-op にして Macro / Keybind Mod のクラッシュを回避する。
     */
    private static final String PERMISSIONS_MANAGER_CLIENT = "com.mumfrey.liteloader.permissions.PermissionsManagerClient";

    // AngelicaConfig の ASM パッチは行わない。
    // GTNHLib の config システムが static フィールドを初期化時にリセットするため、
    // クラスロード時のフィールド書き換えは効果がありません。
    // 代わりに CrossTieCorePlugin で設定ファイルを直接書き換えて対応します。

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }

        // GTNHLib 0.9.x: MixinBlock_IconWrapper へのパッチ
        // GTNHLib 0.10.0 以降では MixinBlock_IconWrapper が廃止されたため、
        // クラスが見つからなければ isClass が false を返すので安全にスキップされる。
        if (isClass(transformedName, name, GTNHLIB_BLOCK_ICON_MIXIN, null)) {
            return patchGtnhLibBlockIconMixin(basicClass);
        }

        if (isClass(transformedName, name, ANGELICA_CTM_RENDER_BLOCKS_MIXIN, null)
                || isClass(transformedName, name, ANGELICA_CTM_CC_RENDER_BLOCKS_MIXIN, null)
                || isClass(transformedName, name, ANGELICA_CTM_CC_RENDER_BLOCKS_NO_CC, null)
                || isClass(transformedName, name, ANGELICA_CTM_CC_RENDER_BLOCKS_NO_CTM, null)) {
            return patchAngelicaPaneIconRedirect(basicClass);
        }
        if (isClass(transformedName, name, MCPATCHER_GLASS_PANE_RENDERER, null)) {
            return patchGlassPaneRenderer(basicClass);
        }

        // Hodgepodge: GuavaPooler の getString() を String.intern() にリダイレクト
        // Mixin では MixinTargetAlreadyLoadedException が発生するため ASM で対処する
        if (isClass(transformedName, name, HODGEPODGE_GUAVA_POOLER, null)) {
            return patchHodgepodgeGuavaPooler(basicClass);
        }

        // NGTLib/RTM ScriptUtil: NashornScriptEngineFactory が利用不可な環境で
        // ScriptUtil.doScript(String) を ScriptUtilFallback にリダイレクト
        if (isClass(transformedName, name, SCRIPT_UTIL_CLASS, null)) {
            return patchScriptUtil(basicClass);
        }

        // Macro/Keybind Mod MacroModPermissions: tamperCheck() 呼び出しを削除
        // パーミッションシステムを正常に動作させつつクラッシュを回避する
        if (isClass(transformedName, name, MACRO_MOD_PERMISSIONS, null)) {
            return patchMacroModPermissions(basicClass);
        }

        // LiteLoader PermissionsManagerClient: tamperCheck() を no-op にする
        if (isClass(transformedName, name, PERMISSIONS_MANAGER_CLIENT, null)) {
            return patchPermissionsManagerClientTamperCheck(basicClass);
        }

        // SplashProgress$3 (スプラッシュ描画スレッド): Angelica の GL リダイレクターによる
        // テクスチャ状態のキャッシュ問題を回避するため、ASM で run() の先頭に
        // リフレクション経由の glEnable(GL_TEXTURE_2D) + glColor4f(1,1,1,1) を注入する
        if (isClass(transformedName, name, SPLASH_PROGRESS_3, null)) {
            return patchSplashProgress3(basicClass);
        }

        // AngelicaConfig は ASM パッチしない。
        // GTNHLib の config システムが static フィールドを初期化時にリセットするため、
        // クラスロード時のフィールド書き換えは効果がありません。
        // CrossTieCorePlugin.injectData() で設定ファイルを直接書き換えて対応します。

        return basicClass;
    }

    private boolean isClass(String transformedName, String name, String dottedName, String internalName) {
        return dottedName.equals(transformedName)
                || dottedName.equals(name)
                || dottedName.replace('.', '/').equals(transformedName)
                || dottedName.replace('.', '/').equals(name)
                || (internalName != null && (internalName.equals(transformedName) || internalName.equals(name)));
    }

    /**
     * GTNHLib 0.9.x 向けパッチ: {@code MixinBlock_IconWrapper.nhlib$getParticleIcon} を
     * {@link net.suzumiya.crosstie.compat.GtnhLibIconCompat#getParticleIcon}
     * にリダイレクト。
     *
     * <p>
     * GTNHLib 0.10.0 以降ではこのメソッドが呼ばれることはありません
     * ({@code MixinBlock_IconWrapper} クラス自体が存在しないため)。
     */
    private byte[] patchGtnhLibBlockIconMixin(byte[] basicClass) {
        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);

        boolean changed = false;
        for (Object methodObject : classNode.methods) {
            MethodNode method = (MethodNode) methodObject;
            if ("nhlib$getParticleIcon".equals(method.name)
                    && "(Lnet/minecraft/world/IBlockAccess;IIII)Lnet/minecraft/util/IIcon;".equals(method.desc)) {
                replaceMethodBody(method, gtnhLibParticleIconBody());
                changed = true;
            }
        }

        return changed ? writeClass(classNode) : basicClass;
    }

    private byte[] patchAngelicaPaneIconRedirect(byte[] basicClass) {
        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);

        boolean changed = false;
        for (Object methodObject : classNode.methods) {
            MethodNode method = (MethodNode) methodObject;
            if (("tweakPaneIcons".equals(method.name) || "func_147787_a".equals(method.name)
                    || "func_147793_a".equals(method.name) || "func_147777_a".equals(method.name))
                    && ("(Lnet/minecraft/client/renderer/RenderBlocks;Lnet/minecraft/block/Block;II)Lnet/minecraft/util/IIcon;"
                            .equals(method.desc)
                            || "(Lnet/minecraft/block/Block;II)Lnet/minecraft/util/IIcon;".equals(method.desc)
                            || "(Lnet/minecraft/block/Block;Lnet/minecraft/world/IBlockAccess;IIII)Lnet/minecraft/util/IIcon;"
                                    .equals(method.desc)
                            || "(Lnet/minecraft/block/Block;I)Lnet/minecraft/util/IIcon;".equals(method.desc))) {
                replaceMethodBody(method, angelicaPaneIconBody(method.name, method.desc));
                changed = true;
            }
        }

        return changed ? writeClass(classNode) : basicClass;
    }

    private byte[] patchGlassPaneRenderer(byte[] basicClass) {
        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);

        boolean changed = false;
        for (Object methodObject : classNode.methods) {
            MethodNode method = (MethodNode) methodObject;
            if ("setupIcons".equals(method.name)
                    && "(Lnet/minecraft/client/renderer/RenderBlocks;Lnet/minecraft/block/Block;Lnet/minecraft/util/IIcon;III)Z"
                            .equals(method.desc)) {
                replaceMethodBody(method, returnFalseBody());
                changed = true;
            }
        }

        return changed ? writeClass(classNode) : basicClass;
    }

    /**
     * Hodgepodge {@code StringPooler$GuavaPooler} へのパッチ。
     *
     * <p>
     * Hodgepodge の GuavaPooler は {@code com.google.common.collect.Interner} を使って
     * 文字列をインターンします。Guava が複数のクラスローダー (例: LaunchClassLoader と
     * AppClassLoader) でロードされると次のような {@code LinkageError} が発生します:
     * 
     * <pre>
     *   loader constraint violation: loader previously initiated loading
     *   for a different type with name "com/google/common/collect/Interner"
     * </pre>
     *
     * <p>
     * このパッチは {@code getString(String)} の本体を {@code s.intern()} に差し替えて
     * Guava の {@code Interner} を完全に回避します。
     *
     * <p>
     * <b>なぜ Mixin ではなく ASM トランスフォーマーを使うのか:</b><br>
     * Hodgepodge の {@code NBTTagCompoundHashMapTransformer} が
     * {@code NBTTagCompound} を
     * 早期変換する過程で {@code StringPooler$GuavaPooler} が Mixin フェーズより前に
     * ロードされます。その結果 Mixin で介入しようとすると
     * {@code MixinTargetAlreadyLoadedException} が発生して適用に失敗します。
     * ASM トランスフォーマーはクラスローダーがクラスを読み込む時点で動作するため、
     * このタイミング問題を回避できます。
     */
    private byte[] patchHodgepodgeGuavaPooler(byte[] basicClass) {
        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);

        boolean changed = false;
        for (Object methodObject : classNode.methods) {
            MethodNode method = (MethodNode) methodObject;
            if ("getString".equals(method.name)
                    && "(Ljava/lang/String;)Ljava/lang/String;".equals(method.desc)) {
                replaceMethodBody(method, stringInternBody());
                changed = true;
            }
        }

        if (changed) {
            System.out.println(
                    "[CrossTie] Patched StringPooler$GuavaPooler.getString() -> String.intern()"
                            + " (Guava Interner loader constraint workaround)");
        }
        return changed ? writeClass(classNode) : basicClass;
    }

    /**
     * NGTLib/RTM {@code ScriptUtil.doScript(String)} を
     * {@link net.suzumiya.crosstie.compat.ScriptUtilFallback#doScript(String)}
     * にリダイレクト。
     *
     * <p>
     * 元の実装は直接 {@code jdk.nashorn.api.scripting.NashornScriptEngineFactory}
     * を参照するが、このクラスが存在しない環境でも動作するよう、
     * ASM でメソッド本体を差し替える。
     *
     * <p>
     * <b>なぜ Mixin ではなく ASM トランスフォーマーを使うのか:</b><br>
     * Mixin は {@code shouldApplyMixin} で条件判定されるが、CrossTieMixinPlugin
     * が正しくロードされない場合がある。ASM トランスフォーマーは
     * {@link net.suzumiya.crosstie.asm.CrossTieCorePlugin} で
     * 登録されているため、必ず動作する。
     */
    private byte[] patchScriptUtil(byte[] basicClass) {
        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);

        boolean changed = false;
        for (Object methodObject : classNode.methods) {
            MethodNode method = (MethodNode) methodObject;
            if ("doScript".equals(method.name)
                    && "(Ljava/lang/String;)Ljavax/script/ScriptEngine;".equals(method.desc)) {
                replaceMethodBody(method, scriptUtilFallbackBody());
                changed = true;
            }
        }

        if (changed) {
            System.out.println("[CrossTie] Patched ScriptUtil.doScript(String) -> ScriptUtilFallback.doScript(String)");
        }
        return changed ? writeClass(classNode) : basicClass;
    }

    /**
     * Macro/Keybind Mod {@code MacroModPermissions} をパッチ。
     *
     * <p>
     * 元の実装は、パーミッション操作時に {@code PermissionsManagerClient.tamperCheck()}
     * を呼び出します。サーバー接続時に60秒間 tick されていない場合に
     * {@code IllegalStateException} がスローされます。
     *
     * <p>
     * {@code refreshPermissions} は try/catch を含む複雑なバイトコードのため、
     * {@code tamperCheck()} 呼び出しだけを削除すると stack map と不整合になり
     * {@code VerifyError} が発生します。そのためメソッド全体を no-op に置き換えます。
     * 他メソッドでは {@code tamperCheck()} 呼び出しのみを削除します。
     */
    private byte[] patchMacroModPermissions(byte[] basicClass) {
        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);

        int removedCalls = 0;
        for (Object methodObject : classNode.methods) {
            MethodNode method = (MethodNode) methodObject;

            // メソッド名（refreshPermissions）の縛りを消去し、全メソッドを対象にします。
            // これにより、registerPermission 等に含まれる tamperCheck も確実に捕まえられます。
            for (int i = 0; i < method.instructions.size(); i++) {
                if (method.instructions.get(i) instanceof MethodInsnNode) {
                    MethodInsnNode insn = (MethodInsnNode) method.instructions.get(i);

                    if ("tamperCheck".equals(insn.name)
                            && "com/mumfrey/liteloader/permissions/PermissionsManagerClient".equals(insn.owner)) {

                        // 呼び出し命令（INVOKEVIRTUAL）を POP に置き換える
                        method.instructions.set(insn, new InsnNode(Opcodes.POP));
                        removedCalls++;
                    }
                }
            }
        }

        if (removedCalls > 0) {
            System.out.println(
                    "[CrossTie] Patched MacroModPermissions -> removed " + removedCalls + " tamperCheck() call(s)");
            return writeClass(classNode);
        }
        return basicClass;
    }

    /**
     * LiteLoader {@code PermissionsManagerClient.tamperCheck()} を no-op にする。
     *
     * <p>
     * Mixin が ModDetector 判定の都合で適用されない場合の保険として、
     * coremod ASM でも tamperCheck を無効化します。
     */
    private byte[] patchPermissionsManagerClientTamperCheck(byte[] basicClass) {
        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);

        boolean changed = false;
        for (Object methodObject : classNode.methods) {
            MethodNode method = (MethodNode) methodObject;
            if ("tamperCheck".equals(method.name) && "()V".equals(method.desc)) {
                replaceMethodBody(method, emptyVoidReturnBody());
                changed = true;
            }
        }

        if (changed) {
            System.out.println("[CrossTie] Patched PermissionsManagerClient.tamperCheck() -> no-op");
        }
        return changed ? writeClass(classNode) : basicClass;
    }

    /**
     * {@code ScriptUtilFallback.doScript(String)} を呼ぶだけのメソッド本体を生成します。
     *
     * <pre>
     *   ALOAD_0   // パラメータ script (String)
     *   INVOKESTATIC net/suzumiya/crosstie/compat/ScriptUtilFallback.doScript (Ljava/lang/String;)Ljavax/script/ScriptEngine;
     *   ARETURN
     * </pre>
     */
    private InsnList scriptUtilFallbackBody() {
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "net/suzumiya/crosstie/compat/ScriptUtilFallback",
                "doScript",
                "(Ljava/lang/String;)Ljavax/script/ScriptEngine;",
                false));
        instructions.add(new InsnNode(Opcodes.ARETURN));
        return instructions;
    }

    private InsnList gtnhLibParticleIconBody() {
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        instructions.add(new VarInsnNode(Opcodes.ILOAD, 4));
        instructions.add(new VarInsnNode(Opcodes.ILOAD, 5));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "net/suzumiya/crosstie/compat/GtnhLibIconCompat",
                "getParticleIcon",
                "(Ljava/lang/Object;Lnet/minecraft/world/IBlockAccess;IIII)Lnet/minecraft/util/IIcon;",
                false));
        instructions.add(new InsnNode(Opcodes.ARETURN));
        return instructions;
    }

    private InsnList angelicaPaneIconBody(String methodName, String methodDesc) {
        InsnList instructions = new InsnList();
        if ("(Lnet/minecraft/client/renderer/RenderBlocks;Lnet/minecraft/block/Block;II)Lnet/minecraft/util/IIcon;"
                .equals(methodDesc)) {
            instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
            instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
            instructions.add(new VarInsnNode(Opcodes.ILOAD, 4));
            instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "net/suzumiya/crosstie/compat/AngelicaPaneIconCompat",
                    "getPaneIcon",
                    "(Lnet/minecraft/block/Block;II)Lnet/minecraft/util/IIcon;",
                    false));
        } else if ("(Lnet/minecraft/block/Block;II)Lnet/minecraft/util/IIcon;".equals(methodDesc)) {
            instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
            instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
            instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "net/suzumiya/crosstie/compat/AngelicaPaneIconCompat",
                    "getPaneIcon",
                    "(Lnet/minecraft/block/Block;II)Lnet/minecraft/util/IIcon;",
                    false));
        } else if ("(Lnet/minecraft/block/Block;Lnet/minecraft/world/IBlockAccess;IIII)Lnet/minecraft/util/IIcon;"
                .equals(methodDesc)) {
            instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
            instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
            instructions.add(new VarInsnNode(Opcodes.ILOAD, 4));
            instructions.add(new VarInsnNode(Opcodes.ILOAD, 5));
            instructions.add(new VarInsnNode(Opcodes.ILOAD, 6));
            instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "net/suzumiya/crosstie/compat/AngelicaPaneIconCompat",
                    "getPaneIcon",
                    "(Lnet/minecraft/block/Block;Lnet/minecraft/world/IBlockAccess;IIII)Lnet/minecraft/util/IIcon;",
                    false));
        } else {
            instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
            instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "net/suzumiya/crosstie/compat/AngelicaPaneIconCompat",
                    "getPaneIcon",
                    "(Lnet/minecraft/block/Block;I)Lnet/minecraft/util/IIcon;",
                    false));
        }
        instructions.add(new InsnNode(Opcodes.ARETURN));
        return instructions;
    }

    private InsnList returnFalseBody() {
        InsnList instructions = new InsnList();
        instructions.add(new InsnNode(Opcodes.ICONST_0));
        instructions.add(new InsnNode(Opcodes.IRETURN));
        return instructions;
    }

    private InsnList emptyVoidReturnBody() {
        InsnList instructions = new InsnList();
        instructions.add(new InsnNode(Opcodes.RETURN));
        return instructions;
    }

    /**
     * {@code String.intern()} を呼ぶだけのメソッド本体を生成します。
     *
     * <pre>
     *   ALOAD_1   // パラメータ s
     *   INVOKEVIRTUAL java/lang/String.intern ()Ljava/lang/String;
     *   ARETURN
     * </pre>
     */
    private InsnList stringInternBody() {
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/String",
                "intern",
                "()Ljava/lang/String;",
                false));
        instructions.add(new InsnNode(Opcodes.ARETURN));
        return instructions;
    }

    /**
     * SplashProgress$3 の run() メソッド先頭にリフレクション経由の GL 状態リセットを注入する。
     *
     * <p>
     * Angelica のバイトコードリダイレクターは GL11.glEnable() の呼び出しを
     * GLStateManager.glEnable() に書き換える。このパッチは {@link SplashGLFix} を
     * 介してリフレクションで GL11 を呼び出すため、リダイレクターの影響を受けない。
     * </p>
     */
    private byte[] patchSplashProgress3(byte[] basicClass) {
        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);

        boolean changed = false;
        for (Object methodObject : classNode.methods) {
            MethodNode method = (MethodNode) methodObject;
            org.objectweb.asm.tree.AbstractInsnNode[] instructions = method.instructions.toArray();
            for (org.objectweb.asm.tree.AbstractInsnNode insn : instructions) {
                if (!(insn instanceof MethodInsnNode))
                    continue;
                MethodInsnNode minsn = (MethodInsnNode) insn;

                // setGL(): Display.getDrawable().makeCurrent() の直後に注入（GLコンテキスト確立後）
                boolean isMakeCurrent = "org/lwjgl/opengl/Drawable".equals(minsn.owner)
                        && "makeCurrent".equals(minsn.name);
                // run(): Display.update() の直後にも注入（毎フレーム先頭）
                boolean isUpdate = "org/lwjgl/opengl/Display".equals(minsn.owner)
                        && "update".equals(minsn.name)
                        && "()V".equals(minsn.desc);
                // setGL(): glClearColor() の直前に clear color cache を dirty にする
                boolean isClearColor = "org/lwjgl/opengl/GL11".equals(minsn.owner)
                        && "glClearColor".equals(minsn.name)
                        && "(FFFF)V".equals(minsn.desc);
                // drawBar()/Angelica memory bar: text drawing just before binding the splash font texture
                boolean isDrawString = "drawString".equals(minsn.name)
                        && "(Ljava/lang/String;III)I".equals(minsn.desc);
                // run(): logo/forge texture bind can also be skipped by Angelica's cached binding
                boolean isSplashTextureBind = "cpw/mods/fml/client/SplashProgress$Texture".equals(minsn.owner)
                        && "bind".equals(minsn.name)
                        && "()V".equals(minsn.desc);

                if (isMakeCurrent || isUpdate || isClearColor || isDrawString || isSplashTextureBind) {
                    InsnList patch = new InsnList();
                    patch.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                            "net/suzumiya/crosstie/util/SplashGLFix",
                            isClearColor || isUpdate ? "markSplashStateDirty" : "prepareTexturedSplashDraw",
                            "()V",
                            false));
                    if (isClearColor || isDrawString || isSplashTextureBind) {
                        method.instructions.insertBefore(insn, patch);
                    } else {
                        method.instructions.insert(insn, patch);
                    }
                    changed = true;
                    System.out.println("[CrossTie] SplashProgress$3." + method.name + "() patched "
                            + (isClearColor || isDrawString || isSplashTextureBind ? "before " : "after ")
                            + minsn.name + "()");
                }
            }
        }
        return changed ? writeClass(classNode) : basicClass;
    }

    private void replaceMethodBody(MethodNode method, InsnList instructions) {
        method.access &= ~Opcodes.ACC_ABSTRACT;
        method.access &= ~Opcodes.ACC_NATIVE;
        method.instructions.clear();
        method.instructions.add(instructions);
        method.tryCatchBlocks.clear();
        method.localVariables.clear();
        // メソッド本体を完全に入れ替えるため、古い StackMapTable のフレーム情報
        // (FrameNodeとしてinstructionsに残っていたもの)は instructions.clear() で
        // 一緒に消える。maxStack/maxLocals および新しいStackMapTableは
        // writeClass() 側で ClassWriter.COMPUTE_FRAMES により再計算される。
    }

    private byte[] writeClass(ClassNode classNode) {
        // COMPUTE_MAXS では maxStack/maxLocals のみが再計算され、StackMapTable
        // (Java 7+のフレーム情報)は再生成されない。バイトコードを書き換えると
        // 古いフレーム情報と実際のスタック状態が食い違い、
        // VerifyError: Instruction type does not match stack map の原因になる。
        // COMPUTE_FRAMES を使うことで StackMapTable を実際のバイトコードから
        // 正しく再計算させる(maxStack/maxLocalsもこちらで計算される)。
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                try {
                    return super.getCommonSuperClass(type1, type2);
                } catch (Throwable t) {
                    // COMPUTE_FRAMES はフレーム計算のために型の継承関係解決を行うが、
                    // クラスローダーの都合で解決できないMOD/ゲームクラスが
                    // 含まれる場合に ClassNotFoundException 等で失敗することがある。
                    // その場合は安全側に倒して Object を共通の親クラスとして返す。
                    System.err.println("[CrossTie] getCommonSuperClass fallback for "
                            + type1 + " / " + type2 + ": " + t);
                    return "java/lang/Object";
                }
            }
        };
        try {
            classNode.accept(writer);
        } catch (Exception e) {
            System.err.println("[CrossTie] Failed to write class: " + classNode.name);
            e.printStackTrace();
            return null;
        }
        return writer.toByteArray();
    }
}
