// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.cape;
import java.util.*;
/** UI visibility only; the backend independently verifies the account UUID and web login. */
public final class CapeAdministrators {
    public static final Map<UUID,String> ACCOUNTS=Map.of(UUID.fromString("db78ac18-99d5-4aee-b1d7-893cfc9e358d"),"ToryHi");
    private CapeAdministrators(){}
    public static boolean includes(UUID uuid){return uuid!=null&&ACCOUNTS.containsKey(uuid);}
}
