package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.CategorizedLink;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LinkCrawlAndCategorizationServiceImpl implements LinkCrawlAndCategorizationService {

    private final BrokenLinkAndDuplicateTracker brokenLinkTracker;

    private static final Set<String> SOCIAL_MEDIA_DOMAINS = Set.of(
            "facebook.com", "twitter.com", "linkedin.com",
            "instagram.com", "youtube.com", "tiktok.com"
    );

    @Override
    public CategorizedLink categorizeLinks(Document document) {
        Elements allLinks = document.select("a[href]");
        String baseUrl = document.baseUri();

        return CategorizedLink.builder()
                .navigationLinks(extractLinksInContext(allLinks, "nav"))
                .footerLinks(extractLinksInContext(allLinks, "footer"))
                .sidebarLinks(extractLinksInContext(allLinks, "aside", ".sidebar"))
                .breadcrumbLinks(extractLinksInContext(allLinks, ".breadcrumb", "nav[aria-label=breadcrumb]"))
                .outboundLinks(extractExternalLinks(allLinks, baseUrl))
                .socialMediaLinks(extractSocialMediaLinks(allLinks))
                .affiliateLinks(extractAffiliateLinks(allLinks))
                .backlinks(new ArrayList<>()) // To be populated separately
                .build();
    }

    @Override
    public Map<String, List<String>> analyzeLinkIssues(String baseUrl, CategorizedLink links) {
        return brokenLinkTracker.analyzeLinks(baseUrl, links);
    }

    private List<String> extractLinksInContext(Elements links, String... selectors) {
        return links.stream()
                .filter(link -> Arrays.stream(selectors)
                        .anyMatch(selector -> isInContext(link, selector)))
                .map(link -> link.absUrl("href"))
                .filter(this::isValidLink)
                .collect(Collectors.toList());
    }

    private boolean isInContext(Element link, String selector) {
        return link.parents().stream()
                .anyMatch(parent -> parent.is(selector) ||
                        parent.className().contains(selector.replace(".", "")));
    }

    private List<String> extractExternalLinks(Elements links, String baseUrl) {
        return links.stream()
                .map(link -> link.absUrl("href"))
                .filter(url -> !url.startsWith(baseUrl) &&
                        !url.isBlank() &&
                        !url.startsWith("javascript:"))
                .collect(Collectors.toList());
    }

    private List<String> extractSocialMediaLinks(Elements links) {
        return links.stream()
                .map(link -> link.absUrl("href"))
                .filter(url -> SOCIAL_MEDIA_DOMAINS.stream()
                        .anyMatch(domain -> url.contains(domain)))
                .collect(Collectors.toList());
    }

    private List<String> extractAffiliateLinks(Elements links) {
        return links.stream()
                .map(link -> link.absUrl("href"))
                .filter(url -> url.matches(".*(ref=|affiliate|partner|tag=).*"))
                .collect(Collectors.toList());
    }

    private boolean isValidLink(String url) {
        return url != null &&
                !url.isBlank() &&
                !url.startsWith("#") &&
                !url.startsWith("mailto:");
    }
}