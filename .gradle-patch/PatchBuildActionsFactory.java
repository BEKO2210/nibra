import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class PatchBuildActionsFactory {
    public static void main(String[] args) throws Exception {
        try (InputStream input = Files.newInputStream(Path.of(args[0]))) {
            ClassReader reader = new ClassReader(input);
            ClassWriter writer = new ClassWriter(0);
            reader.accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM9, writer) {
                @Override public MethodVisitor visitMethod(
                        int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    if (name.equals("canUseCurrentProcess")) {
                        MethodVisitor target = super.visitMethod(
                                access, name, descriptor, signature, exceptions);
                        target.visitCode();
                        target.visitInsn(Opcodes.ICONST_1);
                        target.visitInsn(Opcodes.IRETURN);
                        target.visitMaxs(1, 3);
                        target.visitEnd();
                        return null;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            Files.write(Path.of(args[1]), writer.toByteArray());
        }
    }
}
