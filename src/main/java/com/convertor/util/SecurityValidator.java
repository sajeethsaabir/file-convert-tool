package com.convertor.util;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

public final class SecurityValidator {

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024L;
    private static final Pattern CONTROL_CHARS;

    private static final Set<String> DOCX_EXT = Set.of(".docx");
    private static final Set<String> PDF_EXT = Set.of(".pdf");
    private static final Set<String> MD_EXT = Set.of(".md", ".markdown");
    private static final Set<String> ALL_EXT = Set.of(".docx", ".pdf", ".md", ".markdown");

    static {
        CONTROL_CHARS = Pattern.compile("[\\p{C}&&[^\\p{Print}\\r\\n\\t]]");
    }

    private SecurityValidator() {}

    public static Path validatePath(String pathStr, Set<String> allowedExtensions) {
        if (pathStr == null || pathStr.isBlank()) {
            throw new SecurityException("Path must not be empty");
        }
        if (pathStr.contains("\0")) {
            throw new SecurityException("Path contains null bytes (possible injection)");
        }

        Path path;
        try {
            path = Path.of(pathStr).normalize().toAbsolutePath();
        } catch (InvalidPathException e) {
            throw new SecurityException("Invalid path: " + e.getMessage());
        }

        String name = path.getFileName().toString().toLowerCase();
        boolean extensionValid = allowedExtensions.stream()
                .anyMatch(ext -> name.endsWith(ext));
        if (!extensionValid) {
            throw new SecurityException("Invalid file extension: '" + name
                    + "'. Allowed: " + allowedExtensions);
        }

        return path;
    }

    public static Path validateInputFile(String pathStr, Set<String> allowedExtensions) {
        Path path = validatePath(pathStr, allowedExtensions);

        try {
            path = path.toRealPath();
        } catch (IOException e) {
            throw new SecurityException("Cannot resolve real path (possible symlink issue): " + path);
        }

        if (!Files.exists(path)) {
            throw new SecurityException("File not found: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new SecurityException("Not a regular file: " + path);
        }
        if (!Files.isReadable(path)) {
            throw new SecurityException("File is not readable: " + path);
        }
        try {
            long size = Files.size(path);
            if (size > MAX_FILE_SIZE) {
                throw new SecurityException("File exceeds maximum size of 50 MB: " + path + " (" + size + " bytes)");
            }
            if (size == 0) {
                throw new SecurityException("File is empty: " + path);
            }
        } catch (IOException e) {
            throw new SecurityException("Cannot read file size: " + path);
        }
        return path;
    }

    public static Path validateOutputFile(String pathStr, Set<String> allowedExtensions) {
        Path path = validatePath(pathStr, allowedExtensions);

        try {
            path = path.toRealPath();
        } catch (IOException e) {
            throw new SecurityException("Cannot resolve real path for output: " + path);
        }

        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            throw new SecurityException("Output directory does not exist: " + parent);
        }
        if (parent != null && !Files.isWritable(parent)) {
            throw new SecurityException("Output directory is not writable: " + parent);
        }
        return path;
    }

    public static BufferedInputStream validateDocxMagicBytes(InputStream in) throws IOException {
        BufferedInputStream bis = (in instanceof BufferedInputStream)
                ? (BufferedInputStream) in : new BufferedInputStream(in);
        bis.mark(4);
        byte[] magic = new byte[4];
        int read = bis.read(magic);
        bis.reset();
        if (read < 4) {
            throw new SecurityException("File too small to be a valid DOCX (need 4 bytes, got " + read + ")");
        }
        if (magic[0] != 'P' || magic[1] != 'K' || magic[2] != 0x03 || magic[3] != 0x04) {
            throw new SecurityException("Invalid DOCX file: ZIP/PK header not found");
        }
        return bis;
    }

    public static BufferedInputStream validatePdfMagicBytes(InputStream in) throws IOException {
        BufferedInputStream bis = (in instanceof BufferedInputStream)
                ? (BufferedInputStream) in : new BufferedInputStream(in);
        bis.mark(5);
        byte[] magic = new byte[5];
        int read = bis.read(magic);
        bis.reset();
        if (read < 5) {
            throw new SecurityException("File too small to be a valid PDF (need 5 bytes, got " + read + ")");
        }
        String header = new String(magic, StandardCharsets.US_ASCII);
        if (!header.equals("%PDF-")) {
            throw new SecurityException("Invalid PDF file: PDF header not found");
        }
        return bis;
    }

    public static String sanitize(String input) {
        if (input == null) return "";
        return input
                .replace("\0", "")
                .replaceAll(CONTROL_CHARS.pattern(), "");
    }

    public static void configureXXEProtection() {
        System.setProperty("org.apache.poi.util.SAXHelper.ignoringExternalEntities", "true");
        System.setProperty("org.apache.poi.util.XMLHelper.ignoreExternalEntities", "true");
        System.setProperty("javax.xml.XMLConstants.featureSecureProcessing", "true");
        System.setProperty("jdk.xml.enableExternalGeneralEntities", "false");
        System.setProperty("jdk.xml.enableExternalParameterEntities", "false");

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setExpandEntityReferences(false);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("Failed to configure XXE protection", e);
        }
    }

    public static Set<String> extDocx() { return DOCX_EXT; }
    public static Set<String> extPdf() { return PDF_EXT; }
    public static Set<String> extMd() { return MD_EXT; }
    public static Set<String> extAll() { return ALL_EXT; }
}
