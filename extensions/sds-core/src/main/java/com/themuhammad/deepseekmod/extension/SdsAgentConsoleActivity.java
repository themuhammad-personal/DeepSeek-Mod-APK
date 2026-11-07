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
import android.widget.Toast;

import java.io.File;
import java.util.List;

/**
 * The native Agent Console.
 *
 * It shows the agent session, lets the user approve or deny a proposal, stops a
 * running tool, lists the tool allow-list and the observer diagnostics. It never
 * runs an unregistered tool and it never touches the network.
 */
public final class SdsAgentConsoleActivity extends Activity {
    private static final int BG = Color.rgb(13, 17, 25);
    private static final int PANEL = Color.rgb(24, 31, 43);
    private static final int TEXT = Color.rgb(238, 242, 249);
    private static final int MUTED = Color.rgb(159, 171, 190);
    private static final int ACCENT = Color.rgb(92, 132, 255);
    private static final int WARN = Color.rgb(255, 210, 130);

    private final SdsAgentSession session = new SdsAgentSession();
    private SdsToolRegistry registry;
    private SdsToolExecutor executor;

    private LinearLayout proposalList;
    private LinearLayout timelineList;
    private LinearLayout toolList;
    private TextView queueStatus;
    private TextView sessionStatus;
    private TextView diagnosticsStatus;
    private TextView resultStatus;
    private Switch proposalMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        File workspace = new File(getFilesDir(), "workspace");
        //noinspection ResultOfMethodCallIgnored
        workspace.mkdirs();
        registry = SdsWorkspaceTools.createRegistry();
        executor = new SdsToolExecutor(registry, SdsExecutionPolicy.SAFE_DEFAULT);
        buildScreen();
        proposalMode.setChecked(SdsAgentHooks.isEnabled());
        proposalMode.setOnCheckedChangeListener((button, checked) -> {
            SdsAgentHooks.setEnabled(checked);
            refresh();
        });
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (proposalList != null) refresh();
    }

    @Override
    protected void onDestroy() {
        if (executor != null) executor.shutdown();
        super.onDestroy();
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
        modeCard.addView(text("Tool proposal mode", 16, TEXT, true), matchWrap());
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
        addGap(root, 12);
        root.addView(modeCard, matchWrap());

        LinearLayout sessionCard = card();
        sessionCard.addView(text("Agent session", 16, TEXT, true), matchWrap());
        sessionStatus = text("", 13, TEXT, false);
        LinearLayout.LayoutParams sessionParams = matchWrap();
        sessionParams.topMargin = dp(6);
        sessionCard.addView(sessionStatus, sessionParams);
        resultStatus = text("", 12, MUTED, false);
        LinearLayout.LayoutParams resultParams = matchWrap();
        resultParams.topMargin = dp(6);
        sessionCard.addView(resultStatus, resultParams);

        LinearLayout sessionActions = new LinearLayout(this);
        sessionActions.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        Button approve = button("Approve");
        approve.setOnClickListener(v -> onApprove());
        sessionActions.addView(approve, wrapWrap());
        Button deny = button("Deny");
        deny.setOnClickListener(v -> onDeny());
        LinearLayout.LayoutParams denyParams = wrapWrap();
        denyParams.leftMargin = dp(8);
        sessionActions.addView(deny, denyParams);
        Button stop = button("Stop");
        stop.setOnClickListener(v -> onStop());
        LinearLayout.LayoutParams stopParams = wrapWrap();
        stopParams.leftMargin = dp(8);
        sessionActions.addView(stop, stopParams);
        LinearLayout.LayoutParams sessionActionParams = matchWrap();
        sessionActionParams.topMargin = dp(10);
        sessionCard.addView(sessionActions, sessionActionParams);
        addGap(root, 12);
        root.addView(sessionCard, matchWrap());

        LinearLayout diagnosticsCard = card();
        diagnosticsCard.addView(text("Observer diagnostics", 16, TEXT, true), matchWrap());
        diagnosticsStatus = text("", 12, MUTED, false);
        LinearLayout.LayoutParams diagnosticsParams = matchWrap();
        diagnosticsParams.topMargin = dp(6);
        diagnosticsCard.addView(diagnosticsStatus, diagnosticsParams);
        Button resetDiagnostics = button("Reset diagnostics");
        resetDiagnostics.setOnClickListener(v -> {
            SdsAgentHooks.getDiagnostics().reset();
            refresh();
        });
        LinearLayout.LayoutParams resetParams = wrapWrap();
        resetParams.topMargin = dp(8);
        diagnosticsCard.addView(resetDiagnostics, resetParams);
        addGap(root, 12);
        root.addView(diagnosticsCard, matchWrap());

        LinearLayout toolsCard = card();
        toolsCard.addView(text("Tool allow-list", 16, TEXT, true), matchWrap());
        toolList = new LinearLayout(this);
        toolList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams toolParams = matchWrap();
        toolParams.topMargin = dp(6);
        toolsCard.addView(toolList, toolParams);
        addGap(root, 12);
        root.addView(toolsCard, matchWrap());

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        queueStatus = text("Pending proposals: 0", 14, TEXT, true);
        controls.addView(queueStatus, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button refreshButton = button("Refresh");
        refreshButton.setOnClickListener(v -> refresh());
        controls.addView(refreshButton, wrapWrap());
        Button clear = button("Clear");
        clear.setOnClickListener(v -> {
            SdsAgentHooks.clearPendingCalls();
            session.reset();
            refresh();
        });
        LinearLayout.LayoutParams clearParams = wrapWrap();
        clearParams.leftMargin = dp(8);
        controls.addView(clear, clearParams);
        addGap(root, 8);
        root.addView(controls, matchWrap());

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        proposalList = new LinearLayout(this);
        proposalList.setOrientation(LinearLayout.VERTICAL);
        content.addView(proposalList, matchWrap());
        content.addView(text("Activity timeline", 16, TEXT, true), matchWrap());
        timelineList = new LinearLayout(this);
        timelineList.setOrientation(LinearLayout.VERTICAL);
        content.addView(timelineList, matchWrap());
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = dp(8);
        root.addView(scroll, scrollParams);

        TextView footer = text("DeepSeek sign-in and chat remain unchanged. A tool only runs after an explicit approval.",
                11, MUTED, false);
        LinearLayout.LayoutParams footerParams = matchWrap();
        footerParams.topMargin = dp(10);
        root.addView(footer, footerParams);
        setContentView(root);
    }

    private void refresh() {
        refreshProposals();
        refreshSession();
        refreshDiagnostics();
        refreshTimeline();
        refreshTools();
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
        card.addView(text(call.getName(), 16, TEXT, true), matchWrap());
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
        Button approve = button("Approve");
        approve.setOnClickListener(v -> {
            onProposal(call);
            onApprove();
        });
        LinearLayout.LayoutParams approveParams = wrapWrap();
        approveParams.leftMargin = dp(8);
        actions.addView(approve, approveParams);
        Button dismiss = button("Dismiss");
        dismiss.setOnClickListener(v -> {
            SdsAgentHooks.dismissPendingToolCall(call.getId());
            refresh();
        });
        LinearLayout.LayoutParams dismissParams = wrapWrap();
        dismissParams.leftMargin = dp(8);
        actions.addView(dismiss, dismissParams);
        LinearLayout.LayoutParams actionParams = matchWrap();
        actionParams.topMargin = dp(10);
        card.addView(actions, actionParams);
        return card;
    }

    private void onProposal(SdsToolCall call) {
        session.onProposal(call);
    }

    private void onApprove() {
        String callId = session.getPendingCallId();
        if (callId == null || callId.isEmpty()) {
            Toast.makeText(this, "There is no proposal to approve", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!session.approve(callId)) {
            Toast.makeText(this, "The proposal cannot be approved in this state", Toast.LENGTH_SHORT).show();
            return;
        }
        SdsToolCall call = findPending(callId);
        if (call == null) {
            Toast.makeText(this, "The proposal is no longer available", Toast.LENGTH_SHORT).show();
            refresh();
            return;
        }
        runApproved(call);
    }

    private void runApproved(SdsToolCall call) {
        if (!session.onRunStarted()) return;
        refresh();
        final SdsToolCall approved = call;
        Thread worker = new Thread(() -> {
            SdsToolContext context = new SdsToolContext(
                    new File(getFilesDir(), "workspace"),
                    SdsExecutionPolicy.SAFE_DEFAULT,
                    session.getCancellation());
            SdsToolResult result = executor.run(approved, context, true);
            runOnUiThread(() -> {
                if (result.getStatus() == SdsToolResult.Status.SUCCESS) {
                    session.onRunSucceeded(result);
                } else {
                    session.onRunFailed(result);
                }
                refresh();
            });
        }, "sds-console-tool");
        worker.start();
    }

    private void onDeny() {
        String callId = session.getPendingCallId();
        if (callId == null || callId.isEmpty() || !session.deny(callId)) {
            Toast.makeText(this, "There is no proposal to deny", Toast.LENGTH_SHORT).show();
            return;
        }
        SdsAgentHooks.dismissPendingToolCall(callId);
        refresh();
    }

    private void onStop() {
        if (!session.requestStop()) {
            Toast.makeText(this, "There is nothing to stop", Toast.LENGTH_SHORT).show();
            return;
        }
        refresh();
    }

    private SdsToolCall findPending(String callId) {
        for (SdsToolCall call : SdsAgentHooks.snapshotPendingToolCalls()) {
            if (call.getId().equals(callId)) return call;
        }
        return null;
    }

    private void refreshSession() {
        if (sessionStatus == null) return;
        StringBuilder builder = new StringBuilder();
        builder.append("State: ").append(session.getState().name()).append('\n');
        builder.append("Pending tool: ")
                .append(session.getPendingToolName().isEmpty() ? "none" : session.getPendingToolName())
                .append('\n');
        builder.append("Cancellation: ")
                .append(session.getCancellation().isCancelled() ? "requested" : "not requested");
        sessionStatus.setText(builder.toString());
        if (resultStatus != null) {
            SdsToolResult result = session.getLastResult();
            resultStatus.setText(result == null
                    ? "No tool has run yet."
                    : "Last result: " + result.getStatus().name()
                    + (result.getSafeError().isEmpty() ? "" : " - " + result.getSafeError()));
        }
    }

    private void refreshDiagnostics() {
        if (diagnosticsStatus == null) return;
        diagnosticsStatus.setText(SdsAgentHooks.getDiagnostics().summary());
    }

    private void refreshTimeline() {
        if (timelineList == null) return;
        timelineList.removeAllViews();
        List<SdsAgentEvent> events = session.getEvents();
        if (events.isEmpty()) {
            timelineList.addView(text("No activity yet.", 13, MUTED, false), matchWrap());
            return;
        }
        for (int index = events.size() - 1; index >= 0; index--) {
            SdsAgentEvent event = events.get(index);
            TextView line = text(event.getType().name()
                    + (event.getDetail().isEmpty() ? "" : " - " + event.getDetail()), 12, MUTED, false);
            LinearLayout.LayoutParams params = matchWrap();
            params.topMargin = dp(3);
            timelineList.addView(line, params);
        }
    }

    private void refreshTools() {
        if (toolList == null) return;
        toolList.removeAllViews();
        for (String line : registry.describe()) {
            TextView view = text(line, 12, Color.rgb(203, 215, 232), false);
            view.setTypeface(Typeface.MONOSPACE);
            LinearLayout.LayoutParams params = matchWrap();
            params.topMargin = dp(4);
            toolList.addView(view, params);
        }
        TextView note = text("Only these tools can run. There is no shell and no network access.",
                11, WARN, false);
        LinearLayout.LayoutParams noteParams = matchWrap();
        noteParams.topMargin = dp(8);
        toolList.addView(note, noteParams);
    }

    private void copyText(String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("SDS tool proposal", value));
            Toast.makeText(this, "Proposal arguments copied", Toast.LENGTH_SHORT).show();
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
