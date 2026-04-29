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


    private static final String GTNHLIB_BLOCK_ICON_MIXIN =
            "com.gtnewhorizon.gtnhlib.mixins.early.models.MixinBlock_IconWrapper";
    private static final String ANGELICA_CTM_RENDER_BLOCKS_MIXIN =
            "com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.ctm.MixinRenderBlocks";
    private static final String MCPATCHER_GLASS_PANE_RENDERER =
            "com.prupe.mcpatcher.ctm.GlassPaneRenderer";



    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }


        if (isClass(transformedName, name, GTNHLIB_BLOCK_ICON_MIXIN, null)) {
            return patchGtnhLibBlockIconMixin(basicClass);
        }

        if (isClass(transformedName, name, ANGELICA_CTM_RENDER_BLOCKS_MIXIN, null)) {
            return patchAngelicaPaneIconRedirect(basicClass);
        }
        if (isClass(transformedName, name, MCPATCHER_GLASS_PANE_RENDERER, null)) {
            return patchGlassPaneRenderer(basicClass);
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
            if ("tweakPaneIcons".equals(method.name)
                    && "(Lnet/minecraft/client/renderer/RenderBlocks;Lnet/minecraft/block/Block;II)Lnet/minecraft/util/IIcon;"
                            .equals(method.desc)) {
                replaceMethodBody(method, angelicaPaneIconBody());
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

    private InsnList angelicaPaneIconBody() {
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        instructions.add(new VarInsnNode(Opcodes.ILOAD, 4));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "net/suzumiya/crosstie/compat/AngelicaPaneIconCompat",
                "getPaneIcon",
                "(Lnet/minecraft/block/Block;II)Lnet/minecraft/util/IIcon;",
                false));
        instructions.add(new InsnNode(Opcodes.ARETURN));
        return instructions;
    }

    private InsnList returnFalseBody() {
        InsnList instructions = new InsnList();
        instructions.add(new InsnNode(Opcodes.ICONST_0));
        instructions.add(new InsnNode(Opcodes.IRETURN));
        return instructions;
    }

    private void replaceMethodBody(MethodNode method, InsnList instructions) {
        for (int i = 0; i < method.instructions.size(); i++) {
            AbstractInsnNode instruction = method.instructions.get(i);
            if (instruction instanceof LabelNode || instruction instanceof LineNumberNode) {
                continue;
            }
            method.instructions.remove(instruction);
            i--;
        }
        method.instructions.clear();
        method.instructions.add(instructions);
        method.tryCatchBlocks.clear();
        method.localVariables.clear();
        method.maxStack = 6;
    }



    private byte[] writeClass(ClassNode classNode) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return writer.toByteArray();
    }
}
