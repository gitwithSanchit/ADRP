package com.minorproject.docprocessor.controller;

import com.minorproject.docprocessor.service.TextExtractionService;
import com.minorproject.docprocessor.service.SummarizerService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.multipart.MultipartFile;

@Controller
public class UploadController {

    private final TextExtractionService textExtractionService;
    private final SummarizerService summarizerService;

    public UploadController(TextExtractionService textExtractionService,
                            SummarizerService summarizerService) {
        this.textExtractionService = textExtractionService;
        this.summarizerService = summarizerService;
    }

    @PostMapping("/upload")
    public String handleFileUpload(@RequestParam("file") MultipartFile file,
                                   @RequestParam("level") String level,
                                   Model model) {

        if (file.isEmpty()) {
            model.addAttribute("error", "Please upload a valid file.");
            return "index";
        }

        try {
            // Extract file name
            String fileName = file.getOriginalFilename();
            model.addAttribute("fileName", fileName);

            // Extract text
            String extractedText = textExtractionService.extractText(file.getBytes());
            model.addAttribute("originalText", extractedText);

            // Summary Level Logic
            int sentenceCount;
            switch (level.toLowerCase()) {
                case "short": sentenceCount = 2; break;
                case "medium": sentenceCount = 4; break;
                case "detailed": sentenceCount = 7; break;
                default: sentenceCount = 3;
            }

            // Generate Summary
            String summary = summarizerService.generateSummary(extractedText, sentenceCount);
            model.addAttribute("summary", summary);

            // Stats
            int originalWords = extractedText.split("\\s+").length;
            int summaryWords = summary.split("\\s+").length;
            int compression = 100 - (summaryWords * 100 / originalWords);

            model.addAttribute("originalWords", originalWords);
            model.addAttribute("summaryWords", summaryWords);
            model.addAttribute("compression", compression);

            return "result";

        } catch (Exception e) {
            model.addAttribute("error", "Error processing file: " + e.getMessage());
            return "index";
        }
    }

    // ⭐ DOWNLOAD SUMMARY (TXT FORMAT)
    @PostMapping("/download/txt")
    public ResponseEntity<byte[]> downloadTxt(@RequestParam("content") String content) {

        byte[] fileBytes = content.getBytes();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=summary.txt")
                .body(fileBytes);
    }
}
