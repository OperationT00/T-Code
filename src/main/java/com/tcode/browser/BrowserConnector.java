package com.tcode.browser;

public interface BrowserConnector {
    String status();

    String connectDefault();

    String disconnect();
}
