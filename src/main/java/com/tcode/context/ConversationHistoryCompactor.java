package com.tcode.context;

import com.tcode.llm.LlmClient;
import com.tcode.memory.TokenBudget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Compacts the actual LLM message history used by Agent loops.
 */
public class ConversationHistoryCompactor {

    private static final Logger log = LoggerFactory.getLogger(ConversationHistoryCompactor.class);

    private static final int DEFAULT_RETAIN_RECENT_ROUNDS = 3;
    private static final int MAX_SUMMARY_INPUT_CHARS = 60_000;

    private static final String SUMMARY_PROMPT = """
            Please compact the following conversation history into a concise Chinese handoff summary.
            Output exactly these Markdown sections, in this order:

            ## Goal
            The user's current high-level objective.

            ## Constraints
            Explicit user constraints, preferences, and things not to do.

            ## Done
            Important completed work, including meaningful tool calls and core results.

            ## Current State
            Current code/test/task state after the completed work.

            ## Key Decisions
            Architecture or implementation decisions already made.

            ## Open Issues
            Known problems, failed tests, missing verification, or unresolved questions.

            ## Read Files
            Important files that were read or inspected.

            ## Modified Files
            Important files that were changed.

            ## Next Steps
            Concrete recommended next actions.

            Rules:
            - Write in Chinese.
            - Keep each section concise; use "无" if a section has no information.
            - Do not restate every original message.
            - Do not list every tool call.
            - Preserve file paths, commands, test names, errors, and user constraints when present.
            - Output only the Markdown summary, with no prefix or meta commentary.
            %s
            === Conversation to compact ===
            %s
            === End ===
            """;

    private LlmClient llmClient;
    private final int retainRecentRounds;

    public ConversationHistoryCompactor(LlmClient llmClient) {
        this(llmClient, DEFAULT_RETAIN_RECENT_ROUNDS);
    }

    public ConversationHistoryCompactor(LlmClient llmClient, int retainRecentRounds) {
        this.llmClient = llmClient;
        this.retainRecentRounds = Math.max(1, retainRecentRounds);
    }

    public void setLlmClient(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public boolean compactIfNeeded(List<LlmClient.Message> history, int triggerTokens) {
        return compact(history, triggerTokens, false, null);
    }

    public boolean compactNow(List<LlmClient.Message> history, String focus) {
        return compact(history, 0, true, focus);
    }

    private boolean compact(List<LlmClient.Message> history, int triggerTokens, boolean force, String focus) {
        if (history == null || history.isEmpty()) return false;
        int currentTokens = TokenBudget.estimateMessagesTokens(history);
        if (!force && currentTokens < triggerTokens) return false;

        int systemEnd = "system".equals(history.get(0).role()) ? 1 : 0;

        List<Integer> userIndices = new ArrayList<>();
        for (int i = systemEnd; i < history.size(); i++) {
            if ("user".equals(history.get(i).role())) {
                userIndices.add(i);
            }
        }
        if (userIndices.size() <= retainRecentRounds) {
            log.info("compactIfNeeded skip: only {} user turns, < retain {}",
                    userIndices.size(), retainRecentRounds);
            return false;
        }

        int splitIdx = userIndices.get(userIndices.size() - retainRecentRounds);
        if (splitIdx <= systemEnd) return false;

        List<LlmClient.Message> oldMsgs = new ArrayList<>(history.subList(systemEnd, splitIdx));
        if (oldMsgs.isEmpty()) return false;

        String summary;
        try {
            summary = focus == null || focus.isBlank()
                    ? summarize(oldMsgs)
                    : summarize(oldMsgs, focus.trim());
        } catch (IOException e) {
            log.warn("conversation summary LLM call failed; skip compaction", e);
            return false;
        }
        if (summary == null || summary.isBlank()) {
            log.warn("conversation summary returned empty; skip compaction");
            return false;
        }

        List<LlmClient.Message> rebuilt = new ArrayList<>();
        for (int i = 0; i < systemEnd; i++) {
            rebuilt.add(history.get(i));
        }
        rebuilt.add(LlmClient.Message.user("[已压缩的历史对话摘要]\n" + summary.trim()));
        rebuilt.add(LlmClient.Message.assistant("好的，我已了解之前的上下文，请继续。"));
        rebuilt.addAll(history.subList(splitIdx, history.size()));

        int afterTokens = TokenBudget.estimateMessagesTokens(rebuilt);
        history.clear();
        history.addAll(rebuilt);
        log.info(String.format(Locale.ROOT,
                "compacted conversationHistory: tokens %d -> %d, messages %d -> %d, summary chars %d",
                currentTokens, afterTokens, userIndices.size() + systemEnd, rebuilt.size(),
                summary.length()));
        return true;
    }

    protected String summarize(List<LlmClient.Message> messages) throws IOException {
        return summarize(messages, null);
    }

    protected String summarize(List<LlmClient.Message> messages, String focus) throws IOException {
        if (llmClient == null) {
            throw new IOException("LLM client not configured");
        }
        StringBuilder sb = new StringBuilder();
        for (LlmClient.Message m : messages) {
            sb.append(m.role().toUpperCase(Locale.ROOT)).append(": ");
            if (m.content() != null) {
                sb.append(m.content());
            }
            if (m.toolCalls() != null) {
                for (LlmClient.ToolCall tc : m.toolCalls()) {
                    sb.append("\n  TOOL_CALL ").append(tc.function().name())
                            .append(": ").append(tc.function().arguments());
                }
            }
            sb.append("\n\n");
            if (sb.length() > MAX_SUMMARY_INPUT_CHARS) {
                sb.append("...(content truncated)\n");
                break;
            }
        }
        String focusInstruction = focus == null || focus.isBlank()
                ? ""
                : "User requested focus: " + focus.trim()
                + "\nPrioritize preserving information related to this focus.\n";
        String prompt = String.format(SUMMARY_PROMPT, focusInstruction, sb.toString());
        List<LlmClient.Message> req = List.of(
                LlmClient.Message.system("你是一个对话摘要助手，只输出摘要本身，不输出元描述。"),
                LlmClient.Message.user(prompt)
        );
        LlmClient.ChatResponse response = llmClient.chat(req, null);
        return response == null ? null : response.content();
    }

    public int retainRecentRounds() {
        return retainRecentRounds;
    }
}
