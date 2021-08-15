package test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Arrays;

import bwr.ReflectionDriver;

public class TestReflection {

    private static Field getField(final Class<?> cls, final String fieldName) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            for (final Field declaredField : ReflectionDriver.getDeclaredFields(c)) {
                if (declaredField.getName().equals(fieldName)) {
                    return declaredField;
                }
            }
        }
        throw new IllegalArgumentException("Field " + fieldName + " not found in class " + cls.getName());
    }

    private static Method getMethod(final Class<?> cls, final String methodName, final Class<?>... paramTypes) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            for (final Method declaredMethod : ReflectionDriver.getDeclaredMethods(c)) {
                if (declaredMethod.getName().equals(methodName)
                        && Arrays.equals(declaredMethod.getParameterTypes(), paramTypes)) {
                    return declaredMethod;
                }
            }
        }
        throw new IllegalArgumentException("Method " + methodName + " not found in class " + cls.getName());
    }

    public static void main(final String[] args) {
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        final Field ucpField = getField(classLoader.getClass(), "ucp");
        ReflectionDriver.setAccessible(ucpField, true);
        final Object ucp = ReflectionDriver.getFieldValue(classLoader, ucpField);
        if (ucp == null) {
            throw new IllegalArgumentException("ucp field is null");
        }
        final Method method = getMethod(ucp.getClass(), "getURLs");
        final URL[] urls = (URL[]) ReflectionDriver.invoke(method, ucp, new Object[0]);
        System.out.println(Arrays.toString(urls));
    }

}
