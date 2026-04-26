package net.suzumiya.crosstie.util;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ReflectionFieldCache {

    private static final ConcurrentMap<Key, Field> DECLARED_FIELD_CACHE = new ConcurrentHashMap<Key, Field>();
    private static final ConcurrentMap<Class<?>, Field[]> PUBLIC_FIELDS_CACHE = new ConcurrentHashMap<Class<?>, Field[]>();

    private ReflectionFieldCache() {
    }

    public static Object getDeclaredFieldValue(Class<?> owner, Object instance, String name) {
        if (owner == null || instance == null || name == null) {
            return null;
        }
        try {
            Field field = getDeclaredField(owner, name);
            return field == null ? null : field.get(instance);
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return null;
        }
    }

    public static Field[] getPublicFields(Class<?> owner) {
        if (owner == null) {
            return new Field[0];
        }
        Field[] cached = PUBLIC_FIELDS_CACHE.get(owner);
        if (cached != null) {
            return cached;
        }
        Field[] fields = owner.getFields();
        Field[] previous = PUBLIC_FIELDS_CACHE.putIfAbsent(owner, fields);
        return previous == null ? fields : previous;
    }

    private static Field getDeclaredField(Class<?> owner, String name) throws NoSuchFieldException {
        Key key = new Key(owner, name);
        Field cached = DECLARED_FIELD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        Field previous = DECLARED_FIELD_CACHE.putIfAbsent(key, field);
        return previous == null ? field : previous;
    }

    private static final class Key {
        private final Class<?> owner;
        private final String name;

        private Key(Class<?> owner, String name) {
            this.owner = owner;
            this.name = name;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Key)) {
                return false;
            }
            Key other = (Key) obj;
            return this.owner == other.owner && this.name.equals(other.name);
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(this.owner) + this.name.hashCode();
        }
    }
}

