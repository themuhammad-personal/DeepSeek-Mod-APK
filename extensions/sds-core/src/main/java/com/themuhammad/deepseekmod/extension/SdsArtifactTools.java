package com.themuhammad.deepseekmod.extension;

/**
 * The native artifact tools.
 *
 * The tools work on the app-private artifact store. They never touch the
 * network and they never leave the app directory. Creating an artifact writes,
 * so it always needs an explicit approval, exactly like the file write tool.
 */
public final class SdsArtifactTools {
    public static final String CREATE = "artifact.create";
    public static final String LIST = "artifact.list";
    public static final String READ = "artifact.read";

    private SdsArtifactTools() {
    }

    /** Builds a registry that contains exactly the artifact tools. */
    public static SdsToolRegistry createRegistry() {
        SdsToolRegistry registry = new SdsToolRegistry();
        registry.register(new CreateTool());
        registry.register(new ListTool());
        registry.register(new ReadTool());
        return registry;
    }

    /** Stores a new artifact. */
    public static final class CreateTool implements SdsTool {
        @Override
        public String getName() { return CREATE; }

        @Override
        public String getDescription() { return "Stores a piece of content as an artifact."; }

        @Override
        public String getSchema() { return "{\"title\": string, \"language\": string, \"content\": string}"; }

        @Override
        public boolean isReadOnly() { return false; }

        @Override
        public boolean requiresNetwork() { return false; }

        @Override
        public SdsToolResult execute(SdsToolContext context, SdsToolCall call) {
            try {
                SdsArtifactStore store = SdsRuntimeRegistry.getArtifactStore();
                if (store == null) {
                    return new SdsToolResult(SdsToolResult.Status.FAILED, "", "the artifact store is not ready");
                }
                SdsToolArguments arguments = SdsToolArguments.parse(call.getArgumentsJson());
                arguments.requireOnly("title", "language", "content");
                String title = arguments.getString("title", "");
                String language = arguments.getString("language", "text");
                String content = arguments.getString("content", "");
                SdsArtifactStore.Artifact artifact = store.create(title, language, content);
                return new SdsToolResult(SdsToolResult.Status.SUCCESS, artifact.getId(),
                        "stored the artifact " + artifact.getId() + " (" + artifact.getTitle() + ")");
            } catch (IllegalArgumentException | IllegalStateException refused) {
                return new SdsToolResult(SdsToolResult.Status.FAILED, "",
                        String.valueOf(refused.getMessage()));
            }
        }
    }

    /** Lists the stored artifacts. */
    public static final class ListTool implements SdsTool {
        @Override
        public String getName() { return LIST; }

        @Override
        public String getDescription() { return "Lists the stored artifacts."; }

        @Override
        public String getSchema() { return "{}"; }

        @Override
        public boolean isReadOnly() { return true; }

        @Override
        public boolean requiresNetwork() { return false; }

        @Override
        public SdsToolResult execute(SdsToolContext context, SdsToolCall call) {
            try {
                SdsArtifactStore store = SdsRuntimeRegistry.getArtifactStore();
                if (store == null) {
                    return new SdsToolResult(SdsToolResult.Status.FAILED, "", "the artifact store is not ready");
                }
                SdsToolArguments.parse(call.getArgumentsJson()).requireOnly();
                String block = store.asPromptBlock(8 * 1024);
                return new SdsToolResult(SdsToolResult.Status.SUCCESS, "",
                        block.isEmpty() ? "there are no artifacts" : block);
            } catch (IllegalArgumentException refused) {
                return new SdsToolResult(SdsToolResult.Status.FAILED, "",
                        "the arguments did not match the schema");
            }
        }
    }

    /** Reads the content of one artifact. */
    public static final class ReadTool implements SdsTool {
        @Override
        public String getName() { return READ; }

        @Override
        public String getDescription() { return "Reads the content of one stored artifact."; }

        @Override
        public String getSchema() { return "{\"id\": string}"; }

        @Override
        public boolean isReadOnly() { return true; }

        @Override
        public boolean requiresNetwork() { return false; }

        @Override
        public SdsToolResult execute(SdsToolContext context, SdsToolCall call) {
            try {
                SdsArtifactStore store = SdsRuntimeRegistry.getArtifactStore();
                if (store == null) {
                    return new SdsToolResult(SdsToolResult.Status.FAILED, "", "the artifact store is not ready");
                }
                SdsToolArguments arguments = SdsToolArguments.parse(call.getArgumentsJson());
                arguments.requireOnly("id");
                String id = arguments.getString("id", "");
                SdsArtifactStore.Artifact artifact = store.find(id);
                if (artifact == null) {
                    return new SdsToolResult(SdsToolResult.Status.FAILED, "", "the artifact does not exist");
                }
                return new SdsToolResult(SdsToolResult.Status.SUCCESS, artifact.getId(), artifact.getContent());
            } catch (IllegalArgumentException refused) {
                return new SdsToolResult(SdsToolResult.Status.FAILED, "",
                        "the arguments did not match the schema");
            }
        }
    }
}
