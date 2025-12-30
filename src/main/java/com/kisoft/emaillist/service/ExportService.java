package com.kisoft.emaillist.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Export Service - Handles exporting content to PDF and DOCX formats.
 * This service provides functionality to convert HTML content from the email
 * editor into downloadable document formats:
 * - PDF - Using {@code OpenHTMLToPDF} library (XHTML to PDF renderer with PDFBox)
 * - DOCX - Using {@code Apache POI} library for Microsoft Word format
 * Key Features:
 * - Sanitizes Microsoft Office VML markup from pasted content
 * - Handles base64 embedded images
 * - Preserves text formatting (bold, italic, underline, colors)
 * - Supports tables with proper styling
 * - Theme-aware text colors (black for light theme, white for dark theme)
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-27
 * @see com.kisoft.emaillist.controller.EmailController
 * @see com.openhtmltopdf.pdfboxout.PdfRendererBuilder
 * @see org.apache.poi.xwpf.usermodel.XWPFDocument
 */
@Service
@Slf4j
public class ExportService {

    /**
     * Exports HTML content to PDF format.
     * @param subject The document title/subject
     * @param htmlContent The HTML content to export
     * @return byte array containing the PDF document
     * @throws IOException if PDF generation fails
     */
    public byte[] exportToPdf(String subject, String htmlContent) throws IOException {
        log.info("Exporting to PDF. Subject: {}, Content length: {} chars", subject, htmlContent.length());

        // Log detailed image information
        logImageDetails(htmlContent, "PDF Export");

        // Convert HTML to XHTML for OpenHTMLToPDF
        String xhtml = convertToXhtml(subject, htmlContent);
        log.debug("XHTML content length: {} chars", xhtml.length());

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();

            // Use file:/// as base URI - OpenHTMLToPDF handles data: URIs natively
            builder.withHtmlContent(xhtml, "file:///");
            builder.toStream(outputStream);
            builder.run();

            byte[] pdfBytes = outputStream.toByteArray();
            log.info("PDF export completed. Size: {} bytes", pdfBytes.length);

            if (pdfBytes.length < 5000) {
                log.warn("PDF file is suspiciously small ({} bytes). Images may not be included.", pdfBytes.length);
            }

            return pdfBytes;
        } catch (Exception e) {
            log.error("PDF generation failed: {}", e.getMessage(), e);
            throw new IOException("PDF generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Exports HTML content to DOCX (Microsoft Word) format.
     * @param subject The document title/subject
     * @param htmlContent The HTML content to export
     * @return byte array containing the DOCX document
     * @throws IOException if document generation fails
     */
    public byte[] exportToDocx(String subject, String htmlContent) throws IOException {
        log.info("Exporting to DOCX. Subject: {}, Content length: {} chars", subject, htmlContent.length());

        // Log detailed image information
        logImageDetails(htmlContent, "DOCX Export");

        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            // Add title
            XWPFParagraph titlePara = document.createParagraph();
            titlePara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titlePara.createRun();
            titleRun.setText(subject);
            titleRun.setBold(true);
            titleRun.setFontSize(18);
            titleRun.addBreak();

            // Parse HTML and sanitize Microsoft markup
            Document htmlDoc = Jsoup.parse(htmlContent);
            sanitizeMicrosoftMarkup(htmlDoc);
            convertHtmlToDocx(document, htmlDoc.body());

            document.write(outputStream);
            log.info("DOCX export completed. Size: {} bytes", outputStream.size());
            return outputStream.toByteArray();
        }
    }

    /**
     * Converts HTML content to well-formed XHTML for PDF rendering.
     * Preserves inline styles from the editor to match visual appearance.
     */
    private String convertToXhtml(String subject, String htmlContent) {
        // Parse and clean HTML
        Document doc = Jsoup.parse(htmlContent);

        // Count images before processing
        int imagesBefore = doc.select("img").size();
        log.debug("Images in document before processing: {}", imagesBefore);

        // Remove Microsoft Office VML/namespaced elements and attributes
        sanitizeMicrosoftMarkup(doc);

        // Count images after sanitize
        int imagesAfterSanitize = doc.select("img").size();
        if (imagesAfterSanitize < imagesBefore) {
            log.warn("sanitizeMicrosoftMarkup removed {} images!", imagesBefore - imagesAfterSanitize);
        }

        // Clean unsupported CSS values for OpenHTMLToPDF
        cleanUnsupportedCss(doc);

        // Count images after CSS cleanup
        int imagesAfterCss = doc.select("img").size();
        if (imagesAfterCss < imagesAfterSanitize) {
            log.warn("cleanUnsupportedCss removed {} images!", imagesAfterSanitize - imagesAfterCss);
        }
        cleanUnsupportedCss(doc);

        doc.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml)
                .charset("UTF-8");

        // Process images - preserve their dimensions from editor and ensure they render
        int imageCount = 0;
        for (Element img : doc.select("img")) {
            String src = img.attr("src");
            String style = img.attr("style");

            if (src == null || src.isEmpty()) {
                log.warn("Found image with empty src, skipping");
                continue;
            }

            // Log image source type for debugging
            if (src.startsWith("data:image")) {
                log.debug("Image {}: data URI, length={}", imageCount + 1, src.length());
            } else {
                log.debug("Image {}: external src={}", imageCount + 1, src.substring(0, Math.min(100, src.length())));
            }

            // Extract width from style or attribute
            String width = extractCssValue(style, "width");
            if (width == null || width.isEmpty()) {
                width = img.attr("width");
            }

            // Build proper style for PDF rendering
            StringBuilder newStyle = new StringBuilder();

            // Set width if specified
            if (width != null && !width.isEmpty()) {
                if (!width.endsWith("px") && !width.endsWith("%") && width.matches("\\d+")) {
                    width = width + "px";
                }
                newStyle.append("width: ").append(width).append("; ");
                // Also set as attribute for better compatibility
                img.attr("width", width.replace("px", ""));
            }

            // Height should be auto for proper scaling
            newStyle.append("height: auto; ");

            // Display and margin
            newStyle.append("display: inline-block; margin: 5px; ");

            // Preserve float if present
            if (style != null && style.contains("float")) {
                String floatVal = extractCssValue(style, "float");
                if (floatVal != null && !floatVal.isEmpty()) {
                    newStyle.append("float: ").append(floatVal).append("; ");
                    if ("left".equals(floatVal)) {
                        newStyle.append("margin-right: 15px; ");
                    } else if ("right".equals(floatVal)) {
                        newStyle.append("margin-left: 15px; ");
                    }
                }
            }

            img.attr("style", newStyle.toString().trim());
            imageCount++;
            log.debug("Processed image {}: src length={}, width={}", imageCount, src.length(), width);
        }

        log.info("Processed {} images for PDF export", imageCount);

        // Process tables - preserve their widths
        for (Element table : doc.select("table")) {
            String style = table.attr("style");
            String width = extractCssValue(style, "width");
            StringBuilder tableStyle = new StringBuilder();
            if (width != null && !width.isEmpty()) {
                tableStyle.append("width: ").append(width).append("; ");
            }
            tableStyle.append("border-collapse: collapse; margin: 10px 0;");
            table.attr("style", tableStyle.toString());

            // Ensure table cells have borders
            for (Element cell : table.select("td, th")) {
                String cellStyle = cell.attr("style");
                if (cellStyle == null || !cellStyle.contains("border")) {
                    cell.attr("style", (cellStyle != null ? cellStyle + "; " : "") + "border: 1px solid #ccc; padding: 5px;");
                }
            }
        }

        // Build complete XHTML document with comprehensive CSS
        StringBuilder xhtml = new StringBuilder();
        xhtml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xhtml.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" ");
        xhtml.append("\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n");
        xhtml.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n");
        xhtml.append("<head>\n");
        xhtml.append("  <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>\n");
        xhtml.append("  <title>").append(escapeXml(subject)).append("</title>\n");
        xhtml.append("  <style type=\"text/css\">\n");
        xhtml.append("    @page { size: letter; margin: 0.75in; }\n");
        xhtml.append("    body { font-family: Arial, Helvetica, sans-serif; font-size: 12pt; line-height: 1.4; margin: 0; padding: 15px; color: #000; }\n");
        xhtml.append("    h1 { color: #333; font-size: 16pt; margin-bottom: 15px; text-align: center; }\n");
        xhtml.append("    p { margin: 8px 0; }\n");
        xhtml.append("    div { margin: 5px 0; }\n");
        // IMPORTANT: Don't set max-width on images - let the width attribute control size
        xhtml.append("    table { border-collapse: collapse; margin: 10px 0; }\n");
        xhtml.append("    td, th { border: 1px solid #ccc; padding: 6px 8px; vertical-align: top; }\n");
        xhtml.append("    a { color: #0066cc; text-decoration: underline; }\n");
        xhtml.append("    ul, ol { margin: 8px 0 8px 25px; padding: 0; }\n");
        xhtml.append("    li { margin: 4px 0; }\n");
        xhtml.append("    strong, b { font-weight: bold; }\n");
        xhtml.append("    em, i { font-style: italic; }\n");
        xhtml.append("    u { text-decoration: underline; }\n");
        xhtml.append("    .content-wrapper { width: 100%; }\n");
        xhtml.append("  </style>\n");
        xhtml.append("</head>\n");
        xhtml.append("<body>\n");
        xhtml.append("  <h1>").append(escapeXml(subject)).append("</h1>\n");
        xhtml.append("  <div class=\"content-wrapper\">\n");

