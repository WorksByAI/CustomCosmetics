package com.customcosmetics.distribution;

import java.lang.invoke.MethodType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientLoadProbeTest {
    @Test void interfaceReferencesResolvePublicObjectMethodsButRejectMissingOrProtectedMethods() throws Exception {
        var resolve = ClientLoadProbe.class.getDeclaredMethod("hasMethod", Class.class, String.class, MethodType.class);
        resolve.setAccessible(true);
        assertEquals(true, resolve.invoke(null, Runnable.class, "toString", MethodType.methodType(String.class)));
        assertEquals(true, resolve.invoke(null, Runnable.class, "equals", MethodType.methodType(boolean.class, Object.class)));
        assertEquals(false, resolve.invoke(null, Runnable.class, "clone", MethodType.methodType(Object.class)));
        assertEquals(false, resolve.invoke(null, Runnable.class, "missingGameMethod", MethodType.methodType(void.class)));
    }
}
