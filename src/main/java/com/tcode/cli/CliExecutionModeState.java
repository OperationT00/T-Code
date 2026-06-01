package com.tcode.cli;

final class CliExecutionModeState {
    enum Mode {
        REACT("react"),
        PLAN("plan"),
        TEAM("team");

        private final String snapshotName;

        Mode(String snapshotName) {
            this.snapshotName = snapshotName;
        }

        String snapshotName() {
            return snapshotName;
        }
    }

    private Mode pendingMode;

    boolean hasPendingMode() {
        return pendingMode != null;
    }

    Mode pendingMode() {
        return pendingMode;
    }

    void activate(Mode mode) {
        if (mode == Mode.REACT) {
            throw new IllegalArgumentException("ReAct is the default mode, not a pending mode");
        }
        pendingMode = mode;
    }

    Mode cancelPending() {
        Mode canceled = pendingMode;
        pendingMode = null;
        return canceled;
    }

    Mode modeFor(CliCommandParser.CommandType commandType) {
        if (commandType == CliCommandParser.CommandType.SWITCH_PLAN) {
            return Mode.PLAN;
        }
        if (commandType == CliCommandParser.CommandType.SWITCH_TEAM) {
            return Mode.TEAM;
        }
        return pendingMode == null ? Mode.REACT : pendingMode;
    }

    void reset() {
        pendingMode = null;
    }
}
