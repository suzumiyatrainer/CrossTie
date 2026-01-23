package net.suzumiya.crosstie.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;

/**
 * Guava Compatibility Fixer (Standalone Version)
 * JourneyMapなどが新しいGuava (MoreObjects) を参照しているのを、
 * 1.7.10環境の古いGuava (Objects) に書き戻してクラッシュを防ぎます。
 */
public class GuavaFixTransformer implements IClassTransformer {

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null)
            return null;

        if (transformedName.startsWith("journeymap.")) {
            // System.out.println("[CrossTie] Inspecting class for Guava Fix: " +
            // transformedName);
            return fixGuava(basicClass);
        }
        return basicClass;
    }

    private byte[] fixGuava(byte[] basicClass) {
        try {
            ClassReader reader = new ClassReader(basicClass);
            ClassWriter writer = new ClassWriter(reader, 0);

            ClassVisitor adapter = new GuavaRemapper(writer);
            reader.accept(adapter, 0);
            return writer.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return basicClass;
        }
    }

    // ClassRemapperの簡易実装 (Downgrade Mode)
    private static class GuavaRemapper extends ClassVisitor {
        public GuavaRemapper(ClassVisitor cv) {
            super(Opcodes.ASM5, cv);
        }

        private String mapType(String type) {
            if (type == null)
                return null;
            // MoreObjects -> Objects へのダウングレード
            if (type.equals("com/google/common/base/MoreObjects$ToStringHelper")) {
                return "com/google/common/base/Objects$ToStringHelper";
            }
            if (type.equals("com/google/common/base/MoreObjects")) {
                return "com/google/common/base/Objects";
            }
            return type;
        }

        private String mapDesc(String desc) {
            if (desc == null)
                return null;
            // ディスクリプタ内の型を置換
            String newDesc = desc;
            if (newDesc.contains("com/google/common/base/MoreObjects$ToStringHelper")) {
                newDesc = newDesc.replace("com/google/common/base/MoreObjects$ToStringHelper",
                        "com/google/common/base/Objects$ToStringHelper");
            }
            if (newDesc.contains("com/google/common/base/MoreObjects")) {
                newDesc = newDesc.replace("com/google/common/base/MoreObjects", "com/google/common/base/Objects");
            }
            return newDesc;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            super.visit(version, access, name, mapDesc(signature), mapType(superName), interfaces); // nameは変えない(JourneyMapのクラス名はそのまま)
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            return super.visitField(access, name, mapDesc(desc), mapDesc(signature), value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, mapDesc(desc), mapDesc(signature), exceptions);
            return new MethodAdapter(mv);
        }

        private class MethodAdapter extends MethodVisitor {
            public MethodAdapter(MethodVisitor mv) {
                super(Opcodes.ASM5, mv);
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                super.visitTypeInsn(opcode, mapType(type));
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                super.visitFieldInsn(opcode, mapType(owner), name, mapDesc(desc));
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                // MoreObjects.toStringHelper() 呼び出しを Objects にリダイレクト
                if (opcode == Opcodes.INVOKESTATIC &&
                        "com/google/common/base/MoreObjects".equals(owner) &&
                        "toStringHelper".equals(name)) {
                    // System.out.println("[CrossTie] Fixed MoreObjects.toStringHelper call");
                    owner = "com/google/common/base/Objects";
                }

                super.visitMethodInsn(opcode, mapType(owner), name, mapDesc(desc), itf);
            }

            @Override
            public void visitLocalVariable(String name, String desc, String signature, Label start, Label end,
                    int index) {
                super.visitLocalVariable(name, mapDesc(desc), mapDesc(signature), start, end, index);
            }
        }
    }
}
