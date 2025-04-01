package africa.siteanalysisagent.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.stream.Stream;

@Data
@Builder
public class CategorizedLink {
    // Structural Links
    private List<String> navigationLinks;
    private List<String> footerLinks;
    private List<String> sidebarLinks;
    private List<String> breadcrumbLinks;

    // Relationship Links
    private List<String> outboundLinks;
    private List<String> backlinks;
    private List<String> affiliateLinks;
    private List<String> socialMediaLinks;

    // Resource Links
    private List<String> imageLinks;
    private List<String> scriptLinks;
    private List<String> stylesheetLinks;

    // Helper methods
    public int getExternalLinkCount(String baseUrl) {
        return (int) getAllLinks().stream()
                .filter(link -> !link.startsWith(baseUrl) &&
                        link.matches("^https?://.*"))
                .count();
    }

    public List<String> getExternalLinks(String baseUrl) {
        return getAllLinks().stream()
                .filter(link -> !link.startsWith(baseUrl) &&
                        link.matches("^https?://.*"))
                .toList();
    }

    public int getInternalLinkCount(String baseUrl) {
        return (int) getAllLinks().stream()
                .filter(link -> link.startsWith(baseUrl))
                .count();
    }

    public List<String> getInternalLinks(String baseUrl) {
        return getAllLinks().stream()
                .filter(link -> link.startsWith(baseUrl))
                .toList();
    }

    public int getResourceLinkCount() {
        return getResourceLinks().size();
    }

    public List<String> getResourceLinks() {
        return Stream.of(
                safeList(imageLinks),
                safeList(scriptLinks),
                safeList(stylesheetLinks)
        ).flatMap(List::stream).toList();
    }

    public int getTotalLinkCount() {
        return getAllLinks().size();
    }

    public List<String> getAllLinks() {
        return Stream.of(
                safeList(navigationLinks),
                safeList(footerLinks),
                safeList(sidebarLinks),
                safeList(breadcrumbLinks),
                safeList(outboundLinks),
                safeList(backlinks),
                safeList(affiliateLinks),
                safeList(socialMediaLinks),
                safeList(imageLinks),
                safeList(scriptLinks),
                safeList(stylesheetLinks)
        ).flatMap(List::stream).toList();
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

    private List<String> safeList(List<String> list) {
        return list != null ? list : List.of();
    }
}