        String bodyHtml = doc.body().html();
        xhtml.append(bodyHtml);
        xhtml.append("  </div>\n");
        xhtml.append("</body>\n");
        xhtml.append("</html>");

        // Final check - count images in output
        int finalImageCount = bodyHtml.split("<img").length - 1;
        log.info("Final XHTML body contains {} <img> tags, body length: {} chars", finalImageCount, bodyHtml.length());

        // Log a sample of the first image if present
        if (bodyHtml.contains("<img") && bodyHtml.contains("data:image")) {
            int imgStart = bodyHtml.indexOf("<img");
            int srcEnd = bodyHtml.indexOf(">", imgStart);
            if (srcEnd > imgStart) {
                String imgTag = bodyHtml.substring(imgStart, Math.min(srcEnd + 1, imgStart + 200));
                log.debug("First image tag sample: {}...", imgTag);
            }
        }

        return xhtml.toString();
    }

    /**
     * Extracts a CSS property value from an inline style string.
     * @param style The inline style string
     * @param property The CSS property name (e.g., "width", "float")
     * @return The property value or null if not found
     */
    private String extractCssValue(String style, String property) {
        if (style == null || style.isEmpty()) return null;

        Pattern pattern = Pattern.compile(property + "\\s*:\\s*([^;]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(style);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    /**
     * Cleans unsupported CSS values that cause OpenHTMLToPDF warnings or errors.
     * Removes linear-gradient backgrounds that crash the PDF renderer,
     * unsupported cursor values, and editor-specific elements.
     */
    private void cleanUnsupportedCss(Document doc) {
        // Remove table resize handles (they have linear-gradient that crashes PDF renderer)
        doc.select(".table-resize-handle").remove();
        doc.select(".table-resize-col").remove();
        doc.select(".table-resize-row").remove();
        doc.select(".table-resize-corner").remove();

        // Remove data-resize-init attributes
        for (Element el : doc.select("[data-resize-init]")) {
            el.removeAttr("data-resize-init");
        }

        for (Element el : doc.select("[style]")) {
            String style = el.attr("style");
            if (style != null && !style.isEmpty()) {
                // Replace 'initial' with default values or remove
                style = style.replaceAll(":\\s*initial\\s*;?", ": inherit;");
                // Remove linear-gradient (causes NullPointerException in OpenHTMLToPDF)
                style = style.replaceAll("background\\s*:[^;]*linear-gradient[^;]*;?", "");
                style = style.replaceAll("background-image\\s*:[^;]*linear-gradient[^;]*;?", "");
                // Remove unsupported cursor values
                style = style.replaceAll("cursor\\s*:\\s*(col-resize|row-resize|nwse-resize|nesw-resize|ew-resize|ns-resize)[^;]*;?", "");
                // Remove unsupported properties
                style = style.replaceAll("background-origin\\s*:[^;]+;?", "");
                style = style.replaceAll("background-clip\\s*:[^;]+;?", "");
                style = style.replaceAll("box-sizing\\s*:[^;]+;?", "");
                style = style.replaceAll("resize\\s*:[^;]+;?", "");
                style = style.replaceAll("overflow\\s*:\\s*auto[^;]*;?", "");
                style = style.replaceAll("-webkit-[^;]+;?", "");
                style = style.replaceAll("-moz-[^;]+;?", "");
                style = style.replaceAll("-ms-[^;]+;?", "");
                // Remove z-index (not needed for PDF)
                style = style.replaceAll("z-index\\s*:[^;]+;?", "");
                // Clean up double semicolons and trim
                style = style.replaceAll(";\\s*;", ";").replaceAll("^\\s*;", "").trim();
                if (style.isEmpty()) {
                    el.removeAttr("style");
                } else {
                    el.attr("style", style);
                }
            }
        }
    }

    /**
     * Removes Microsoft Office VML/namespaced elements and attributes from HTML.
     * This includes v:*, o:*, w:* prefixed attributes commonly found in Word/Outlook HTML.
     */
    private void sanitizeMicrosoftMarkup(Document doc) {
        // Remove VML and Office namespace elements (v:*, o:*, w:*, etc.)
        doc.select("[v|*], [o|*], [w|*], [x|*]").remove();

        // Remove all elements with Microsoft namespaced tags
        for (Element el : doc.select("*")) {
            String tagName = el.tagName();
            if (tagName.contains(":") &&
                (tagName.startsWith("v:") || tagName.startsWith("o:") ||
                 tagName.startsWith("w:") || tagName.startsWith("x:"))) {
                el.remove();
            }
        }

        // Remove Microsoft-specific attributes from all elements
        for (Element el : doc.select("*")) {
            // Get a copy of attribute keys to avoid ConcurrentModificationException
            java.util.List<String> attrsToRemove = new java.util.ArrayList<>();
            for (org.jsoup.nodes.Attribute attr : el.attributes()) {
                String key = attr.getKey();
                // Remove namespaced attributes (v:shapes, o:title, etc.)
                if (key.contains(":")) {
                    attrsToRemove.add(key);
                }
                // Remove Microsoft-specific class names and attributes
                if (key.equalsIgnoreCase("mso-") || key.startsWith("mso-")) {
                    attrsToRemove.add(key);
                }
            }
            for (String attr : attrsToRemove) {
                el.removeAttr(attr);
            }
        }

        // Clean up inline styles with mso-* properties
        for (Element el : doc.select("[style]")) {
            String style = el.attr("style");
            if (style.contains("mso-")) {
                // Remove mso-* CSS properties
                String cleanedStyle = style.replaceAll("mso-[^;:]+:[^;]+;?", "").trim();
                if (cleanedStyle.isEmpty()) {
                    el.removeAttr("style");
                } else {
                    el.attr("style", cleanedStyle);
                }
            }
        }

        // Remove conditional comments content (often contains VML)
        doc.select("*").forEach(el -> {
            // Remove any remaining problematic attributes
            el.removeAttr("xmlns:v");
            el.removeAttr("xmlns:o");
            el.removeAttr("xmlns:w");
            el.removeAttr("xmlns:x");
            el.removeAttr("xmlns:m");
        });

        log.debug("Sanitized Microsoft markup from HTML document");
    }

    /**
     * Converts HTML elements to Word document content.
     */
    private void convertHtmlToDocx(XWPFDocument document, Element element) {
        for (Node node : element.childNodes()) {
            if (node instanceof TextNode textNode) {
                String text = textNode.text().trim();
                if (!text.isEmpty()) {
                    XWPFParagraph para = document.createParagraph();
                    XWPFRun run = para.createRun();
                    run.setText(text);
                }
            } else if (node instanceof Element el) {
                processHtmlElement(document, el);
            }
        }
    }

    /**
     * Processes individual HTML elements for Word conversion.
     */
    private void processHtmlElement(XWPFDocument document, Element element) {
        String tagName = element.tagName().toLowerCase();

        switch (tagName) {
            case "p", "div" -> {
                // First, process any images in this element
                Elements images = element.select("> img");
                for (Element img : images) {
                    processImage(document, img);
                }

                // Create paragraph for text content
                XWPFParagraph para = document.createParagraph();
                processInlineContent(para, element);

                // Apply paragraph-level styles
                applyParagraphStyles(para, element);

                // Process child block elements (nested divs, etc.)
                for (Element child : element.children()) {
                    String childTag = child.tagName().toLowerCase();
                    if (childTag.equals("div") || childTag.equals("p") || childTag.equals("table")
                            || childTag.equals("ul") || childTag.equals("ol") || childTag.matches("h[1-6]")) {
                        processHtmlElement(document, child);
                    }
                }
            }
            case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                XWPFParagraph para = document.createParagraph();
                XWPFRun run = para.createRun();
                run.setText(element.text());
                run.setBold(true);
                int fontSize = switch (tagName) {
                    case "h1" -> 24;
                    case "h2" -> 20;
                    case "h3" -> 16;
                    case "h4" -> 14;
                    case "h5" -> 12;
                    default -> 11;
                };
                run.setFontSize(fontSize);
            }
            case "br" -> {
                XWPFParagraph para = document.createParagraph();
                para.createRun().addBreak();
            }
            case "hr" -> {
                XWPFParagraph para = document.createParagraph();
                para.setBorderBottom(Borders.SINGLE);
            }
            case "ul", "ol" -> {
                boolean ordered = tagName.equals("ol");
                int itemNum = 1;
                for (Element li : element.select("> li")) {
                    XWPFParagraph para = document.createParagraph();
                    para.setIndentationLeft(720);
                    XWPFRun run = para.createRun();
                    String bullet = ordered ? (itemNum++ + ". ") : "• ";
                    run.setText(bullet + li.text());
                }
            }
            case "table" -> processTable(document, element);
            case "img" -> processImage(document, element);
            case "a" -> {
                XWPFParagraph para = document.createParagraph();
                XWPFRun run = para.createRun();
                run.setText(element.text());
                run.setColor("0066CC");
                run.setUnderline(UnderlinePatterns.SINGLE);
            }
            case "strong", "b" -> {
                XWPFParagraph para = document.createParagraph();
                XWPFRun run = para.createRun();
                run.setText(element.text());
                run.setBold(true);
            }
            case "em", "i" -> {
                XWPFParagraph para = document.createParagraph();
                XWPFRun run = para.createRun();
                run.setText(element.text());
                run.setItalic(true);
            }
            case "u" -> {
                XWPFParagraph para = document.createParagraph();
                XWPFRun run = para.createRun();
                run.setText(element.text());
                run.setUnderline(UnderlinePatterns.SINGLE);
            }
            case "span" -> {
                // Process span with styles
                XWPFParagraph para = document.createParagraph();
                XWPFRun run = para.createRun();
                run.setText(element.text());
                applyInlineStyles(run, element);
            }
            default -> convertHtmlToDocx(document, element);
        }
    }

    /**
     * Applies paragraph-level styles from element attributes.
     */
    private void applyParagraphStyles(XWPFParagraph para, Element element) {
        String style = element.attr("style");
        if (style == null || style.isEmpty()) return;

        // Text alignment
        if (style.contains("text-align")) {
            if (style.contains("center")) {
                para.setAlignment(ParagraphAlignment.CENTER);
            } else if (style.contains("right")) {
                para.setAlignment(ParagraphAlignment.RIGHT);
            } else if (style.contains("justify")) {
                para.setAlignment(ParagraphAlignment.BOTH);
            }
        }
    }

    /**
     * Processes inline content within a paragraph.
     */
    private void processInlineContent(XWPFParagraph para, Element element) {
        for (Node node : element.childNodes()) {
            if (node instanceof TextNode textNode) {
                String text = textNode.text();
                if (!text.trim().isEmpty()) {
                    XWPFRun run = para.createRun();
                    run.setText(text);
                    applyParentStyles(run, element);
                }
            } else if (node instanceof Element el) {
                processInlineElement(para, el);
            }
        }
    }

    /**
     * Processes inline elements with proper styling.
     */
    private void processInlineElement(XWPFParagraph para, Element el) {
        String tag = el.tagName().toLowerCase();

        // Handle images inline
        if (tag.equals("img")) {
            // Images need their own paragraph in DOCX
            return; // Will be handled separately
        }

        // Handle line breaks
        if (tag.equals("br")) {
            XWPFRun run = para.createRun();
            run.addBreak();
            return;
        }

        // For elements with text content
        String text = el.ownText();
        if (!text.isEmpty()) {
            XWPFRun run = para.createRun();
            run.setText(text);

            // Apply formatting based on tag
            switch (tag) {
                case "strong", "b" -> run.setBold(true);
                case "em", "i" -> run.setItalic(true);
                case "u" -> run.setUnderline(UnderlinePatterns.SINGLE);
                case "s", "strike", "del" -> run.setStrikeThrough(true);
                case "sub" -> run.setSubscript(VerticalAlign.SUBSCRIPT);
                case "sup" -> run.setSubscript(VerticalAlign.SUPERSCRIPT);
                case "a" -> {
                    run.setColor("0066CC");
                    run.setUnderline(UnderlinePatterns.SINGLE);
                }
                case "span" -> applySpanStyles(run, el);
            }

            // Apply inline styles
            applyInlineStyles(run, el);
        }

        // Process child elements recursively
        for (Element child : el.children()) {
            processInlineElement(para, child);
        }
    }

    /**
     * Applies styles from parent elements.
     */
    private void applyParentStyles(XWPFRun run, Element parent) {
        String style = parent.attr("style");
        if (style != null && !style.isEmpty()) {
            applyStyleString(run, style);
        }
    }

    /**
     * Applies styles from span elements.
     */
    private void applySpanStyles(XWPFRun run, Element span) {
        String style = span.attr("style");
        if (style != null && !style.isEmpty()) {
            applyStyleString(run, style);
        }
    }

    /**
     * Applies inline CSS styles to a run.
     */
    private void applyInlineStyles(XWPFRun run, Element el) {
        String style = el.attr("style");
        if (style != null && !style.isEmpty()) {
            applyStyleString(run, style);
        }
    }

    /**
     * Parses and applies a CSS style string to a run.
     */
    private void applyStyleString(XWPFRun run, String style) {
        // Font size
        Pattern fontSizePattern = Pattern.compile("font-size\\s*:\\s*(\\d+(?:\\.\\d+)?)(px|pt|em)?", Pattern.CASE_INSENSITIVE);
        Matcher fontSizeMatcher = fontSizePattern.matcher(style);
        if (fontSizeMatcher.find()) {
            try {
                double size = Double.parseDouble(fontSizeMatcher.group(1));
                String unit = fontSizeMatcher.group(2);
                // Convert px to pt (approximately 0.75)
                if ("px".equalsIgnoreCase(unit)) {
                    size = size * 0.75;
                } else if ("em".equalsIgnoreCase(unit)) {
                    size = size * 12; // Default em to 12pt
                }
                run.setFontSize((int) Math.round(size));
            } catch (NumberFormatException ignored) {}
        }

        // Font weight
        if (style.contains("font-weight") && (style.contains("bold") || style.contains("700") || style.contains("800") || style.contains("900"))) {
            run.setBold(true);
        }

        // Font style
        if (style.contains("font-style") && style.contains("italic")) {
            run.setItalic(true);
        }

        // Text decoration
        if (style.contains("text-decoration") && style.contains("underline")) {
            run.setUnderline(UnderlinePatterns.SINGLE);
        }
        if (style.contains("text-decoration") && style.contains("line-through")) {
            run.setStrikeThrough(true);
        }

        // Color
        Pattern colorPattern = Pattern.compile("(?:^|;|\\s)color\\s*:\\s*#([0-9A-Fa-f]{6})", Pattern.CASE_INSENSITIVE);
        Matcher colorMatcher = colorPattern.matcher(style);
        if (colorMatcher.find()) {
            run.setColor(colorMatcher.group(1));
        }

        // RGB color
        Pattern rgbPattern = Pattern.compile("color\\s*:\\s*rgb\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)", Pattern.CASE_INSENSITIVE);
        Matcher rgbMatcher = rgbPattern.matcher(style);
        if (rgbMatcher.find()) {
            int r = Integer.parseInt(rgbMatcher.group(1));
            int g = Integer.parseInt(rgbMatcher.group(2));
            int b = Integer.parseInt(rgbMatcher.group(3));
            String hexColor = String.format("%02X%02X%02X", r, g, b);
            run.setColor(hexColor);
        }

        // Font family
        Pattern fontFamilyPattern = Pattern.compile("font-family\\s*:\\s*([^;]+)", Pattern.CASE_INSENSITIVE);
        Matcher fontFamilyMatcher = fontFamilyPattern.matcher(style);
        if (fontFamilyMatcher.find()) {
            String fontFamily = fontFamilyMatcher.group(1).trim()
                    .replaceAll("['\"]", "")
                    .split(",")[0].trim();
            run.setFontFamily(fontFamily);
        }
    }

    /**
     * Processes HTML table into Word table.
     */
    private void processTable(XWPFDocument document, Element tableElement) {
        Elements rows = tableElement.select("tr");
        if (rows.isEmpty()) return;

        Element firstRow = rows.first();
        if (firstRow == null) return;
        
        int numCols = firstRow.select("td, th").size();
        if (numCols == 0) return;

        XWPFTable table = document.createTable(rows.size(), numCols);
        table.setWidth("100%");

        int rowIndex = 0;
        for (Element row : rows) {
            Elements cells = row.select("td, th");
            int colIndex = 0;
            for (Element cell : cells) {
                if (colIndex < numCols) {
                    XWPFTableCell tableCell = table.getRow(rowIndex).getCell(colIndex);
                    tableCell.setText(cell.text());

                    if (cell.tagName().equalsIgnoreCase("th")) {
                        tableCell.getParagraphs().get(0).getRuns().forEach(r -> r.setBold(true));
                    }
                }
                colIndex++;
            }
            rowIndex++;
        }
    }

    /**
     * Processes HTML image into Word document.
     */
    private void processImage(XWPFDocument document, Element imgElement) {
        String src = imgElement.attr("src");

        if (src.startsWith("data:image")) {
            try {
                // Parse data URI: data:image/png;base64,xxxxx
                int commaIndex = src.indexOf(',');
                if (commaIndex < 0) {
                    log.warn("Invalid data URI format for image");
                    return;
                }

                String header = src.substring(0, commaIndex);
                String base64Data = src.substring(commaIndex + 1);

                // Determine MIME type
                String mimeType = "image/jpeg";
                if (header.contains("image/png")) {
                    mimeType = "image/png";
                } else if (header.contains("image/gif")) {
                    mimeType = "image/gif";
                } else if (header.contains("image/bmp")) {
                    mimeType = "image/bmp";
                }

                byte[] imageData = Base64.getDecoder().decode(base64Data);
                log.debug("Decoded image: {} bytes, type: {}", imageData.length, mimeType);

                int pictureType = switch (mimeType) {
                    case "image/png" -> XWPFDocument.PICTURE_TYPE_PNG;
                    case "image/gif" -> XWPFDocument.PICTURE_TYPE_GIF;
                    case "image/bmp" -> XWPFDocument.PICTURE_TYPE_BMP;
                    default -> XWPFDocument.PICTURE_TYPE_JPEG;
                };

                // Get actual image dimensions
                BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageData));
                int actualWidth = 400;
                int actualHeight = 300;

                if (bufferedImage != null) {
                    actualWidth = bufferedImage.getWidth();
                    actualHeight = bufferedImage.getHeight();
                    log.debug("Image actual dimensions: {}x{}", actualWidth, actualHeight);
                }

                // Check for width/height in style or attributes
                int targetWidth = actualWidth;
                int targetHeight = actualHeight;
                double aspectRatio = (double) actualHeight / actualWidth;

                String widthAttr = imgElement.attr("width");
                String heightAttr = imgElement.attr("height");
                String style = imgElement.attr("style");

                boolean hasExplicitWidth = false;
                boolean hasExplicitHeight = false;

                // Parse width from attributes or style
                if (!widthAttr.isEmpty()) {
                    try {
                        targetWidth = Integer.parseInt(widthAttr.replaceAll("[^0-9]", ""));
                        hasExplicitWidth = true;
                    } catch (NumberFormatException ignored) {}
                } else if (style.contains("width")) {
                    Pattern widthPattern = Pattern.compile("width\\s*:\\s*(\\d+)");
                    Matcher matcher = widthPattern.matcher(style);
                    if (matcher.find()) {
                        targetWidth = Integer.parseInt(matcher.group(1));
                        hasExplicitWidth = true;
                    }
                }

                // Parse height from attributes or style (but not "height: auto")
                if (!heightAttr.isEmpty() && !heightAttr.equalsIgnoreCase("auto")) {
                    try {
                        targetHeight = Integer.parseInt(heightAttr.replaceAll("[^0-9]", ""));
                        hasExplicitHeight = true;
                    } catch (NumberFormatException ignored) {}
                } else if (style.contains("height") && !style.contains("height: auto") && !style.contains("height:auto")) {
                    Pattern heightPattern = Pattern.compile("height\\s*:\\s*(\\d+)");
                    Matcher matcher = heightPattern.matcher(style);
                    if (matcher.find()) {
                        targetHeight = Integer.parseInt(matcher.group(1));
                        hasExplicitHeight = true;
                    }
                }

                // If only width is set, calculate height to preserve aspect ratio
                if (hasExplicitWidth && !hasExplicitHeight) {
                    targetHeight = (int) Math.round(targetWidth * aspectRatio);
                    log.debug("Calculated proportional height: {} (aspect ratio: {})", targetHeight, aspectRatio);
                }

                // Scale to fit page width (max 6 inches = 432 points)
                int maxWidth = 432;
                if (targetWidth > maxWidth) {
                    double scale = (double) maxWidth / targetWidth;
                    targetWidth = maxWidth;
                    targetHeight = (int) (targetHeight * scale);
                }

                XWPFParagraph para = document.createParagraph();
                para.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun run = para.createRun();

                // Convert points to EMUs (1 point = 12700 EMUs)
                int emuWidth = Units.toEMU(targetWidth);
                int emuHeight = Units.toEMU(targetHeight);

                run.addPicture(
                        new ByteArrayInputStream(imageData),
                        pictureType,
                        "image",
                        emuWidth,
                        emuHeight
                );

                log.debug("Added image to DOCX: {}x{} (EMU: {}x{})", targetWidth, targetHeight, emuWidth, emuHeight);
            } catch (Exception e) {
                log.warn("Failed to add image to DOCX: {}", e.getMessage(), e);
            }
        } else if (!src.isEmpty()) {
            // Handle local file paths: file:/// or absolute Windows paths
            try {
                String normalizedPath = src;
                if (normalizedPath.startsWith("file:///")) {
                    normalizedPath = normalizedPath.substring("file:///".length());
                } else if (normalizedPath.startsWith("file://")) {
                    normalizedPath = normalizedPath.substring("file://".length());
                }
                // If it's a Windows absolute path like C:\ or D:\ keep as is
                boolean isAbsoluteLocal = normalizedPath.matches("^[A-Za-z]:\\\\.*") || new File(normalizedPath).isAbsolute();

                if (isAbsoluteLocal) {
                    Path path = Paths.get(normalizedPath);
                    if (Files.exists(path)) {
                        byte[] imageData = Files.readAllBytes(path);
                        String mimeType = detectMimeTypeFromPath(path.toString());
                        int pictureType = switch (mimeType) {
                            case "image/png" -> XWPFDocument.PICTURE_TYPE_PNG;
                            case "image/gif" -> XWPFDocument.PICTURE_TYPE_GIF;
                            case "image/bmp" -> XWPFDocument.PICTURE_TYPE_BMP;
                            default -> XWPFDocument.PICTURE_TYPE_JPEG;
                        };

                        BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageData));
                        int actualWidth = bufferedImage != null ? bufferedImage.getWidth() : 400;
                        int actualHeight = bufferedImage != null ? bufferedImage.getHeight() : 300;

                        int targetWidth = actualWidth;
                        int targetHeight = actualHeight;
                        double aspectRatio = actualWidth > 0 ? (double) actualHeight / actualWidth : 1.0;

                        // Respect inline dimensions if present
                        String widthAttr = imgElement.attr("width");
                        String heightAttr = imgElement.attr("height");
                        String style = imgElement.attr("style");
                        boolean hasExplicitWidth = false;
                        boolean hasExplicitHeight = false;

                        if (!widthAttr.isEmpty()) {
                            try {
                                targetWidth = Integer.parseInt(widthAttr.replaceAll("[^0-9]", ""));
                                hasExplicitWidth = true;
                            } catch (NumberFormatException ignored) {}
                        } else if (style.contains("width")) {
                            Pattern widthPattern = Pattern.compile("width\\s*:\\s*(\\d+)");
                            Matcher matcher = widthPattern.matcher(style);
                            if (matcher.find()) {
                                targetWidth = Integer.parseInt(matcher.group(1));
                                hasExplicitWidth = true;
                            }
                        }
                        if (!heightAttr.isEmpty() && !heightAttr.equalsIgnoreCase("auto")) {
                            try {
                                targetHeight = Integer.parseInt(heightAttr.replaceAll("[^0-9]", ""));
                                hasExplicitHeight = true;
                            } catch (NumberFormatException ignored) {}
                        } else if (style.contains("height") && !style.contains("height: auto") && !style.contains("height:auto")) {
                            Pattern heightPattern = Pattern.compile("height\\s*:\\s*(\\d+)");
                            Matcher matcher = heightPattern.matcher(style);
                            if (matcher.find()) {
                                targetHeight = Integer.parseInt(matcher.group(1));
                                hasExplicitHeight = true;
                            }
                        }
                        if (hasExplicitWidth && !hasExplicitHeight) {
                            targetHeight = (int) Math.round(targetWidth * aspectRatio);
                        }
                        int maxWidth = 432;
                        if (targetWidth > maxWidth) {
                            double scale = (double) maxWidth / targetWidth;
                            targetWidth = maxWidth;
                            targetHeight = (int) (targetHeight * scale);
                        }

                        XWPFParagraph para = document.createParagraph();
                        para.setAlignment(ParagraphAlignment.CENTER);
                        XWPFRun run = para.createRun();
                        int emuWidth = Units.toEMU(targetWidth);
                        int emuHeight = Units.toEMU(targetHeight);
                        run.addPicture(new ByteArrayInputStream(imageData), pictureType, path.getFileName().toString(), emuWidth, emuHeight);

                        log.info("Embedded local image into DOCX: path={}, size={} bytes, target={}x{}", path.toAbsolutePath(), imageData.length, targetWidth, targetHeight);
                    } else {
                        log.warn("Local image path not found: {}", path.toAbsolutePath());
                        XWPFParagraph para = document.createParagraph();
                        XWPFRun run = para.createRun();
                        run.setText("[Missing image: " + normalizedPath + "]");
                        run.setItalic(true);
                        run.setColor("666666");
                    }
                } else if (src.startsWith("http://") || src.startsWith("https://")) {
                    // Remote URL: keep placeholder to avoid network calls
                    XWPFParagraph para = document.createParagraph();
                    XWPFRun run = para.createRun();
                    run.setText("[Image URL: " + src + "]");
                    run.setItalic(true);
                    run.setColor("666666");
                    log.info("DOCX export left remote image as placeholder: {}", src);
                } else {
                    // Relative or classpath resource: log and leave placeholder
                    XWPFParagraph para = document.createParagraph();
                    XWPFRun run = para.createRun();
                    run.setText("[Image: " + src + "]");
                    run.setItalic(true);
                    run.setColor("666666");
                    log.info("DOCX export encountered non-absolute image source, left as placeholder: {}", src);
                }
            } catch (Exception e) {
                log.warn("Failed to process external image '{}': {}", src, e.getMessage());
                XWPFParagraph para = document.createParagraph();
                XWPFRun run = para.createRun();
                run.setText("[Image error: " + src + "]");
                run.setItalic(true);
                run.setColor("FF0000");
            }
        }
    }

    private String detectMimeTypeFromPath(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        return "image/jpeg";
    }

    /**
     * Escapes special XML characters.
     */
    private String escapeXml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Logs detailed information about all images in the HTML content.
     * Includes image type, size, dimensions, and source information.
     * @param htmlContent The HTML content to analyze
     * @param context Description of the operation (e.g., "PDF Export", "DOCX Export")
     */
    private void logImageDetails(String htmlContent, String context) {
        if (htmlContent == null || htmlContent.isEmpty()) {
            log.info("[{}] No content to analyze for images", context);
            return;
        }

        Document doc = Jsoup.parse(htmlContent);
        Elements images = doc.select("img");

        if (images.isEmpty()) {
            log.info("[{}] No images found in content", context);
            return;
        }

        log.info("[{}] Found {} image(s):", context, images.size());

        int index = 1;
        for (Element img : images) {
            String src = img.attr("src");
            String width = img.attr("width");
            String height = img.attr("height");
            String style = img.attr("style");
            String alt = img.attr("alt");
            String originalPath = firstNonBlank(
                    img.attr("data-original-filepath"),
                    img.attr("data-filepath"),
                    img.attr("data-path"));

            // Extract dimensions from style if not in attributes
            if ((width == null || width.isEmpty()) && style != null) {
                String styleWidth = extractCssValue(style, "width");
                if (styleWidth != null) width = styleWidth;
            }
            if ((height == null || height.isEmpty()) && style != null) {
                String styleHeight = extractCssValue(style, "height");
                if (styleHeight != null) height = styleHeight;
            }

            StringBuilder logMsg = new StringBuilder();
            logMsg.append("  [Image ").append(index).append("] ");

            if (src.startsWith("data:image")) {
                // Base64 embedded image
                int commaIndex = src.indexOf(',');
                if (commaIndex > 0) {
                    String header = src.substring(0, commaIndex);
                    String base64Data = src.substring(commaIndex + 1);

                    // Extract MIME type
                    String mimeType = "unknown";
                    if (header.contains("image/png")) mimeType = "image/png";
                    else if (header.contains("image/jpeg")) mimeType = "image/jpeg";
                    else if (header.contains("image/gif")) mimeType = "image/gif";
                    else if (header.contains("image/webp")) mimeType = "image/webp";
                    else if (header.contains("image/bmp")) mimeType = "image/bmp";

                    // Calculate approximate size
                    int sizeBytes = (int) Math.round((base64Data.length() * 3.0) / 4.0);
                    double sizeKB = sizeBytes / 1024.0;

                    logMsg.append("Type: ").append(mimeType);
                    logMsg.append(" | Size: ~").append(String.format("%.2f", sizeKB)).append(" KB");
                    logMsg.append(" | Source: embedded base64");
                    logMsg.append(" | Stored path: ").append(originalPath != null ? originalPath : "(embedded/pasted - no file path)");
                } else {
                    logMsg.append("Type: data URI (malformed)");
                }
            } else if (src.startsWith("file:///") || src.startsWith("file://")) {
                // Local file path
                String filePath = src.replace("file:///", "").replace("file://", "");
                logMsg.append("Type: local file");
                logMsg.append(" | Path: ").append(filePath);
                logMsg.append(" | Stored path: ").append(originalPath != null ? originalPath : filePath);
            } else if (src.startsWith("http://") || src.startsWith("https://")) {
                // Remote URL
                logMsg.append("Type: remote URL");
                logMsg.append(" | URL: ").append(src);
                logMsg.append(" | Stored path: ").append(originalPath != null ? originalPath : "(remote URL)");
            } else if (src.startsWith("/") || src.contains(":\\")) {
                // Absolute path
                logMsg.append("Type: absolute path");
                logMsg.append(" | Path: ").append(src);
                logMsg.append(" | Stored path: ").append(originalPath != null ? originalPath : src);
            } else {
                // Relative path or other
                logMsg.append("Type: relative/other");
                logMsg.append(" | Source: ").append(src.length() > 100 ? src.substring(0, 100) + "..." : src);
                logMsg.append(" | Stored path: ").append(originalPath != null ? originalPath : "(relative/embedded - no full path)");
            }

            // Add dimensions
            if (width != null && !width.isEmpty()) {
                logMsg.append(" | Width: ").append(width);
            }
            if (height != null && !height.isEmpty() && !"auto".equalsIgnoreCase(height)) {
                logMsg.append(" | Height: ").append(height);
            }

            // Add alt text if present
            if (alt != null && !alt.isEmpty()) {
                logMsg.append(" | Alt: ").append(alt.length() > 50 ? alt.substring(0, 50) + "..." : alt);
            }

            log.info(logMsg.toString());
            index++;
        }

        // Also log table count
        Elements tables = doc.select("table");
        if (!tables.isEmpty()) {
            log.info("[{}] Found {} table(s)", context, tables.size());
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}

