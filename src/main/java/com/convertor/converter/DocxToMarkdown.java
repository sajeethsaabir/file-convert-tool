package com.convertor.converter;

import org.apache.poi.xwpf.usermodel.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class DocxToMarkdown {

    public String convert(InputStream in) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(in)) {
            StringBuilder md = new StringBuilder();
            for (IBodyElement element : doc.getBodyElements()) {
                switch (element.getElementType()) {
                    case PARAGRAPH -> processParagraph((XWPFParagraph) element, md);
                    case TABLE -> processTable((XWPFTable) element, md);
                }
            }
            return md.toString().strip();
        }
    }

    private void processParagraph(XWPFParagraph para, StringBuilder md) {
        String text = para.getText().trim();
        if (text.isEmpty()) return;

        int headingLevel = getHeadingLevel(para);
        if (headingLevel > 0) {
            md.append("#".repeat(headingLevel)).append(" ").append(text).append("\n\n");
            return;
        }

        if (isListItem(para)) {
            String prefix = isOrdered(para) ? "1." : "-";
            md.append("  ".repeat(Math.max(0, getIndentLevel(para))))
              .append(prefix).append(" ").append(formatText(para)).append("\n");
            return;
        }

        if (text.matches("^[-_*]{3,}$")) {
            md.append("---\n\n");
            return;
        }

        md.append(formatText(para)).append("\n\n");
    }

    private int getHeadingLevel(XWPFParagraph para) {
        String style = para.getStyle();
        if (style == null) return 0;
        String lower = style.toLowerCase();
        if (lower.startsWith("heading")) {
            String num = lower.replace("heading", "").trim();
            try {
                return Integer.parseInt(num);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        try {
            int level = Integer.parseInt(style);
            if (level >= 1 && level <= 6) return level;
        } catch (NumberFormatException ignored) {}
        return 0;
    }

    private boolean isListItem(XWPFParagraph para) {
        var ilvl = para.getNumIlvl();
        return ilvl != null && ilvl.intValue() >= 0;
    }

    private boolean isOrdered(XWPFParagraph para) {
        if (!isListItem(para)) return false;
        String numFmt = para.getNumFmt();
        if (numFmt == null) return false;
        return !"bullet".equalsIgnoreCase(numFmt);
    }

    private int getIndentLevel(XWPFParagraph para) {
        var ilvl = para.getNumIlvl();
        return Math.max(0, ilvl != null ? ilvl.intValue() : 0);
    }

    private String formatText(XWPFParagraph para) {
        StringBuilder sb = new StringBuilder();
        for (XWPFRun run : para.getRuns()) {
            String text = run.getText(0);
            if (text == null || text.isBlank()) continue;
            if (run.isBold()) sb.append("**");
            if (run.isItalic()) sb.append("*");
            if (run.getFontFamily() != null && "Courier New".equalsIgnoreCase(run.getFontFamily())) {
                sb.append("`");
            }
            sb.append(text);
            if (run.getFontFamily() != null && "Courier New".equalsIgnoreCase(run.getFontFamily())) {
                sb.append("`");
            }
            if (run.isItalic()) sb.append("*");
            if (run.isBold()) sb.append("**");
        }
        return sb.toString().trim();
    }

    private void processTable(XWPFTable table, StringBuilder md) {
        List<XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) return;

        for (int i = 0; i < rows.size(); i++) {
            XWPFTableRow row = rows.get(i);
            List<XWPFTableCell> cells = row.getTableCells();
            if (cells.isEmpty()) continue;

            StringBuilder line = new StringBuilder("|");
            for (XWPFTableCell cell : cells) {
                line.append(" ").append(cell.getText().trim()).append(" |");
            }
            md.append(line).append("\n");

            if (i == 0) {
                StringBuilder sep = new StringBuilder("|");
                for (int j = 0; j < cells.size(); j++) {
                    sep.append(" --- |");
                }
                md.append(sep).append("\n");
            }
        }
        md.append("\n");
    }
}
