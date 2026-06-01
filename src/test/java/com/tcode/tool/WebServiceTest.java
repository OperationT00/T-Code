package com.tcode.tool;

import com.tcode.web.HtmlExtractor;
import com.tcode.web.NetworkPolicy;
import com.tcode.web.SearchProvider;
import com.tcode.web.SearchResult;
import com.tcode.web.WebFetcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WebServiceTest {

    @Test
    void reportsUnavailableSearchProviderAndRejectsBlankUrl() {
        WebService service = new WebService(
                () -> new SearchProvider() {
                    @Override public String name() { return "stub"; }
                    @Override public boolean isReady() { return false; }
                    @Override public String unavailableHint() { return "configure stub"; }
                    @Override public List<SearchResult> search(String query, int topK) { return List.of(); }
                },
                WebFetcher::new,
                HtmlExtractor::new,
                NetworkPolicy::new);

        assertTrue(service.search("T-Code", 5).contains("configure stub"));
        assertTrue(service.fetch(" ", 100).contains("URL"));
    }
}
