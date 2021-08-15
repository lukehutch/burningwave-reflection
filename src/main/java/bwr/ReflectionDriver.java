/*
 * This file is derived from Burningwave Core.
 *
 * Author: Roberto Gentili
 * 
 * Hosted at: https://github.com/burningwave/core
 * 
 * Modified by: Luke Hutchison
 *
 * Modifications hosted at:  https://github.com/lukehutch/burningwave-reflection
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Roberto Gentili
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package bwr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import sun.misc.Unsafe;

@SuppressWarnings("all")
public class ReflectionDriver {
    static Unsafe unsafe;
    static Runnable illegalAccessLoggerEnabler;
    static Runnable illegalAccessLoggerDisabler;

    static MethodHandle getDeclaredFieldsRetriever;
    static MethodHandle getDeclaredMethodsRetriever;
    static MethodHandle getDeclaredConstructorsRetriever;
    static MethodHandle methodInvoker;
    static MethodHandle constructorInvoker;
    static BiConsumer<AccessibleObject, Boolean> accessibleSetter;
    static Function<Class<?>, MethodHandles.Lookup> consulterRetriever;
    static TriFunction<ClassLoader, Object, String, Package> packageRetriever;

    static Long loadedPackagesMapMemoryOffset;
    static Long loadedClassesVectorMemoryOffset;

    static Class<?> classLoaderDelegateClass;
    static Class<?> builtinClassLoaderClass;

    private static final Logger logger = Logger.getLogger(ReflectionDriver.class.getName());

    private static final int jvmMajorVersion;

    static {
        String jvmVersionStr = System.getProperty("java.version");
        int startIdx = 0;
        while (startIdx < jvmVersionStr.length() && !Character.isDigit(jvmVersionStr.charAt(startIdx))) {
            startIdx++;
        }
        if (startIdx > 0) {
            jvmVersionStr = jvmVersionStr.substring(startIdx);
        }
        if (jvmVersionStr.startsWith("1.")) {
            jvmVersionStr = jvmVersionStr.substring(2);
        }
        int endIdx = 1;
        while (endIdx < jvmVersionStr.length() && Character.isDigit(jvmVersionStr.charAt(endIdx))) {
            endIdx++;
        }
        int majorVersion = 0;
        try {
            majorVersion = Integer.parseInt(jvmVersionStr.substring(0, endIdx));
        } catch (final NumberFormatException e) {
            // Ignore
        }
        jvmMajorVersion = majorVersion;

        Initializer.build();
    }

    @FunctionalInterface
    public static interface TriFunction<P0, P1, P2, R> {
        R apply(P0 p0, P1 p1, P2 p2);
    }

    public static void disableIllegalAccessLogger() {
        if (illegalAccessLoggerDisabler != null) {
            illegalAccessLoggerDisabler.run();
        }
    }

    public static void enableIllegalAccessLogger() {
        if (illegalAccessLoggerEnabler != null) {
            illegalAccessLoggerEnabler.run();
        }
    }

    public static void setAccessible(final AccessibleObject object, final boolean flag) {
        try {
            accessibleSetter.accept(object, flag);
        } catch (final Throwable exc) {
            throw new RuntimeException(exc);
        }
    }

    public static Class<?> defineAnonymousClass(final Class<?> outerClass, final byte[] byteCode,
            final Object[] var3) {
        return unsafe.defineAnonymousClass(outerClass, byteCode, var3);
    }

    public static Package retrieveLoadedPackage(final ClassLoader classLoader, final Object packageToFind,
            final String packageName) throws Throwable {
        return packageRetriever.apply(classLoader, packageToFind, packageName);
    }

    public static Collection<Class<?>> retrieveLoadedClasses(final ClassLoader classLoader) {
        return (Collection<Class<?>>) unsafe.getObject(classLoader, loadedClassesVectorMemoryOffset);
    }

    public static Map<String, ?> retrieveLoadedPackages(final ClassLoader classLoader) {
        return (Map<String, ?>) unsafe.getObject(classLoader, loadedPackagesMapMemoryOffset);
    }

    public static boolean isBuiltinClassLoader(final ClassLoader classLoader) {
        return builtinClassLoaderClass != null && builtinClassLoaderClass.isAssignableFrom(classLoader.getClass());
    }

    public static boolean isClassLoaderDelegate(final ClassLoader classLoader) {
        return classLoaderDelegateClass != null
                && classLoaderDelegateClass.isAssignableFrom(classLoader.getClass());
    }

    public static Class<?> getBuiltinClassLoaderClass() {
        return builtinClassLoaderClass;
    }

    public static Class getClassLoaderDelegateClass() {
        return classLoaderDelegateClass;
    }

    public static Lookup getConsulter(final Class<?> cls) {
        return consulterRetriever.apply(cls);
    }

    public static Object invoke(final Method method, final Object target, final Object[] params) {
        try {
            return methodInvoker.invoke(method, target, params);
        } catch (final Throwable exc) {
            throw new RuntimeException(exc);
        }
    }

    public static <T> T newInstance(final Constructor<T> ctor, final Object[] params) {
        try {
            return (T) constructorInvoker.invoke(ctor, params);
        } catch (final Throwable exc) {
            throw new RuntimeException(exc);
        }
    }

    public static Field getDeclaredField(final Class<?> cls, final String name) {
        for (final Field field : getDeclaredFields(cls)) {
            if (field.getName().equals(name)) {
                return field;
            }
        }
        return null;
    }

    public static Field[] getDeclaredFields(final Class<?> cls) {
        try {
            return (Field[]) getDeclaredFieldsRetriever.invoke(cls, false);
        } catch (final Throwable exc) {
            throw new RuntimeException(exc);
        }
    }

    public static <T> Constructor<T>[] getDeclaredConstructors(final Class<T> cls) {
        try {
            return (Constructor<T>[]) getDeclaredConstructorsRetriever.invoke(cls, false);
        } catch (final Throwable exc) {
            throw new RuntimeException(exc);
        }
    }

    public static Method[] getDeclaredMethods(final Class<?> cls) {
        try {
            return (Method[]) getDeclaredMethodsRetriever.invoke(cls, false);
        } catch (final Throwable exc) {
            throw new RuntimeException(exc);
        }
    }

    public static <T> T getFieldValue(Object target, final Field field) {
        target = Modifier.isStatic(field.getModifiers()) ? field.getDeclaringClass() : target;
        final long fieldOffset = Modifier.isStatic(field.getModifiers()) ? unsafe.staticFieldOffset(field)
                : unsafe.objectFieldOffset(field);
        final Class<?> cls = field.getType();
        if (!cls.isPrimitive()) {
            if (!Modifier.isVolatile(field.getModifiers())) {
                return (T) unsafe.getObject(target, fieldOffset);
            } else {
                return (T) unsafe.getObjectVolatile(target, fieldOffset);
            }
        } else if (cls == int.class) {
            if (!Modifier.isVolatile(field.getModifiers())) {
                return (T) Integer.valueOf(unsafe.getInt(target, fieldOffset));
            } else {
                return (T) Integer.valueOf(unsafe.getIntVolatile(target, fieldOffset));
            }
        } else if (cls == long.class) {
            if (!Modifier.isVolatile(field.getModifiers())) {
                return (T) Long.valueOf(unsafe.getLong(target, fieldOffset));
            } else {
                return (T) Long.valueOf(unsafe.getLongVolatile(target, fieldOffset));
            }
        } else if (cls == float.class) {
            if (!Modifier.isVolatile(field.getModifiers())) {
                return (T) Float.valueOf(unsafe.getFloat(target, fieldOffset));
            } else {
                return (T) Float.valueOf(unsafe.getFloatVolatile(target, fieldOffset));
            }
        } else if (cls == double.class) {
            if (!Modifier.isVolatile(field.getModifiers())) {
                return (T) Double.valueOf(unsafe.getDouble(target, fieldOffset));
            } else {
                return (T) Double.valueOf(unsafe.getDoubleVolatile(target, fieldOffset));
            }
        } else if (cls == boolean.class) {
            if (!Modifier.isVolatile(field.getModifiers())) {
                return (T) Boolean.valueOf(unsafe.getBoolean(target, fieldOffset));
            } else {
                return (T) Boolean.valueOf(unsafe.getBooleanVolatile(target, fieldOffset));
            }
        } else if (cls == byte.class) {
            if (!Modifier.isVolatile(field.getModifiers())) {
                return (T) Byte.valueOf(unsafe.getByte(target, fieldOffset));
            } else {
                return (T) Byte.valueOf(unsafe.getByteVolatile(target, fieldOffset));
            }
        } else {
            if (!Modifier.isVolatile(field.getModifiers())) {
                return (T) Character.valueOf(unsafe.getChar(target, fieldOffset));
            } else {
                return (T) Character.valueOf(unsafe.getCharVolatile(target, fieldOffset));
            }
        }
    }

    public static void setFieldValue(Object target, final Field field, final Object value) {
        if (value != null && !isAssignableFrom(field.getType(), value.getClass())) {
            throw new RuntimeException("Value " + value + " is not assignable to " + field.getName());
        }
        target = Modifier.isStatic(field.getModifiers()) ? field.getDeclaringClass() : target;
        final long fieldOffset = Modifier.isStatic(field.getModifiers()) ? unsafe.staticFieldOffset(field)
                : unsafe.objectFieldOffset(field);
        final Class<?> cls = field.getType();
        if (!cls.isPrimitive()) {
            if (!Modifier.isVolatile(field.getModifiers())) {
                unsafe.putObject(target, fieldOffset, value);
            } else {
                unsafe.putObjectVolatile(target, fieldOffset, value);
            }
        } else if (cls == int.class) {
            if (!Modifier.isVolatile(field.getModifiers())) {
                unsafe.putInt(target, fieldOffset, ((Integer) value).intValue());
            } else {
                unsafe.putIntVolatile(target, fieldOffset, ((Integer) value).intValue());
            }
        } else if (cls == long.class) {
            if (!Modifier.isVolatile(field.getModifiers())) {
                unsafe.putLong(target, fieldOffset, ((Long) value).longValue());
            } else {
                unsafe.putLongVolatile(target, fieldOffset, ((Long) value).longValue());
            }
        } else if (cls == float.class) {
            if (!Modifier.isVolatile(field.getModifiers())) {
                unsafe.putFloat(target, fieldOffset, ((Float) value).floatValue());
            } else {
                unsafe.putFloatVolatile(target, fieldOffset, ((Float) value).floatValue());
            }
        } else if (cls == double.class) {
            if (!Modifier.isVolatile(field.getModifiers())) {
                unsafe.putDouble(target, fieldOffset, ((Double) value).doubleValue());
            } else {
                unsafe.putDoubleVolatile(target, fieldOffset, ((Double) value).doubleValue());
            }
        } else if (cls == boolean.class) {
            if (!Modifier.isVolatile(field.getModifiers())) {
                unsafe.putBoolean(target, fieldOffset, ((Boolean) value).booleanValue());
            } else {
                unsafe.putBooleanVolatile(target, fieldOffset, ((Boolean) value).booleanValue());
            }
        } else if (cls == byte.class) {
            if (!Modifier.isVolatile(field.getModifiers())) {
                unsafe.putByte(target, fieldOffset, ((Byte) value).byteValue());
            } else {
                unsafe.putByteVolatile(target, fieldOffset, ((Byte) value).byteValue());
            }
        } else if (cls == char.class) {
            if (!Modifier.isVolatile(field.getModifiers())) {
                unsafe.putChar(target, fieldOffset, ((Character) value).charValue());
            } else {
                unsafe.putCharVolatile(target, fieldOffset, ((Character) value).charValue());
            }
        }
    }

    private static byte[] readAllBytes(final InputStream inputStream) throws IOException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final byte[] data = new byte[4];
        for (int nRead; (nRead = inputStream.read(data, 0, data.length)) != -1;) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        final byte[] byteArray = buffer.toByteArray();
        return byteArray;
    }

    private static boolean isAssignableFrom(final Class<?> cls_01, final Class<?> cls_02) {
        return getClassOrWrapper(cls_01).isAssignableFrom(getClassOrWrapper(cls_02));
    }

    private static Class<?> getClassOrWrapper(final Class<?> cls) {
        if (cls.isPrimitive()) {
            if (cls == int.class) {
                return Integer.class;
            } else if (cls == long.class) {
                return Long.class;
            } else if (cls == float.class) {
                return Float.class;
            } else if (cls == double.class) {
                return Double.class;
            } else if (cls == boolean.class) {
                return Boolean.class;
            } else if (cls == byte.class) {
                return Byte.class;
            } else if (cls == char.class) {
                return Character.class;
            }
        }
        return cls;
    }

    private static abstract class Initializer {
        private Initializer() {
            try {
                final Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafeField.setAccessible(true);
                ReflectionDriver.unsafe = (Unsafe) theUnsafeField.get(null);
            } catch (final Throwable exc) {
                logger.log(Level.INFO, "Exception while retrieving unsafe");
                throw new RuntimeException(exc);
            }
        }

        void init() {
            initConsulterRetriever();
            initMembersRetrievers();
            initAccessibleSetter();
            initConstructorInvoker();
            initMethodInvoker();
            initSpecificElements();
            initClassesVectorField();
            initPackagesMapField();
        }

        abstract void initConsulterRetriever();

        abstract void initAccessibleSetter();

        abstract void initSpecificElements();

        abstract void initConstructorInvoker();

        abstract void initMethodInvoker();

        private void initPackagesMapField() {
            try {
                ReflectionDriver.loadedPackagesMapMemoryOffset = ReflectionDriver.unsafe
                        .objectFieldOffset(ReflectionDriver.getDeclaredField(ClassLoader.class, "packages"));
            } catch (final Throwable exc) {
                logger.log(Level.SEVERE, "Could not initialize field memory offset of loaded classes vector");
                throw new RuntimeException(exc);
            }
        }

        private void initClassesVectorField() {
            try {
                ReflectionDriver.loadedClassesVectorMemoryOffset = ReflectionDriver.unsafe
                        .objectFieldOffset(ReflectionDriver.getDeclaredField(ClassLoader.class, "classes"));
            } catch (final Throwable exc) {
                logger.log(Level.SEVERE, "Could not initialize field memory offset of packages map");
                throw new RuntimeException(exc);
            }
        }

        private static void build() {
            (jvmMajorVersion <= 8 ? new ForJava8() : jvmMajorVersion <= 13 ? new ForJava9() : new ForJava14())
                    .init();
        }

        private void initMembersRetrievers() {
            try {
                final MethodHandles.Lookup consulter = ReflectionDriver.getConsulter(Class.class);
                ReflectionDriver.getDeclaredFieldsRetriever = consulter.findSpecial(Class.class,
                        "getDeclaredFields0", MethodType.methodType(Field[].class, boolean.class), Class.class);

                ReflectionDriver.getDeclaredMethodsRetriever = consulter.findSpecial(Class.class,
                        "getDeclaredMethods0", MethodType.methodType(Method[].class, boolean.class), Class.class);

                ReflectionDriver.getDeclaredConstructorsRetriever = consulter.findSpecial(Class.class,
                        "getDeclaredConstructors0", MethodType.methodType(Constructor[].class, boolean.class),
                        Class.class);
            } catch (final Throwable exc) {
                throw new RuntimeException(exc);
            }
        }

        private static class ForJava8 extends Initializer {
            @Override
            void initConsulterRetriever() {
                try {
                    final Field modes = MethodHandles.Lookup.class.getDeclaredField("allowedModes");
                    modes.setAccessible(true);
                    ReflectionDriver.consulterRetriever = new Function<Class<?>, Lookup>() {
                        @Override
                        public Lookup apply(final Class<?> cls) {
                            final MethodHandles.Lookup consulter = MethodHandles.lookup().in(cls);
                            try {
                                modes.setInt(consulter, -1);
                            } catch (final Throwable exc) {
                                throw new RuntimeException(exc);
                            }
                            return consulter;
                        }
                    };
                } catch (final Throwable exc) {
                    logger.log(Level.SEVERE, "Could not initialize consulter retriever");
                    throw new RuntimeException(exc);
                }
            }

            @Override
            void initAccessibleSetter() {
                try {
                    final Method accessibleSetterMethod = AccessibleObject.class.getDeclaredMethod("setAccessible0",
                            AccessibleObject.class, boolean.class);
                    final MethodHandle accessibleSetterMethodHandle = ReflectionDriver
                            .getConsulter(AccessibleObject.class).unreflect(accessibleSetterMethod);
                    ReflectionDriver.accessibleSetter = new BiConsumer<AccessibleObject, Boolean>() {
                        @Override
                        public void accept(final AccessibleObject accessibleObject, final Boolean flag) {
                            try {
                                accessibleSetterMethodHandle.invoke(accessibleObject, flag);
                            } catch (final Throwable exc) {
                                throw new RuntimeException(exc);
                            }
                        }
                    };
                } catch (final Throwable exc) {
                    logger.log(Level.SEVERE, "Could not initialize accessible setter");
                    throw new RuntimeException(exc);
                }
            }

            @Override
            void initConstructorInvoker() {
                try {
                    final Class<?> nativeAccessorImplClass = Class
                            .forName("sun.reflect.NativeConstructorAccessorImpl");
                    final Method method = nativeAccessorImplClass.getDeclaredMethod("newInstance0",
                            Constructor.class, Object[].class);
                    final MethodHandles.Lookup consulter = ReflectionDriver.getConsulter(nativeAccessorImplClass);
                    ReflectionDriver.constructorInvoker = consulter.unreflect(method);
                } catch (final Throwable exc) {
                    logger.log(Level.SEVERE, "Could not initialize constructor invoker");
                    throw new RuntimeException(exc);
                }
            }

            @Override
            void initMethodInvoker() {
                try {
                    final Class<?> nativeAccessorImplClass = Class.forName("sun.reflect.NativeMethodAccessorImpl");
                    final Method method = nativeAccessorImplClass.getDeclaredMethod("invoke0", Method.class,
                            Object.class, Object[].class);
                    final MethodHandles.Lookup consulter = ReflectionDriver.getConsulter(nativeAccessorImplClass);
                    ReflectionDriver.methodInvoker = consulter.unreflect(method);
                } catch (final Throwable exc) {
                    logger.log(Level.SEVERE, "Could not initialize method invoker");
                    throw new RuntimeException(exc);
                }
            }

            @Override
            void initSpecificElements() {
                ReflectionDriver.packageRetriever = new TriFunction<ClassLoader, Object, String, Package>() {
                    @Override
                    public Package apply(final ClassLoader classLoader, final Object object,
                            final String packageName) {
                        return (Package) object;
                    }
                };
            }

        }

        private static class ForJava9 extends Initializer {
            ForJava9() {
                try {
                    final Class<?> cls = Class.forName("jdk.internal.module.IllegalAccessLogger");
                    final Field logger = cls.getDeclaredField("logger");
                    final long loggerFieldOffset = ReflectionDriver.unsafe.staticFieldOffset(logger);
                    final Object illegalAccessLogger = ReflectionDriver.unsafe.getObjectVolatile(cls,
                            loggerFieldOffset);
                    ReflectionDriver.illegalAccessLoggerDisabler = new Runnable() {
                        @Override
                        public void run() {
                            ReflectionDriver.unsafe.putObjectVolatile(cls, loggerFieldOffset, null);
                        }
                    };
                    ReflectionDriver.illegalAccessLoggerEnabler = new Runnable() {
                        @Override
                        public void run() {
                            ReflectionDriver.unsafe.putObjectVolatile(cls, loggerFieldOffset, illegalAccessLogger);
                        }
                    };
                    ReflectionDriver.disableIllegalAccessLogger();
                } catch (final Throwable e) {

                }
            }

            @Override
            void initConsulterRetriever() {
                try (InputStream inputStream = ReflectionDriver.class.getClassLoader()
                        .getResourceAsStream(this.getClass().getPackage().getName().replace(".", "/")
                                + "/ConsulterRetrieverForJDK9.bwc")) {
                    final Class<?> methodHandleWrapperClass = ReflectionDriver.defineAnonymousClass(Class.class,
                            readAllBytes(inputStream), null);
                    final MethodHandles.Lookup consulter = MethodHandles.lookup();
                    final MethodHandle methodHandle = consulter.findStatic(MethodHandles.class, "privateLookupIn",
                            MethodType.methodType(MethodHandles.Lookup.class, Class.class,
                                    MethodHandles.Lookup.class));
                    ReflectionDriver.unsafe
                            .putObject(methodHandleWrapperClass,
                                    ReflectionDriver.unsafe.staticFieldOffset(
                                            methodHandleWrapperClass.getDeclaredField("consulterRetriever")),
                                    methodHandle);
                    ReflectionDriver.consulterRetriever = (Function<Class<?>, MethodHandles.Lookup>) ReflectionDriver.unsafe
                            .allocateInstance(methodHandleWrapperClass);
                } catch (final Throwable exc) {
                    logger.log(Level.SEVERE, "Could not initialize consulter retriever");
                    throw new RuntimeException(exc);
                }

            }

            @Override
            void initAccessibleSetter() {
                try (InputStream inputStream = ReflectionDriver.class.getClassLoader()
                        .getResourceAsStream(this.getClass().getPackage().getName().replace(".", "/")
                                + "/AccessibleSetterInvokerForJDK9.bwc");) {
                    final Class<?> methodHandleWrapperClass = ReflectionDriver
                            .defineAnonymousClass(AccessibleObject.class, readAllBytes(inputStream), null);
                    ReflectionDriver.unsafe.putObject(methodHandleWrapperClass,
                            ReflectionDriver.unsafe.staticFieldOffset(
                                    methodHandleWrapperClass.getDeclaredField("methodHandleRetriever")),
                            ReflectionDriver.getConsulter(methodHandleWrapperClass));
                    ReflectionDriver.accessibleSetter = (BiConsumer<AccessibleObject, Boolean>) ReflectionDriver.unsafe
                            .allocateInstance(methodHandleWrapperClass);
                } catch (final Throwable exc) {
                    logger.log(Level.SEVERE, "Could not initialize accessible setter");
                    throw new RuntimeException(exc);
                }
            }

            @Override
            void initConstructorInvoker() {
                try {
                    final Class<?> nativeAccessorImplClass = Class
                            .forName("jdk.internal.reflect.NativeConstructorAccessorImpl");
                    final Method method = nativeAccessorImplClass.getDeclaredMethod("newInstance0",
                            Constructor.class, Object[].class);
                    final MethodHandles.Lookup consulter = ReflectionDriver.getConsulter(nativeAccessorImplClass);
                    ReflectionDriver.constructorInvoker = consulter.unreflect(method);
                } catch (final Throwable exc) {
                    logger.log(Level.SEVERE, "Could not initialize constructor invoker");
                    throw new RuntimeException(exc);
                }
            }

            @Override
            void initMethodInvoker() {
                try {
                    final Class<?> nativeMethodAccessorImplClass = Class
                            .forName("jdk.internal.reflect.NativeMethodAccessorImpl");
                    final Method invoker = nativeMethodAccessorImplClass.getDeclaredMethod("invoke0", Method.class,
                            Object.class, Object[].class);
                    final MethodHandles.Lookup consulter = ReflectionDriver
                            .getConsulter(nativeMethodAccessorImplClass);
                    ReflectionDriver.methodInvoker = consulter.unreflect(invoker);
                } catch (final Throwable exc) {
                    logger.log(Level.SEVERE, "Could not initialize method invoker");
                    throw new RuntimeException(exc);
                }
            }

            @Override
            void initSpecificElements() {
                try {
                    final MethodHandles.Lookup classLoaderConsulter = ReflectionDriver.consulterRetriever
                            .apply(ClassLoader.class);
                    final MethodType methodType = MethodType.methodType(Package.class, String.class);
                    final MethodHandle methodHandle = classLoaderConsulter.findSpecial(ClassLoader.class,
                            "getDefinedPackage", methodType, ClassLoader.class);
                    ReflectionDriver.packageRetriever = new TriFunction<ClassLoader, Object, String, Package>() {
                        @Override
                        public Package apply(final ClassLoader classLoader, final Object object,
                                final String packageName) {
                            try {
                                return (Package) methodHandle.invokeExact(classLoader, packageName);
                            } catch (final Throwable exc) {
                                throw new RuntimeException(exc);
                            }
                        }
                    };
                } catch (final Throwable exc) {
                    logger.log(Level.SEVERE, "Could not initialize package retriever");
                    throw new RuntimeException(exc);
                }
                try {
                    ReflectionDriver.builtinClassLoaderClass = Class
                            .forName("jdk.internal.loader.BuiltinClassLoader");
                } catch (final Throwable exc) {
                    logger.log(Level.SEVERE, "Could not initialize builtin class loader class");
                    throw new RuntimeException(exc);
                }
                try (InputStream inputStream = ReflectionDriver.class.getClassLoader()
                        .getResourceAsStream(this.getClass().getPackage().getName().replace('.', '/')
                                + "/ClassLoaderDelegateForJDK9.bwc")) {
                    ReflectionDriver.classLoaderDelegateClass = ReflectionDriver.defineAnonymousClass(
                            ReflectionDriver.builtinClassLoaderClass, readAllBytes(inputStream), null);
                } catch (final Throwable exc) {
                    logger.log(Level.SEVERE, "Could not initialize class loader delegate class");
                    throw new RuntimeException(exc);
                }
                try {
                    initDeepConsulterRetriever();
                } catch (final Throwable exc) {
                    logger.log(Level.INFO, "Could not initialize deep consulter retriever");
                    throw new RuntimeException(exc);
                }
            }

            void initDeepConsulterRetriever() throws Throwable {
                final Constructor<MethodHandles.Lookup> lookupCtor = MethodHandles.Lookup.class
                        .getDeclaredConstructor(Class.class, int.class);
                ReflectionDriver.setAccessible(lookupCtor, true);
                final Field fullPowerModeConstant = MethodHandles.Lookup.class.getDeclaredField("FULL_POWER_MODES");
                ReflectionDriver.setAccessible(fullPowerModeConstant, true);
                final int fullPowerModeConstantValue = fullPowerModeConstant.getInt(null);
                final MethodHandle methodHandle = lookupCtor
                        .newInstance(MethodHandles.Lookup.class, fullPowerModeConstantValue)
                        .findConstructor(MethodHandles.Lookup.class,
                                MethodType.methodType(void.class, Class.class, int.class));
                ReflectionDriver.consulterRetriever = new Function<Class<?>, Lookup>() {
                    @Override
                    public Lookup apply(final Class<?> cls) {
                        try {
                            return (MethodHandles.Lookup) methodHandle.invoke(cls, fullPowerModeConstantValue);
                        } catch (final Throwable exc) {
                            throw new RuntimeException(exc);
                        }
                    }
                };
            }
        }

        private static class ForJava14 extends ForJava9 {
            @Override
            void initDeepConsulterRetriever() throws Throwable {
                final Constructor<?> lookupCtor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class,
                        Class.class, int.class);
                ReflectionDriver.setAccessible(lookupCtor, true);
                final Field fullPowerModeConstant = MethodHandles.Lookup.class.getDeclaredField("FULL_POWER_MODES");
                ReflectionDriver.setAccessible(fullPowerModeConstant, true);
                final int fullPowerModeConstantValue = fullPowerModeConstant.getInt(null);
                final MethodHandle mthHandle = ((MethodHandles.Lookup) lookupCtor
                        .newInstance(MethodHandles.Lookup.class, null, fullPowerModeConstantValue)).findConstructor(
                                MethodHandles.Lookup.class,
                                MethodType.methodType(void.class, Class.class, Class.class, int.class));
                ReflectionDriver.consulterRetriever = new Function<Class<?>, Lookup>() {
                    @Override
                    public Lookup apply(final Class<?> cls) {
                        try {
                            return (MethodHandles.Lookup) mthHandle.invoke(cls, null, fullPowerModeConstantValue);
                        } catch (final Throwable exc) {
                            throw new RuntimeException(exc);
                        }
                    }
                };
            }
        }
    }
}
