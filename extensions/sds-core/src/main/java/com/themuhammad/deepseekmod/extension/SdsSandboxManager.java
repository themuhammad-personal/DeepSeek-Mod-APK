package com.themuhammad.deepseekmod.extension;

import java.io.File;

/**
 * The state of the Linux Studio runtime.
 *
 * The manager only looks at the file system. It never downloads anything and
 * it never starts a process, so it can be inspected and tested without a
 * device.
 */
public final class SdsSandboxManager {
    private final File nativeLibraryDirectory;
    private final File filesDirectory;

    public SdsSandboxManager(File nativeLibraryDirectory, File filesDirectory) {
        this.nativeLibraryDirectory = nativeLibraryDirectory;
        this.filesDirectory = filesDirectory;
    }

    /** The PRoot launcher, or null when the runtime was not bundled. */
    public File findLauncher() {
        if (nativeLibraryDirectory == null || !nativeLibraryDirectory.isDirectory()) return null;
        for (String name : SdsSandboxConfig.LAUNCHER_NAMES) {
            File candidate = new File(nativeLibraryDirectory, name);
            if (candidate.isFile()) return candidate;
        }
        return null;
    }

    /** True when the PRoot runtime is present. */
    public boolean isRuntimeAvailable() {
        return findLauncher() != null;
    }

    /** The directory the root filesystem is unpacked into. */
    public File getRootfsDirectory() {
        if (filesDirectory == null) return null;
        return new File(filesDirectory, SdsSandboxConfig.ROOTFS_DIRECTORY);
    }

    /** The directory the sandbox mounts as the home of the root user. */
    public File getWorkspaceDirectory() {
        if (filesDirectory == null) return null;
        return new File(filesDirectory, SdsSandboxConfig.WORKSPACE_DIRECTORY);
    }

    /** The marker that records the checksum of the unpacked root filesystem. */
    public File getRootfsMarker() {
        File rootfs = getRootfsDirectory();
        return rootfs == null ? null : new File(rootfs, SdsSandboxConfig.ROOTFS_MARKER);
    }

    /** True when the root filesystem was unpacked and its checksum was recorded. */
    public boolean isRootfsReady() {
        File marker = getRootfsMarker();
        if (marker == null || !marker.isFile()) return false;
        try {
            java.io.FileInputStream stream = new java.io.FileInputStream(marker);
            try {
                byte[] buffer = new byte[SdsSandboxConfig.ROOTFS_SHA256.length()];
                int read = stream.read(buffer);
                if (read != buffer.length) return false;
                return SdsSandboxConfig.ROOTFS_SHA256.equals(
                        new String(buffer, java.nio.charset.Charset.forName("UTF-8")).trim());
            } finally {
                stream.close();
            }
        } catch (java.io.IOException refused) {
            return false;
        }
    }

    /** True when a command can run right now. */
    public boolean isReady() {
        return isRuntimeAvailable() && isRootfsReady();
    }

    /** A short description of the state, shown in the console. */
    public String describe() {
        StringBuilder builder = new StringBuilder();
        builder.append(isRuntimeAvailable()
                ? "the PRoot runtime is bundled"
                : "the PRoot runtime is not bundled in this build");
        builder.append(isRootfsReady()
                ? ", the root filesystem is ready"
                : ", the root filesystem is not downloaded yet");
        return builder.toString();
    }
}
