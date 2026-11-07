package com.themuhammad.deepseekmod.extension;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * The built-in file tools.
 *
 * They only touch the app-private workspace, they never run a command, they
 * never use the network and every path is resolved through
 * {@link SdsWorkspaceGuard}. The write tool is not read-only, so it always needs
 * an explicit approval.
 */
public final class SdsWorkspaceTools {
    public static final String LIST = "workspace.list";
    public static final String READ = "workspace.read";
    public static final String WRITE = "workspace.write";

    private static final int MAX_READ_CHARS = 16 * 1024;
    private static final int MAX_WRITE_CHARS = 64 * 1024;
    private static final int MAX_ENTRIES = 200;
    private static final int MAX_DEPTH = 4;

    private SdsWorkspaceTools() {
    }

    /** Builds a registry that contains exactly the workspace tools. */
    public static SdsToolRegistry createRegistry() {
        SdsToolRegistry registry = new SdsToolRegistry();
        registry.register(new ListTool());
        registry.register(new ReadTool());
        registry.register(new WriteTool());
        return registry;
    }

    /** Lists the entries of a directory inside the workspace. */
    public static final class ListTool implements SdsTool {
        @Override
        public String getName() { return LIST; }

        @Override
        public String getDescription() { return "Lists the entries of a directory in the private workspace."; }

        @Override
        public String getSchema() { return "{\"path\": string, \"depth\": int}"; }

        @Override
        public boolean isReadOnly() { return true; }

        @Override
        public boolean requiresNetwork() { return false; }

        @Override
        public SdsToolResult execute(SdsToolContext context, SdsToolCall call) throws Exception {
            try {
                SdsToolArguments arguments = SdsToolArguments.parse(call.getArgumentsJson());
                arguments.requireOnly("path", "depth");
                String path = arguments.getString("path", ".");
                int depth = arguments.getInt("depth", 1);
                if (depth < 1) depth = 1;
                if (depth > MAX_DEPTH) depth = MAX_DEPTH;

                File root = SdsWorkspaceGuard.resolveWithin(context.getWorkspace(), path);
                if (!root.exists()) {
                    return new SdsToolResult(SdsToolResult.Status.FAILED, "", "the path does not exist");
                }
                if (!root.isDirectory()) {
                    return new SdsToolResult(SdsToolResult.Status.FAILED, "", "the path is not a directory");
                }
                List<String> lines = new ArrayList<>();
                collect(root, "", depth, lines, context);
                StringBuilder output = new StringBuilder();
                for (String line : lines) {
                    if (output.length() > 0) output.append('\n');
                    output.append(line);
                }
                return new SdsToolResult(SdsToolResult.Status.SUCCESS, output.toString(), "");
            } catch (SecurityException refused) {
                return new SdsToolResult(SdsToolResult.Status.FAILED, "", "the path was refused");
            } catch (IllegalArgumentException invalid) {
                return new SdsToolResult(SdsToolResult.Status.FAILED, "",
                        "the arguments did not match the schema");
            }
        }

        private void collect(File directory, String prefix, int depth, List<String> lines,
                             SdsToolContext context) {
            if (lines.size() >= MAX_ENTRIES) return;
            File[] children = directory.listFiles();
            if (children == null) return;
            Arrays.sort(children, new Comparator<File>() {
                @Override
                public int compare(File left, File right) {
                    return left.getName().compareTo(right.getName());
                }
            });
            for (File child : children) {
                if (lines.size() >= MAX_ENTRIES) return;
                if (context.isCancelled()) return;
                String name = prefix + child.getName();
                lines.add(name + (child.isDirectory() ? "/" : "") + "\t" + child.length());
                if (child.isDirectory() && depth > 1) {
                    collect(child, name + "/", depth - 1, lines, context);
                }
            }
        }
    }

    /** Reads a text file from the private workspace. */
    public static final class ReadTool implements SdsTool {
        @Override
        public String getName() { return READ; }

        @Override
        public String getDescription() { return "Reads a text file from the private workspace."; }

        @Override
        public String getSchema() { return "{\"path\": string, \"maxChars\": int}"; }

        @Override
        public boolean isReadOnly() { return true; }

        @Override
        public boolean requiresNetwork() { return false; }

