package com.themuhammad.deepseekmod.extension;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The checksum of a file.
 *
 * The Linux Studio only unpacks a root filesystem whose checksum is the one of
 * the official list, so a file that was changed on the way cannot become the
 * filesystem of the studio.
 */
public final class SdsHash {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private SdsHash() {
    }

    /** The SHA-256 of a file, written in lower case. */
    public static String sha256(File file) throws IOException {
        if (file == null) throw new IllegalArgumentException("file is required");
        if (!file.isFile()) throw new IOException("the file does not exist");
        MessageDigest digest = digest();
        InputStream input = null;
        try {
            input = new FileInputStream(file);
            byte[] chunk = new byte[64 * 1024];
            int read;
            while ((read = input.read(chunk)) > 0) {
                digest.update(chunk, 0, read);
            }
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                    // The checksum was computed.
                }
            }
        }
        return toHex(digest.digest());
    }

    /** The SHA-256 of a piece of data, written in lower case. */
    public static String sha256(byte[] data) {
        if (data == null) throw new IllegalArgumentException("data is required");
        return toHex(digest().digest(data));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException refused) {
            // Every Android device knows SHA-256.
            throw new IllegalStateException("SHA-256 is not available");
        }
    }

    private static String toHex(byte[] data) {
        StringBuilder builder = new StringBuilder(data.length * 2);
        for (int index = 0; index < data.length; index++) {
            int value = data[index] & 0xff;
            builder.append(HEX[value >>> 4]);
            builder.append(HEX[value & 0x0f]);
        }
        return builder.toString();
    }
}
