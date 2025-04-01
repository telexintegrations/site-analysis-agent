package africa.siteanalysisagent.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class SiteAnalysis {
    private String baseUrl;
    private List<String> scannedPages;  // All pages that were scanned
    private int totalPagesScanned;      // Count of scanned pages
    private int totalLinksFound;        // Total links across all pages

    // Detailed link information
    private Map<String, List<String>> pageLinksMap;  // Page URL -> List of all links on page
    private Map<String, String> linkStatusMap;       // Link URL -> Status (e.g., "ACTIVE 200", "BROKEN 404")

    // Summary counts
    private int totalActiveLinks;
    private int totalBrokenLinks;
    private int totalInternalLinks;
    private int totalExternalLinks;
    private int totalResourceLinks;

    // Categorized links for easy access
    private Map<String, CategorizedLink> categorizedLinks;  // Page URL -> CategorizedLink
    private List<String> activeLinks;    // Just URLs
    private List<String> brokenLinks;    // Just URLs
    private List<String> internalLinks;  // Just URLs
    private List<String> externalLinks;  // Just URLs
    private List<String> resourceLinks;


    // If you need the original detailed structure from your service
    private Map<String, Map<String, String>> detailedLinkStatus;  // Page URL -> (Link URL -> Status)
}