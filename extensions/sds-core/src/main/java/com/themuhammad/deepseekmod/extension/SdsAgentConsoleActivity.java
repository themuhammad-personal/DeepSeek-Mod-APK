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
import android.widget.EditText;
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
    private LinearLayout memoryList;
    private LinearLayout projectList;
    private LinearLayout commandList;
    private LinearLayout artifactList;
    private LinearLayout uiSurface;
    private TextView studioStatus;
    private TextView studioOutput;
    private Switch sandboxSwitch;
    private EditText studioInput;
    private TextView researchOutput;
    private EditText researchInput;
    private LinearLayout mcpList;
    private EditText mcpNameInput;
    private EditText mcpUrlInput;
    private EditText mcpTokenInput;
    private Switch networkSwitch;
    private TextView queueStatus;
    private TextView sessionStatus;
    private TextView diagnosticsStatus;
    private TextView resultStatus;
    private Switch proposalMode;
    private Switch includeMemory;
    private Switch handoffSwitch;
    private EditText memoryInput;
    private EditText projectInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        File workspace = new File(getFilesDir(), "workspace");
        //noinspection ResultOfMethodCallIgnored
        workspace.mkdirs();
        SdsAppContext.initialize(getApplicationContext());
        registry = buildToolRegistry();
        executor = new SdsToolExecutor(registry, SdsRuntimeRegistry.currentPolicy());
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
        handoffSwitch = new Switch(this);
        handoffSwitch.setText("Return the result of a tool to the chat");
        handoffSwitch.setTextColor(TEXT);
        handoffSwitch.setTextSize(13);
        handoffSwitch.setChecked(SdsToolResultHandoff.isEnabled());
        handoffSwitch.setOnCheckedChangeListener((button, checked) -> {
            SdsToolResultHandoff.setEnabled(checked);
        });
        addGap(root, 8);
        root.addView(handoffSwitch, matchWrap());
        TextView handoffNote = text(
                "Off by default: the flow is proved statically, not on a device. The result is "
                        + "added once, marked with " + SdsToolResultHandoff.MARKER + ".",
                11, MUTED, false);
        LinearLayout.LayoutParams handoffNoteParams = matchWrap();
        handoffNoteParams.topMargin = dp(4);
        root.addView(handoffNote, handoffNoteParams);

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
        stop.setOnClickListener(v -> onStopClicked());
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

        LinearLayout memoryCard = card();
        memoryCard.addView(text("Memories", 16, TEXT, true), matchWrap());
        TextView memoryNote = text(
                "App-private notes. They are added to a prompt only when you ask for /memory "
                        + "or turn the switch on.", 11, MUTED, false);
        LinearLayout.LayoutParams memoryNoteParams = matchWrap();
        memoryNoteParams.topMargin = dp(4);
        memoryCard.addView(memoryNote, memoryNoteParams);
        includeMemory = new Switch(this);
        includeMemory.setText("Add the memories to every prompt");
        includeMemory.setTextColor(TEXT);
        includeMemory.setTextSize(13);
        includeMemory.setChecked(SdsRuntimeRegistry.isIncludeMemoryInPrompt());
        includeMemory.setOnCheckedChangeListener((button, checked) ->
                SdsRuntimeRegistry.setIncludeMemoryInPrompt(checked));
        LinearLayout.LayoutParams includeMemoryParams = matchWrap();
        includeMemoryParams.topMargin = dp(6);
        memoryCard.addView(includeMemory, includeMemoryParams);
        memoryInput = new EditText(this);
        memoryInput.setHint("key = note");
        memoryInput.setTextColor(TEXT);
        memoryInput.setHintTextColor(MUTED);
        memoryInput.setTextSize(13);
        LinearLayout.LayoutParams memoryInputParams = matchWrap();
        memoryInputParams.topMargin = dp(6);
        memoryCard.addView(memoryInput, memoryInputParams);
        Button addMemory = button("Add memory");
        addMemory.setOnClickListener(v -> onAddMemory());
        LinearLayout.LayoutParams addMemoryParams = wrapWrap();
        addMemoryParams.topMargin = dp(6);
        memoryCard.addView(addMemory, addMemoryParams);
        memoryList = new LinearLayout(this);
        memoryList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams memoryListParams = matchWrap();
        memoryListParams.topMargin = dp(6);
        memoryCard.addView(memoryList, memoryListParams);
        addGap(root, 12);
        root.addView(memoryCard, matchWrap());

        LinearLayout projectCard = card();
        projectCard.addView(text("Projects", 16, TEXT, true), matchWrap());
        TextView projectNote = text(
                "A project is a named folder in the private workspace.", 11, MUTED, false);
        LinearLayout.LayoutParams projectNoteParams = matchWrap();
        projectNoteParams.topMargin = dp(4);
        projectCard.addView(projectNote, projectNoteParams);
        projectInput = new EditText(this);
        projectInput.setHint("project name");
        projectInput.setTextColor(TEXT);
        projectInput.setHintTextColor(MUTED);
        projectInput.setTextSize(13);
        LinearLayout.LayoutParams projectInputParams = matchWrap();
        projectInputParams.topMargin = dp(6);
        projectCard.addView(projectInput, projectInputParams);
        Button addProject = button("Create project");
        addProject.setOnClickListener(v -> onCreateProject());
        LinearLayout.LayoutParams addProjectParams = wrapWrap();
        addProjectParams.topMargin = dp(6);
        projectCard.addView(addProject, addProjectParams);
        projectList = new LinearLayout(this);
        projectList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams projectListParams = matchWrap();
        projectListParams.topMargin = dp(6);
        projectCard.addView(projectList, projectListParams);
        addGap(root, 12);
        root.addView(projectCard, matchWrap());

        LinearLayout mcpCard = card();
        mcpCard.addView(text("MCP servers", 16, TEXT, true), matchWrap());
        TextView mcpNote = text(
                "A server the user entered. The tools of the server join the allow-list under "
                        + "mcp.<server>.<tool> and every call needs its own approval.",
                11, MUTED, false);
        LinearLayout.LayoutParams mcpNoteParams = matchWrap();
        mcpNoteParams.topMargin = dp(4);
        mcpCard.addView(mcpNote, mcpNoteParams);
        networkSwitch = new Switch(this);
        networkSwitch.setText("Allow the agent to use the network");
        networkSwitch.setTextColor(TEXT);
        networkSwitch.setTextSize(13);
        networkSwitch.setChecked(SdsRuntimeRegistry.isNetworkAllowed());
        networkSwitch.setOnCheckedChangeListener((button, checked) -> {
            SdsRuntimeRegistry.setNetworkAllowed(checked);
            rebuildExecutor();
            refreshTools();
        });
        LinearLayout.LayoutParams networkSwitchParams = matchWrap();
        networkSwitchParams.topMargin = dp(6);
        mcpCard.addView(networkSwitch, networkSwitchParams);
        mcpNameInput = new EditText(this);
        mcpNameInput.setHint("server name");
        mcpNameInput.setTextColor(TEXT);
        mcpNameInput.setHintTextColor(MUTED);
        mcpNameInput.setTextSize(13);
        LinearLayout.LayoutParams mcpNameParams = matchWrap();
        mcpNameParams.topMargin = dp(6);
        mcpCard.addView(mcpNameInput, mcpNameParams);
        mcpUrlInput = new EditText(this);
        mcpUrlInput.setHint("https://host/path");
        mcpUrlInput.setTextColor(TEXT);
        mcpUrlInput.setHintTextColor(MUTED);
        mcpUrlInput.setTextSize(13);
        LinearLayout.LayoutParams mcpUrlParams = matchWrap();
        mcpUrlParams.topMargin = dp(4);
        mcpCard.addView(mcpUrlInput, mcpUrlParams);
        mcpTokenInput = new EditText(this);
        mcpTokenInput.setHint("token, optional and kept on the device");
        mcpTokenInput.setTextColor(TEXT);
        mcpTokenInput.setHintTextColor(MUTED);
        mcpTokenInput.setTextSize(13);
        LinearLayout.LayoutParams mcpTokenParams = matchWrap();
        mcpTokenParams.topMargin = dp(4);
        mcpCard.addView(mcpTokenInput, mcpTokenParams);
        Button addServer = button("Add the server");
        addServer.setOnClickListener(v -> onAddMcpServer());
        LinearLayout.LayoutParams addServerParams = wrapWrap();
        addServerParams.topMargin = dp(6);
        mcpCard.addView(addServer, addServerParams);
        Button refreshServers = button("Ask the servers for their tools");
        refreshServers.setOnClickListener(v -> onRefreshMcpTools());
        LinearLayout.LayoutParams refreshServersParams = wrapWrap();
        refreshServersParams.topMargin = dp(6);
        mcpCard.addView(refreshServers, refreshServersParams);
        mcpList = new LinearLayout(this);
        mcpList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mcpListParams = matchWrap();
        mcpListParams.topMargin = dp(6);
        mcpCard.addView(mcpList, mcpListParams);
        addGap(root, 12);
        root.addView(mcpCard, matchWrap());

        LinearLayout researchCard = card();
        researchCard.addView(text("Deep Research", 16, TEXT, true), matchWrap());
        TextView researchNote = text(
                "Fetches one address and returns its text. The synthesis is written by the "
                        + "model in the chat, the app never invents an answer.",
                11, MUTED, false);
        LinearLayout.LayoutParams researchNoteParams = matchWrap();
        researchNoteParams.topMargin = dp(4);
        researchCard.addView(researchNote, researchNoteParams);
        researchInput = new EditText(this);
        researchInput.setHint("https://address");
        researchInput.setTextColor(TEXT);
        researchInput.setHintTextColor(MUTED);
        researchInput.setTextSize(13);
        LinearLayout.LayoutParams researchInputParams = matchWrap();
        researchInputParams.topMargin = dp(6);
        researchCard.addView(researchInput, researchInputParams);
        Button fetchAddress = button("Fetch the address after an approval");
        fetchAddress.setOnClickListener(v -> onFetchResearchAddress());
        LinearLayout.LayoutParams fetchAddressParams = wrapWrap();
        fetchAddressParams.topMargin = dp(6);
        researchCard.addView(fetchAddress, fetchAddressParams);
        researchOutput = new TextView(this);
        researchOutput.setTextColor(Color.rgb(180, 210, 255));
        researchOutput.setTextSize(12);
        researchOutput.setVisibility(View.GONE);
        LinearLayout.LayoutParams researchOutputParams = matchWrap();
        researchOutputParams.topMargin = dp(6);
        researchCard.addView(researchOutput, researchOutputParams);
        addGap(root, 12);
        root.addView(researchCard, matchWrap());

        LinearLayout studioCard = card();
        studioCard.addView(text("Linux Studio", 16, TEXT, true), matchWrap());
        TextView studioNote = text(
                "A PRoot runtime with an Ubuntu base filesystem. PRoot translates the paths "
                        + "in user space, it is not a kernel sandbox, so every command needs "
                        + "your explicit approval.",
                11, MUTED, false);
        LinearLayout.LayoutParams studioNoteParams = matchWrap();
        studioNoteParams.topMargin = dp(4);
        studioCard.addView(studioNote, studioNoteParams);
        studioStatus = text("The state of the runtime is unknown.", 12, Color.rgb(203, 215, 232), false);
        LinearLayout.LayoutParams studioStatusParams = matchWrap();
        studioStatusParams.topMargin = dp(6);
        studioCard.addView(studioStatus, studioStatusParams);
        sandboxSwitch = new Switch(this);
        sandboxSwitch.setText("Offer the sandbox command to the agent");
        sandboxSwitch.setTextColor(TEXT);
        sandboxSwitch.setTextSize(13);
        sandboxSwitch.setChecked(SdsRuntimeRegistry.isSandboxEnabled());
        sandboxSwitch.setOnCheckedChangeListener((button, checked) -> {
            SdsRuntimeRegistry.setSandboxEnabled(checked);
            registry = buildToolRegistry();
            refreshTools();
        });
        LinearLayout.LayoutParams sandboxSwitchParams = matchWrap();
        sandboxSwitchParams.topMargin = dp(6);
        studioCard.addView(sandboxSwitch, sandboxSwitchParams);
        studioInput = new EditText(this);
        studioInput.setHint("command, for example uname -a");
        studioInput.setTextColor(TEXT);
        studioInput.setHintTextColor(MUTED);
        studioInput.setTextSize(13);
        LinearLayout.LayoutParams studioInputParams = matchWrap();
        studioInputParams.topMargin = dp(6);
        studioCard.addView(studioInput, studioInputParams);
        Button runCommand = button("Run the command after an approval");
        runCommand.setOnClickListener(v -> onRunSandboxCommand());
        LinearLayout.LayoutParams runCommandParams = wrapWrap();
        runCommandParams.topMargin = dp(6);
        studioCard.addView(runCommand, runCommandParams);
        studioOutput = new TextView(this);
        studioOutput.setTextColor(Color.rgb(180, 210, 255));
        studioOutput.setTextSize(12);
        studioOutput.setTypeface(Typeface.MONOSPACE);
        studioOutput.setBackgroundColor(Color.rgb(18, 24, 34));
        studioOutput.setPadding(dp(8), dp(8), dp(8), dp(8));
        studioOutput.setVisibility(View.GONE);
        LinearLayout.LayoutParams studioOutputParams = matchWrap();
        studioOutputParams.topMargin = dp(6);
        studioCard.addView(studioOutput, studioOutputParams);
        addGap(root, 12);
        root.addView(studioCard, matchWrap());

        LinearLayout uiCard = card();
        uiCard.addView(text("Generative UI", 16, TEXT, true), matchWrap());
        TextView uiNote = text(
                "An interface the assistant asked for. It is built from plain Android views, "
                        + "so no script and no remote content is involved.",
                11, MUTED, false);
        LinearLayout.LayoutParams uiNoteParams = matchWrap();
        uiNoteParams.topMargin = dp(4);
        uiCard.addView(uiNote, uiNoteParams);
        uiSurface = new LinearLayout(this);
        uiSurface.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams uiSurfaceParams = matchWrap();
        uiSurfaceParams.topMargin = dp(6);
        uiCard.addView(uiSurface, uiSurfaceParams);
        addGap(root, 12);
        root.addView(uiCard, matchWrap());

        LinearLayout artifactCard = card();
        artifactCard.addView(text("Artifacts", 16, TEXT, true), matchWrap());
        TextView artifactNote = text(
                "Content the assistant produced. It stays in the private directory until you save or share it.",
                11, MUTED, false);
        LinearLayout.LayoutParams artifactNoteParams = matchWrap();
        artifactNoteParams.topMargin = dp(4);
        artifactCard.addView(artifactNote, artifactNoteParams);
        artifactList = new LinearLayout(this);
        artifactList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams artifactListParams = matchWrap();
        artifactListParams.topMargin = dp(6);
        artifactCard.addView(artifactList, artifactListParams);
        addGap(root, 12);
        root.addView(artifactCard, matchWrap());

        Button exportActivity = button("Export the activity to a file");
        exportActivity.setOnClickListener(v -> onExportTimeline());
        LinearLayout.LayoutParams exportParams = wrapWrap();
        exportParams.topMargin = dp(6);
        root.addView(exportActivity, exportParams);

        LinearLayout commandCard = card();
        commandCard.addView(text("Slash commands", 16, TEXT, true), matchWrap());
        TextView commandNote = text(
                "Type a command at the start of a chat message. It is expanded natively before the prompt is sent.",
                11, MUTED, false);
        LinearLayout.LayoutParams commandNoteParams = matchWrap();
        commandNoteParams.topMargin = dp(4);
        commandCard.addView(commandNote, commandNoteParams);
        commandList = new LinearLayout(this);
        commandList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams commandListParams = matchWrap();
        commandListParams.topMargin = dp(6);
        commandCard.addView(commandList, commandListParams);
        addGap(root, 12);
        root.addView(commandCard, matchWrap());

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

    /**
     * Builds the allow-list of the console.
     *
     * The sandbox command is only on the list when the user turned it on, so
     * the model cannot reach it while the switch is off.
     */
    private SdsToolRegistry buildToolRegistry() {
        SdsToolRegistry built = SdsWorkspaceTools.createRegistry();
        for (SdsTool tool : SdsArtifactTools.createRegistry().all()) {
            built.register(tool);
        }
        for (SdsTool tool : SdsUiTools.createRegistry().all()) {
            built.register(tool);
        }
        if (SdsRuntimeRegistry.isSandboxEnabled()) {
            for (SdsTool tool : SdsSandboxTool.createRegistry().all()) {
                built.register(tool);
            }
        }
        for (SdsTool tool : SdsResearchTools.createRegistry().all()) {
            built.register(tool);
        }
        for (SdsTool tool : SdsMcpTools.createRegistry(SdsRuntimeRegistry.getMcpConfigStore()).all()) {
            built.register(tool);
        }
        return built;
    }

    private void refresh() {
        refreshProposals();
        refreshSession();
        refreshDiagnostics();
        refreshTimeline();
        refreshTools();
        refreshMemories();
        refreshProjects();
        refreshCommands();
        refreshArtifacts();
        refreshGenerativeUi();
        refreshSandbox();
        refreshMcpServers();
    }

    private void onAddMemory() {
        SdsMemoryStore store = SdsRuntimeRegistry.getMemoryStore();
        if (store == null) {
            Toast.makeText(this, "The memory store is not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        String input = memoryInput == null ? "" : memoryInput.getText().toString().trim();
        int separator = input.indexOf('=');
        if (separator <= 0) {
            Toast.makeText(this, "Use the form key = note", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            store.put(input.substring(0, separator).trim(), input.substring(separator + 1).trim());
            if (memoryInput != null) memoryInput.setText("");
            refresh();
        } catch (IllegalArgumentException | IllegalStateException refused) {
            Toast.makeText(this, String.valueOf(refused.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void onCreateProject() {
        SdsProjectRegistry registry = SdsRuntimeRegistry.getProjectRegistry();
        if (registry == null) {
            Toast.makeText(this, "The project registry is not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = projectInput == null ? "" : projectInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "The project name is required", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            registry.create(name);
            if (projectInput != null) projectInput.setText("");
            refresh();
        } catch (IllegalArgumentException | IllegalStateException refused) {
            Toast.makeText(this, String.valueOf(refused.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshMemories() {
        if (memoryList == null) return;
        memoryList.removeAllViews();
        SdsMemoryStore store = SdsRuntimeRegistry.getMemoryStore();
        if (store == null || store.size() == 0) {
            memoryList.addView(text("No memories yet.", 13, MUTED, false), matchWrap());
            return;
        }
        for (java.util.Map.Entry<String, String> entry : store.snapshot().entrySet()) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView value = text(entry.getKey() + ": " + entry.getValue(), 12, TEXT, false);
            row.addView(value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Button delete = button("Delete");
            delete.setOnClickListener(v -> {
                SdsMemoryStore current = SdsRuntimeRegistry.getMemoryStore();
                if (current != null) current.remove(entry.getKey());
                refresh();
            });
            row.addView(delete, wrapWrap());
            LinearLayout.LayoutParams rowParams = matchWrap();
            rowParams.topMargin = dp(4);
            memoryList.addView(row, rowParams);
        }
    }

    private void refreshProjects() {
        if (projectList == null) return;
        projectList.removeAllViews();
        SdsProjectRegistry registry = SdsRuntimeRegistry.getProjectRegistry();
        if (registry == null || registry.size() == 0) {
            projectList.addView(text("No projects yet.", 13, MUTED, false), matchWrap());
            return;
        }
        for (SdsProjectRegistry.Project project : registry.snapshot()) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = text(project.getName() + " (" + project.getFolder() + ")", 12, TEXT, false);
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Button delete = button("Delete");
            delete.setOnClickListener(v -> {
                SdsProjectRegistry current = SdsRuntimeRegistry.getProjectRegistry();
                if (current != null) current.remove(project.getName());
                refresh();
            });
            row.addView(delete, wrapWrap());
            LinearLayout.LayoutParams rowParams = matchWrap();
            rowParams.topMargin = dp(4);
            projectList.addView(row, rowParams);
        }
    }

    /**
     * Joins the tool names of a server.
     *
     * String.join needs a newer platform than the app supports, so the names
     * are joined here.
     */
    private static String joinToolNames(String server) {
        List<String> names = SdsMcpTools.knownTools(server);
        if (names.isEmpty()) return "none known yet";
        StringBuilder builder = new StringBuilder();
        for (String name : names) {
            if (builder.length() > 0) builder.append(", ");
            builder.append(name);
        }
        return builder.toString();
    }

    private void rebuildExecutor() {
        if (executor != null) executor.shutdown();
        executor = new SdsToolExecutor(registry, SdsRuntimeRegistry.currentPolicy());
    }

    private void onAddMcpServer() {
        SdsMcpConfigStore store = SdsRuntimeRegistry.getMcpConfigStore();
        if (store == null) {
            Toast.makeText(this, "The MCP list is not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = mcpNameInput == null ? "" : mcpNameInput.getText().toString().trim();
        String url = mcpUrlInput == null ? "" : mcpUrlInput.getText().toString().trim();
        String token = mcpTokenInput == null ? "" : mcpTokenInput.getText().toString().trim();
        try {
            store.add(name, url, token);
            if (mcpNameInput != null) mcpNameInput.setText("");
            if (mcpUrlInput != null) mcpUrlInput.setText("");
            if (mcpTokenInput != null) mcpTokenInput.setText("");
            refresh();
        } catch (IllegalArgumentException | IllegalStateException refused) {
            Toast.makeText(this, String.valueOf(refused.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void onRefreshMcpTools() {
        final SdsMcpConfigStore store = SdsRuntimeRegistry.getMcpConfigStore();
        if (store == null || store.enabledServers().isEmpty()) {
            Toast.makeText(this, "There is no enabled server", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!SdsRuntimeRegistry.isNetworkAllowed()) {
            Toast.makeText(this, "The network is switched off", Toast.LENGTH_SHORT).show();
            return;
        }
        final Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                String message;
                try {
                    List<SdsMcpClient.ToolInfo> tools = SdsMcpTools.refresh(store);
                    message = tools.isEmpty()
                            ? "the servers offered no tool"
                            : "the servers offered " + tools.size() + " tool(s)";
                } catch (Exception refused) {
                    message = "a server could not be reached";
                }
                final String text = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        registry = buildToolRegistry();
                        rebuildExecutor();
                        refresh();
                        Toast.makeText(SdsAgentConsoleActivity.this, text, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }, "sds-mcp");
        worker.start();
    }

    private void refreshMcpServers() {
        if (mcpList == null) return;
        mcpList.removeAllViews();
        SdsMcpConfigStore store = SdsRuntimeRegistry.getMcpConfigStore();
        if (store == null || store.size() == 0) {
            mcpList.addView(text("No server yet.", 13, MUTED, false), matchWrap());
            return;
        }
        for (SdsMcpConfigStore.Server server : store.snapshot()) {
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            TextView name = text(server.getName() + " - " + server.getUrl(), 12, TEXT, false);
            column.addView(name, matchWrap());
            TextView tools = text("tools: " + joinToolNames(server.getName()),
                    11, MUTED, false);
            LinearLayout.LayoutParams toolsParams = matchWrap();
            toolsParams.topMargin = dp(2);
            column.addView(tools, toolsParams);

            LinearLayout buttons = new LinearLayout(this);
            buttons.setGravity(Gravity.CENTER_VERTICAL);
            Button toggle = button(server.isEnabled() ? "Disable" : "Enable");
            toggle.setOnClickListener(v -> {
                SdsMcpConfigStore current = SdsRuntimeRegistry.getMcpConfigStore();
                if (current != null) current.setEnabled(server.getName(), !server.isEnabled());
                registry = buildToolRegistry();
                rebuildExecutor();
                refresh();
            });
            buttons.addView(toggle, wrapWrap());
            Button delete = button("Delete");
            delete.setOnClickListener(v -> {
                SdsMcpConfigStore current = SdsRuntimeRegistry.getMcpConfigStore();
                if (current != null) current.remove(server.getName());
                registry = buildToolRegistry();
                rebuildExecutor();
                refresh();
            });
            buttons.addView(delete, wrapWrap());
            LinearLayout.LayoutParams buttonParams = matchWrap();
            buttonParams.topMargin = dp(4);
            column.addView(buttons, buttonParams);

            LinearLayout.LayoutParams columnParams = matchWrap();
            columnParams.topMargin = dp(6);
            mcpList.addView(column, columnParams);
        }
    }

    private void onFetchResearchAddress() {
        final String url = researchInput == null ? "" : researchInput.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "The address is required", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!url.startsWith("https://")) {
            Toast.makeText(this, "Only https is allowed", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!SdsRuntimeRegistry.isNetworkAllowed()) {
            Toast.makeText(this, "The network is switched off", Toast.LENGTH_SHORT).show();
            return;
        }
        // The approval shows the exact address before anything is fetched.
        new android.app.AlertDialog.Builder(this)
                .setTitle("Fetch this address?")
                .setMessage(url)
                .setNegativeButton(android.content.DialogInterface.BUTTON_NEGATIVE, null)
                .setPositiveButton(android.content.DialogInterface.BUTTON_POSITIVE,
                        (dialog, which) -> fetchResearchAddress(url))
                .show();
    }

    private void fetchResearchAddress(final String url) {
        if (researchOutput != null) {
            researchOutput.setVisibility(View.VISIBLE);
            researchOutput.setText("fetching...");
        }
        final Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                String text;
                try {
                    SdsHttpFetcher.Response response =
                            SdsHttpFetcher.fetch(url, SdsHttpFetcher.MAX_BODY_BYTES);
                    if (!response.isOk()) {
                        text = "the address answered with the status " + response.getStatus();
                    } else {
                        text = SdsHttpFetcher.toPlainText(response.getBody(),
                                SdsResearchTools.MAX_TEXT_CHARS);
                    }
                } catch (Exception refused) {
                    text = "the address could not be reached";
                }
                final String result = text == null ? "" : text;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (researchOutput != null) {
                            researchOutput.setText(result.isEmpty() ? "no text" : result);
                        }
                    }
                });
            }
        }, "sds-research");
        worker.start();
    }

    private void refreshSandbox() {
        if (studioStatus == null) return;
        SdsSandboxManager manager = SdsRuntimeRegistry.getSandboxManager();
        if (manager == null) {
            studioStatus.setText("The Linux Studio was not prepared in this build.");
            return;
        }
        studioStatus.setText(manager.describe());
    }

    private void onRunSandboxCommand() {
        final String command = studioInput == null ? "" : studioInput.getText().toString().trim();
        if (command.isEmpty()) {
            Toast.makeText(this, "The command is required", Toast.LENGTH_SHORT).show();
            return;
        }
        final SdsSandboxManager manager = SdsRuntimeRegistry.getSandboxManager();
        if (manager == null || !manager.isReady()) {
            Toast.makeText(this, "The Linux Studio is not ready", Toast.LENGTH_SHORT).show();
            refreshSandbox();
            return;
        }
        // The approval is explicit and it shows the exact command.
        new android.app.AlertDialog.Builder(this)
                .setTitle("Run this command in the Linux Studio?")
                .setMessage(command)
                .setNegativeButton(android.content.DialogInterface.BUTTON_NEGATIVE, null)
                .setPositiveButton(android.content.DialogInterface.BUTTON_POSITIVE,
                        (dialog, which) -> runSandboxCommandInBackground(command))
                .show();
    }

    private void runSandboxCommandInBackground(final String command) {
        if (studioOutput != null) {
            studioOutput.setVisibility(View.VISIBLE);
            studioOutput.setText("running...");
        }
        final Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                final SdsToolResult result = executeSandboxCommand(command);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (studioOutput == null) return;
                        String output = result.getOutput();
                        String error = result.getSafeError();
                        StringBuilder builder = new StringBuilder();
                        if (output != null && !output.isEmpty()) builder.append(output);
                        if (error != null && !error.isEmpty()) builder.append(error);
                        studioOutput.setText(builder.length() == 0 ? "no output" : builder.toString());
                    }
                });
            }
        }, "sds-studio");
        worker.start();
    }

    private SdsToolResult executeSandboxCommand(String command) {
        SdsToolRegistry sandboxRegistry = SdsSandboxTool.createRegistry();
        SdsTool tool = sandboxRegistry.find(SdsSandboxTool.RUN);
        if (tool == null) {
            return new SdsToolResult(SdsToolResult.Status.FAILED, "", "the sandbox tool is missing");
        }
        File workspace = SdsRuntimeRegistry.getWorkspace();
        if (workspace == null) {
            workspace = new File(getFilesDir(), "workspace");
        }
        SdsToolContext context = new SdsToolContext(workspace, SdsExecutionPolicy.SAFE_DEFAULT,
                new SdsCancellation());
        try {
            return tool.execute(context, new SdsToolCall("console-" + System.currentTimeMillis(),
                    SdsSandboxTool.RUN, "{\"command\":\"" + escapeJson(command) + "\"}"));
        } catch (Exception failed) {
            return new SdsToolResult(SdsToolResult.Status.FAILED, "", "the command could not be run");
        }
    }

    private static String escapeJson(String value) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (c == '"' || c == '\\') {
                builder.append('\\').append(c);
            } else if (c == '\n') {
                builder.append("\\n");
            } else if (c == '\r') {
                builder.append("\\r");
            } else if (c == '\t') {
                builder.append("\\t");
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private void refreshGenerativeUi() {
        if (uiSurface == null) return;
        uiSurface.removeAllViews();
        SdsUiSpec spec = SdsRuntimeRegistry.getLastUiSpec();
        if (spec == null) {
            uiSurface.addView(text("No interface was requested yet.", 13, MUTED, false), matchWrap());
            return;
        }
        if (!spec.getTitle().isEmpty()) {
            TextView title = text(spec.getTitle(), 17, TEXT, true);
            uiSurface.addView(title, matchWrap());
        }
        if (!spec.getSubtitle().isEmpty()) {
            TextView subtitle = text(spec.getSubtitle(), 12, MUTED, false);
            LinearLayout.LayoutParams params = matchWrap();
            params.topMargin = dp(2);
            uiSurface.addView(subtitle, params);
        }
        if (!spec.getBody().isEmpty()) {
            TextView body = text(spec.getBody(), 13, Color.rgb(203, 215, 232), false);
            LinearLayout.LayoutParams params = matchWrap();
            params.topMargin = dp(6);
            uiSurface.addView(body, params);
        }
        for (String item : spec.getItems()) {
            TextView line = text("\u2022  " + item, 13, Color.rgb(203, 215, 232), false);
            LinearLayout.LayoutParams params = matchWrap();
            params.topMargin = dp(3);
            uiSurface.addView(line, params);
        }
        if (!spec.getCode().isEmpty()) {
            TextView code = text(spec.getCode(), 12, Color.rgb(180, 210, 255), false);
            code.setTypeface(Typeface.MONOSPACE);
            code.setBackgroundColor(Color.rgb(18, 24, 34));
            code.setPadding(dp(8), dp(8), dp(8), dp(8));
            LinearLayout.LayoutParams params = matchWrap();
            params.topMargin = dp(6);
            uiSurface.addView(code, params);
        }
        if (spec.hasAction()) {
            Button action = button(spec.getAction());
            action.setOnClickListener(v -> onGenerativeUiAction(spec));
            LinearLayout.LayoutParams params = wrapWrap();
            params.topMargin = dp(8);
            uiSurface.addView(action, params);
        }
    }

    private void onGenerativeUiAction(SdsUiSpec spec) {
        String argument = spec.getArgument();
        if (argument.isEmpty()) {
            Toast.makeText(this, "The action carries no argument", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            android.content.ClipData clip = android.content.ClipData.newPlainText("DeepSeek", argument);
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard == null) {
                Toast.makeText(this, "The clipboard is not available", Toast.LENGTH_SHORT).show();
                return;
            }
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "The argument was copied to the clipboard", Toast.LENGTH_SHORT).show();
        } catch (Throwable refused) {
            Toast.makeText(this, "The action could not be carried out", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshArtifacts() {
        if (artifactList == null) return;
        artifactList.removeAllViews();
        SdsArtifactStore store = SdsRuntimeRegistry.getArtifactStore();
        if (store == null || store.size() == 0) {
            artifactList.addView(text("No artifacts yet.", 13, MUTED, false), matchWrap());
            return;
        }
        for (SdsArtifactStore.Artifact artifact : store.snapshot()) {
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            TextView title = text(
                    artifact.getTitle() + "  [" + artifact.getLanguage() + ", "
                            + artifact.getContentLength() + " characters]", 12, TEXT, false);
            column.addView(title, matchWrap());

            LinearLayout buttons = new LinearLayout(this);
            buttons.setGravity(Gravity.CENTER_VERTICAL);
            Button view = button("View");
            view.setOnClickListener(v -> onViewArtifact(artifact));
            buttons.addView(view, wrapWrap());
            Button save = button("Save");
            save.setOnClickListener(v -> onSaveArtifact(artifact));
            buttons.addView(save, wrapWrap());
            Button share = button("Share");
            share.setOnClickListener(v -> onShareArtifact(artifact));
            buttons.addView(share, wrapWrap());
            Button delete = button("Delete");
            delete.setOnClickListener(v -> {
                SdsArtifactStore current = SdsRuntimeRegistry.getArtifactStore();
                if (current != null) current.delete(artifact.getId());
                refresh();
            });
            buttons.addView(delete, wrapWrap());
            LinearLayout.LayoutParams buttonParams = matchWrap();
            buttonParams.topMargin = dp(4);
            column.addView(buttons, buttonParams);

            LinearLayout.LayoutParams columnParams = matchWrap();
            columnParams.topMargin = dp(6);
            artifactList.addView(column, columnParams);
        }
    }

    private void onViewArtifact(SdsArtifactStore.Artifact artifact) {
        try {
            new android.app.AlertDialog.Builder(this)
                    .setTitle(artifact.getTitle())
                    .setMessage(artifact.getContent())
                    .setPositiveButton(android.content.DialogInterface.BUTTON_POSITIVE, null)
                    .show();
        } catch (Throwable refused) {
            Toast.makeText(this, "The artifact could not be shown", Toast.LENGTH_SHORT).show();
        }
    }

    private void onSaveArtifact(SdsArtifactStore.Artifact artifact) {
        String name = artifact.getExportName();
        String mime = artifact.getLanguage().equals("markdown") ? SdsExportService.MIME_MARKDOWN
                : SdsExportService.MIME_CODE;
        if (!SdsExportService.save(this, name, artifact.getContent(), mime)) {
            Toast.makeText(this, "The system file picker could not be opened", Toast.LENGTH_SHORT).show();
        }
    }

    private void onShareArtifact(SdsArtifactStore.Artifact artifact) {
        if (!SdsExportService.share(this, artifact.getTitle(), artifact.getContent())) {
            Toast.makeText(this, "The share sheet could not be opened", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SdsExportService.REQUEST_SAVE) return;
        if (resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) {
            SdsExportService.clearPending();
            Toast.makeText(this, "Nothing was saved", Toast.LENGTH_SHORT).show();
            return;
        }
        if (SdsExportService.writePending(this, data.getData())) {
            Toast.makeText(this, "The file was saved", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "The file could not be written", Toast.LENGTH_SHORT).show();
        }
    }

    private void onExportTimeline() {
        StringBuilder builder = new StringBuilder("DeepSeek Agent activity\n");
        for (SdsAgentEvent event : session.getEvents()) {
            builder.append(event.getAtMs())
                    .append("  ")
                    .append(event.getType())
                    .append("  ")
                    .append(event.getDetail())
                    .append('\n');
        }
        if (!SdsExportService.save(this, "deepseek-agent-activity.txt", builder.toString(),
                SdsExportService.MIME_TEXT)) {
            Toast.makeText(this, "The system file picker could not be opened", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshCommands() {
        if (commandList == null) return;
        commandList.removeAllViews();
        for (String line : SdsSlashCommands.describe()) {
            TextView view = text(line, 12, Color.rgb(203, 215, 232), false);
            view.setTypeface(Typeface.MONOSPACE);
            LinearLayout.LayoutParams params = matchWrap();
            params.topMargin = dp(3);
            commandList.addView(view, params);
        }
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
                    SdsRuntimeRegistry.currentPolicy(),
                    session.getCancellation());
            SdsToolResult result = executor.run(approved, context, true);
            if (result != null && result.getStatus() == SdsToolResult.Status.SUCCESS) {
                // The result waits for the next accumulated content of the conversation.
                SdsToolResultHandoff.setPendingResult(result.getOutput());
            }
            runOnUiThread(() -> {
                if (result.getStatus() == SdsToolResult.Status.SUCCESS) {
                    session.onRunSucceeded(result);
                } else {
                    session.onRunFailed(result);
                }
                refresh();
            });
        }, "sds-console-tool");
        worker.setDaemon(true);
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

    private void onStopClicked() {
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
        if (registry == null) {
            toolList.addView(text("The allow-list is not ready.", 13, MUTED, false), matchWrap());
            return;
        }
        for (String line : registry.describe()) {
            TextView view = text(line, 12, Color.rgb(203, 215, 232), false);
            view.setTypeface(Typeface.MONOSPACE);
            LinearLayout.LayoutParams params = matchWrap();
            params.topMargin = dp(4);
            toolList.addView(view, params);
        }
        TextView note = text("Only these tools can run. The network tools need the network "
                + "switch and the sandbox command needs the Linux Studio switch.",
                11, WARN, false);
        LinearLayout.LayoutParams noteParams = matchWrap();
        noteParams.topMargin = dp(8);
        toolList.addView(note, noteParams);
    }

    private void copyText(String value) {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) {
                Toast.makeText(this, "The clipboard is not available", Toast.LENGTH_SHORT).show();
                return;
            }
            clipboard.setPrimaryClip(ClipData.newPlainText("SDS tool proposal", value));
            Toast.makeText(this, "Proposal arguments copied", Toast.LENGTH_SHORT).show();
        } catch (Throwable refused) {
            Toast.makeText(this, "The clipboard refused the copy", Toast.LENGTH_SHORT).show();
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
