package org.example.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class LemmaService {

    private LuceneMorphology russianMorphology;
    private LuceneMorphology englishMorphology;


    private final Pattern wordPattern = Pattern.compile("[а-яёА-ЯЁa-zA-Z]+");
    private final Pattern cyrillicPattern = Pattern.compile("[а-яёА-ЯЁ]+");
    private final Pattern latinPattern = Pattern.compile("[a-zA-Z]+");


    private final Set<String> servicePOS = new HashSet<>(Arrays.asList(
            "МЕЖД",      // Междометие
            "ПРЕДЛ",     // Предлог
            "СОЮЗ",      // Союз
            "ЧАСТ",      // Частица
            "INT",       // Междометие (англ)
            "CONJ",      // Союз (англ)
            "PREP",      // Предлог (англ)
            "PART"       // Частица (англ)
    ));


    private final Set<String> russianStopWords = new HashSet<>(Arrays.asList(
            "и", "в", "не", "на", "я", "он", "что", "то", "это", "как",
            "а", "по", "но", "за", "вы", "так", "же", "от", "из", "у",
            "к", "до", "бы", "мы", "о", "при", "во", "со", "без", "над",
            "для", "об", "под", "про", "перед", "через", "после", "вокруг",
            "или", "да", "нет", "ли", "быть", "мочь", "сказать", "знать",
            "хотеть", "видеть", "идти", "взять", "дать", "жить", "смотреть",
            "думать", "говорить", "стать", "работать", "понять", "получить"
    ));


    private final Set<String> englishStopWords = new HashSet<>(Arrays.asList(
            "a", "an", "the", "and", "or", "but", "in", "on", "at", "to",
            "for", "of", "with", "by", "from", "up", "about", "into", "over",
            "after", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "having", "do", "does", "did", "doing",
            "will", "would", "shall", "should", "may", "might", "must",
            "can", "could", "i", "you", "he", "she", "it", "we", "they",
            "me", "him", "her", "us", "them", "my", "your", "his", "its",
            "our", "their", "this", "that", "these", "those", "what", "which",
            "who", "whom", "whose", "where", "when", "why", "how"
    ));

    public LemmaService() {
        try {

            this.russianMorphology = new RussianLuceneMorphology();
        } catch (IOException e) {
            System.err.println("Не удалось загрузить русскую морфологию: " + e.getMessage());
        }

        try {

            this.englishMorphology = new EnglishLuceneMorphology();
        } catch (IOException e) {
            System.err.println("Не удалось загрузить английскую морфологию: " + e.getMessage());
        }
    }


    public Map<String, Integer> getLemmas(String text) {
        Map<String, Integer> lemmas = new HashMap<>();

        if (text == null || text.trim().isEmpty()) {
            return lemmas;
        }

        String cleanedText = text.toLowerCase()
                .replaceAll("[^а-яёa-z\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (cleanedText.isEmpty()) {
            return lemmas;
        }

        String[] words = cleanedText.split("\\s+");

        for (String word : words) {
            if (word.length() < 2 ||
                    russianStopWords.contains(word) ||
                    englishStopWords.contains(word)) {
                continue;
            }

            try {
                boolean isCyrillic = cyrillicPattern.matcher(word).matches();
                boolean isLatin = latinPattern.matcher(word).matches();

                if (isCyrillic && russianMorphology != null) {
                    processRussianWord(word, lemmas);

                } else if (isLatin && englishMorphology != null) {
                    processEnglishWord(word, lemmas);

                } else {

                    lemmas.put(word, lemmas.getOrDefault(word, 0) + 1);
                }

            } catch (Exception e) {

                lemmas.put(word, lemmas.getOrDefault(word, 0) + 1);
            }
        }

        return lemmas;
    }

    private void processRussianWord(String word, Map<String, Integer> lemmas) {
        List<String> normalForms = russianMorphology.getNormalForms(word);

        if (normalForms == null || normalForms.isEmpty()) {
            return;
        }

        List<String> morphInfo = russianMorphology.getMorphInfo(word);

        if (isServiceWord(morphInfo)) {
            return;
        }

        String lemma = normalForms.get(0);

        lemmas.put(lemma, lemmas.getOrDefault(lemma, 0) + 1);
    }

    private void processEnglishWord(String word, Map<String, Integer> lemmas) {
        List<String> normalForms = englishMorphology.getNormalForms(word);

        if (normalForms == null || normalForms.isEmpty()) {
            return;
        }

        List<String> morphInfo = englishMorphology.getMorphInfo(word);

        if (isServiceWord(morphInfo)) {
            return;
        }

        String lemma = normalForms.get(0);

        lemmas.put(lemma, lemmas.getOrDefault(lemma, 0) + 1);
    }


    private boolean isServiceWord(List<String> morphInfo) {
        if (morphInfo == null || morphInfo.isEmpty()) {
            return false;
        }

        for (String info : morphInfo) {
            for (String pos : servicePOS) {
                if (info.contains(pos)) {
                    return true;
                }
            }
        }

        return false;
    }

    public String cleanHtml(String html) {
        if (html == null || html.trim().isEmpty()) {
            return "";
        }

        try {

            String withoutScripts = html
                    .replaceAll("<script[^>]*>.*?</script>", " ")
                    .replaceAll("<style[^>]*>.*?</style>", " ")
                    .replaceAll("<!--.*?-->", " ");

            String withSpaces = withoutScripts
                    .replaceAll("<br[^>]*>", "\n")
                    .replaceAll("<p[^>]*>", "\n")
                    .replaceAll("<div[^>]*>", "\n")
                    .replaceAll("<li[^>]*>", "\n• ")
                    .replaceAll("<h[1-6][^>]*>", "\n")
                    .replaceAll("</h[1-6]>", "\n")
                    .replaceAll("<[^>]+>", " ");

            String decoded = withSpaces
                    .replaceAll("&nbsp;", " ")
                    .replaceAll("&quot;", "\"")
                    .replaceAll("&amp;", "&")
                    .replaceAll("&lt;", "<")
                    .replaceAll("&gt;", ">")
                    .replaceAll("&apos;", "'")
                    .replaceAll("&#39;", "'")
                    .replaceAll("&ndash;", "-")
                    .replaceAll("&mdash;", "-")
                    .replaceAll("&laquo;", "\"")
                    .replaceAll("&raquo;", "\"")
                    .replaceAll("&hellip;", "...");

            String cleaned = decoded
                    .replaceAll("\\s+", " ")      // Множественные пробелы -> один
                    .replaceAll("\\n\\s*\\n+", "\n\n") // Убираем пустые строки
                    .trim();

            String title = extractTitle(html);
            if (!title.isEmpty()) {
                cleaned = title + "\n\n" + cleaned;
            }

            return cleaned;

        } catch (Exception e) {
            System.err.println("Ошибка при очистке HTML: " + e.getMessage());
            return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        }
    }

    private String extractTitle(String html) {
        try {
            int titleStart = html.indexOf("<title");
            if (titleStart == -1) {
                return "";
            }

            titleStart = html.indexOf(">", titleStart) + 1;
            int titleEnd = html.indexOf("</title>", titleStart);

            if (titleEnd > titleStart) {
                return html.substring(titleStart, titleEnd).trim();
            }
        } catch (Exception e) {

        }
        return "";
    }

    public String cleanHtmlWithJsoup(String html) {
        if (html == null || html.trim().isEmpty()) {
            return "";
        }

        try {
            org.jsoup.nodes.Document doc = Jsoup.parse(html);
            doc.select("script, style, noscript, iframe, object, embed").remove();

            String title = doc.title();

            String bodyText = doc.body().text();

            String result = title.isEmpty() ? bodyText : title + "\n\n" + bodyText;

            return result.replaceAll("\\s+", " ").trim();

        } catch (Exception e) {

            return cleanHtml(html);
        }
    }

    public Set<String> getUniqueLemmas(String text) {
        return getLemmas(text).keySet();
    }

    public List<Map.Entry<String, Integer>> getSortedLemmas(String text) {
        Map<String, Integer> lemmas = getLemmas(text);
        return lemmas.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .collect(Collectors.toList());
    }

    public void testLemmatization() {
        System.out.println("=== Тестирование лемматизации ===");

        String testText = "Повторное появление леопарда в Осетии позволяет предположить, " +
                "что леопард постоянно обитает в некоторых районах Северного Кавказа.";

        System.out.println("Текст: " + testText);
        System.out.println("Леммы:");

        Map<String, Integer> lemmas = getLemmas(testText);
        lemmas.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .forEach(entry -> System.out.printf("  %-15s : %d%n", entry.getKey(), entry.getValue()));

    }


    public void testHtmlCleaning() {
        System.out.println("\n=== Тестирование очистки HTML ===");

        String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Пример страницы</title>
                <style>body { color: red; }</style>
                <script>console.log('test');</script>
            </head>
            <body>
                <h1>Заголовок статьи</h1>
                <p>Первый абзац с <strong>важным</strong> текстом.</p>
                <p>Второй абзац &nbsp; с &quot;кавычками&quot;.</p>
                <ul>
                    <li>Первый пункт</li>
                    <li>Второй пункт</li>
                </ul>
            </body>
            </html>
            """;

        System.out.println("Очищенный текст:");
        System.out.println(cleanHtml(html));

    }
}