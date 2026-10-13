// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import com.customcosmetics.config.ConfigManager;
import java.io.*;
import java.net.*;
import java.util.*;

/** Owns mod network resources. Offline mode closes in-flight I/O and invalidates old work. */
public final class RemoteAccess {
    private static long generation;
    private static final Set<AutoCloseable> resources = new HashSet<>();
    private RemoteAccess() {}
    public static synchronized boolean online() { return !ConfigManager.offlineMode; }
    public static synchronized long generation() { return generation; }
    public static synchronized void requireOnline() throws IOException {
        if (!online()) throw new IOException("Offline mode is enabled.");
    }
    public static synchronized boolean current(long version) { return online() && version == generation; }
    public static void offline(boolean offline) {
        List<AutoCloseable> closing;
        synchronized (RemoteAccess.class) {
            ConfigManager.offlineMode = offline; generation++;
            closing = new ArrayList<>(resources); resources.clear();
        }
        for (var resource : closing) try { resource.close(); } catch (Exception ignored) {}
    }
    @FunctionalInterface public interface Factory<T> { T create() throws Exception; }
    public static synchronized <T extends AutoCloseable> T channel(Factory<T> factory) throws Exception {
        requireOnline(); T channel = factory.create(); resources.add(channel); return channel;
    }
    public static synchronized void release(AutoCloseable resource) { resources.remove(resource); }
    public static synchronized HttpURLConnection manage(HttpURLConnection connection) throws IOException {
        if (connection instanceof Managed) return connection;
        try { requireOnline(); } catch (IOException offline) { connection.disconnect(); throw offline; }
        var managed = new Managed(connection, generation); resources.add(managed); return managed;
    }
    private static final class Managed extends HttpURLConnection implements AutoCloseable {
        private final HttpURLConnection delegate;
        private final long version;
        private volatile boolean closed;
        Managed(HttpURLConnection delegate, long version) { super(delegate.getURL()); this.delegate = delegate; this.version = version; }
        private void check() throws IOException { if (closed || !current(version)) throw new IOException("Remote request cancelled."); }
        @Override public void connect() throws IOException { check(); delegate.connect(); }
        @Override public InputStream getInputStream() throws IOException { check(); return delegate.getInputStream(); }
        @Override public OutputStream getOutputStream() throws IOException { check(); return delegate.getOutputStream(); }
        @Override public int getResponseCode() throws IOException { check(); return delegate.getResponseCode(); }
        @Override public String getResponseMessage() throws IOException { check(); return delegate.getResponseMessage(); }
        @Override public InputStream getErrorStream() { return current(version) ? delegate.getErrorStream() : null; }
        @Override public void disconnect() { close(); }
        @Override public void close() { closed = true; delegate.disconnect(); release(this); }
        @Override public boolean usingProxy() { return delegate.usingProxy(); }
        @Override public void setRequestMethod(String method) throws ProtocolException { delegate.setRequestMethod(method); }
        @Override public String getRequestMethod() { return delegate.getRequestMethod(); }
        @Override public void setRequestProperty(String key, String value) { delegate.setRequestProperty(key,value); }
        @Override public void addRequestProperty(String key, String value) { delegate.addRequestProperty(key,value); }
        @Override public String getRequestProperty(String key) { return delegate.getRequestProperty(key); }
        @Override public Map<String,List<String>> getRequestProperties() { return delegate.getRequestProperties(); }
        @Override public String getHeaderField(String key) { return current(version) ? delegate.getHeaderField(key) : null; }
        @Override public Map<String,List<String>> getHeaderFields() { return current(version) ? delegate.getHeaderFields() : Map.of(); }
        @Override public void setConnectTimeout(int timeout) { delegate.setConnectTimeout(timeout); }
        @Override public void setReadTimeout(int timeout) { delegate.setReadTimeout(timeout); }
        @Override public int getConnectTimeout() { return delegate.getConnectTimeout(); }
        @Override public int getReadTimeout() { return delegate.getReadTimeout(); }
        @Override public void setInstanceFollowRedirects(boolean value) { delegate.setInstanceFollowRedirects(value); }
        @Override public boolean getInstanceFollowRedirects() { return delegate.getInstanceFollowRedirects(); }
        @Override public void setUseCaches(boolean value) { delegate.setUseCaches(value); }
        @Override public boolean getUseCaches() { return delegate.getUseCaches(); }
        @Override public void setDoOutput(boolean value) { delegate.setDoOutput(value); }
        @Override public boolean getDoOutput() { return delegate.getDoOutput(); }
        @Override public void setDoInput(boolean value) { delegate.setDoInput(value); }
        @Override public void setFixedLengthStreamingMode(int length) { delegate.setFixedLengthStreamingMode(length); }
        @Override public void setFixedLengthStreamingMode(long length) { delegate.setFixedLengthStreamingMode(length); }
        @Override public void setChunkedStreamingMode(int length) { delegate.setChunkedStreamingMode(length); }
    }
}
