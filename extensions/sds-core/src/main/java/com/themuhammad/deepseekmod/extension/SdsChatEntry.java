package com.themuhammad.deepseekmod.extension;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/**
 * The native Agent Console entry that is added to the official chat screen.
 *
 * The entry is a plain Android view that is placed in the activity window. It
 * does not touch the Compose tree, the message renderer or the composer, and it
 * only opens the native Console Activity.
 *
 * The whole class is defensive on purpose: a failure to attach the entry must
 * never disturb the official chat.
 */
public final class SdsChatEntry {
    private static final int ENTRY_ID = 0x53445300;
    private static final int MARGIN_DP = 12;
    private static final int BOTTOM_MARGIN_DP = 96;

    private static WeakReference<View> attached;

    private SdsChatEntry() {
    }

    /** Adds the entry to the activity window. Safe to call more than once. */
    public static void attach(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        try {
            SdsAppContext.initialize(activity.getApplicationContext());
        } catch (Throwable ignored) {
            // The runtime stays inactive when it cannot be prepared.
        }
        try {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        attachOnUiThread(activity);
                    } catch (Throwable ignored) {
                        // The official chat must keep working even when the entry fails.
                    }
                }
            });
        } catch (Throwable ignored) {
            // Same reason: never disturb the official chat.
        }
    }

    private static void attachOnUiThread(Activity activity) {
        if (activity.isFinishing()) return;
        ViewGroup root = activity.findViewById(android.R.id.content);
        if (!(root instanceof FrameLayout)) return;

        View existing = root.findViewById(ENTRY_ID);
        if (existing != null) {
            attached = new WeakReference<>(existing);
            return;
        }

        TextView entry = new TextView(activity);
        entry.setId(ENTRY_ID);
        entry.setText("Agent");
        entry.setTextSize(12);
        entry.setTextColor(Color.WHITE);
        entry.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        entry.setGravity(Gravity.CENTER);
        entry.setPadding(dp(activity, 14), dp(activity, 7), dp(activity, 14), dp(activity, 7));
        entry.setContentDescription("Super DeepSeek Agent Console");
        entry.setBackground(pill(activity));
        entry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openConsole(activity);
            }
        });

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        params.rightMargin = dp(activity, MARGIN_DP);
        params.bottomMargin = dp(activity, BOTTOM_MARGIN_DP);
        root.addView(entry, params);
        attached = new WeakReference<>(entry);
    }

    /** Removes the entry from the activity window. */
    public static void detach() {
        View view = attached == null ? null : attached.get();
        if (view == null) return;
        try {
            ViewGroup parent = (ViewGroup) view.getParent();
            if (parent != null) parent.removeView(view);
        } catch (Throwable ignored) {
            // The entry is gone either way.
        }
        attached = null;
    }

    /** True when an entry is currently attached. */
    public static boolean isAttached() {
        View view = attached == null ? null : attached.get();
        return view != null && view.getParent() != null;
    }

    private static void openConsole(Activity activity) {
        try {
            Intent intent = new Intent(activity, SdsAgentConsoleActivity.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            activity.startActivity(intent);
        } catch (Throwable ignored) {
            // A failed start must not crash the chat.
        }
    }

    private static GradientDrawable pill(Activity activity) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(235, 92, 132, 255));
        background.setCornerRadius(dp(activity, 18));
        background.setStroke(dp(activity, 1), Color.argb(90, 255, 255, 255));
        return background;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
