package net.suzumiya.crosstie.asm;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class CrossTieClassTransformer implements IClassTransformer {


    /**
     * GTNHLib 0.9.x で使用されていた {@code MixinBlock_IconWrapper} のクラス名。
     *
     * <p>GTNHLib 0.10.0 ではこの Mixin が廃止され、{@code BlockIconTransformer} が
     * {@code Block} クラスに直接 ASM でフックを注入する方式に変わりました。
     * パッチ対象の {@code nhlib$getParticleIcon} メソッドはもはや存在しないため、
     * この定数は互換性チェック用に残しています。
     *
     * @deprecated GTNHLib 0.10.0 以降は不要
     */
    @Deprecated
    private static final String GTNHLIB_BLOCK_ICON_MIXIN =
            "com.gtnewhorizon.gtnhlib.mixins.early.models.MixinBlock_IconWrapper";
    private static final String ANGELICA_CTM_RENDER_BLOCKS_MIXIN =
            "com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.ctm.MixinRenderBlocks";
    private static final String ANGELICA_CTM_CC_RENDER_BLOCKS_MIXIN =
            "com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.ctm_cc.MixinRenderBlocks";
    private static final String ANGELICA_CTM_CC_RENDER_BLOCKS_NO_CC =
            "com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.ctm_cc.MixinRenderBlocksNoCC";
    private static final String ANGELICA_CTM_CC_RENDER_BLOCKS_NO_CTM =
            "com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.ctm_cc.MixinRenderBlocksNoCTM";
    private static final String MCPATCHER_GLASS_PANE_RENDERER =
            "com.prupe.mcpatcher.ctm.GlassPaneRenderer";

    /**
     * Hodgepodge の GuavaPooler クラス名。
     *
     * <p>Hodgepodge の {@code NBTTagCompoundHashMapTransformer} が {@code NBTTagCompound} を
     * 早期変換する際に {@code StringPooler$GuavaPooler} がロードされます。
     * これにより Mixin フェーズより前にクラスがロード済みとなり、
     * {@code MixinTargetAlreadyLoadedException} が発生します。
     * そのため Mixin ではなく ASM トランスフォーマーで対処します。
     */
    private static final String HODGEPODGE_GUAVA_POOLER =
            "com.mitchej123.hodgepodge.util.StringPooler$GuavaPooler";



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
     * {@link net.suzumiya.crosstie.compat.GtnhLibIconCompat#getParticleIcon} にリダイレクト。
     *
     * <p>GTNHLib 0.10.0 以降ではこのメソッドが呼ばれることはありません
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
                    && ("(Lnet/minecraft/client/renderer/RenderBlocks;Lnet/minecraft/block/Block;II)Lnet/minecraft/util/IIcon;".equals(method.desc)
                            || "(Lnet/minecraft/block/Block;II)Lnet/minecraft/util/IIcon;".equals(method.desc)
                            || "(Lnet/minecraft/block/Block;Lnet/minecraft/world/IBlockAccess;IIII)Lnet/minecraft/util/IIcon;".equals(method.desc)
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
     * <p>Hodgepodge の GuavaPooler は {@code com.google.common.collect.Interner} を使って
     * 文字列をインターンします。Guava が複数のクラスローダー (例: LaunchClassLoader と
     * AppClassLoader) でロードされると次のような {@code LinkageError} が発生します:
     * <pre>
     *   loader constraint violation: loader previously initiated loading
     *   for a different type with name "com/google/common/collect/Interner"
     * </pre>
     *
     * <p>このパッチは {@code getString(String)} の本体を {@code s.intern()} に差し替えて
     * Guava の {@code Interner} を完全に回避します。
     *
     * <p><b>なぜ Mixin ではなく ASM トランスフォーマーを使うのか:</b><br>
     * Hodgepodge の {@code NBTTagCompoundHashMapTransformer} が {@code NBTTagCompound} を
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
        if ("(Lnet/minecraft/client/renderer/RenderBlocks;Lnet/minecraft/block/Block;II)Lnet/minecraft/util/IIcon;".equals(methodDesc)) {
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
        } else if ("(Lnet/minecraft/block/Block;Lnet/minecraft/world/IBlockAccess;IIII)Lnet/minecraft/util/IIcon;".equals(methodDesc)) {
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

    private void replaceMethodBody(MethodNode method, InsnList instructions) {
        method.access &= ~Opcodes.ACC_ABSTRACT;
        method.access &= ~Opcodes.ACC_NATIVE;
        method.instructions.clear();
        method.instructions.add(instructions);
        method.tryCatchBlocks.clear();
        method.localVariables.clear();
        // method.maxStack = 6; // 削除：COMPUTE_MAXSに任せる
    }



    private byte[] writeClass(ClassNode classNode) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
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
