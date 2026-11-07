package com.themuhammad.deepseekmod.extension;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/**
 * The native export.
 *
 * Two flows are offered and both are handled by the system:
 *
 * <ul>
 *   <li>Save: the system file picker asks the user where the file goes. The
 *       content is written by the system, so the app never needs a storage
 *       permission and the private files are never exposed.</li>
 *   <li>Share: the system share sheet receives the content as text. No file
 *       provider and no permission is involved.</li>
 * </ul>
 *
 * Nothing is written to a place the user did not choose, and nothing is sent
 * anywhere on its own.
 */
public final class SdsExportService {
    public static final int REQUEST_SAVE = 4101;
    public static final String MIME_TEXT = "text/plain";
    public static final String MIME_MARKDOWN = "text/markdown";
    public static final String MIME_CODE = "text/plain";

    private static volatile String pendingContent;
    private static volatile String pendingName;

    private SdsExportService() {
    }

    /** Opens the system file picker so the user can save the content. */
    public static boolean save(Activity activity, String suggestedName, String content, String mimeType) {
        if (activity == null || content == null) return false;
        try {
            pendingContent = content;
            pendingName = suggestedName == null || suggestedName.isEmpty() ? "export.txt" : suggestedName;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(mimeType == null ? MIME_TEXT : mimeType);
            intent.putExtra(Intent.EXTRA_TITLE, pendingName);
            activity.startActivityForResult(intent, REQUEST_SAVE);
            return true;
        } catch (Throwable refused) {
            pendingContent = null;
            pendingName = null;
            return false;
        }
    }

    /** Opens the system share sheet with the content. */
    public static boolean share(Activity activity, String title, String content) {
        if (activity == null || content == null) return false;
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(MIME_TEXT);
            intent.putExtra(Intent.EXTRA_SUBJECT, title == null ? "DeepSeek" : title);
            intent.putExtra(Intent.EXTRA_TEXT, content);
            activity.startActivity(Intent.createChooser(intent, "Share"));
            return true;
        } catch (Throwable refused) {
            return false;
        }
    }

    /**
     * Writes the pending content into the location the user chose.
     *
     * @return true when the file was written.
     */
    public static boolean writePending(Context context, Uri destination) {
        String content = pendingContent;
        pendingContent = null;
        pendingName = null;
        if (destination == null || content == null) return false;
        java.io.OutputStream stream = null;
        try {
            stream = context.getContentResolver().openOutputStream(destination);
            byte[] bytes = content.getBytes(java.nio.charset.Charset.forName("UTF-8"));
            stream.write(bytes);
            stream.flush();
            return true;
        } catch (Throwable refused) {
            return false;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Throwable ignored) {
                    // Nothing else can be done here.
                }
            }
        }
    }

    /** The name of the file the user is about to create. */
    public static String getPendingName() {
        return pendingName;
    }
}
