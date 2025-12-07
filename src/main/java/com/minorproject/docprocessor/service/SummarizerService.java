package com.minorproject.docprocessor.service;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SummarizerService {

    public String generateSummary(String text, int sentenceCount) {
        if (text == null || text.isBlank()) {
            return "No content available for summarization.";
        }

        // Split into sentences (basic)
        String[] sentences = text.split("(?<=[.!?])\\s+");

        if (sentences.length <= sentenceCount) {
            return text; // Already short enough
        }

        // Word frequency map
        Map<String, Integer> freq = new HashMap<>();
        for (String sentence : sentences) {
            for (String word : sentence.toLowerCase().split("\\W+")) {
                if (word.length() > 3) {  // ignore short words
                    freq.put(word, freq.getOrDefault(word, 0) + 1);
                }
            }
        }

        // Sentence scores
        Map<String, Integer> sentenceScores = new HashMap<>();
        for (String sentence : sentences) {
            int score = 0;
            for (String word : sentence.toLowerCase().split("\\W+")) {
                score += freq.getOrDefault(word, 0);
            }
            sentenceScores.put(sentence, score);
        }

        // Pick top N sentences
        return sentenceScores.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(sentenceCount)
                .map(Map.Entry::getKey)
                .sorted() // maintain original order
                .reduce("", (a, b) -> a + " " + b)
                .trim();
    }
}
