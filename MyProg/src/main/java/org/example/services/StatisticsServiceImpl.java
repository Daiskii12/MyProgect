package org.example.services;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.example.dto.statistics.*;
import org.example.repositories.SiteRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final StatisticsCalculator statisticsCalculator;

    @Override
    public StatisticsResponse getStatistics() {
        StatisticsResponse response = new StatisticsResponse();

        try {
            Map<String, Object> totalStats = statisticsCalculator.calculateTotalStatistics();

            TotalStatistics total = new TotalStatistics();
            total.setSites((Integer) totalStats.get("total_sites"));
            total.setPages((Integer) totalStats.get("total_pages"));
            total.setLemmas((Integer) totalStats.get("total_lemmas"));
            total.setIndexing((Integer) totalStats.get("indexing_sites") > 0);

            List<DetailedStatisticsItem> detailed = new ArrayList<>();
            List<org.example.model.Site> allSites = siteRepository.findAll();

            for (org.example.model.Site site : allSites) {
                Map<String, Object> siteStats = statisticsCalculator.calculateSiteStatistics(site);

                DetailedStatisticsItem item = new DetailedStatisticsItem();
                item.setUrl(site.getUrl());
                item.setName(site.getName());
                item.setStatus(site.getStatus().name());

                LocalDateTime statusTime = site.getStatusTime();
                if (statusTime != null) {
                    long statusTimeMillis = statusTime
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli();
                    item.setStatusTime(statusTimeMillis);
                } else {
                    item.setStatusTime(System.currentTimeMillis());
                }

                item.setError(site.getLastError() != null ? site.getLastError() : "");
                item.setPages((Integer) siteStats.get("successful_pages"));
                item.setLemmas((Integer) siteStats.get("lemma_count"));

                detailed.add(item);
            }

            StatisticsData data = new StatisticsData();
            data.setTotal(total);
            data.setDetailed(detailed);

            response.setStatistics(data);
            response.setResult(true);

        } catch (Exception e) {
            response.setResult(false);
            System.err.println("Ошибка при получении статистики: " + e.getMessage());
            e.printStackTrace();
        }

        return response;
    }
}