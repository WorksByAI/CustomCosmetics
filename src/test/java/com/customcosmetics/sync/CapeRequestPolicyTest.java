// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;
import java.net.HttpURLConnection;
import java.net.URI;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapeRequestPolicyTest {
    @Test void slowUploadAcknowledgementsGetABoundedBudgetWithoutLengtheningConnectionEstablishment()throws Exception{
        var upload=connection();CapeRequestPolicy.configure(upload,true);
        var read=connection();CapeRequestPolicy.configure(read,false);
        assertEquals(15_000,upload.getConnectTimeout());assertEquals(read.getConnectTimeout(),upload.getConnectTimeout());
        assertEquals(180_000,upload.getReadTimeout());assertEquals(30_000,read.getReadTimeout());
        assertFalse(upload.getUseCaches());assertEquals("application/json",upload.getRequestProperty("Accept"));
    }
    private static HttpURLConnection connection()throws Exception{return new HttpURLConnection(URI.create("http://127.0.0.1:25577/capes/submit").toURL()){
        @Override public void connect(){}@Override public void disconnect(){}@Override public boolean usingProxy(){return false;}
    };}
}
