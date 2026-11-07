package com.themuhammad.deepseekmod.extension;

import android.content.Context;

import java.io.File;

/**
 * The Android glue of the native runtime.
 *
 * It fills the runtime registry with the app-private directories. Everything it
 * touches lives inside the application data directory, so it is removed with the
 * app and it is never reachable by another app.
 */
public final class SdsAppContext {
    public static final String WORKSPACE_DIRECTORY = "workspace";
    public static final String MEMORY_FILE = "sds-memory.txt";
    public static final String PROJECT_FILE = "sds-projects.txt";
    public static final String ARTIFACT_FILE = "sds-artifacts.txt";

    private SdsAppContext() {
    }

    /**
     * Prepares the runtime. Safe to call more than once.
     */
    public static synchronized void initialize(Context context) {
        if (context == null) return;
        try {
            Context application = context.getApplicationContext();
            Context target = application == null ? context : application;
            File files = target.getFilesDir();
            if (files == null) return;

            File workspace = new File(files, WORKSPACE_DIRECTORY);
            if (!workspace.exists()) {
                //noinspection ResultOfMethodCallIgnored
                workspace.mkdirs();
            }
            SdsRuntimeRegistry.setWorkspace(workspace);
            SdsRuntimeRegistry.setMemoryStore(new SdsMemoryStore(new File(files, MEMORY_FILE)));
            SdsRuntimeRegistry.setProjectRegistry(new SdsProjectRegistry(new File(files, PROJECT_FILE)));
            SdsRuntimeRegistry.setArtifactStore(new SdsArtifactStore(new File(files, ARTIFACT_FILE)));
            File nativeLibraryDirectory = null;
            try {
                nativeLibraryDirectory = new File(target.getApplicationInfo().nativeLibraryDir);
            } catch (Throwable ignored) {
                // The runtime stays unavailable when the path is unknown.
            }
            SdsRuntimeRegistry.setSandboxManager(new SdsSandboxManager(nativeLibraryDirectory, files));
            SdsRuntimeRegistry.ensureDefaults();
        } catch (Throwable ignored) {
            // A feature that cannot prepare itself stays inactive.
        }
    }
}
