package com.themuhammad.deepseekmod.extension;

import java.io.File;
import java.io.IOException;

/**
 * Best-effort canonical-path confinement for app-private workspace operations.
 * Revalidate immediately before each file operation. This is not a kernel sandbox
 * and does not eliminate all time-of-check/time-of-use races.
 */
public final class SdsWorkspaceGuard {
    private SdsWorkspaceGuard() {}

    public static File resolveWithin(File workspace, String relativePath) throws IOException {
        if (workspace == null) throw new IllegalArgumentException("workspace is required");
        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new IllegalArgumentException("relative path is required");
        }
        if (relativePath.indexOf('\u0000') >= 0) {
            throw new SecurityException("NUL bytes are not allowed in file paths");
        }

        File requested = new File(relativePath);
        if (requested.isAbsolute()) {
            throw new SecurityException("absolute paths are not allowed");
        }

        File canonicalRoot = workspace.getCanonicalFile();
        File canonicalTarget = new File(canonicalRoot, relativePath).getCanonicalFile();
        String rootPath = canonicalRoot.getPath();
        String targetPath = canonicalTarget.getPath();

        if (!targetPath.equals(rootPath)
                && !targetPath.startsWith(rootPath + File.separator)) {
            throw new SecurityException("path escapes the private workspace");
        }
        return canonicalTarget;
    }
}
