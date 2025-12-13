package com.minorproject.docprocessor.service;

import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Service
public class TextExtractionService {

    private final Tika tika = new Tika();
    // Logger is better than System.out.println for real projects
    private static final Logger logger = LoggerFactory.getLogger(TextExtractionService.class);

    public String extractText(byte[] fileBytes) throws IOException {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new IOException("File is empty");
        }

        try {
            // Tika detects type (PDF, DOCX, TXT) automatically
            String extractedText = tika.parseToString(new ByteArrayInputStream(fileBytes));

            // Edge Case: Scanned PDFs (Images) often return empty strings
            if (extractedText == null || extractedText.isBlank()) {
                logger.warn("Parsed file but found no text. It might be an image-only PDF.");
                return ""; // Return empty so controller can handle it
            }

            return extractedText.trim();

        } catch (Exception e) {
            logger.error("Text extraction failed", e);
            throw new IOException("Error parsing document: " + e.getMessage(), e);
        }
    }
}