package org.example.services;

import lombok.RequiredArgsConstructor;
import org.example.dto.statistics.SearchResponse;
import org.example.dto.statistics.SearchResult;
import org.example.model.*;
import org.example.repositories.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final LemmaService lemmaService;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;

    private static final double TOO_FREQUENT_THRESHOLD = 0.8;

    public SearchResponse search(String query, String siteUrl, int offset, int limit) {
        SearchResponse response = new SearchResponse();

        try {
            Map<String, Integer> queryLemmas = lemmaService.getLemmas(query);

            if (queryLemmas.isEmpty()) {
                response.setResult(true);
                response.setCount(0);
                response.setData(Collections.emptyList());
                return response;
            }

            List<Site> targetSites = new ArrayList<>();
            if (siteUrl != null && !siteUrl.isEmpty()) {
                siteRepository.findByUrl(siteUrl)
                        .ifPresent(targetSites::add);
            } else {
                targetSites = siteRepository.findAll();
            }

            if (targetSites.isEmpty()) {
                response.setResult(true);
                response.setCount(0);
                response.setData(Collections.emptyList());
                return response;
            }

            List<SearchResult> searchResults = new ArrayList<>();

            for (Site site : targetSites) {
                List<SearchResult> siteResults = searchInSite(site, queryLemmas);
                searchResults.addAll(siteResults);
            }

            searchResults.sort((r1, r2) -> Double.compare(r2.getRelevance(), r1.getRelevance()));

            int total = searchResults.size();
            int endIndex = Math.min(offset + limit, total);
            List<SearchResult> paginatedResults = searchResults.subList(
                    Math.min(offset, total), endIndex);

            response.setResult(true);
            response.setCount(total);

        } catch (Exception e) {
            response.setResult(false);
            response.setError("Ошибка при выполнении поиска: " + e.getMessage());
        }

        return response;
    }

    private List<SearchResult> searchInSite(Site site, Map<String, Integer> queryLemmas) {
        Map<String, Lemma> dbLemmas = new HashMap<>();
        List<String> filteredLemmas = new ArrayList<>();

        for (String lemmaText : queryLemmas.keySet()) {
            Optional<Lemma> lemmaOpt = lemmaRepository.findByLemmaAndSite(lemmaText, site);
            if (lemmaOpt.isPresent()) {
                Lemma lemma = lemmaOpt.get();

                int totalPages = pageRepository.countBySite(site);
                if (totalPages > 0) {
                    double frequencyRatio = (double) lemma.getFrequency() / totalPages;
                    if (frequencyRatio < TOO_FREQUENT_THRESHOLD) {
                        dbLemmas.put(lemmaText, lemma);
                        filteredLemmas.add(lemmaText);
                    }
                }
            }
        }

        if (filteredLemmas.isEmpty()) {
            return Collections.emptyList();
        }

        filteredLemmas.sort((l1, l2) -> {
            int freq1 = dbLemmas.get(l1).getFrequency();
            int freq2 = dbLemmas.get(l2).getFrequency();
            return Integer.compare(freq1, freq2);
        });

        Set<Page> foundPages = null;

        for (String lemmaText : filteredLemmas) {
            Lemma lemma = dbLemmas.get(lemmaText);
            List<Index> indices = indexRepository.findByLemma(lemma);

            Set<Page> pagesForLemma = indices.stream()
                    .map(Index::getPage)
                    .collect(Collectors.toSet());

            if (foundPages == null) {
                foundPages = new HashSet<>(pagesForLemma);
            } else {
                foundPages.retainAll(pagesForLemma);
            }

            if (foundPages.isEmpty()) {
                break;
            }
        }

        if (foundPages == null || foundPages.isEmpty()) {
            return Collections.emptyList();
        }

        List<SearchResult> results = new ArrayList<>();
        double maxAbsRelevance = 0;
        Map<Page, Double> pageRelevance = new HashMap<>();

        for (Page page : foundPages) {
            double absRelevance = calculateAbsoluteRelevance(page, dbLemmas.values());
            pageRelevance.put(page, absRelevance);
            maxAbsRelevance = Math.max(maxAbsRelevance, absRelevance);
        }

        for (Map.Entry<Page, Double> entry : pageRelevance.entrySet()) {
            Page page = entry.getKey();
            double absRelevance = entry.getValue();
            double relativeRelevance = maxAbsRelevance > 0 ? absRelevance / maxAbsRelevance : 0;

            SearchResult result = new SearchResult();
            result.setUri(page.getPath());
            result.setTitle(extractTitle(page.getContent()));
            result.setSnippet(generateSnippet(page.getContent(),
                    new ArrayList<>(queryLemmas.keySet())));
            result.setRelevance(relativeRelevance);
            result.setSite(page.getSite().getUrl());
            result.setSiteName(page.getSite().getName());

            results.add(result);
        }

        return results;
    }


    private double calculateAbsoluteRelevance(Page page, Collection<Lemma> lemmas) {
        double totalRank = 0;

        for (Lemma lemma : lemmas) {
            Optional<Index> indexOpt = indexRepository.findByPageAndLemma(page, lemma);
            if (indexOpt.isPresent()) {
                totalRank += indexOpt.get().getRank();
            }
        }

        return totalRank;
    }

    private String extractTitle(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        try {
            int titleStart = html.indexOf("<title");
            if (titleStart != -1) {
                titleStart = html.indexOf(">", titleStart) + 1;
                int titleEnd = html.indexOf("</title>", titleStart);
                if (titleEnd > titleStart) {
                    String title = html.substring(titleStart, titleEnd).trim();
                    return title.length() > 100 ? title.substring(0, 100) + "..." : title;
                }
            }

            int h1Start = html.indexOf("<h1");
            if (h1Start != -1) {
                h1Start = html.indexOf(">", h1Start) + 1;
                int h1End = html.indexOf("</h1>", h1Start);
                if (h1End > h1Start) {
                    String h1 = html.substring(h1Start, h1End).trim();
                    return h1.length() > 100 ? h1.substring(0, 100) + "..." : h1;
                }
            }

        } catch (Exception e) {
        }

        return "Без заголовка";
    }

    private String generateSnippet(String html, List<String> searchWords) {
        String cleanText = lemmaService.cleanHtml(html);

        if (cleanText.isEmpty()) {
            return "";
        }

        List<Integer> positions = new ArrayList<>();
        String lowerText = cleanText.toLowerCase();

        for (String word : searchWords) {
            int pos = 0;
            while ((pos = lowerText.indexOf(word.toLowerCase(), pos)) != -1) {
                if ((pos == 0 || !Character.isLetter(lowerText.charAt(pos - 1))) &&
                        (pos + word.length() >= lowerText.length() ||
                                !Character.isLetter(lowerText.charAt(pos + word.length())))) {
                    positions.add(pos);
                }
                pos += word.length();
            }
        }

        if (positions.isEmpty()) {
            return getTextExcerpt(cleanText, 0, 200);
        }

        Collections.sort(positions);

        int snippetPosition = positions.get(0);

        String snippet = getTextExcerpt(cleanText, snippetPosition, 150);

        snippet = highlightWords(snippet, searchWords);

        return snippet;
    }

    private String getTextExcerpt(String text, int centerPos, int radius) {
        int start = Math.max(0, centerPos - radius);
        int end = Math.min(text.length(), centerPos + radius);

        while (start > 0 && Character.isLetter(text.charAt(start - 1))) {
            start--;
        }
        while (end < text.length() && Character.isLetter(text.charAt(end))) {
            end++;
        }

        String excerpt = text.substring(start, end);
        if (start > 0) excerpt = "..." + excerpt;
        if (end < text.length()) excerpt = excerpt + "...";

        return excerpt;
    }

    private String highlightWords(String text, List<String> words) {
        String result = text;

        for (String word : words) {
            String pattern = "\\b(" + word.toLowerCase() + ")\\b";
            result = result.replaceAll("(?i)" + pattern, "<b>$1</b>");
        }

        return result;
    }


    public SearchResponse advancedSearch(String query, String siteUrl,
                                         boolean exactMatch, boolean searchInTitle,
                                         int offset, int limit) {

        return search(query, siteUrl, offset, limit);
    }


    public void debugSearch(String query, String siteUrl) {
        System.out.println("\n=== Отладка поиска ===");
        System.out.println("Запрос: " + query);
        System.out.println("Сайт: " + (siteUrl != null ? siteUrl : "все"));

        Map<String, Integer> lemmas = lemmaService.getLemmas(query);
        System.out.println("Найденные леммы: " + lemmas.keySet());

        SearchResponse response = search(query, siteUrl, 0, 5);

        System.out.println("Результатов: " + response.getCount());
        System.out.println("Успешно: " + response.isResult());

        if (response.getError() != null) {
            System.out.println("Ошибка: " + response.getError());
        }
    }
}