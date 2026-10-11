// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import com.customcosmetics.config.ConfigManager;
import java.io.IOException;
import java.net.*;
import java.util.List;
import javax.net.ssl.*;

/** Service-scoped SNI compatibility. Retains the supplied trust manager and HTTPS hostname verifier. */
public final class SyncTls {
    private SyncTls() {}
    public static void configure(HttpURLConnection connection) {
        if (!ConfigManager.syncServerSni && connection instanceof HttpsURLConnection https
                && https.getURL().getHost().equalsIgnoreCase(SyncEndpoint.normalizeHost(ConfigManager.syncServerIp)))
            https.setSSLSocketFactory(withoutServerName(https.getSSLSocketFactory()));
    }
    public static SSLSocketFactory withoutServerName(SSLSocketFactory delegate) {
        return new SSLSocketFactory() {
            private Socket configureSocket(Socket socket) {
                if (socket instanceof SSLSocket ssl) {
                    SSLParameters parameters = ssl.getSSLParameters();
                    parameters.setServerNames(List.of());
                    parameters.setEndpointIdentificationAlgorithm("HTTPS");
                    ssl.setSSLParameters(parameters);
                    return new ServiceSocket(ssl);
                }
                return socket;
            }
            @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
            @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
            @Override public Socket createSocket() throws IOException { return configureSocket(delegate.createSocket()); }
            @Override public Socket createSocket(Socket socket, String host, int port, boolean close) throws IOException {
                return configureSocket(delegate.createSocket(socket, host, port, close));
            }
            @Override public Socket createSocket(String host, int port) throws IOException { return configureSocket(delegate.createSocket(host, port)); }
            @Override public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException {
                return configureSocket(delegate.createSocket(host, port, local, localPort));
            }
            @Override public Socket createSocket(InetAddress host, int port) throws IOException { return configureSocket(delegate.createSocket(host, port)); }
            @Override public Socket createSocket(InetAddress host, int port, InetAddress local, int localPort) throws IOException {
                return configureSocket(delegate.createSocket(host, port, local, localPort));
            }
        };
    }

    // HttpsURLConnection sets a peer host again on raw JSSE sockets, which also restores SNI.
    // Keep the verified peer host set by JSSE's connect/layering and prevent that extra mutation.
    private static final class ServiceSocket extends SSLSocket {
        private final SSLSocket delegate;
        ServiceSocket(SSLSocket delegate) { this.delegate = delegate; }
        @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
        @Override public String[] getEnabledCipherSuites() { return delegate.getEnabledCipherSuites(); }
        @Override public void setEnabledCipherSuites(String[] suites) { delegate.setEnabledCipherSuites(suites); }
        @Override public String[] getSupportedProtocols() { return delegate.getSupportedProtocols(); }
        @Override public String[] getEnabledProtocols() { return delegate.getEnabledProtocols(); }
        @Override public void setEnabledProtocols(String[] protocols) { delegate.setEnabledProtocols(protocols); }
        @Override public SSLSession getSession() { return delegate.getSession(); }
        @Override public SSLSession getHandshakeSession() { return delegate.getHandshakeSession(); }
        @Override public void addHandshakeCompletedListener(HandshakeCompletedListener listener) { delegate.addHandshakeCompletedListener(listener); }
        @Override public void removeHandshakeCompletedListener(HandshakeCompletedListener listener) { delegate.removeHandshakeCompletedListener(listener); }
        @Override public void startHandshake() throws IOException { delegate.startHandshake(); }
        @Override public void setUseClientMode(boolean mode) { delegate.setUseClientMode(mode); }
        @Override public boolean getUseClientMode() { return delegate.getUseClientMode(); }
        @Override public void setNeedClientAuth(boolean need) { delegate.setNeedClientAuth(need); }
        @Override public boolean getNeedClientAuth() { return delegate.getNeedClientAuth(); }
        @Override public void setWantClientAuth(boolean want) { delegate.setWantClientAuth(want); }
        @Override public boolean getWantClientAuth() { return delegate.getWantClientAuth(); }
        @Override public void setEnableSessionCreation(boolean enabled) { delegate.setEnableSessionCreation(enabled); }
        @Override public boolean getEnableSessionCreation() { return delegate.getEnableSessionCreation(); }
        @Override public SSLParameters getSSLParameters() { return delegate.getSSLParameters(); }
        @Override public void setSSLParameters(SSLParameters parameters) { delegate.setSSLParameters(parameters); }
        @Override public String getApplicationProtocol() { return delegate.getApplicationProtocol(); }
        @Override public String getHandshakeApplicationProtocol() { return delegate.getHandshakeApplicationProtocol(); }
        @Override public void connect(SocketAddress address) throws IOException { delegate.connect(address); }
        @Override public void connect(SocketAddress address, int timeout) throws IOException { delegate.connect(address, timeout); }
        @Override public void bind(SocketAddress address) throws IOException { delegate.bind(address); }
        @Override public java.io.InputStream getInputStream() throws IOException { return delegate.getInputStream(); }
        @Override public java.io.OutputStream getOutputStream() throws IOException { return delegate.getOutputStream(); }
        @Override public synchronized void close() throws IOException { delegate.close(); }
        @Override public void setSoTimeout(int timeout) throws SocketException { delegate.setSoTimeout(timeout); }
        @Override public int getSoTimeout() throws SocketException { return delegate.getSoTimeout(); }
        @Override public void setTcpNoDelay(boolean enabled) throws SocketException { delegate.setTcpNoDelay(enabled); }
        @Override public boolean getTcpNoDelay() throws SocketException { return delegate.getTcpNoDelay(); }
        @Override public void setKeepAlive(boolean enabled) throws SocketException { delegate.setKeepAlive(enabled); }
        @Override public boolean getKeepAlive() throws SocketException { return delegate.getKeepAlive(); }
        @Override public InetAddress getInetAddress() { return delegate.getInetAddress(); }
        @Override public InetAddress getLocalAddress() { return delegate.getLocalAddress(); }
        @Override public int getPort() { return delegate.getPort(); }
        @Override public int getLocalPort() { return delegate.getLocalPort(); }
        @Override public SocketAddress getRemoteSocketAddress() { return delegate.getRemoteSocketAddress(); }
        @Override public SocketAddress getLocalSocketAddress() { return delegate.getLocalSocketAddress(); }
        @Override public boolean isConnected() { return delegate.isConnected(); }
        @Override public boolean isBound() { return delegate.isBound(); }
        @Override public boolean isClosed() { return delegate.isClosed(); }
        @Override public boolean isInputShutdown() { return delegate.isInputShutdown(); }
        @Override public boolean isOutputShutdown() { return delegate.isOutputShutdown(); }
        @Override public void shutdownInput() throws IOException { delegate.shutdownInput(); }
        @Override public void shutdownOutput() throws IOException { delegate.shutdownOutput(); }
    }
}
