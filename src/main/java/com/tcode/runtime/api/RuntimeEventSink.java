package com.tcode.runtime.api;

@FunctionalInterface
public interface RuntimeEventSink {
    void emit(String type, String data);
}
