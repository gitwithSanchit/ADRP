package com.minorproject.docprocessor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SummarizerService {

    private static final Logger logger = LoggerFactory.getLogger(SummarizerService.class);
    private static final String SCRIPT_NAME = "summarizer.py";

    // --- 1. MAIN ENTRY POINT ---
    public String generateSummary(String text, String level) {
        if (text == null || text.isBlank()) return "No content available.";

        // Determine Intensity
        int pythonChunks;
        int textRankSentences;

        switch (level.toLowerCase()) {
            case "detailed":
                pythonChunks = 8;   // Optimized: 8 chunks covers the plot without freezing CPU
                textRankSentences = 50;
                break;
            case "short":
                pythonChunks = 2;   // Fast check
                textRankSentences = 5;
                break;
            case "medium":
            default:
                pythonChunks = 4;   // Balanced
                textRankSentences = 15;
                break;
        }

        // Attempt 1: AI Summarization (Best Quality)
        try {
            String aiSummary = runPythonSummarizer(text, pythonChunks);
            // Validation: Ensure we got a real response
            if (aiSummary != null && aiSummary.length() > 50 && !aiSummary.startsWith("Error")) {
                return aiSummary;
            }
        } catch (Exception e) {
            logger.error("AI Summarization failed. Switching to TextRank fallback.", e);
        }

        // Attempt 2: Fallback to TextRank (Algorithm)
        return generateTextRankSummary(text, textRankSentences);
    }

    // --- 2. PYTHON INTEGRATION ---
    private String runPythonSummarizer(String text, int chunks) throws IOException, InterruptedException {
        // Robust Path Finding
        Path scriptPath = findScriptPath();
        if (scriptPath == null) {
            throw new FileNotFoundException("Could not find " + SCRIPT_NAME + " in project directories.");
        }

        ProcessBuilder pb = new ProcessBuilder(
                "python",
                scriptPath.toString(),
                String.valueOf(chunks)
        );

        pb.redirectErrorStream(true); // Merge Error stream to debug if needed
        Process process = pb.start();

        // Write Input
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.write(text);
            writer.flush();
        }

        // Read Output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Filter out junk logs from TensorFlow/HuggingFace
                if (!line.contains("%") && !line.contains("it/s") && !line.contains("tensorflow") && !line.contains("oneDNN")) {
                    output.append(line).append(" ");
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            logger.warn("Python script exited with code: {}", exitCode);
        }

        return output.toString().trim();
    }

    private Path findScriptPath() {
        // 1. Try hardcoded path (Highest priority for your setup)
        Path hardcoded = Paths.get("C:\\Users\\sanch\\IdeaProjects\\docprocessor\\docprocessor", SCRIPT_NAME);
        if (Files.exists(hardcoded)) return hardcoded;

        // 2. Try current directory
        Path current = Paths.get(System.getProperty("user.dir"), SCRIPT_NAME);
        if (Files.exists(current)) return current;

        // 3. Try nested project structure
        Path nested = Paths.get(System.getProperty("user.dir"), "docprocessor", SCRIPT_NAME);
        if (Files.exists(nested)) return nested;

        return null;
    }

    // --- 3. TEXTRANK FALLBACK ---

    private String generateTextRankSummary(String text, int finalSentenceCount) {
        List<String> allSentences = splitSentences(text);
        if (allSentences.size() <= finalSentenceCount) return text;

        if (allSentences.size() > 100) {
            return generateLargeDocumentSummary(allSentences, finalSentenceCount);
        } else {
            return runTextRank(allSentences, finalSentenceCount);
        }
    }

    private String generateLargeDocumentSummary(List<String> allSentences, int targetCount) {
        int chunkSize = 50;
        List<String> chunkSummaries = new ArrayList<>();
        int totalChunks = (int) Math.ceil((double) allSentences.size() / chunkSize);
        int sentencesPerChunk = Math.max(1, targetCount / totalChunks);

        for (int i = 0; i < allSentences.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, allSentences.size());
            List<String> chunk = allSentences.subList(i, end);
            String miniSummary = runTextRank(chunk, sentencesPerChunk);
            if (!miniSummary.isBlank()) chunkSummaries.add(miniSummary);
        }
        return String.join(" ", chunkSummaries);
    }

    private String runTextRank(List<String> sentences, int numSentences) {
        int n = sentences.size();
        if (n == 0) return "";
        double[][] graph = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j) graph[i][j] = calculateSimilarity(sentences.get(i), sentences.get(j));
            }
        }

        double[] scores = new double[n];
        Arrays.fill(scores, 1.0);

        for (int iter = 0; iter < 20; iter++) {
            double[] newScores = new double[n];
            for (int i = 0; i < n; i++) {
                double incomingSum = 0;
                for (int j = 0; j < n; j++) {
                    if (i != j && graph[j][i] > 0) {
                        double sumWeightsJ = Arrays.stream(graph[j]).sum();
                        incomingSum += (graph[j][i] / (sumWeightsJ + 1e-9)) * scores[j];
                    }
                }
                newScores[i] = 0.15 + (0.85 * incomingSum);
            }
            scores = newScores;
        }

        Map<Integer, Double> indexToScore = new HashMap<>();
        for(int i=0; i<n; i++) indexToScore.put(i, scores[i]);

        return indexToScore.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .limit(numSentences)
                .map(Map.Entry::getKey)
                .sorted()
                .map(sentences::get)
                .collect(Collectors.joining(" "));
    }

    private double calculateSimilarity(String s1, String s2) {
        Set<String> w1 = tokenize(s1);
        Set<String> w2 = tokenize(s2);
        if (w1.isEmpty() || w2.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(w1);
        intersection.retainAll(w2);

        double denominator = Math.log(w1.size()) + Math.log(w2.size());
        return denominator == 0 ? 0.0 : intersection.size() / denominator;
    }

    // --- 4. UTILITY METHODS ---

    public List<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();

        Map<String, Integer> freq = new HashMap<>();
        Arrays.stream(text.toLowerCase().split("\\W+"))
                .filter(word -> word.length() > 4 && !STOP_WORDS.contains(word) && !isNumeric(word))
                .forEach(word -> freq.merge(word, 1, Integer::sum));

        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(6)
                .map(e -> capitalize(e.getKey()))
                .collect(Collectors.toList());
    }

    public String analyzeSentiment(String text) {
        if (text == null || text.isBlank()) return "Neutral";
        String lower = text.toLowerCase();

        long pos = Arrays.stream(lower.split("\\W+")).filter(POSITIVE_WORDS::contains).count();
        long neg = Arrays.stream(lower.split("\\W+")).filter(NEGATIVE_WORDS::contains).count();

        if (pos > neg) return "Positive";
        if (neg > pos) return "Negative";
        return "Neutral";
    }

    private List<String> splitSentences(String text) {
        Pattern pattern = Pattern.compile("(?<=[.!?])\\s+(?=[A-Z\"'])");
        return Arrays.stream(pattern.split(text))
                .map(s -> s.trim().replace("\n", " "))
                .filter(s -> s.length() > 15)
                .collect(Collectors.toList());
    }

    private Set<String> tokenize(String sentence) {
        return Arrays.stream(sentence.toLowerCase().split("\\W+"))
                .filter(w -> !w.isEmpty() && !STOP_WORDS.contains(w) && !isNumeric(w))
                .collect(Collectors.toSet());
    }

    private boolean isNumeric(String str) { return str.matches("-?\\d+(\\.\\d+)?"); }
    private String capitalize(String str) { return (str == null || str.isEmpty()) ? str : str.substring(0, 1).toUpperCase() + str.substring(1); }

    // --- STATIC DICTIONARIES ---
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "is", "at", "which", "on", "and", "a", "an", "in", "to", "of",
            "for", "with", "as", "by", "that", "this", "it", "or", "be", "are",
            "from", "was", "were", "but", "not", "have", "has", "had", "they",
            "you", "we", "can", "will", "if", "would", "should", "could", "their", "there"
    );
    private static final Set<String> POSITIVE_WORDS = Set.of(
            "excellent", "good", "great", "success", "growth", "positive", "win",
            "happy", "love", "joy", "best", "improving", "solution", "effective"
    );
    private static final Set<String> NEGATIVE_WORDS = Set.of(
            "fail", "bad", "loss", "error", "decline", "problem", "risk",
            "poor", "sad", "hate", "worst", "difficult", "issue", "mistake"
    );
}