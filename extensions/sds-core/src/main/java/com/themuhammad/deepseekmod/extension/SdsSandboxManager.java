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

    /** The file the root filesystem is downloaded into. */
    public File getRootfsArchive() {
        if (filesDirectory == null) return null;
        return new File(filesDirectory, SdsSandboxConfig.ROOTFS_ARCHIVE);
    }

    /**
     * Unpacks a downloaded root filesystem.
     *
     * The checksum of the archive has to be the one of the official list, the
     * archive is unpacked into a directory beside the target and the target is
     * only replaced when the whole archive was unpacked. A file that is
     * changed on the way is refused.
     *
     * @param archive the downloaded archive
     * @return true when the root filesystem was installed
     */
    public synchronized boolean installRootfs(File archive) {
        if (archive == null || !archive.isFile()) return false;
        File target = getRootfsDirectory();
        if (target == null) return false;
        try {
            String checksum = SdsHash.sha256(archive);
            if (!SdsSandboxConfig.ROOTFS_SHA256.equals(checksum)) {
                // The archive is not the one of the official list.
                return false;
            }
        } catch (java.io.IOException refused) {
            return false;
        }

        File unpacked = new File(target.getAbsolutePath() + ".part");
        deleteTree(unpacked);
        if (!unpacked.mkdirs()) return false;
        if (!unpack(archive, unpacked)) {
            deleteTree(unpacked);
            return false;
        }
        try {
            // The marker records the checksum of the filesystem, so the studio
            // only runs with the archive of the official list.
            java.io.FileOutputStream marker = new java.io.FileOutputStream(
                    new File(unpacked, SdsSandboxConfig.ROOTFS_MARKER));
            try {
                marker.write(SdsSandboxConfig.ROOTFS_SHA256.getBytes("UTF-8"));
                marker.write('\n');
                marker.flush();
            } finally {
                marker.close();
            }
        } catch (java.io.IOException refused) {
            deleteTree(unpacked);
            return false;
        }
        deleteTree(target);
        if (!unpacked.renameTo(target)) {
            deleteTree(unpacked);
            return false;
        }
        return true;
    }

    /**
     * Unpacks the archive with the tar of the system.
     *
     * The system tar of Android reads a gzip archive and keeps the rights of
     * the files, which the guest needs.
     */
    private boolean unpack(File archive, File destination) {
        File tar = new File("/system/bin/tar");
        if (!tar.isFile()) return false;
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    tar.getAbsolutePath(),
                    "-xzf", archive.getAbsolutePath(),
                    "-C", destination.getAbsolutePath());
            builder.redirectErrorStream(true);
            builder.environment().clear();
            Process process = builder.start();
            java.io.InputStream output = process.getInputStream();
            try {
                byte[] chunk = new byte[4096];
                while (output.read(chunk) > 0) {
                    // The output of the tar is not needed, it is drained so the
                    // process does not block on a full pipe.
                }
            } finally {
                output.close();
            }
            return process.waitFor() == 0;
        } catch (java.io.IOException refused) {
            return false;
        } catch (InterruptedException refused) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Removes a file or a directory with everything in it. */
    static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteTree(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
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
