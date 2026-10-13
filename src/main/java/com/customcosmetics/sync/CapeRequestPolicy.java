// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import java.net.HttpURLConnection;

/** Upload acknowledgements may arrive after a slow Tunnel transfer. Ordinary reads stay short. */
final class CapeRequestPolicy {
    static final int CONNECT_TIMEOUT_MS=15_000;
    static final int READ_TIMEOUT_MS=30_000;
    static final int SUBMISSION_TIMEOUT_MS=180_000;
    private CapeRequestPolicy(){}
    static void configure(HttpURLConnection connection,boolean submission){
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(submission?SUBMISSION_TIMEOUT_MS:READ_TIMEOUT_MS);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept","application/json");
    }
}
