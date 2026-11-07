package com.themuhammad.deepseekmod.extension;

import java.util.ArrayList;
import java.util.List;

/**
 * The Deep Research tools.
 *
 * The tool fetches one address and returns its readable text. It is the
 * retrieval half of the research flow: the model receives the text in the
 * console and writes the synthesis itself, so no answer is invented by the
 * app.
 *
 * The tool needs the network, so the executor refuses it while the network
 * policy forbids it, and it is not read-only, so every single fetch needs an
 * explicit approval.
 */
public final class SdsResearchTools {
    public static final String FETCH = "research.fetch";
    public static final int MAX_TEXT_CHARS = 16 * 1024;

    private SdsResearchTools() {
    }

    /** Builds a registry that contains exactly the research tools. */
    public static SdsToolRegistry createRegistry() {
        SdsToolRegistry registry = new SdsToolRegistry();
        registry.register(new FetchTool());
        return registry;
    }

    /** Fetches one address and returns its readable text. */
    public static final class FetchTool implements SdsTool {
        @Override
        public String getName() { return FETCH; }

        @Override
        public String getDescription() {
            return "Fetches one https address and returns its text for the research.";
        }

        @Override
        public String getSchema() { return "{\"url\": string, \"maxChars\": int}"; }

        @Override
        public boolean isReadOnly() { return false; }

        @Override
        public boolean requiresNetwork() { return true; }

        @Override
        public SdsToolResult execute(SdsToolContext context, SdsToolCall call) {
            try {
                SdsToolArguments arguments = SdsToolArguments.parse(call.getArgumentsJson());
                arguments.requireOnly("url", "maxChars");
                String url = arguments.getString("url", "").trim();
                if (url.isEmpty()) {
                    return new SdsToolResult(SdsToolResult.Status.FAILED, "", "the address is required");
                }
                int maxChars = arguments.getInt("maxChars", MAX_TEXT_CHARS);
                if (maxChars < 200) maxChars = 200;
                if (maxChars > MAX_TEXT_CHARS) maxChars = MAX_TEXT_CHARS;
                SdsHttpFetcher.Response response =
                        SdsHttpFetcher.fetch(url, SdsHttpFetcher.MAX_BODY_BYTES);
                if (!response.isOk()) {
                    return new SdsToolResult(SdsToolResult.Status.FAILED, "",
                            "the address answered with the status " + response.getStatus());
                }
                String text = SdsHttpFetcher.toPlainText(response.getBody(), maxChars);
                return new SdsToolResult(SdsToolResult.Status.SUCCESS, text, "");
            } catch (IllegalArgumentException refused) {
                return new SdsToolResult(SdsToolResult.Status.FAILED, "",
                        "the arguments did not match the schema");
            } catch (Exception refused) {
                return new SdsToolResult(SdsToolResult.Status.FAILED, "",
                        "the address could not be reached");
            }
        }
    }

    /** The addresses the console offers, kept out of the tool itself. */
    public static List<String> suggestedAddresses() {
        List<String> addresses = new ArrayList<>();
        addresses.add("https://en.wikipedia.org/wiki/Main_Page");
        addresses.add("https://example.org/");
        return addresses;
    }
}
