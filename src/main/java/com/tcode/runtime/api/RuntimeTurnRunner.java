package com.tcode.runtime.api;

@FunctionalInterface
public interface RuntimeTurnRunner {
    String run(String input, RuntimeEventSink events) throws Exception;
}
