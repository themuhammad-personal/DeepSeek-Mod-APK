package com.themuhammad.deepseekmod.extension;

/**
 * The configuration of the Linux Studio.
 *
 * The runtime is PRoot, the same userspace path translation runtime that the
 * reference proof of concept used. PRoot is <b>not</b> a kernel sandbox: the
 * commands run with the identity of the app, so only a command the user
 * approved may ever run, and the runtime must never see a session token.
 *
 * The root filesystem is Ubuntu Base for arm64. It is pinned by its SHA-256
 * and it is only downloaded when the user turns the download on.
 */
public final class SdsSandboxConfig {
    public static final String ROOTFS_URL =
            "https://cdimage.ubuntu.com/ubuntu-base/releases/noble/release/ubuntu-base-24.04.5-base-arm64.tar.gz";
    public static final String ROOTFS_SHA256 =
            "a91d5a93010193712d346d761372b7c9db6dfcf093893161c64ca107f05914f2";
    public static final String ROOTFS_VERSION = "Ubuntu Base 24.04.5 LTS arm64";

    /** The names the PRoot launcher can have inside the native library directory. */
    public static final String[] LAUNCHER_NAMES = {
            "libproroot.so", "libproroot_5.so", "libproroot_4.so", "libproroot_3.so"
    };

    public static final String ROOTFS_DIRECTORY = "sds-rootfs";
    /** The name of the downloaded archive in the app-private directory. */
    public static final String ROOTFS_ARCHIVE = "sds-rootfs.tar.gz";
    /** The largest archive that is accepted. */
    public static final long ROOTFS_MAX_BYTES = 64L * 1024L * 1024L;
    /** The time a single read of the archive may take. */
    public static final int ROOTFS_READ_TIMEOUT_MS = 60_000;
    public static final String ROOTFS_MARKER = "rootfs.sha256";
    public static final String WORKSPACE_DIRECTORY = "workspace";

    public static final int MAX_COMMAND_CHARS = 2_000;
    public static final int MAX_OUTPUT_CHARS = 12_000;
    public static final int DEFAULT_TIMEOUT_SECONDS = 20;
    public static final int MAX_TIMEOUT_SECONDS = 60;

    private SdsSandboxConfig() {
    }

    /** The fixed environment of a sandbox command. */
    public static String[][] environment(String tmpDirectory, String home) {
        return new String[][]{
                {"PROROOT_TMP_DIR", tmpDirectory},
                {"HOME", home},
                {"PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"},
                {"TERM", "xterm"},
                {"TMPDIR", "/tmp"},
        };
    }
}
