package com.mdau.ukena.common;

import java.security.SecureRandom;

/**
 * One-time passwords for accounts we create on someone's behalf (approved
 * makers, invited staff). They're emailed once and should be changed, but
 * until then they must not be guessable — 14 characters from a 56-letter
 * alphabet is about 81 bits.
 */
public final class SecurePasswords {

    /** No 0/O, 1/l/I — these get read out over the phone and typed from email. */
    private static final char[] ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecurePasswords() {}

    public static String generate() {
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 14; i++) {
            if (i == 7) sb.append('-');
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
