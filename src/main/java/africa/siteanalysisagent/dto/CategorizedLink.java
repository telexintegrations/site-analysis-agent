package africa.siteanalysisagent.dto;

import java.util.List;

public record CategorizedLink(
        List<String> navigationLinks,
        List<String> footerLinks,
        List<String> sidebarLinks,
        List<String> breadcrumbLinks,
        List<String> outboundLinks,
        List<String> backlinks,
        List<String> affiliateLinks,
        List<String> socialMediaLinks
) {
}
