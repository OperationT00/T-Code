package com.tcode.cli;

import com.tcode.agent.Agent;
import com.tcode.config.TCodeConfig;
import com.tcode.llm.LlmClient;
import com.tcode.llm.LlmClientFactory;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class CliModelCommandDispatcher {

    record Context(
            PrintStream ui,
            TCodeConfig config,
            Supplier<LlmClient> currentClient,
            Agent reactAgent,
            Consumer<LlmClient> refreshStatus
    ) {
    }

    record Result(boolean handled, LlmClient client) {
        static Result notHandled(LlmClient client) {
            return new Result(false, client);
        }
    }

    record ModelSelection(String provider, String model, boolean explicitModel) {
    }

    private CliModelCommandDispatcher() {
    }

    static Result dispatch(CliCommandParser.ParsedCommand command, Context context) {
        LlmClient currentClient = context.currentClient().get();
        if (command.type() != CliCommandParser.CommandType.SWITCH_MODEL) {
            return Result.notHandled(currentClient);
        }

        String selection = command.payload();
        if (selection == null || selection.isEmpty()) {
            printStatus(context.ui(), currentClient);
            return new Result(true, currentClient);
        }

        ModelSelection target = resolveModelSelection(selection);
        if (target.explicitModel()) {
            ensureProviderConfig(context.config(), target.provider()).setModel(target.model());
        }
        LlmClient newClient = LlmClientFactory.create(target.provider(), context.config());
        if (newClient == null) {
            context.ui().println("❌ 切换失败：未配置 " + target.provider() + " 的 API Key\n");
            return new Result(true, currentClient);
        }

        context.config().setDefaultProvider(target.provider());
        context.config().save();
        context.reactAgent().setLlmClient(newClient);
        context.ui().println("✅ 已切换到: " + newClient.getModelName() + " (" + newClient.getProviderName() + ")");
        context.ui().println("   上下文策略: " + context.reactAgent().getMemoryManager().getContextProfile().summary());
        context.ui().println("   对话上下文已保留，使用 /clear 可清空\n");
        if (context.refreshStatus() != null) {
            context.refreshStatus().accept(newClient);
        }
        return new Result(true, newClient);
    }

    static ModelSelection resolveModelSelection(String raw) {
        String value = raw == null ? "" : raw.trim();
        String normalized = value.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "glm" -> new ModelSelection("glm", "glm-5.1", true);
            case "deepseek" -> new ModelSelection("deepseek", null, false);
            case "step", "stepfun", "step-fun" -> new ModelSelection("step", null, false);
            case "kimi", "moonshot", "moonshotai", "moonshot-ai" -> new ModelSelection("kimi", null, false);
            default -> {
                if (normalized.startsWith("glm-")) {
                    yield new ModelSelection("glm", value, true);
                }
                if (normalized.startsWith("deepseek")) {
                    yield new ModelSelection("deepseek", value, true);
                }
                if (normalized.startsWith("step")) {
                    yield new ModelSelection("step", value, true);
                }
                if (normalized.startsWith("kimi-") || normalized.startsWith("moonshot-")) {
                    yield new ModelSelection("kimi", value, true);
                }
                yield new ModelSelection(normalized, null, false);
            }
        };
    }

    private static void printStatus(PrintStream out, LlmClient currentClient) {
        out.println("🤖 当前模型: " + currentClient.getModelName() + " (" + currentClient.getProviderName() + ")");
        out.println("   GLM 明确模型：");
        out.println("   /model glm-5.1       - 切换到 GLM-5.1");
        out.println("   /model glm-5v-turbo  - 切换到 GLM-5V-Turbo 多模态");
        out.println("   其它 provider 使用你配置里的具体模型：");
        out.println("   /model deepseek      - 切换到 DeepSeek（读取配置模型）");
        out.println("   /model step          - 切换到 StepFun（读取配置模型）");
        out.println("   /model kimi          - 切换到 Kimi（读取配置模型）\n");
    }

    private static TCodeConfig.ProviderConfig ensureProviderConfig(TCodeConfig config, String provider) {
        if (config.getProviders() == null) {
            config.setProviders(new LinkedHashMap<>());
        }
        return config.getProviders().computeIfAbsent(provider, ignored -> new TCodeConfig.ProviderConfig());
    }
}
