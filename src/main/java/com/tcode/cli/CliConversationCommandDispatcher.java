package com.tcode.cli;

import com.tcode.agent.Agent;
import com.tcode.hitl.SwitchableHitlHandler;

import java.io.PrintStream;

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
            default -> false;
        };
    }
}
