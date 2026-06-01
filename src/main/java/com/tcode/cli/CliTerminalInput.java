package com.tcode.cli;

import org.jline.reader.LineReader;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;

final class CliTerminalInput {
    record PrefillResult(String seedBuffer, boolean canceled, boolean submitted) {
        static PrefillResult canceledInput() {
            return new PrefillResult("", true, false);
        }

        static PrefillResult submittedInput() {
            return new PrefillResult("", false, true);
        }

        static PrefillResult seed(String seedBuffer) {
            return new PrefillResult(seedBuffer, false, false);
        }
    }

    record KeyReadResult(Integer key, boolean ignoredControlSequence) {
        static KeyReadResult keyPressed(int key) {
            return new KeyReadResult(key, false);
        }

        static KeyReadResult ignoredSequence() {
            return new KeyReadResult(null, true);
        }

        static KeyReadResult unavailable() {
            return new KeyReadResult(null, false);
        }
    }

    private CliTerminalInput() {
    }

    static boolean readEscCancel(Terminal terminal) {
        if (terminal == null) {
            return false;
        }
        try {
            NonBlockingReader reader = terminal.reader();
            int next = reader.read(50);
            if (next == NonBlockingReader.READ_EXPIRED || next < 0) {
                return false;
            }
            String escTail = next == 27 ? readInputBurst(terminal, 80, 20, 120) : null;
            if (next != 27) {
                while (true) {
                    int more = reader.read(1);
                    if (more == NonBlockingReader.READ_EXPIRED || more < 0) {
                        break;
                    }
                }
            }
            return decideEscCancel(next, escTail);
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean decideEscCancel(int firstByte, String escTail) {
        if (firstByte != 27) {
            return false;
        }
        return CliInputNormalization.classifyEscapeSequence(escTail)
                == CliInputNormalization.EscapeSequenceType.STANDALONE_ESC;
    }

    static KeyReadResult readSingleKey(Terminal terminal) {
        try {
            terminal.flush();
            Attributes originalAttributes = terminal.enterRawMode();
            try {
                int key = terminal.reader().read();
                if (key < 0) {
                    return KeyReadResult.unavailable();
                }
                if (key == 27) {
                    String sequence = readInputBurst(terminal, 80, 20, 120);
                    CliInputNormalization.EscapeSequenceType type =
                            CliInputNormalization.classifyEscapeSequence(sequence);
                    if (type == CliInputNormalization.EscapeSequenceType.STANDALONE_ESC) {
                        return KeyReadResult.keyPressed(27);
                    }
                    if (type == CliInputNormalization.EscapeSequenceType.CONTROL_SEQUENCE
                            || type == CliInputNormalization.EscapeSequenceType.BRACKETED_PASTE) {
                        return KeyReadResult.ignoredSequence();
                    }
                }
                return KeyReadResult.keyPressed(key);
            } finally {
                terminal.setAttributes(originalAttributes);
            }
        } catch (Exception e) {
            return KeyReadResult.unavailable();
        }
    }

    static PrefillResult readPrefill(Terminal terminal, LineReader lineReader) {
        try {
            terminal.flush();
            Attributes originalAttributes = terminal.enterRawMode();
            try {
                int key = terminal.reader().read();
                if (key < 0) {
                    return null;
                }
                if (key == 27) {
                    return readEscapeInput(terminal, lineReader);
                }
                if (CliInputNormalization.isSubmitKey(key)) {
                    return PrefillResult.submittedInput();
                }
                String rawInput = switch (key) {
                    case 8, 127 -> "";
                    default -> Character.toString((char) key);
                };
                rawInput += readInputBurst(terminal, 20, 25, 250);
                return PrefillResult.seed(CliInputNormalization.prepareSeedBuffer(rawInput));
            } finally {
                terminal.setAttributes(originalAttributes);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static PrefillResult readEscapeInput(Terminal terminal, LineReader lineReader)
            throws IOException, InterruptedException {
        String sequence = readInputBurst(terminal, 80, 20, 300);
        CliInputNormalization.EscapeSequenceType type =
                CliInputNormalization.classifyEscapeSequence(sequence);
        if (type == CliInputNormalization.EscapeSequenceType.STANDALONE_ESC) {
            return PrefillResult.canceledInput();
        }
        if (type == CliInputNormalization.EscapeSequenceType.BRACKETED_PASTE) {
            String pastedText = CliInputNormalization.bracketedPastePayload(sequence);
            while (!CliInputNormalization.containsBracketedPasteEnd(pastedText)) {
                String burst = readInputBurst(terminal, 30, 25, 500);
                if (burst.isEmpty()) {
                    break;
                }
                pastedText += burst;
            }
            return PrefillResult.seed(CliInputNormalization.prepareSeedBuffer(
                    CliInputNormalization.stripBracketedPasteEndMarker(pastedText)));
        }
        if (type == CliInputNormalization.EscapeSequenceType.CONTROL_SEQUENCE) {
            return PrefillResult.seed(CliInputHistory.seedBufferForHistoryNavigation(lineReader, sequence));
        }
        return PrefillResult.canceledInput();
    }

    private static String readInputBurst(Terminal terminal, long firstWaitMs, long idleWaitMs, long maxWaitMs)
            throws IOException, InterruptedException {
        NonBlockingReader reader = terminal.reader();
        StringBuilder buffer = new StringBuilder();
        long start = System.currentTimeMillis();
        long waitMs = firstWaitMs;
        while (System.currentTimeMillis() - start < maxWaitMs) {
            int next = reader.read(waitMs);
            if (next == NonBlockingReader.READ_EXPIRED || next < 0) {
                break;
            }
            buffer.append((char) next);
            waitMs = idleWaitMs;
        }
        return buffer.toString();
    }
}
