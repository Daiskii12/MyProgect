package org.example.services;

import lombok.RequiredArgsConstructor;
import org.example.model.Site;
import org.example.repositories.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatisticsCalculator {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    public Map<String, Object> calculateSiteStatistics(Site site) {
        Map<String, Object> stats = new HashMap<>();

        stats.put("site_id", site.getId());
        stats.put("site_name", site.getName());
        stats.put("site_url", site.getUrl());
        stats.put("status", site.getStatus().name());
        stats.put("last_error", site.getLastError());
        stats.put("status_time", site.getStatusTime());

        int totalPages = pageRepository.countBySite(site);
        int successfulPages = pageRepository.countSuccessfulPagesBySite(site);
        stats.put("total_pages", totalPages);
        stats.put("successful_pages", successfulPages);
        stats.put("error_pages", totalPages - successfulPages);

        int lemmaCount = lemmaRepository.countBySite(site);
        Integer totalFrequency = lemmaRepository.sumFrequencyBySite(site);
        stats.put("lemma_count", lemmaCount);
        stats.put("total_frequency", totalFrequency != null ? totalFrequency : 0);

        if (successfulPages > 0 && totalFrequency != null) {
            double avgLemmasPerPage = (double) totalFrequency / successfulPages;
            stats.put("avg_lemmas_per_page", Math.round(avgLemmasPerPage * 100.0) / 100.0);
        }

        LocalDateTime lastIndexTime = site.getStatusTime();
        if (lastIndexTime != null) {
            stats.put("last_index_time", lastIndexTime);
            stats.put("minutes_since_last_index",
                    java.time.Duration.between(lastIndexTime, LocalDateTime.now()).toMinutes());
        }

        return stats;
    }

    public Map<String, Object> calculateTotalStatistics() {
        Map<String, Object> totalStats = new HashMap<>();

        List<Site> allSites = siteRepository.findAll();

        totalStats.put("total_sites", allSites.size());
        totalStats.put("indexing_sites", siteRepository.countIndexingSites());
        totalStats.put("failed_sites", siteRepository.countFailedSites());

        int totalPages = 0;
        int totalLemmas = 0;

        for (Site site : allSites) {
            totalPages += pageRepository.countBySite(site);
            totalLemmas += lemmaRepository.countBySite(site);
        }

        totalStats.put("total_pages", totalPages);
        totalStats.put("total_lemmas", totalLemmas);

        return totalStats;
    }
}