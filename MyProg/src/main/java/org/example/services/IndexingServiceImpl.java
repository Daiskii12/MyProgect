package org.example.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.config.Site;
import org.example.config.SitesList;
import org.example.model.Page;
import org.example.model.SiteStatus;
import org.example.repositories.PageRepository;
import org.example.repositories.SiteRepository;
import org.jsoup.Connection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    private ForkJoinPool pool;
    private Map<String, SiteIndexer> siteIndexers = new ConcurrentHashMap<>();
    private volatile boolean isIndexing = false;

    @Override
    public synchronized boolean startIndexing() {
        if (isIndexing) {
            log.warn("Индексация уже запущена");
            return false;
        }

        isIndexing = true;
        pool = new ForkJoinPool();

        for (Site siteConfig : sitesList.getSites()) {
            SiteIndexer indexer = new SiteIndexer(siteConfig);
            siteIndexers.put(siteConfig.getUrl(), indexer);
            pool.execute(() -> {
                try {
                    indexSite(siteConfig);
                } catch (Exception e) {
                    log.error("Ошибка индексации сайта: {}", siteConfig.getUrl(), e);
                }
            });
        }

        return true;
    }

    @Override
    public synchronized boolean stopIndexing() {
        if (!isIndexing) {
            return false;
        }

        isIndexing = false;
        pool.shutdownNow();

        siteRepository.findAll().forEach(site -> {
            if (site.getStatus() == SiteStatus.INDEXING) {
                site.setStatus(SiteStatus.FAILED);
                site.setLastError("Индексация остановлена пользователем");
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
            }
        });

        return true;
    }

    @Override
    public boolean indexPage(String url) {
        return false;
    }

    private void indexSite(Site siteConfig) {
        org.example.model.Site siteEntity = new org.example.model.Site();
        siteEntity.setUrl(siteConfig.getUrl());
        siteEntity.setName(siteConfig.getName());
        siteEntity.setStatus(SiteStatus.INDEXING);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteEntity = siteRepository.save(siteEntity);

        try {
            WebCrawler crawler = new WebCrawler(siteConfig.getUrl(), siteEntity);
            Set<String> pages = crawler.compute();

            siteEntity.setStatus(SiteStatus.INDEXED);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);

            log.info("Сайт {} проиндексирован. Найдено страниц: {}", siteConfig.getUrl(), pages.size());

        } catch (Exception e) {
            log.error("Ошибка при индексации сайта: {}", siteConfig.getUrl(), e);
            siteEntity.setStatus(SiteStatus.FAILED);
            siteEntity.setLastError(e.getMessage());
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);
        }
    }

    private class WebCrawler extends RecursiveTask<Set<String>> {
        private final String url;
        private final org.example.model.Site site;
        private final Set<String> visitedUrls;

        private static final List<String> EXCLUDED_EXTENSIONS = Arrays.asList(
                ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".svg",
                ".ico", ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
                ".zip", ".rar", ".7z", ".tar", ".gz",
                ".mp3", ".mp4", ".avi", ".mov", ".wmv",
                ".css", ".js", ".json", ".xml"
        );

        public WebCrawler(String url, org.example.model.Site site) {
            this.url = url;
            this.site = site;
            this.visitedUrls = Collections.synchronizedSet(new HashSet<>());
        }

        @Override
        protected Set<String> compute() {
            Set<String> result = new HashSet<>();

            if (shouldExcludeUrl(url)) {
                return result;
            }

            result.add(url);
            visitedUrls.add(url);

            try {
                Thread.sleep(1500);

                Document doc = Jsoup.connect(url)
                        .userAgent("HeliontSearchBot")
                        .referrer("http://www.google.com")
                        .timeout(10000)
                        .followRedirects(true)
                        .ignoreContentType(false)
                        .get();

                savePage(url, doc.html(), 200);

                Elements links = doc.select("a[href]");
                List<WebCrawler> tasks = new ArrayList<>();

                for (var link : links) {
                    String absUrl = link.attr("abs:href");

                    absUrl = normalizeUrl(absUrl);

                    if (isValidUrlForCrawling(absUrl, site.getUrl()) &&
                            !visitedUrls.contains(absUrl) &&
                            !pageRepository.existsByPathAndSite(absUrl, site) &&
                            !shouldExcludeUrl(absUrl)) {

                        WebCrawler task = new WebCrawler(absUrl, site);
                        task.fork();
                        tasks.add(task);
                    }
                }

                for (WebCrawler task : tasks) {
                    result.addAll(task.join());
                }

            } catch (IOException e) {
                log.warn("Не удалось загрузить страницу {}: {}", url, e.getMessage());
                if (!shouldExcludeUrl(url)) {
                    savePage(url, "", 404);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            return result;
        }


        private boolean shouldExcludeUrl(String url) {
            if (url == null || url.isEmpty()) {
                return true;
            }

            String lowerUrl = url.toLowerCase();
            for (String ext : EXCLUDED_EXTENSIONS) {
                if (lowerUrl.endsWith(ext)) {
                    return true;
                }
            }

            if (lowerUrl.startsWith("mailto:")) {
                return true;
            }

            if (lowerUrl.startsWith("tel:")) {
                return true;
            }

            if (lowerUrl.contains("#")) {
                return true;
            }

            try {
                Connection.Response response = Jsoup.connect(url)
                        .userAgent("HeliontSearchBot")
                        .timeout(3000)
                        .ignoreContentType(true)
                        .execute();

                String contentType = response.contentType();
                if (contentType != null && !contentType.startsWith("text/html")) {
                    return true;
                }
            } catch (Exception e) {
            }

            return false;
        }


        private boolean isValidUrlForCrawling(String url, String baseUrl) {
            if (url == null || url.isEmpty()) {
                return false;
            }

            try {
                URI uri = new URI(url);
                URI baseUri = new URI(baseUrl);

                String host = uri.getHost();
                String baseHost = baseUri.getHost();

                if (host == null || baseHost == null) {
                    return false;
                }

                host = host.replace("www.", "");
                baseHost = baseHost.replace("www.", "");

                return host.equals(baseHost);

            } catch (Exception e) {
                return false;
            }
        }


        private String normalizeUrl(String url) {
            if (url == null) return "";

            int fragmentIndex = url.indexOf('#');
            if (fragmentIndex != -1) {
                url = url.substring(0, fragmentIndex);
            }

            int queryIndex = url.indexOf('?');
            if (queryIndex != -1) {
                url = url.substring(0, queryIndex);
            }

            return url.trim();
        }

        private void savePage(String urlPath, String content, int code) {
            try {
                String normalizedPath = normalizeUrl(urlPath);

                if (shouldExcludeUrl(normalizedPath)) {
                    return;
                }

                Page page;
                Optional<Page> existingPage = pageRepository.findByPathAndSite(normalizedPath, site);

                if (existingPage.isPresent()) {
                    page = existingPage.get();
                    page.setCode(code);
                    page.setContent(content);
                    log.debug("Обновлена существующая страница: {}", normalizedPath);
                } else {
                    page = new Page();
                    page.setSite(site);
                    page.setPath(normalizedPath);
                    page.setCode(code);
                    page.setContent(content);
                    log.debug("Создана новая страница: {}", normalizedPath);
                }

                pageRepository.save(page);

                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);

            } catch (DataIntegrityViolationException e) {
                log.warn("Нарушение уникальности для пути: {}. Пропускаем.", urlPath);
            } catch (Exception e) {
                log.error("Ошибка при сохранении страницы {}: {}", urlPath, e.getMessage());
            }
        }
    }

    private static class SiteIndexer {
        private final Site site;

        public SiteIndexer(Site site) {
            this.site = site;
        }
    }
}