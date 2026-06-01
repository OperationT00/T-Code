package com.tcode.tool;

import com.tcode.web.FetchResult;
import com.tcode.web.HtmlExtractor;
import com.tcode.web.NetworkPolicy;
import com.tcode.web.SearchProvider;
import com.tcode.web.SearchProviderFactory;
import com.tcode.web.SearchResult;
import com.tcode.web.WebFetcher;

import java.util.List;
import java.util.function.Supplier;

public final class WebService {
    private final Supplier<SearchProvider> searchProviderFactory;
    private final Supplier<WebFetcher> webFetcherFactory;
    private final Supplier<HtmlExtractor> htmlExtractorFactory;
    private final Supplier<NetworkPolicy> networkPolicyFactory;
    private SearchProvider searchProvider;
    private WebFetcher webFetcher;
    private HtmlExtractor htmlExtractor;
    private NetworkPolicy networkPolicy;

    public WebService() {
        this(SearchProviderFactory::create, WebFetcher::new, HtmlExtractor::new, NetworkPolicy::new);
    }

    WebService(Supplier<SearchProvider> searchProviderFactory,
               Supplier<WebFetcher> webFetcherFactory,
               Supplier<HtmlExtractor> htmlExtractorFactory,
               Supplier<NetworkPolicy> networkPolicyFactory) {
        this.searchProviderFactory = searchProviderFactory;
        this.webFetcherFactory = webFetcherFactory;
        this.htmlExtractorFactory = htmlExtractorFactory;
        this.networkPolicyFactory = networkPolicyFactory;
    }

    public String search(String query, int topK) {
        if (query == null || query.isBlank()) {
            return "搜索关键词不能为空";
        }
        SearchProvider provider = searchProvider();
        if (!provider.isReady()) {
            return "警告: " + provider.unavailableHint();
        }
        try {
            return formatSearchResults(provider.name(), query, provider.search(query.trim(), topK));
        } catch (Exception e) {
            return "搜索失败 (" + provider.name() + "): " + e.getMessage();
        }
    }

    public String fetch(String url, int maxChars) {
        if (url == null || url.isBlank()) {
            return "URL 不能为空";
        }
        NetworkPolicy policy = networkPolicy();
        String denyReason = policy.checkUrl(url);
        if (denyReason != null) {
            return "网络访问被拒绝: " + denyReason;
        }
        String rateReason = policy.acquire();
        if (rateReason != null) {
            return rateReason;
        }

        try {
            WebFetcher.RawResponse raw = webFetcher().fetch(url.trim());
            HtmlExtractor.Extracted extracted = htmlExtractor().extract(raw.body(), raw.url());
            String markdown = extracted.markdown();
            int originalLength = markdown.length();
            boolean truncated = false;
            if (maxChars > 0 && markdown.length() > maxChars) {
                markdown = markdown.substring(0, maxChars);
                truncated = true;
            }
            return formatFetchResult(FetchResult.ok(raw.url(), extracted.title(), markdown, originalLength, truncated));
        } catch (Exception e) {
            return "抓取失败: " + e.getMessage();
        }
    }

    private synchronized SearchProvider searchProvider() {
        if (searchProvider == null) searchProvider = searchProviderFactory.get();
        return searchProvider;
    }

    private synchronized WebFetcher webFetcher() {
        if (webFetcher == null) webFetcher = webFetcherFactory.get();
        return webFetcher;
    }

    private synchronized HtmlExtractor htmlExtractor() {
        if (htmlExtractor == null) htmlExtractor = htmlExtractorFactory.get();
        return htmlExtractor;
    }

    private synchronized NetworkPolicy networkPolicy() {
        if (networkPolicy == null) networkPolicy = networkPolicyFactory.get();
        return networkPolicy;
    }

    private String formatSearchResults(String providerName, String query, List<SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "搜索 [" + providerName + "] " + query + "\n\n未找到相关结果。";
        }
        StringBuilder output = new StringBuilder("搜索 [").append(providerName).append("] ")
                .append(query).append("\n\n");
        for (SearchResult result : results) {
            output.append(result.position()).append(". ").append(result.title()).append("\n");
            if (!result.snippet().isBlank()) {
                String snippet = result.snippet();
                output.append("   ").append(snippet.length() > 200 ? snippet.substring(0, 200) + "..." : snippet)
                        .append("\n");
            }
            if (!result.url().isBlank()) {
                output.append("   ").append(result.url());
                if (!result.source().isBlank()) output.append("  (").append(result.source()).append(")");
                output.append("\n");
            }
            output.append("\n");
        }
        return output.toString().trim();
    }

    private String formatFetchResult(FetchResult result) {
        StringBuilder output = new StringBuilder("抓取: ").append(result.url()).append("\n");
        if (!result.title().isBlank()) output.append("标题: ").append(result.title()).append("\n");
        if (result.bodyEmpty()) {
            return output.append("\n").append(result.hint()).append("\n").toString();
        }
        output.append("正文 ").append(result.contentLength()).append(" 字符");
        if (result.truncated()) output.append("（已截断）");
        return output.append("\n\n---\n\n").append(result.markdown()).toString();
    }
}
