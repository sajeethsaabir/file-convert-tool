package com.convertor.parser;

import java.util.ArrayList;
import java.util.List;
import com.convertor.model.MarkdownDocument;
import com.convertor.model.MarkdownDocument.*;

public class MarkdownParser {

    public MarkdownDocument parse(String md) {
        md = sanitize(md);
        List<Block> blocks = new ArrayList<>();
        String[] lines = md.split("\n", -1);
        int i = 0;

        while (i < lines.length) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                i++;
                continue;
            }

            if (trimmed.startsWith("```")) {
                String lang = trimmed.substring(3).trim();
                StringBuilder code = new StringBuilder();
                i++;
                while (i < lines.length && !lines[i].trim().startsWith("```")) {
                    code.append(lines[i]).append("\n");
                    i++;
                }
                i++;
                blocks.add(new CodeBlock(lang.isEmpty() ? null : lang,
                        code.length() > 0 ? code.substring(0, code.length() - 1) : ""));
                continue;
            }

            if (trimmed.startsWith("> ")) {
                StringBuilder quote = new StringBuilder();
                while (i < lines.length && lines[i].trim().startsWith("> ")) {
                    quote.append(lines[i].trim().substring(2)).append("\n");
                    i++;
                }
                blocks.add(new Blockquote(quote.toString().trim().replace("\n", " ")));
                continue;
            }

            if (trimmed.startsWith("#") && trimmed.length() > 1) {
                char c2 = trimmed.charAt(1);
                if (c2 == ' ' || c2 == '#') {
                    int level = 1;
                    while (level < trimmed.length() && trimmed.charAt(level) == '#') level++;
                    if (level > 6) { i++; continue; }
                    if (level < trimmed.length() && trimmed.charAt(level) == ' ') {
                        blocks.add(new Heading(level, trimmed.substring(level).trim()));
                        i++;
                        continue;
                    }
                }
            }

            if (isHorizontalRule(trimmed)) {
                blocks.add(new HorizontalRule());
                i++;
                continue;
            }

            if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
                List<String> items = new ArrayList<>();
                while (i < lines.length) {
                    String t = lines[i].trim();
                    if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ")) {
                        items.add(t.substring(2).trim());
                        i++;
                    } else if (!t.isEmpty() && !t.startsWith("#") && !t.startsWith("```")
                            && !t.startsWith("> ") && !t.startsWith("|")) {
                        break;
                    } else {
                        break;
                    }
                }
                blocks.add(new ListBlock(false, items));
                continue;
            }

            if (isOrderedListItem(trimmed)) {
                List<String> items = new ArrayList<>();
                while (i < lines.length) {
                    String t = lines[i].trim();
                    if (isOrderedListItem(t)) {
                        int dot = t.indexOf('.');
                        items.add(t.substring(dot + 1).trim());
                        i++;
                    } else {
                        break;
                    }
                }
                blocks.add(new ListBlock(true, items));
                continue;
            }

            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                List<String> headerCells = parseTableRow(trimmed);
                i++;
                if (i < lines.length && lines[i].trim().matches("^[|]?[-:|\\s]+[|]?$")) {
                    i++;
                }
                List<List<String>> rows = new ArrayList<>();
                while (i < lines.length) {
                    String t = lines[i].trim();
                    if (t.startsWith("|")) {
                        rows.add(t.endsWith("|") ? parseTableRow(t) : parseTableRow(t + "|"));
                        i++;
                    } else {
                        break;
                    }
                }
                blocks.add(new Table(headerCells, rows));
                continue;
            }
            if (trimmed.startsWith("|")) {
                i++;
                continue;
            }

            StringBuilder para = new StringBuilder();
            while (i < lines.length) {
                String t = lines[i].trim();
                if (t.isEmpty()) break;
                char fc = t.charAt(0);
                if (fc == '#') break;
                if (t.startsWith("```")) break;
                if (t.startsWith("> ")) break;
                if (fc == '|') break;
                if (fc == '-' || fc == '*' || fc == '+') {
                    if (isHorizontalRule(t)) break;
                    if (t.length() > 1 && t.charAt(1) == ' ') break;
                }
                if (isOrderedListItem(t)) break;
                para.append(lines[i]).append(" ");
                i++;
            }
            String text = para.toString().trim();
            if (!text.isEmpty()) {
                blocks.add(new Paragraph(text));
            }
        }

        return new MarkdownDocument(blocks);
    }

    private boolean isHorizontalRule(String s) {
        if (s.length() < 3) return false;
        char c = s.charAt(0);
        if (c != '-' && c != '*' && c != '_') return false;
        for (int j = 1; j < s.length(); j++) {
            if (s.charAt(j) != c) return false;
        }
        return true;
    }

    private boolean isOrderedListItem(String s) {
        if (s.isEmpty() || !Character.isDigit(s.charAt(0))) return false;
        int j = 0;
        while (j < s.length() && Character.isDigit(s.charAt(j))) j++;
        return j < s.length() && s.charAt(j) == '.' && j + 1 < s.length() && s.charAt(j + 1) == ' ';
    }

    private List<String> parseTableRow(String line) {
        String s = line.trim();
        if (s.startsWith("|")) s = s.substring(1);
        if (s.endsWith("|")) s = s.substring(0, s.length() - 1);
        List<String> cells = new ArrayList<>();
        for (String cell : s.split("\\|")) {
            cells.add(cell.trim());
        }
        return cells;
    }

    private String sanitize(String input) {
        if (input == null) return "";
        return input
                .replace("\0", "")
                .replaceAll("[\\p{Cc}&&[^\\r\\n\\t]]", "");
    }
}
