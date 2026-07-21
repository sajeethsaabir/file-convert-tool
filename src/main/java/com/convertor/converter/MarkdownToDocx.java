package com.convertor.converter;

import com.convertor.model.MarkdownDocument.*;
import com.convertor.parser.MarkdownParser;
import org.apache.poi.xwpf.usermodel.*;

import java.io.IOException;
import java.io.OutputStream;

public class MarkdownToDocx {

    private final MarkdownParser parser = new MarkdownParser();

    public void convert(String markdown, OutputStream out) throws IOException {
        var doc = parser.parse(markdown);
        try (XWPFDocument xdoc = new XWPFDocument()) {

            for (Block block : doc.blocks()) {
                switch (block) {
                    case Heading h -> addHeading(xdoc, h.level(), h.text());
                    case Paragraph p -> addParagraph(xdoc, p.text());
                    case CodeBlock cb -> addCodeBlock(xdoc, cb.code());
                    case ListBlock lb -> addList(xdoc, lb.ordered(), lb.items());
                    case Table t -> addTable(xdoc, t.headers(), t.rows());
                    case Blockquote bq -> addParagraph(xdoc, "> " + bq.text());
                    case HorizontalRule ignored -> addParagraph(xdoc, "---");
                }
            }

            xdoc.write(out);
        }
    }

    private void addHeading(XWPFDocument doc, int level, String text) {
        XWPFParagraph para = doc.createParagraph();
        para.setStyle("Heading" + level);
        para.createRun().setText(text);
        para.setSpacingAfter(200);
    }

    private void addParagraph(XWPFDocument doc, String text) {
        addStyledParagraph(doc, text, false, false);
    }

    private void addStyledParagraph(XWPFDocument doc, String text, boolean bold, boolean italic) {
        XWPFParagraph para = doc.createParagraph();
        para.setSpacingAfter(100);

        processInlineFormatting(para, text);
    }

    private void processInlineFormatting(XWPFParagraph para, String text) {
        int i = 0;
        int len = text.length();
        while (i < len) {
            char c = text.charAt(i);

            if (c == '*' && i + 1 < len && text.charAt(i + 1) == '*') {
                int end = text.indexOf("**", i + 2);
                if (end > i) {
                    XWPFRun run = para.createRun();
                    run.setBold(true);
                    run.setText(text.substring(i + 2, end));
                    i = end + 2;
                    continue;
                }
            }

            if (c == '*' && (i + 1 >= len || text.charAt(i + 1) != '*')) {
                int end = text.indexOf("*", i + 1);
                if (end > i && (end + 1 >= len || text.charAt(end + 1) != '*')) {
                    XWPFRun run = para.createRun();
                    run.setItalic(true);
                    run.setText(text.substring(i + 1, end));
                    i = end + 1;
                    continue;
                }
            }

            if (c == '`') {
                int end = text.indexOf("`", i + 1);
                if (end > i) {
                    XWPFRun run = para.createRun();
                    run.setFontFamily("Courier New");
                    run.setText(text.substring(i + 1, end));
                    i = end + 1;
                    continue;
                }
            }

            if (c == '[') {
                int closeBracket = text.indexOf("](", i);
                int closeParen = text.indexOf(")", closeBracket + 2);
                if (closeBracket > i && closeParen > closeBracket) {
                    String linkText = text.substring(i + 1, closeBracket);
                    XWPFRun run = para.createRun();
                    run.setUnderline(org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE);
                    run.setColor("0563C1");
                    run.setText(linkText);
                    i = closeParen + 1;
                    continue;
                }
            }

            int next = findNextMarker(text, i + 1);
            int end = (next < 0) ? len : next;
            XWPFRun run = para.createRun();
            run.setText(text.substring(i, end));
            i = end;
        }
    }

    private int findNextMarker(String text, int from) {
        int len = text.length();
        for (int j = from; j < len; j++) {
            char c = text.charAt(j);
            if (c == '*' || c == '`' || c == '[') return j;
        }
        return -1;
    }

    private void addCodeBlock(XWPFDocument doc, String code) {
        XWPFParagraph para = doc.createParagraph();
        para.setIndentationLeft(400);
        para.setSpacingAfter(100);
        para.setBorderBottom(Borders.SINGLE);
        para.setBorderTop(Borders.SINGLE);
        para.setBorderLeft(Borders.SINGLE);
        para.setBorderRight(Borders.SINGLE);

        for (String line : code.split("\n")) {
            XWPFRun run = para.createRun();
            run.setFontFamily("Courier New");
            run.setFontSize(10);
            run.setText(line);
            run.addBreak();
        }
    }

    private void addList(XWPFDocument doc, boolean ordered, java.util.List<String> items) {
        for (int i = 0; i < items.size(); i++) {
            XWPFParagraph para = doc.createParagraph();
            para.setIndentationLeft(400);
            para.setIndentationHanging(200);
            para.setSpacingAfter(60);

            String prefix = ordered ? (i + 1) + ". " : "- ";
            XWPFRun run = para.createRun();
            run.setText(prefix + items.get(i));
        }
    }

    private void addTable(XWPFDocument doc, java.util.List<String> headers,
                          java.util.List<java.util.List<String>> rows) {
        int cols = headers.size();
        XWPFTable table = doc.createTable(rows.size() + 1, cols);
        table.setWidth("100%");

        for (int c = 0; c < cols; c++) {
            table.getRow(0).getCell(c).setText(headers.get(c));
            table.getRow(0).getCell(c).getParagraphs().get(0).getRuns().get(0).setBold(true);
        }

        for (int r = 0; r < rows.size(); r++) {
            var row = rows.get(r);
            for (int c = 0; c < Math.min(row.size(), cols); c++) {
                table.getRow(r + 1).getCell(c).setText(row.get(c));
            }
        }
    }
}
