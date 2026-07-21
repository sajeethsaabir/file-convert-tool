package com.convertor.converter;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.*;
import java.time.LocalDate;

public class PdfToMarkdown {

    public String convert(InputStream in) throws IOException {
        try (PDDocument doc = Loader.loadPDF(in.readAllBytes())) {
            int pageCount = doc.getNumberOfPages();
            StringBuilder md = new StringBuilder();
            md.append("---\ncreated: ").append(LocalDate.now()).append("\npages: ").append(pageCount).append("\n---\n\n");

            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeParagraphStart() {}

                @Override
                protected void writeParagraphEnd() {}

                @Override
                protected void writeLineSeparator() {}

                @Override
                protected void writeWordSeparator() {}

                @Override
                protected void processTextPosition(TextPosition text) {}

                @Override
                public String getText(PDDocument doc) throws IOException {
                    return super.getText(doc);
                }
            };

            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(pageCount);

            String rawText = stripper.getText(doc);
            md.append(heuristicToMarkdown(rawText));
            return md.toString().strip();
        }
    }

    private String heuristicToMarkdown(String text) {
        StringBuilder md = new StringBuilder();
        String[] lines = text.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            char first = trimmed.charAt(0);

            if (trimmed.length() >= 9 && isChapterHeading(trimmed)) {
                md.append("## ").append(trimmed).append("\n\n");
            } else if (trimmed.length() > 3 && trimmed.length() < 100
                    && isAllUpperCase(trimmed)
                    && !isAllPunctuationOrDigits(trimmed)) {
                md.append("## ").append(trimmed).append("\n\n");
            } else if (trimmed.length() < 80
                    && Character.isUpperCase(first)
                    && !endsWith(trimmed, '.') && !endsWith(trimmed, ':')
                    && countWords(trimmed) < 12) {
                md.append("### ").append(trimmed).append("\n\n");
            } else if (first == '•' || first == '-' || first == '*') {
                md.append("- ").append(trimmed.substring(1).trim()).append("\n");
            } else if (isNumberedItem(trimmed, first)) {
                md.append(trimmed).append("\n");
            } else {
                md.append(trimmed).append("\n\n");
            }
        }

        return md.toString().strip();
    }

    private boolean isChapterHeading(String s) {
        String lower = s.toLowerCase();
        return (lower.startsWith("chapter ") || lower.startsWith("section ")
                || lower.startsWith("part ") || lower.startsWith("appendix "))
                && hasDigitAfter(s, s.indexOf(' '));
    }

    private boolean hasDigitAfter(String s, int from) {
        for (int i = from + 1; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) return true;
        }
        return false;
    }

    private boolean isAllUpperCase(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c) && !Character.isUpperCase(c)) return false;
        }
        return true;
    }

    private boolean isAllPunctuationOrDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) return false;
        }
        return true;
    }

    private boolean endsWith(String s, char c) {
        return !s.isEmpty() && s.charAt(s.length() - 1) == c;
    }

    private int countWords(String s) {
        int count = 0;
        boolean inWord = false;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == ' ') {
                inWord = false;
            } else if (!inWord) {
                count++;
                inWord = true;
            }
        }
        return count;
    }

    private boolean isNumberedItem(String s, char first) {
        if (!Character.isDigit(first)) return false;
        int i = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
        return i < s.length() && (s.charAt(i) == '.' || s.charAt(i) == ')')
                && i + 1 < s.length() && s.charAt(i + 1) == ' ';
    }
}
