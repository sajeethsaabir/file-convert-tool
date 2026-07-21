package com.convertor.converter;

import com.convertor.model.MarkdownDocument.*;
import com.convertor.parser.MarkdownParser;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class MarkdownToPdf {

    private final MarkdownParser parser = new MarkdownParser();

    private static final float MARGIN = 50;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth() - 2 * MARGIN;
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float Y_START = PAGE_HEIGHT - MARGIN;
    private static final float FONT_SIZE = 11;
    private static final float LEADING = 15;

    public void convert(String markdown, OutputStream out) throws IOException {
        var doc = parser.parse(markdown);

        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            pdf.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(pdf, page);

            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont fontMono = new PDType1Font(Standard14Fonts.FontName.COURIER);

            float y = Y_START;
            cs.beginText();
            cs.setFont(font, FONT_SIZE);
            cs.newLineAtOffset(MARGIN, y);
            cs.setLeading(LEADING);

            PdfContext ctx = new PdfContext(pdf, page, cs, font, fontBold, fontMono, y);

            for (Block block : doc.blocks()) {
                ctx = renderBlock(ctx, block);
            }

            ctx.cs.endText();
            ctx.cs.close();
            pdf.save(out);
        }
    }

    private record PdfContext(PDDocument pdf, PDPage page, PDPageContentStream cs,
                              PDFont font, PDFont bold, PDFont mono, float y) {}

    private PdfContext renderBlock(PdfContext ctx, Block block) throws IOException {
        return switch (block) {
            case Heading h -> renderHeading(ctx, h);
            case Paragraph p -> renderParagraph(ctx, p);
            case CodeBlock cb -> renderCodeBlock(ctx, cb);
            case ListBlock lb -> renderList(ctx, lb);
            case Table t -> renderTable(ctx, t);
            case Blockquote bq -> renderBlockquote(ctx, bq);
            case HorizontalRule ignored -> renderHorizontalRule(ctx);
        };
    }

    private PdfContext renderHeading(PdfContext ctx, Heading h) throws IOException {
        float hSize = switch (h.level()) { case 1 -> 24; case 2 -> 20; case 3 -> 17;
            case 4 -> 15; case 5 -> 13; default -> 12; };
        float hLeading = hSize * 1.5f;
        float needed = hLeading + 10;

        PdfContext c = ensureSpace(ctx, needed);
        c.cs.setFont(c.bold, hSize);
        c.cs.newLineAtOffset(0, -hLeading);
        c.cs.showText(h.text());
        c.cs.setFont(c.font, FONT_SIZE);
        c.cs.setLeading(LEADING);
        return new PdfContext(c.pdf, c.page, c.cs, c.font, c.bold, c.mono, c.y - hLeading - 5);
    }

    private PdfContext renderParagraph(PdfContext ctx, Paragraph p) throws IOException {
        return renderText(ctx, p.text(), FONT_SIZE, LEADING, 0);
    }

    private PdfContext renderCodeBlock(PdfContext ctx, CodeBlock cb) throws IOException {
        float codeSize = 9;
        float codeLeading = 12;
        float indent = 15;
        float totalHeight = cb.code().split("\n").length * codeLeading + 10;

        PdfContext c = ensureSpace(ctx, totalHeight);
        c.cs.setFont(c.mono, codeSize);
        c.cs.setLeading(codeLeading);
        c.cs.newLineAtOffset(indent, -5);

        for (String line : cb.code().split("\n")) {
            c.cs.showText(line.isBlank() ? " " : line);
            c.cs.newLineAtOffset(0, -codeLeading);
        }
        c.cs.setLeading(LEADING);
        float newY = c.y - totalHeight;
        c.cs.newLineAtOffset(-indent, 0);
        c.cs.setFont(c.font, FONT_SIZE);
        return new PdfContext(c.pdf, c.page, c.cs, c.font, c.bold, c.mono, newY);
    }

    private PdfContext renderList(PdfContext ctx, ListBlock lb) throws IOException {
        var items = lb.items();
        float y = ctx.y;
        var cs = ctx.cs;
        var pdf = ctx.pdf;
        var page = ctx.page;
        var font = ctx.font;

        for (int i = 0; i < items.size(); i++) {
            float needed = LEADING + 3;
            if (y - needed < MARGIN) {
                cs.endText();
                page = new PDPage(PDRectangle.A4);
                pdf.addPage(page);
                cs = new PDPageContentStream(pdf, page);
                cs.beginText();
                cs.setFont(font, FONT_SIZE);
                cs.newLineAtOffset(MARGIN, Y_START);
                cs.setLeading(LEADING);
                y = Y_START;
            }

            String prefix = lb.ordered() ? (i + 1) + ". " : "- ";
            String text = prefix + items.get(i);
            cs.setFont(font, FONT_SIZE);
            cs.showText(text);
            cs.newLineAtOffset(0, -LEADING);
            y -= LEADING + 3;
        }

        return new PdfContext(pdf, page, cs, ctx.font, ctx.bold, ctx.mono, y);
    }

    private PdfContext renderTable(PdfContext ctx, Table t) throws IOException {
        var headers = t.headers();
        var rows = t.rows();
        float rowH = 20;
        float totalH = (rows.size() + 1) * rowH + 10;

        PdfContext c = ensureSpace(ctx, totalH);
        var cs = c.cs;
        float colW = PAGE_WIDTH / Math.max(1, headers.size());
        float y = c.y;

        cs.setFont(c.bold, FONT_SIZE);
        for (int i = 0; i < headers.size(); i++) {
            String text = headers.get(i);
            cs.showText(text.length() > 20 ? text.substring(0, 20) : text);
            if (i < headers.size() - 1) {
                cs.newLineAtOffset(colW, 0);
            }
        }
        cs.newLineAtOffset(-(headers.size() - 1) * colW, -rowH);
        y -= rowH;

        cs.setFont(c.font, FONT_SIZE);
        for (var row : rows) {
            for (int i = 0; i < Math.min(row.size(), headers.size()); i++) {
                String text = row.get(i);
                cs.showText(text.length() > 20 ? text.substring(0, 20) : text);
                if (i < headers.size() - 1) {
                    cs.newLineAtOffset(colW, 0);
                }
            }
            cs.newLineAtOffset(-(headers.size() - 1) * colW, -rowH);
            y -= rowH;
        }

        cs.newLineAtOffset(0, -5);
        return new PdfContext(c.pdf, c.page, cs, c.font, c.bold, c.mono, y - 5);
    }

    private PdfContext renderBlockquote(PdfContext ctx, Blockquote bq) throws IOException {
        float indent = 20;
        PdfContext c = ensureSpace(ctx, LEADING + 5);
        c.cs.setFont(c.font, FONT_SIZE - 1);
        c.cs.newLineAtOffset(indent, 0);
        c.cs.showText("> " + bq.text());
        c.cs.newLineAtOffset(-indent, -LEADING);
        c.cs.setFont(c.font, FONT_SIZE);
        return new PdfContext(c.pdf, c.page, c.cs, c.font, c.bold, c.mono, c.y - LEADING - 5);
    }

    private PdfContext renderHorizontalRule(PdfContext ctx) throws IOException {
        PdfContext c = ensureSpace(ctx, 20);
        c.cs.newLineAtOffset(0, -15);
        return new PdfContext(c.pdf, c.page, c.cs, c.font, c.bold, c.mono, c.y - 15);
    }

    private PdfContext renderText(PdfContext ctx, String text, float fontSize,
                                  float leading, float indent) throws IOException {
        if (text == null || text.isBlank()) return ctx;

        String[] words = text.split(" ");
        float y = ctx.y;
        var cs = ctx.cs;
        var pdf = ctx.pdf;
        var page = ctx.page;
        var font = ctx.font;
        float maxW = PAGE_WIDTH - indent;
        float spaceW = font.getStringWidth(" ") / 1000 * fontSize;

        StringBuilder line = new StringBuilder();
        float lineWidth = 0;

        for (String word : words) {
            float wordW;
            try {
                wordW = font.getStringWidth(word) / 1000 * fontSize;
            } catch (Exception e) {
                wordW = word.length() * fontSize * 0.5f;
            }

            if (lineWidth + wordW > maxW && !line.isEmpty()) {
                if (y - leading < MARGIN) {
                    cs.endText();
                    page = new PDPage(PDRectangle.A4);
                    pdf.addPage(page);
                    cs = new PDPageContentStream(pdf, page);
                    cs.beginText();
                    cs.setFont(font, fontSize);
                    cs.newLineAtOffset(MARGIN + indent, Y_START);
                    cs.setLeading(leading);
                    y = Y_START;
                }
                cs.setFont(font, fontSize);
                cs.showText(line.toString());
                cs.newLineAtOffset(0, -leading);
                y -= leading;
                line = new StringBuilder(word);
                lineWidth = wordW;
            } else {
                if (!line.isEmpty()) {
                    line.append(" ");
                    lineWidth += spaceW;
                }
                line.append(word);
                lineWidth += wordW;
            }
        }

        if (!line.isEmpty()) {
            if (y - leading < MARGIN) {
                cs.endText();
                page = new PDPage(PDRectangle.A4);
                pdf.addPage(page);
                cs = new PDPageContentStream(pdf, page);
                cs.beginText();
                cs.setFont(font, fontSize);
                cs.newLineAtOffset(MARGIN + indent, Y_START);
                cs.setLeading(leading);
                y = Y_START;
            }
            cs.setFont(font, fontSize);
            cs.showText(line.toString());
            cs.newLineAtOffset(0, -leading);
            y -= leading;
        }

        return new PdfContext(pdf, page, cs, ctx.font, ctx.bold, ctx.mono, y);
    }

    private PdfContext ensureSpace(PdfContext ctx, float needed) throws IOException {
        if (ctx.y - needed < MARGIN) {
            ctx.cs.endText();
            PDPage page = new PDPage(PDRectangle.A4);
            ctx.pdf.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(ctx.pdf, page);
            cs.beginText();
            cs.setFont(ctx.font, FONT_SIZE);
            cs.newLineAtOffset(MARGIN, Y_START);
            cs.setLeading(LEADING);
            return new PdfContext(ctx.pdf, page, cs, ctx.font, ctx.bold, ctx.mono, Y_START);
        }
        return ctx;
    }
}
