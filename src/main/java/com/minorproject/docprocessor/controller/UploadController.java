package com.minorproject.docprocessor.controller;

import com.minorproject.docprocessor.service.PdfReportService;
import com.minorproject.docprocessor.service.SummarizerService;
import com.minorproject.docprocessor.service.TextExtractionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Controller
public class UploadController {

    private final TextExtractionService textExtractionService;
    private final SummarizerService summarizerService;
    private final PdfReportService pdfReportService;

    public UploadController(TextExtractionService textExtractionService,
                            SummarizerService summarizerService,
                            PdfReportService pdfReportService) {
        this.textExtractionService = textExtractionService;
        this.summarizerService = summarizerService;
        this.pdfReportService = pdfReportService;
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
            String fileName = file.getOriginalFilename();
            model.addAttribute("fileName", fileName);

            // 1. Extract Text
            String extractedText = textExtractionService.extractText(file.getBytes());
            model.addAttribute("originalText", extractedText);

            // 2. Generate Summary (The Upgrade)
            // We now pass 'level' (e.g., "detailed") directly to the service.
            // The service decides if it needs AI (Python) or Math (TextRank).
            String summary = summarizerService.generateSummary(extractedText, level);
            model.addAttribute("summary", summary);

            // 3. NLP Feature: Keywords
            List<String> keywords = summarizerService.extractKeywords(extractedText);
            model.addAttribute("keywords", keywords);

            // 4. NLP Feature: Sentiment Analysis
            String sentiment = summarizerService.analyzeSentiment(extractedText);
            model.addAttribute("sentiment", sentiment);

            // 5. Calculate Stats
            int originalWords = countWords(extractedText);
            int summaryWords = countWords(summary);
            int compression = (originalWords == 0) ? 0 : 100 - (summaryWords * 100 / originalWords);

            model.addAttribute("originalWords", originalWords);
            model.addAttribute("summaryWords", summaryWords);
            model.addAttribute("compression", compression);

            return "result";

        } catch (Exception e) {
            e.printStackTrace(); // Good for debugging in console
            model.addAttribute("error", "Error processing file: " + e.getMessage());
            return "index";
        }
    }

    // ⭐ DOWNLOAD PDF ENDPOINT
    @PostMapping("/download/pdf")
    public ResponseEntity<byte[]> downloadPdf(
            @RequestParam("fileName") String fileName,
            @RequestParam("summary") String summary,
            @RequestParam("originalWords") int originalWords,
            @RequestParam("summaryWords") int summaryWords,
            @RequestParam("compression") int compression) {

        byte[] pdfBytes = pdfReportService.generatePdfReport(fileName, summary, originalWords, summaryWords, compression);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName + "_report.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    // ⭐ DOWNLOAD TXT ENDPOINT
    @PostMapping("/download/txt")
    public ResponseEntity<byte[]> downloadTxt(@RequestParam("content") String content) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=summary.txt")
                .contentType(MediaType.TEXT_PLAIN)
                .body(content.getBytes());
    }

    private int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }
}