        @Override
        public SdsToolResult execute(SdsToolContext context, SdsToolCall call) throws Exception {
            try {
                SdsToolArguments arguments = SdsToolArguments.parse(call.getArgumentsJson());
                arguments.requireOnly("path", "maxChars");
                String path = arguments.requireString("path");
                int maxChars = arguments.getInt("maxChars", 4096);
                if (maxChars < 1) maxChars = 1;
                if (maxChars > MAX_READ_CHARS) maxChars = MAX_READ_CHARS;

                File target = SdsWorkspaceGuard.resolveWithin(context.getWorkspace(), path);
                if (!target.exists()) {
                    return new SdsToolResult(SdsToolResult.Status.FAILED, "", "the file does not exist");
                }
                if (target.isDirectory()) {
                    return new SdsToolResult(SdsToolResult.Status.FAILED, "", "the path is a directory");
                }
                if (target.length() > maxChars * 4L) {
                    return new SdsToolResult(SdsToolResult.Status.FAILED, "", "the file is larger than the limit");
                }
                byte[] content = readBounded(target, maxChars);
                return new SdsToolResult(SdsToolResult.Status.SUCCESS, new String(content, "UTF-8"), "");
            } catch (SecurityException refused) {
                return new SdsToolResult(SdsToolResult.Status.FAILED, "", "the path was refused");
            } catch (IllegalArgumentException invalid) {
                return new SdsToolResult(SdsToolResult.Status.FAILED, "",
                        "the arguments did not match the schema");
            }
        }
    }

    /** Writes a text file inside the private workspace. */
    public static final class WriteTool implements SdsTool {
        @Override
        public String getName() { return WRITE; }

        @Override
        public String getDescription() { return "Writes a text file in the private workspace."; }

        @Override
        public String getSchema() { return "{\"path\": string, \"content\": string}"; }

        @Override
        public boolean isReadOnly() { return false; }

        @Override
        public boolean requiresNetwork() { return false; }

        @Override
        public SdsToolResult execute(SdsToolContext context, SdsToolCall call) throws Exception {
            try {
                SdsToolArguments arguments = SdsToolArguments.parse(call.getArgumentsJson());
                arguments.requireOnly("path", "content");
                String path = arguments.requireString("path");
                String content = arguments.requireString("content");
                if (content.length() > MAX_WRITE_CHARS) {
                    return new SdsToolResult(SdsToolResult.Status.FAILED, "",
                            "the content exceeds the " + MAX_WRITE_CHARS + " character limit");
                }
                File target = SdsWorkspaceGuard.resolveWithin(context.getWorkspace(), path);
                File parent = target.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    return new SdsToolResult(SdsToolResult.Status.FAILED, "",
                            "the directory could not be created");
                }
                if (target.exists() && target.isDirectory()) {
                    return new SdsToolResult(SdsToolResult.Status.FAILED, "", "the path is a directory");
                }
                // The file is written next to the target and then moved into place,
                // so an interrupted write never leaves a half written file behind.
                File temporary = new File(target.getAbsolutePath() + ".sds-part");
                OutputStream output = null;
                try {
                    output = new FileOutputStream(temporary);
                    output.write(content.getBytes("UTF-8"));
                    output.flush();
                } finally {
                    if (output != null) {
                        try {
                            output.close();
                        } catch (IOException ignored) {
                            // The file is closed either way; the result reports the write.
                        }
                    }
                }
                if (!temporary.renameTo(target)) {
                    //noinspection ResultOfMethodCallIgnored
                    temporary.delete();
                    return new SdsToolResult(SdsToolResult.Status.FAILED, "",
                            "the file could not be moved into place");
                }
                return new SdsToolResult(SdsToolResult.Status.SUCCESS,
                        "wrote " + content.length() + " characters", "");
            } catch (SecurityException refused) {
                return new SdsToolResult(SdsToolResult.Status.FAILED, "", "the path was refused");
            } catch (IllegalArgumentException invalid) {
                return new SdsToolResult(SdsToolResult.Status.FAILED, "",
                        "the arguments did not match the schema");
            }
        }
    }

    private static byte[] readBounded(File file, int maxChars) throws IOException {
        InputStream input = null;
        try {
            input = new FileInputStream(file);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int total = 0;
            int read;
            while ((read = input.read(chunk)) > 0) {
                int keep = Math.min(read, maxChars * 4 - total);
                if (keep <= 0) break;
                buffer.write(chunk, 0, keep);
                total += keep;
                if (total >= maxChars * 4) break;
            }
            String text = new String(buffer.toByteArray(), "UTF-8");
            if (text.length() > maxChars) {
                text = text.substring(0, maxChars) + "\n[truncated]";
            }
            return text.getBytes("UTF-8");
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                    // Nothing else can be done here.
                }
            }
        }
    }
}
