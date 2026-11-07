package com.themuhammad.deepseekmod.extension;

/**
 * The native Generative UI tools.
 *
 * The assistant describes a small interface with flat strings and the app
 * renders it with plain Android views. There is no web view, no script and no
 * remote resource in the path, so nothing the assistant writes can execute.
 */
public final class SdsUiTools {
    public static final String RENDER = "ui.render";

    private SdsUiTools() {
    }

    /** Builds a registry that contains exactly the Generative UI tools. */
    public static SdsToolRegistry createRegistry() {
        SdsToolRegistry registry = new SdsToolRegistry();
        registry.register(new RenderTool());
        return registry;
    }

    /** Renders an interface in the console. */
    public static final class RenderTool implements SdsTool {
        @Override
        public String getName() { return RENDER; }

        @Override
        public String getDescription() {
            return "Renders a small native interface in the Agent Console.";
        }

        @Override
        public String getSchema() {
            return "{\"title\": string, \"subtitle\": string, \"body\": string, \"items\": string, "
                    + "\"code\": string, \"language\": string, \"action\": string, \"argument\": string}";
        }

        @Override
        public boolean isReadOnly() { return true; }

        @Override
        public boolean requiresNetwork() { return false; }

        @Override
        public SdsToolResult execute(SdsToolContext context, SdsToolCall call) {
            try {
                SdsToolArguments arguments = SdsToolArguments.parse(call.getArgumentsJson());
                arguments.requireOnly("title", "subtitle", "body", "items", "code", "language",
                        "action", "argument");
                SdsUiSpec spec = SdsUiSpec.fromArguments(
                        arguments.getString("title", ""),
                        arguments.getString("subtitle", ""),
                        arguments.getString("body", ""),
                        arguments.getString("items", ""),
                        arguments.getString("code", ""),
                        arguments.getString("language", "text"),
                        arguments.getString("action", ""),
                        arguments.getString("argument", ""));
                if (spec.isEmpty()) {
                    return new SdsToolResult(SdsToolResult.Status.FAILED, "",
                            "the interface description is empty");
                }
                SdsRuntimeRegistry.setLastUiSpec(spec);
                return new SdsToolResult(SdsToolResult.Status.SUCCESS,
                        "the interface is ready: " + spec.describe(), "");
            } catch (IllegalArgumentException refused) {
                return new SdsToolResult(SdsToolResult.Status.FAILED, "",
                        "the arguments did not match the schema");
            }
        }
    }
}
