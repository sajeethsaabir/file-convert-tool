package com.convertor;

import com.convertor.converter.*;
import com.convertor.ui.MainFrame;
import com.convertor.util.SecurityValidator;

import javax.swing.*;
import java.io.*;
import java.nio.file.*;

public class ConvertorApp {

    public static void main(String[] args) {
        SecurityValidator.configureXXEProtection();

        if (args.length == 0) {
            SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
            return;
        }

        if (args.length < 3) {
            printUsage();
            System.exit(1);
        }

        String mode = args[0].toLowerCase();
        String input = args[1];
        String output = args[2];

        try {
            switch (mode) {
                case "docx2md" -> {
                    Path inPath = SecurityValidator.validateInputFile(input, SecurityValidator.extDocx());
                    Path outPath = SecurityValidator.validateOutputFile(output, SecurityValidator.extMd());
                    try (InputStream is = SecurityValidator.validateDocxMagicBytes(Files.newInputStream(inPath))) {
                        String result = new DocxToMarkdown().convert(is);
                        Files.writeString(outPath, result);
                    }
                }
                case "pdf2md" -> {
                    Path inPath = SecurityValidator.validateInputFile(input, SecurityValidator.extPdf());
                    Path outPath = SecurityValidator.validateOutputFile(output, SecurityValidator.extMd());
                    try (InputStream is = SecurityValidator.validatePdfMagicBytes(Files.newInputStream(inPath))) {
                        String result = new PdfToMarkdown().convert(is);
                        Files.writeString(outPath, result);
                    }
                }
                case "md2docx" -> {
                    Path inPath = SecurityValidator.validateInputFile(input, SecurityValidator.extMd());
                    Path outPath = SecurityValidator.validateOutputFile(output, SecurityValidator.extDocx());
                    String md = Files.readString(inPath);
                    new MarkdownToDocx().convert(md, Files.newOutputStream(outPath));
                }
                case "md2pdf" -> {
                    Path inPath = SecurityValidator.validateInputFile(input, SecurityValidator.extMd());
                    Path outPath = SecurityValidator.validateOutputFile(output, SecurityValidator.extPdf());
                    String md = Files.readString(inPath);
                    new MarkdownToPdf().convert(md, Files.newOutputStream(outPath));
                }
                default -> {
                    System.err.println("Unknown mode: " + mode);
                    printUsage();
                    System.exit(1);
                }
            }
            System.out.println("✓ " + input + " → " + output);
        } catch (SecurityException | IOException e) {
            System.err.println("✗ Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Document Convertor Tool v1.0");
        System.out.println();
        System.out.println("GUI mode:  java -jar convertor-tool.jar");
        System.out.println();
        System.out.println("CLI mode:  java -jar convertor-tool.jar <mode> <input> <output>");
        System.out.println();
        System.out.println("Modes:");
        System.out.println("  docx2md   DOCX → Markdown");
        System.out.println("  pdf2md    PDF  → Markdown");
        System.out.println("  md2docx   Markdown → DOCX");
        System.out.println("  md2pdf    Markdown → PDF");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar convertor-tool.jar docx2md doc.docx doc.md");
        System.out.println("  java -jar convertor-tool.jar pdf2md  doc.pdf doc.md");
        System.out.println("  java -jar convertor-tool.jar md2docx doc.md doc.docx");
        System.out.println("  java -jar convertor-tool.jar md2pdf  doc.md doc.pdf");
    }
}
