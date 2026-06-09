package com.tcode.cli;

import com.tcode.agent.Agent;
import com.tcode.context.ContextEvent;
import com.tcode.hitl.SwitchableHitlHandler;

import java.io.PrintStream;
import java.util.List;

final class CliConversationCommandDispatcher {

    record Context(PrintStream ui, Agent reactAgent, SwitchableHitlHandler hitlHandler) {
    }

    private CliConversationCommandDispatcher() {
    }

    static boolean dispatch(CliCommandParser.ParsedCommand command, Context context) {
        return switch (command.type()) {
            case CANCEL -> {
                context.ui().println("当前没有正在运行的任务。\n");
                yield true;
            }
            case CLEAR -> {
                context.reactAgent().clearHistory();
                context.hitlHandler().clearApprovedAll();
                context.ui().println("🗑️ 当前对话历史已清空，长期记忆保持不变\n");
                yield true;
            }
            case CONTEXT_STATUS -> {
                context.ui().println("📋 上下文状态：");
                context.ui().println(context.reactAgent().getContextStatus());
                context.ui().println();
                yield true;
            }
            case CONTEXT_COMPACT -> {
                context.reactAgent().compactContext(command.payload());
                yield true;
            }
            case CONTEXT_EVENTS -> {
                printEvents(context.ui(), context.reactAgent().recentContextEvents(20));
                yield true;
            }
            case CONTEXT_RECALL -> {
                printEvents(context.ui(), context.reactAgent().searchContextEvents(command.payload(), 20));
                yield true;
            }
            case CONTEXT_SHOW -> {
                context.reactAgent().findContextEvent(command.payload())
                        .ifPresentOrElse(
                                event -> printFullEvent(context.ui(), event),
                                () -> context.ui().println("Context event not found: " + command.payload()));
                yield true;
            }
            case CONTEXT_INJECT -> {
                boolean injected = context.reactAgent().injectContextEvent(command.payload());
                context.ui().println(injected
                        ? "Injected context event: " + command.payload()
                        : "Context event not found: " + command.payload());
                yield true;
            }
            default -> false;
        };
    }

    private static void printEvents(PrintStream out, List<ContextEvent> events) {
        if (events == null || events.isEmpty()) {
            out.println("No context events found.");
            return;
        }
        out.println("Context events:");
        for (ContextEvent event : events) {
            out.printf("  %s  %s%s  %s%n",
                    event.id(),
                    event.role(),
                    event.toolName().isBlank() ? "" : ":" + event.toolName(),
                    snippet(event.content(), 120));
        }
    }

    private static void printFullEvent(PrintStream out, ContextEvent event) {
        out.println("Context event: " + event.id());
        out.println("turn: " + event.turnId());
        out.println("role: " + event.role());
        if (!event.toolName().isBlank()) {
            out.println("tool: " + event.toolName());
        }
        if (!event.metadata().isEmpty()) {
            out.println("metadata: " + event.metadata());
        }
        out.println();
        out.println(event.content());
    }

    private static String snippet(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxChars - 3)) + "...";
    }
}
