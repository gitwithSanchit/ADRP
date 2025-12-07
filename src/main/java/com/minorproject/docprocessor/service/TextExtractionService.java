package com.minorproject.docprocessor.service;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

@Service
public class TextExtractionService {

    private final Tika tika = new Tika();

    public String extractText(byte[] fileBytes) {
        try {
            return tika.parseToString(new java.io.ByteArrayInputStream(fileBytes));
        } catch (Exception e) {
            e.printStackTrace();
            return "Failed to extract text: " + e.getMessage();
        }
    }
}
