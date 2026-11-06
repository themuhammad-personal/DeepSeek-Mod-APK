package com.themuhammad.deepseekmod.extension;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

/**
 * A separate, native proposal-review surface. It deliberately has no executor,
 * shell permission, MCP transport, or automatic approval path.
 */
public final class SdsAgentConsoleActivity extends Activity {
    private static final int BG = Color.rgb(13, 17, 25);
    private static final int PANEL = Color.rgb(24, 31, 43);
    private static final int TEXT = Color.rgb(238, 242, 249);
    private static final int MUTED = Color.rgb(159, 171, 190);
    private static final int ACCENT = Color.rgb(92, 132, 255);

    private LinearLayout proposalList;
    private TextView queueStatus;
    private Switch proposalMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
        proposalMode.setChecked(SdsAgentHooks.isEnabled());
        proposalMode.setOnCheckedChangeListener((button, checked) -> {
            SdsAgentHooks.setEnabled(checked);
            refreshProposals();
        });
        refreshProposals();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (proposalList != null) refreshProposals();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));

        TextView title = text("Super DeepSeek", 25, TEXT, true);
        root.addView(title, matchWrap());

        TextView subtitle = text("Native Agent Console", 14, MUTED, false);
        LinearLayout.LayoutParams subParams = matchWrap();
        subParams.topMargin = dp(3);
        root.addView(subtitle, subParams);

        LinearLayout modeCard = card();
        TextView modeTitle = text("Tool proposal mode", 16, TEXT, true);
        modeCard.addView(modeTitle, matchWrap());
        proposalMode = new Switch(this);
        proposalMode.setText("Allow DeepSeek to propose native tools");
        proposalMode.setTextColor(TEXT);
        proposalMode.setTextSize(14);
        LinearLayout.LayoutParams switchParams = matchWrap();
        switchParams.topMargin = dp(8);
        modeCard.addView(proposalMode, switchParams);
        TextView modeNote = text("Session-only. It resets when the app process restarts.", 12, MUTED, false);
        LinearLayout.LayoutParams noteParams = matchWrap();
        noteParams.topMargin = dp(6);
        modeCard.addView(modeNote, noteParams);
        addGap(root, 14);
        root.addView(modeCard, matchWrap());

        LinearLayout warning = card();
        TextView warningText = text(
                "Review-only build: proposals are never executed. Approve/Run, shell, MCP and result handoff are not connected.",
                13, Color.rgb(255, 210, 130), false);
        warning.addView(warningText, matchWrap());
        addGap(root, 12);
        root.addView(warning, matchWrap());

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        queueStatus = text("Pending proposals: 0", 14, TEXT, true);
        controls.addView(queueStatus, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button refresh = button("Refresh");
        refresh.setOnClickListener(v -> refreshProposals());
        controls.addView(refresh, wrapWrap());
        Button clear = button("Clear");
        clear.setOnClickListener(v -> {
            SdsAgentHooks.clearPendingCalls();
            refreshProposals();
        });
        LinearLayout.LayoutParams clearParams = wrapWrap();
        clearParams.leftMargin = dp(8);
        controls.addView(clear, clearParams);
        addGap(root, 8);
        root.addView(controls, matchWrap());

        ScrollView scroll = new ScrollView(this);
        proposalList = new LinearLayout(this);
        proposalList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(proposalList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = dp(8);
        root.addView(scroll, scrollParams);

        TextView footer = text("DeepSeek sign-in and chat remain unchanged. This screen only reviews parsed proposal text.",
                11, MUTED, false);
        LinearLayout.LayoutParams footerParams = matchWrap();
        footerParams.topMargin = dp(10);
        root.addView(footer, footerParams);
        setContentView(root);
    }

    private void refreshProposals() {
        if (proposalList == null) return;
        proposalList.removeAllViews();
        List<SdsToolCall> calls = SdsAgentHooks.snapshotPendingToolCalls();
        queueStatus.setText("Pending proposals: " + calls.size());
        if (calls.isEmpty()) {
            TextView empty = text(
                    SdsAgentHooks.isEnabled()
                            ? "No proposals yet. Continue a chat and refresh this screen after a tool proposal appears."
                            : "Proposal mode is off. Enable it above to collect tool proposals.",
                    14, MUTED, false);
            LinearLayout.LayoutParams p = matchWrap();
            p.topMargin = dp(10);
            proposalList.addView(empty, p);
            return;
        }
        for (SdsToolCall call : calls) {
            proposalList.addView(proposalCard(call), matchWrap());
        }
    }

    private View proposalCard(SdsToolCall call) {
        LinearLayout card = card();
        TextView name = text(call.getName(), 16, TEXT, true);
        card.addView(name, matchWrap());
        TextView id = text("ID: " + call.getId(), 11, MUTED, false);
        LinearLayout.LayoutParams idParams = matchWrap();
        idParams.topMargin = dp(4);
        card.addView(id, idParams);
        TextView args = text(call.getArgumentsJson(), 13, Color.rgb(203, 215, 232), false);
        args.setTypeface(Typeface.MONOSPACE);
        args.setTextIsSelectable(true);
        LinearLayout.LayoutParams argsParams = matchWrap();
        argsParams.topMargin = dp(10);
        card.addView(args, argsParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        Button copy = button("Copy");
        copy.setOnClickListener(v -> copyText(call.getArgumentsJson()));
        actions.addView(copy, wrapWrap());
        Button dismiss = button("Dismiss");
        dismiss.setOnClickListener(v -> {
            SdsAgentHooks.dismissPendingToolCall(call.getId());
            refreshProposals();
        });
        LinearLayout.LayoutParams dismissParams = wrapWrap();
        dismissParams.leftMargin = dp(8);
        actions.addView(dismiss, dismissParams);
        LinearLayout.LayoutParams actionParams = matchWrap();
        actionParams.topMargin = dp(10);
        card.addView(actions, actionParams);
        return card;
    }

    private void copyText(String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("SDS tool proposal", value));
            android.widget.Toast.makeText(this, "Proposal arguments copied", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(14), dp(13), dp(14), dp(13));
        GradientDrawable background = new GradientDrawable();
        background.setColor(PANEL);
        background.setCornerRadius(dp(16));
        layout.setBackground(background);
        return layout;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTextColor(TEXT);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        return button;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void addGap(LinearLayout parent, int heightDp) {
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
