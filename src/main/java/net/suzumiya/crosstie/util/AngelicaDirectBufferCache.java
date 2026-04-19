package net.suzumiya.crosstie.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Thread-local direct buffer used for zero-GC transfer of corrected float data.
 */
public final class AngelicaDirectBufferCache {

    private static final int INITIAL_FLOAT_CAPACITY = 256;

    private static final ThreadLocal<FloatBuffer> FLOAT_BUFFER = new ThreadLocal<FloatBuffer>() {
        @Override
        protected FloatBuffer initialValue() {
            return allocate(INITIAL_FLOAT_CAPACITY);
        }
    };

    private AngelicaDirectBufferCache() {
    }

    public static FloatBuffer acquire(int requiredFloats) {
        if (requiredFloats <= 0) {
            requiredFloats = 1;
        }

        FloatBuffer current = FLOAT_BUFFER.get();
        if (current.capacity() < requiredFloats) {
            current = allocate(nextPow2(requiredFloats));
            FLOAT_BUFFER.set(current);
        }

        current.clear();
        return current;
    }

    public static FloatBuffer copy(float... values) {
        if (values == null || values.length == 0) {
            return acquire(1);
        }

        FloatBuffer buffer = acquire(values.length);
        buffer.put(values);
        buffer.flip();
        return buffer;
    }

    private static FloatBuffer allocate(int floatCapacity) {
        ByteBuffer bytes = ByteBuffer.allocateDirect(floatCapacity * Float.BYTES).order(ByteOrder.nativeOrder());
        return bytes.asFloatBuffer();
    }

    private static int nextPow2(int value) {
        int n = 1;
        while (n < value) {
            n <<= 1;
        }
        return n;
    }
}
