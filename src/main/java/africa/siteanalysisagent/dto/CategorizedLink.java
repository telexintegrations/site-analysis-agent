package africa.siteanalysisagent.dto;

import lombok.Builder;
import lombok.Data;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@Builder
public class CategorizedLink {
    // Categories with deduplication
    @Builder.Default private Set<String> navigationLinks = new LinkedHashSet<>();
    @Builder.Default private Set<String> footerLinks = new LinkedHashSet<>();
    @Builder.Default private Set<String> sidebarLinks = new LinkedHashSet<>();
    @Builder.Default private Set<String> breadcrumbLinks = new LinkedHashSet<>();
    @Builder.Default private Set<String> outboundLinks = new LinkedHashSet<>();
    @Builder.Default private Set<String> backlinks = new LinkedHashSet<>();
    @Builder.Default private Set<String> affiliateLinks = new LinkedHashSet<>();
    @Builder.Default private Set<String> socialMediaLinks = new LinkedHashSet<>();
    @Builder.Default private Set<String> imageLinks = new LinkedHashSet<>();
    @Builder.Default private Set<String> scriptLinks = new LinkedHashSet<>();
    @Builder.Default private Set<String> stylesheetLinks = new LinkedHashSet<>();

    // Improved URL normalization
    // Helper methods
    public int getExternalLinkCount(String baseUrl) {
        return (int) getAllLinks().stream()
                .filter(link -> !link.startsWith(baseUrl) &&
                        link.matches("^https?://.*"))
                .count();
    }

    public Set<String> getExternalLinks(String baseUrl) {
        return getAllLinks().stream()
                .filter(link -> !link.startsWith(baseUrl) &&
                        link.matches("^https?://.*"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public int getInternalLinkCount(String baseUrl) {
        return (int) getAllLinks().stream()
                .filter(link -> link.startsWith(baseUrl))
                .count();
    }

    public Set<String> getInternalLinks(String baseUrl) {
        return getAllLinks().stream()
                .filter(link -> link.startsWith(baseUrl))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public int getResourceLinkCount() {
        return getResourceLinks().size();
    }

    public Set<String> getResourceLinks() {
        return Stream.of(
                        imageLinks,
                        scriptLinks,
                        stylesheetLinks
                ).flatMap(Set::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public int getTotalLinkCount() {
        return getAllLinks().size();
    }

    public Set<String> getAllLinks() {
        return Stream.of(
                        navigationLinks,
                        footerLinks,
                        sidebarLinks,
                        breadcrumbLinks,
                        outboundLinks,
                        backlinks,
                        affiliateLinks,
                        socialMediaLinks,
                        imageLinks,
                        scriptLinks,
                        stylesheetLinks
                ).flatMap(Set::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static String normalizeUrl(String baseUrl, String url) {
        // Handle null or empty URLs
        if (url == null || url.isBlank()) {
            return baseUrl;
        }

        // If URL is already absolute, return as-is
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }

        // Handle protocol-relative URLs (starting with //)
        if (url.startsWith("//")) {
            return baseUrl.startsWith("https") ? "https:" + url : "http:" + url;
        }

        // Handle root-relative URLs (starting with /)
        if (url.startsWith("/")) {
            return baseUrl.replaceAll("/+$", "") + url;
        }

        // Handle relative URLs (not starting with /)
        if (!url.startsWith("/")) {
            return baseUrl.replaceAll("/+$", "") + "/" + url;
        }

        // Fallback - combine base URL and path
        return baseUrl.replaceAll("/+$", "") + "/" + url.replaceAll("^/+", "");
    }
}