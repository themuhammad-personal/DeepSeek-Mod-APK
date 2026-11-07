package com.themuhammad.deepseekmod.extension;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;

/**
 * Writes a small file so that it cannot be left half written.
 *
 * The content is written into a temporary file in the same directory and the
 * temporary file then takes the place of the target. A process that dies in
 * the middle of a write therefore leaves the previous version of the file
 * intact instead of a truncated one.
 *
 * The stores of the extension use it, so a crash cannot empty the memories,
 * the projects, the artifacts or the configuration of the MCP servers.
 */
public final class SdsAtomicFile {
    private static final String TEMP_SUFFIX = ".sds-tmp";

    private SdsAtomicFile() {
    }

    /**
     * Writes the content into the target file.
     *
     * @param target  the file to write.
     * @param content the content, it must not be null.
     * @param charset the charset of the file.
     * @throws IOException when the file cannot be written.
     */
    public static void write(File target, String content, Charset charset) throws IOException {
        if (target == null) throw new IllegalArgumentException("target is required");
        if (content == null) throw new IllegalArgumentException("content is required");
        if (charset == null) throw new IllegalArgumentException("charset is required");

        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        File temp = new File(parent, target.getName() + TEMP_SUFFIX);
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(temp), charset);
            writer.write(content);
            writer.flush();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                    // The content is already written, the error of the close is not fatal.
                }
            }
        }
        if (!replace(temp, target)) {
            throw new IOException("cannot replace " + target.getName());
        }
    }

    private static boolean replace(File temp, File target) {
        if (temp.renameTo(target)) return true;
        // A few file systems refuse to rename onto an existing file.
        if (target.exists() && !target.delete()) return false;
        return temp.renameTo(target);
    }
}
