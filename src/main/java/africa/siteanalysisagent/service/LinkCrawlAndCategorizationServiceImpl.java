package africa.siteanalysisagent.service;

import africa.siteanalysisagent.dto.CategorizedLink;
import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LinkCrawlAndCategorizationServiceImpl implements LinkCrawlAndCategorizationService {

    private final BrokenLinkAndDuplicateTracker brokenLinkAndDuplicateTracker;

    private final ProgressTracker progressTracker;

    private static final Set<String> SOCIAL_MEDIA_DOMAINS = Set.of(
            "facebook.com", "twitter.com", "linkedin.com", "instagram.com", "youtube.com", "tiktok.com"
    );

    public CategorizedLink categorizedLinkDto(Document document) {
        List<String> navigationLinks = new ArrayList<>();
        List<String> footersLinks = new ArrayList<>();
        List<String> sidebarsLinks = new ArrayList<>();
        List<String> breadcrumbLinks = new ArrayList<>();
        List<String> outboundLinks = new ArrayList<>();
        List<String> backLinks = new ArrayList<>();
        List<String> affiliateLinks = new ArrayList<>();
        List<String> socialMediaLinks = new ArrayList<>();

        Elements allLinks = document.select("a[href]");

        for (Element link : allLinks) {
            String url = link.absUrl("href");

            if (url.isEmpty() || url.startsWith("javascript:") || url.startsWith("#")){
                continue; // ignore empty, javascript, or anchor links
            }
            if (isAffiliateLink(url)) {
                affiliateLinks.add(url);
            } else if (isExternalLink(url, document.baseUri())) {
                outboundLinks.add(url);
            } else if (isInsideTag(link, "nav")) {
                navigationLinks.add(url);
            } else if (isInsideTag(link, "footer")) {
                footersLinks.add(url);
            } else if (isInsideTag(link, "aside") || isInsideClass(link, "sidebar")) {
                sidebarsLinks.add(url);
            } else if (isInsideTag(link, "ol") || isInsideTag(link, "ul")) {
                breadcrumbLinks.add(url);
            } else {
                backLinks.add(url);
            }
        }

        return new CategorizedLink(
                navigationLinks, footersLinks, sidebarsLinks, breadcrumbLinks,
                outboundLinks, backLinks, affiliateLinks, socialMediaLinks
        );
    }

    public void detectBrokenAndDuplicateLinks(String scanId, String channelId, CategorizedLink categorizedLinks) {
        List<String> allLinks = new ArrayList<>();
        allLinks.addAll(categorizedLinks.navigationLinks());
        allLinks.addAll(categorizedLinks.footerLinks());
        allLinks.addAll(categorizedLinks.sidebarLinks());
        allLinks.addAll(categorizedLinks.breadcrumbLinks());
        allLinks.addAll(categorizedLinks.outboundLinks());
        allLinks.addAll(categorizedLinks.backlinks());
        allLinks.addAll(categorizedLinks.affiliateLinks());
        allLinks.addAll(categorizedLinks.socialMediaLinks());

        Set<String> seenLinks = new HashSet<>();
        for (String link : allLinks) {
            if (!seenLinks.add(link)) {
                brokenLinkAndDuplicateTracker.logDuplicateLink(scanId, link);
            }

            // Detect broken links
            int status = getLinkStatus(link);
            if (status == 404 || status == -1) {
                brokenLinkAndDuplicateTracker.logBrokenLink(scanId, link);
                sendCriticalBrokenLinkAlert(channelId, link);
            }
        }
    }

    private int getLinkStatus(String url) {
        try {
            Connection.Response response = Jsoup.connect(url)
                    .ignoreHttpErrors(true)
                    .timeout(5000)
                    .execute();
            return response.statusCode();
        } catch (IOException e) {
            return -1; // -1 means the link is unreachable
        }
    }

    private void sendCriticalBrokenLinkAlert(String channelId, String link) {
        String alertMessage = "🚨 **Critical Broken Link Detected!**\n" +
                "❌ `" + link + "` is returning a 404 error.\n" +
                "🔧 Suggested Fix: Remove or update the link.";
        progressTracker.sendAlert(channelId, alertMessage);
    }


    private boolean isExternalLink(String url, String baseUrl) {
        return !url.startsWith(baseUrl);
    }

    private boolean isInsideTag(Element element, String tagName) {
        return element.parents().stream().anyMatch(parent -> parent.tagName().equalsIgnoreCase(tagName));
    }

    private boolean isInsideClass(Element element, String className) {
        return element.parents().stream().anyMatch(parent -> parent.className().contains(className));
    }

    private boolean isAffiliateLink(String url) {
        return url.contains("ref=") || url.contains("affiliate");
    }
}
