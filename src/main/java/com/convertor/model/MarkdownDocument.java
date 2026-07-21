package com.convertor.model;

import java.util.List;

public record MarkdownDocument(List<Block> blocks) {

    public sealed interface Block {}

    public record Heading(int level, String text) implements Block {}
    public record Paragraph(String text) implements Block {}
    public record CodeBlock(String language, String code) implements Block {}
    public record ListBlock(boolean ordered, List<String> items) implements Block {}
    public record Table(List<String> headers, List<List<String>> rows) implements Block {}
    public record Blockquote(String text) implements Block {}
    public record HorizontalRule() implements Block {}
}
