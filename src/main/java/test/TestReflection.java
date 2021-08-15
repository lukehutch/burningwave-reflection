package test;

import java.lang.reflect.Field;

import bwr.ReflectionDriver;

public class TestReflection {

    private static Field getField(Class<?> cls, String fieldName) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            for (Field declaredField : ReflectionDriver.getDeclaredFields(c)) {
                if (declaredField.getName().equals(fieldName)) {
                    return declaredField;
                }
            }
        }
        throw new IllegalArgumentException("Field " + fieldName + " not found in class " + cls.getName());
    }
    
    public static void main(String[] args) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Field ucpField = getField(classLoader.getClass(), "ucp");
        ReflectionDriver.setAccessible(ucpField, true);
        Object ucp = ReflectionDriver.getFieldValue(classLoader, ucpField);
        System.out.println(ucp);
    }

}
