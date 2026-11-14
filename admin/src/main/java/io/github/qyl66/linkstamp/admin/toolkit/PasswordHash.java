/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.qyl66.linkstamp.admin.toolkit;

/* LinkStamp changes: hash newly created passwords with salted PBKDF2 and verify legacy credentials. */

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

public final class PasswordHash {
    private static final int ITERATIONS = 600_000;
    private static final String PREFIX = "pbkdf2-sha256$600000$";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private PasswordHash() {
    }

    public static String encode(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return PREFIX + HEX.formatHex(salt) + "$" + HEX.formatHex(derive(password, salt));
    }

    public static boolean isEncoded(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    public static boolean matches(String password, String stored) {
        if (password == null || password.length() > 128 || stored == null) {
            return false;
        }
        if (!isEncoded(stored)) {
            return MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8), stored.getBytes(StandardCharsets.UTF_8));
        }
        String value = stored.substring(PREFIX.length());
        if (value.length() != 97 || value.charAt(32) != '$') {
            return false;
        }
        try {
            byte[] salt = HEX.parseHex(value.substring(0, 32));
            byte[] hash = HEX.parseHex(value.substring(33));
            return MessageDigest.isEqual(derive(password, salt), hash);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Password hashing is unavailable", ex);
        } finally {
            spec.clearPassword();
        }
    }
}
