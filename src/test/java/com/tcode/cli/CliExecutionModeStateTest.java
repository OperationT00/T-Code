package com.tcode.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliExecutionModeStateTest {
    @Test
    void activatesCancelsAndResetsPendingMode() {
        CliExecutionModeState state = new CliExecutionModeState();

        assertFalse(state.hasPendingMode());
        assertEquals(CliExecutionModeState.Mode.REACT, state.modeFor(CliCommandParser.CommandType.NONE));

        state.activate(CliExecutionModeState.Mode.PLAN);
        assertTrue(state.hasPendingMode());
        assertEquals(CliExecutionModeState.Mode.PLAN, state.pendingMode());
        assertEquals(CliExecutionModeState.Mode.PLAN, state.modeFor(CliCommandParser.CommandType.NONE));

        assertEquals(CliExecutionModeState.Mode.PLAN, state.cancelPending());
        assertFalse(state.hasPendingMode());

        state.activate(CliExecutionModeState.Mode.TEAM);
        state.reset();
        assertFalse(state.hasPendingMode());
    }

    @Test
    void inlineSwitchCommandOverridesDefaultReactMode() {
        CliExecutionModeState state = new CliExecutionModeState();

        assertEquals(CliExecutionModeState.Mode.PLAN,
                state.modeFor(CliCommandParser.CommandType.SWITCH_PLAN));
        assertEquals(CliExecutionModeState.Mode.TEAM,
                state.modeFor(CliCommandParser.CommandType.SWITCH_TEAM));
    }
}
