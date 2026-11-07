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
    /** The size of the pinned archive, as a cheap second check of the download. */
    public static final long ROOTFS_BYTES = 29936675L;

    /**
     * The files of the PRoot runtime.
     *
     * The runtime is the Proroot build of the reference proof of concept: five
     * unmodified arm64 libraries that belong into {@code lib/arm64-v8a} of the
     * APK. The launcher is the first one and it has to be started from the
     * native library directory of the app, because a file in the private
     * directory of the app may not be executed.
     *
     * The checksums are the ones of the reference build. The runtime is only
     * used when every file is present and every checksum matches, so a
     * half-copied or a changed runtime is refused.
     */
    public static final String LAUNCHER_NAME = "libproroot.so";
    public static final String[] RUNTIME_FILES = {
            "libproroot.so",
            "libproroot-runtime.so",
            "libproroot-bridge.so",
            "libproroot-linker.so",
            "libproroot-stub-loader.so"
    };
    public static final String[] RUNTIME_SHA256 = {
            "a4e74d75b66cdc02b080adfe863dbf9951c3b30610d77beddc95488d5fe5de01",
            "8c47a0a7db32d84c179ebb5bf3640f655a3181860ece5886ae44d92858730c34",
            "1c5bc9537a270e8bf8b1c70222813f57b60b828bfb5503ddf8fe37685092de2f",
            "51a0ec5bfed00e572a0de09e22d9057e2befc386b78e426613d3e0ab03f4ecee",
            "06c6624db3bdc45b9ced151cd781df439a37b47731d244b93e9d6a58cd48cde0"
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
