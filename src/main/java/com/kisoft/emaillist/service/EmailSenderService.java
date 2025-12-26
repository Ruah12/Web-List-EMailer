package com.kisoft.emaillist.service;

import com.kisoft.emaillist.model.SendResult;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

/**
 * Email Sender Service - Sends emails via SMTP using Spring JavaMail.
 *
 * <p>This service provides multiple sending strategies:</p>
 * <ul>
 *   <li><b>Individual</b>: Send one email per recipient (slower but more reliable)</li>
 *   <li><b>Batch</b>: Send to multiple recipients per email (faster but riskier)</li>
 * </ul>
 *
 * <h3>Address Modes:</h3>
 * <ul>
 *   <li><b>To</b>: Recipients see each other's addresses</li>
 *   <li><b>BCC</b>: Recipients are hidden from each other (recommended for mass emails)</li>
 * </ul>
 *
 * <h3>HTML Conversion:</h3>
 * <p>The service includes a sophisticated HTML converter that transforms editor HTML
 * (which uses CSS floats) into email-client-compatible HTML (using tables). This ensures
 * images and text appear side-by-side in email clients like Outlook that don't support floats.</p>
 *
 * <h3>Key Features:</h3>
 * <ul>
 *   <li>Configurable from-address and name</li>
 *   <li>UTF-8 encoding support</li>
 *   <li>HTML email with inline styles</li>
 *   <li>Automatic image-to-table conversion</li>
 *   <li>Rate limiting between sends</li>
 *   <li>Detailed error tracking per recipient</li>
 * </ul>
 *
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-26
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailSenderService {

    /** Spring JavaMail sender for SMTP communication */
    private final JavaMailSender mailSender;

    /** Service for loading email list (used by sendToAll) */
    private final EmailListService emailListService;

    /** From email address (configured in application.properties) */
    @Value("${mail.from}")
    private String fromEmail;

    /** From display name (configured in application.properties) */
    @Value("${mail.from.name:Email Sender}")
    private String fromName;

    public SendResult sendToAll(String subject, String htmlContent) {
        List<String> emailList = emailListService.loadEmailList();
        return sendToEmails(emailList, subject, htmlContent);
    }

    public SendResult sendToSelected(List<String> emails, String subject, String htmlContent) {
        return sendToEmails(emails, subject, htmlContent);
    }

    /**
     * Send emails in batch mode - can use To or BCC
     */
    public SendResult sendBatch(List<String> emails, String subject, String htmlContent, int batchSize, boolean useBcc) {
        int total = emails.size();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<String> failedEmails = new ArrayList<>();
        SendResult result = new SendResult(total, 0, 0, "", failedEmails);

        log.info("sendBatch called: total={}, useBcc={}, batchSize={}", total, useBcc, batchSize);

        for (int i = 0; i < emails.size(); i += batchSize) {
            List<String> batch = emails.subList(i, Math.min(i + batchSize, emails.size()));
            try {
                log.info("Sending batch of {} emails, useBcc={}", batch.size(), useBcc);
                if (useBcc) {
                    log.info("Using BCC method for batch: {}", batch);
                    sendEmailWithBcc(batch, subject, htmlContent);
                } else {
                    log.info("Using TO method for batch: {}", batch);
                    sendEmailWithMultipleTo(batch, subject, htmlContent);
                }
                successCount.addAndGet(batch.size());
                log.info("Batch sent successfully to {} recipients using {}", batch.size(), useBcc ? "BCC" : "TO");

                if (i + batchSize < emails.size()) {
                    Thread.sleep(500);
                }
            } catch (MessagingException | UnsupportedEncodingException e) {
                failCount.addAndGet(batch.size());
                failedEmails.addAll(batch);
                String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                for (String email : batch) {
                    result.addErrorMessage(email, errorMsg);
                }
                log.error("Failed to send batch: {}", errorMsg);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Batch sending interrupted");
                break;
            }
        }

        String message = String.format("Batch sending complete. Success: %d, Failed: %d out of %d total",
                successCount.get(), failCount.get(), total);
        log.info(message);

        result.setSuccessCount(successCount.get());
        result.setFailCount(failCount.get());
        result.setMessage(message);
        return result;
    }

    /**
     * Send emails one by one - can use To or BCC
     */
    public SendResult sendIndividual(List<String> emails, String subject, String htmlContent, boolean useBcc) {
        int total = emails.size();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<String> failedEmails = new ArrayList<>();
        SendResult result = new SendResult(total, 0, 0, "", failedEmails);

        log.info("Starting individual send to {} emails, useBcc={}", total, useBcc);

        for (String email : emails) {
            try {
                if (useBcc) {
                    sendEmailWithBccSingle(email, subject, htmlContent);
                } else {
                    sendEmail(email, subject, htmlContent);
                }
                successCount.incrementAndGet();
                log.info("Email sent to: {}", email);
                Thread.sleep(100);
            } catch (Exception e) {
                failCount.incrementAndGet();
                failedEmails.add(email);
                String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                result.addErrorMessage(email, errorMsg);
                log.error("Failed to send to {}: {}", email, errorMsg);
            }
        }

        String message = String.format("Individual sending complete. Success: %d, Failed: %d out of %d total",
                successCount.get(), failCount.get(), total);
        log.info(message);

        result.setSuccessCount(successCount.get());
        result.setFailCount(failCount.get());
        result.setMessage(message);
        return result;
    }

    private SendResult sendToEmails(List<String> emails, String subject, String htmlContent) {
        int total = emails.size();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<String> failedEmails = new ArrayList<>();
        SendResult result = new SendResult(total, 0, 0, "", failedEmails);

        log.info("Starting to send {} emails with subject: {}", total, subject);

        for (String email : emails) {
            try {
                sendEmail(email, subject, htmlContent);
                successCount.incrementAndGet();
                log.info("Email sent successfully to: {}", email);
                
                // Small delay to avoid overwhelming the server
                Thread.sleep(100);
            } catch (Exception e) {
                failCount.incrementAndGet();
                failedEmails.add(email);
                String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                result.addErrorMessage(email, errorMsg);
                log.error("Failed to send email to {}: {}", email, errorMsg);
            }
        }

        String message = String.format("Sending complete. Success: %d, Failed: %d out of %d total",
                successCount.get(), failCount.get(), total);
        
        log.info(message);
        
        result.setSuccessCount(successCount.get());
        result.setFailCount(failCount.get());
        result.setMessage(message);
        return result;
    }

    private void sendEmail(String to, String subject, String htmlContent) throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        
        helper.setFrom(fromEmail, fromName);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(convertToEmailSafeHtml(htmlContent), true);

        mailSender.send(message);
    }

    /**
     * Converts editor HTML into email-client-compatible HTML.
     *
     * Problem this solves:
     * - Browsers render CSS floats well, but many email clients (notably Outlook) ignore/limit floats.
     * - Without conversion, an "image left + text right" layout often collapses to "image top, text bottom".
     *
     * Strategy:
     * - ONLY convert floated images to table layout (preserves exact layout for non-floated content)
     * - Use table-based layout for floated images to ensure side-by-side rendering in email clients
     * - Preserve all other HTML exactly as-is
     */
    private String convertToEmailSafeHtml(String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) {
            return wrapInEmailTemplate("");
        }

        Document doc = Jsoup.parseBodyFragment(htmlContent);
        Element body = doc.body();

        // Convert white text to black for email (emails typically have white background)
        convertWhiteTextToBlack(body);

        // Convert deprecated <font size="N"> tags to inline CSS font-size
        // HTML font sizes 1-7 map to approximately: 12px (min), 13px, 16px, 18px, 24px, 32px, 48px
        convertFontTagsToInlineStyles(body);

        // Enforce minimum font size of 14px for Outlook compatibility
        enforceMinimumFontSize(body, 14);

        // ONLY process images that have float:left style (intended side-by-side layout)
        java.util.List<Element> floatedImages = new java.util.ArrayList<>();
        for (Element img : body.select("img")) {
            String style = img.attr("style");
            if (isSideBySideImage(style, img)) {
                floatedImages.add(img);
            }
        }

        for (Element img : floatedImages) {
            // Skip if already inside a table (already processed)
            if (img.parents().is("table")) {
                continue;
            }

            // Collect following content as the "text" column
            var textNodes = new java.util.ArrayList<Node>();
            Element imgParent = img.parent();

            // Strategy 1: Image is direct child of body or a block element
            // Collect all following siblings until we hit another floated image or end
            Node cursor = img.nextSibling();
            while (cursor != null) {
                // Stop at another floated image
                if (cursor instanceof Element el && el.tagName().equalsIgnoreCase("img")) {
                    String s = el.attr("style");
                    if (isSideBySideImage(s, el)) {
                        break;
                    }
                }
                textNodes.add(cursor);
                cursor = cursor.nextSibling();
            }

            // Strategy 2: If image is in a container (like a div or p),
            // also collect following sibling elements of the container
            if (imgParent != null && !imgParent.tagName().equalsIgnoreCase("body")) {
                Element siblingEl = imgParent.nextElementSibling();
                while (siblingEl != null) {
                    // Stop if this sibling contains a floated image
                    boolean hasFloatedImg = false;
                    for (Element sibImg : siblingEl.select("img")) {
                        if (isSideBySideImage(sibImg.attr("style"), sibImg)) {
                            hasFloatedImg = true;
                            break;
                        }
                    }
                    if (hasFloatedImg) {
                        break;
                    }
                    textNodes.add(siblingEl.clone());
                    Element temp = siblingEl;
                    siblingEl = siblingEl.nextElementSibling();
                    temp.remove();
                }
            }

            // Build table layout if we have content
            int imgWidthPx = pickImageWidthPx(img);

            if (!textNodes.isEmpty()) {
                Element table = buildTwoColumnEmailLayout(img, textNodes, imgWidthPx);

                // Remove the collected siblings
                for (Node n : textNodes) {
                    if (n.parent() != null) {
                        n.remove();
                    }
                }

                // Replace the image (or its parent if it's the only child)
                if (imgParent != null && imgParent.children().size() == 1 && !imgParent.tagName().equalsIgnoreCase("body")) {
                    imgParent.replaceWith(table);
                } else {
                    img.replaceWith(table);
                }
            } else {
                // No adjacent text - just normalize the image for standalone display
                img.attr("style", normalizeImgStyleForEmail(img.attr("style")));
                img.removeAttr("height"); // Remove explicit height for proportional scaling
            }
        }

        inlineParagraphDefaults(body);

        return wrapInEmailTemplate(body.html());
    }

    private static boolean isAnotherImage(Element el) {
        if (el.tagName().equalsIgnoreCase("img")) {
            return true;
        }
        // Also check if this element contains a floated image
        for (Element img : el.select("img")) {
            String style = img.attr("style");
            if (isSideBySideImage(style, img)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSideBySideImage(String style, Element img) {
        String s = style == null ? "" : style.toLowerCase();

        // Primary signal: float:left
        if (s.contains("float") && s.contains("left")) {
            return true;
        }

        // Secondary signal: explicit width (from resize) + margin-right (common for "image left")
        // Users resizing images in the editor may lose float but keep width.
        boolean hasWidth = s.contains("width") || img.hasAttr("width");
        boolean hasMarginRight = s.contains("margin") && (s.contains(" 15px") || s.contains("15px"));
        return hasWidth && hasMarginRight;
    }

    private static int pickImageWidthPx(Element img) {
        // Try inline style width: Npx first (this is set when user resizes in editor)
        String style = img.attr("style");
        if (style != null && !style.isBlank()) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)width\\s*:\\s*(\\d+)px")
                .matcher(style);
            if (m.find()) {
                Integer w = tryParseLeadingInt(m.group(1));
                if (w != null && w > 0) {
                    return w; // Return exact user-specified width, no clamping
                }
            }
        }

        // Try width attribute
        Integer wAttr = tryParseLeadingInt(img.attr("width"));
        if (wAttr != null && wAttr > 0) {
            return wAttr; // Return exact width, no clamping
        }

        // Safe default similar to the editor's 40% idea.
        return 260;
    }

    private static Integer tryParseLeadingInt(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isBlank()) return null;
        try {
            return Integer.parseInt(v.replaceAll("[^0-9].*$", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Element buildTwoColumnEmailLayout(Element img, java.util.List<Node> textNodes, int imgWidthPx) {
        // Use 100% width so content flows based on email client window size
        // Do NOT use fixed width - let email client determine the width

        Element table = new Element("table");
        table.attr("role", "presentation");
        table.attr("cellpadding", "0");
        table.attr("cellspacing", "0");
        table.attr("border", "0");
        table.attr("width", "100%");
        // MSO-specific styles for Outlook compatibility - use 100% width
        table.attr("style", "border-collapse:collapse; mso-table-lspace:0pt; mso-table-rspace:0pt; width:100%;");

        Element tr = table.appendElement("tr");

        Element tdImg = tr.appendElement("td");
        tdImg.attr("valign", "top");
        tdImg.attr("align", "left");
        tdImg.attr("width", String.valueOf(imgWidthPx));
        tdImg.attr("style", "vertical-align:top; text-align:left; padding-right:15px; width:" + imgWidthPx + "px; mso-table-lspace:0pt; mso-table-rspace:0pt;");

        Element tdText = tr.appendElement("td");
        tdText.attr("valign", "top");
        tdText.attr("align", "left");
        // No fixed width for text column - let it expand to fill available space
        tdText.attr("style", "vertical-align:top; text-align:left; mso-table-lspace:0pt; mso-table-rspace:0pt;");

        Element imgClone = img.clone();
        imgClone.attr("style", normalizeImgStyleForEmail(imgClone.attr("style")));
        imgClone.attr("border", "0");
        imgClone.attr("width", String.valueOf(imgWidthPx));
        imgClone.removeAttr("height"); // Remove explicit height to ensure proportional scaling

        tdImg.appendChild(imgClone);

        boolean hasMeaningfulText = false;
        for (Node n : textNodes) {
            if (n instanceof org.jsoup.nodes.TextNode tn) {
                if (!tn.text().isBlank()) {
                    hasMeaningfulText = true;
                }
            } else if (n instanceof Element el) {
                if (!el.text().isBlank() || el.tagName().equalsIgnoreCase("br")) {
                    hasMeaningfulText = true;
                }
            }
            tdText.appendChild(n.clone());
        }

        if (!hasMeaningfulText) {
            tdText.html("&nbsp;");
        }

        return table;
    }

    /**
     * Enforces a minimum font size for all text in the email.
     * This ensures text is visible in email clients that may have minimum rendering sizes.
     *
     * @param body The document body element
     * @param minPx The minimum font size in pixels (e.g., 12)
     */
    private static void enforceMinimumFontSize(Element body, int minPx) {
        // Find all elements with font-size in style attribute
        for (Element el : body.select("[style*=font-size]")) {
            String style = el.attr("style");
            if (style == null || style.isBlank()) continue;

            // Extract font-size value
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)font-size\\s*:\\s*(\\d+)(px|pt)?")
                .matcher(style);

            if (m.find()) {
                try {
                    int size = Integer.parseInt(m.group(1));
                    String unit = m.group(2);

                    // Convert pt to px (approximately 1pt = 1.33px)
                    if ("pt".equalsIgnoreCase(unit)) {
                        size = (int) Math.round(size * 1.33);
                    }

                    // If font size is below minimum, replace with minimum
                    if (size < minPx) {
                        String newStyle = style.replaceAll(
                            "(?i)font-size\\s*:\\s*\\d+(px|pt)?",
                            "font-size:" + minPx + "px"
                        );
                        el.attr("style", newStyle);
                    }
                } catch (NumberFormatException e) {
                    // Ignore non-numeric font sizes
                }
            }
        }
    }

    /**
     * Converts white text to black for email compatibility.
     * The editor may use white text (for dark theme), but emails typically have white background.
     * This converts white/near-white colors to black so text is visible in email clients.
     */
    private static void convertWhiteTextToBlack(Element body) {
        // Find all elements with white-ish color in style attribute
        for (Element el : body.select("[style*=color]")) {
            String style = el.attr("style");
            if (style == null || style.isBlank()) continue;

            // Check for white color variations
            String styleLower = style.toLowerCase();
            if (styleLower.contains("color: white") ||
                styleLower.contains("color:white") ||
                styleLower.contains("color: #fff") ||
                styleLower.contains("color:#fff") ||
                styleLower.contains("color: #ffffff") ||
                styleLower.contains("color:#ffffff") ||
                styleLower.contains("color: rgb(255, 255, 255)") ||
                styleLower.contains("color:rgb(255,255,255)") ||
                styleLower.contains("color: rgb(255,255,255)")) {

                // Replace white with black
                String newStyle = style
                    .replaceAll("(?i)color\\s*:\\s*white", "color: black")
                    .replaceAll("(?i)color\\s*:\\s*#fff(?!\\w)", "color: #000")
                    .replaceAll("(?i)color\\s*:\\s*#ffffff", "color: #000000")
                    .replaceAll("(?i)color\\s*:\\s*rgb\\s*\\(\\s*255\\s*,\\s*255\\s*,\\s*255\\s*\\)", "color: rgb(0, 0, 0)");
                el.attr("style", newStyle);
            }
        }

        // Also handle <font color="white"> attributes
        for (Element font : body.select("font[color]")) {
            String color = font.attr("color").toLowerCase();
            if (color.equals("white") || color.equals("#fff") || color.equals("#ffffff")) {
                font.attr("color", "#000000");
            }
        }
    }

    /**
     * Converts deprecated HTML font tags to inline CSS styles.
     * The execCommand('fontSize') uses font size values 1-7 which map to:
     * 1=12px (minimum), 2=13px, 3=16px, 4=18px, 5=24px, 6=32px, 7=48px
     * Email clients handle these inconsistently, so we convert to explicit px values.
     * Minimum font size is 14px to ensure visibility in all email clients including MS Outlook.
     */
    private static void convertFontTagsToInlineStyles(Element body) {
        // Map HTML font size attribute (1-7) to CSS pixel sizes
        // Sizes 1-3 map to 14px minimum for Outlook compatibility
        // Dropdown shows: 14(3), 16(4), 18(5), 24(6), 36(7)
        java.util.Map<String, String> fontSizeMap = java.util.Map.of(
            "1", "14px",  // Minimum 14px for Outlook compatibility
            "2", "14px",  // Minimum 14px for Outlook compatibility
            "3", "14px",  // 14px - matches dropdown
            "4", "16px",  // 16px - matches dropdown
            "5", "18px",  // 18px - matches dropdown
            "6", "24px",  // 24px - matches dropdown
            "7", "36px"   // 36px - matches dropdown
        );

        for (Element font : body.select("font[size]")) {
            String sizeAttr = font.attr("size");
            String pxSize = fontSizeMap.getOrDefault(sizeAttr, "14px");

            // Build inline style
            StringBuilder style = new StringBuilder();
            style.append("font-size:").append(pxSize).append(";");

            // Preserve color attribute if present
            String color = font.attr("color");
            if (color != null && !color.isBlank()) {
                style.append("color:").append(color).append(";");
            }

            // Preserve face (font-family) attribute if present
            String face = font.attr("face");
            if (face != null && !face.isBlank()) {
                style.append("font-family:").append(face).append(";");
            }

            // Merge with existing style if any
            String existingStyle = font.attr("style");
            if (existingStyle != null && !existingStyle.isBlank()) {
                style.append(existingStyle);
            }

            // Replace <font> with <span> for better compatibility
            Element span = new Element("span");
            span.attr("style", style.toString());
            span.html(font.html());
            font.replaceWith(span);
        }

        // Also handle font tags with only color or face (no size)
        for (Element font : body.select("font:not([size])")) {
            StringBuilder style = new StringBuilder();

            String color = font.attr("color");
            if (color != null && !color.isBlank()) {
                style.append("color:").append(color).append(";");
            }

            String face = font.attr("face");
            if (face != null && !face.isBlank()) {
                style.append("font-family:").append(face).append(";");
            }

            if (style.length() > 0) {
                String existingStyle = font.attr("style");
                if (existingStyle != null && !existingStyle.isBlank()) {
                    style.append(existingStyle);
                }

                Element span = new Element("span");
                span.attr("style", style.toString());
                span.html(font.html());
                font.replaceWith(span);
            }
        }
    }

    /**
     * Inline paragraph styles for better email client compatibility.
     * Many email clients strip or ignore CSS, so inline styles are more reliable.
     * Text wrapping is handled by the email client based on window size.
     */
    private static void inlineParagraphDefaults(Element body) {
        // Add inline styles to paragraphs - margin/padding AND font-size for Outlook
        for (Element p : body.select("p")) {
            String existingStyle = p.attr("style");
            if (existingStyle == null || existingStyle.isBlank()) {
                p.attr("style", "margin:0 0 10px 0; padding:0; font-size:14px;");
            } else {
                StringBuilder newStyle = new StringBuilder(existingStyle);
                if (!existingStyle.toLowerCase().contains("margin")) {
                    newStyle.append("; margin:0 0 10px 0;");
                }
                if (!existingStyle.toLowerCase().contains("font-size")) {
                    newStyle.append("; font-size:14px;");
                }
                p.attr("style", newStyle.toString());
            }
        }

        // Add inline styles to divs - margin/padding AND font-size for Outlook
        for (Element div : body.select("div")) {
            String existingStyle = div.attr("style");
            if (existingStyle == null || existingStyle.isBlank()) {
                div.attr("style", "margin:0; padding:0; font-size:14px;");
            } else if (!existingStyle.toLowerCase().contains("font-size")) {
                div.attr("style", existingStyle + "; font-size:14px;");
            }
        }

        // Add font-size to spans that don't have it
        for (Element span : body.select("span")) {
            String existingStyle = span.attr("style");
            if (existingStyle == null || existingStyle.isBlank()) {
                span.attr("style", "font-size:14px;");
            } else if (!existingStyle.toLowerCase().contains("font-size")) {
                span.attr("style", existingStyle + "; font-size:14px;");
            }
        }

        // Add font-size to table cells for Outlook
        for (Element td : body.select("td")) {
            String existingStyle = td.attr("style");
            if (existingStyle != null && !existingStyle.toLowerCase().contains("font-size")) {
                td.attr("style", existingStyle + "; font-size:14px;");
            } else if (existingStyle == null || existingStyle.isBlank()) {
                td.attr("style", "font-size:14px;");
            }
        }
    }

    private static String normalizeImgStyleForEmail(String originalStyle) {
        String style = originalStyle == null ? "" : originalStyle;

        // Remove float:left - we use tables instead for email compatibility
        style = style.replaceAll("(?i)float\\s*:\\s*left\\s*;?", "");

        // Remove max-width percentage patterns (40%, etc.) - these don't work well in email
        style = style.replaceAll("(?i)max-width\\s*:\\s*\\d+%\\s*;?", "");

        // REMOVE any explicit height value - we will force height:auto for proportional scaling
        style = style.replaceAll("(?i)height\\s*:\\s*[^;]+;?", "");

        // PRESERVE width:XXXpx - this is the user's resized dimension!
        // Only add defaults if not already present

        // Email friendly defaults - ALWAYS add height:auto for proportional scaling
        style = "height:auto; " + style;

        if (!style.toLowerCase().contains("display")) {
            style = "display:block; " + style;
        }

        // Add max-width:100% only if no pixel max-width is set
        if (!style.toLowerCase().contains("max-width")) {
            style = "max-width:100%; " + style;
        }

        return style.trim();
    }

    private static boolean isHardBlockBoundary(Node node) {
        if (!(node instanceof Element el)) {
            // Text nodes can belong to the wrapping section.
            return false;
        }
        String tag = el.tagName().toLowerCase();
        // Boundaries: new paragraph/div/list/table or another image.
        return tag.equals("p")
            || tag.equals("div")
            || tag.equals("table")
            || tag.equals("ul")
            || tag.equals("ol")
            || tag.equals("h1")
            || tag.equals("h2")
            || tag.equals("h3")
            || tag.equals("img");
    }

    /**
     * Wrap content in a basic email HTML template for better rendering.
     * Includes MSO conditional comments for Outlook compatibility.
     * Text wrapping is handled by the email client based on window size.
     */
    private String wrapInEmailTemplate(String content) {
        return """
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office">
            <head>
                <meta charset="UTF-8">
                <meta http-equiv="X-UA-Compatible" content="IE=edge">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <!--[if mso]>
                <noscript>
                <xml>
                <o:OfficeDocumentSettings>
                  <o:PixelsPerInch>96</o:PixelsPerInch>
                </o:OfficeDocumentSettings>
                </xml>
                </noscript>
                <style>
                  table {border-collapse: collapse;}
                  td {vertical-align: top;}
                </style>
                <![endif]-->
            </head>
            <body style="margin:0; padding:0; font-family: Arial, sans-serif; font-size: 14px; line-height: 1.5;">
            """ + content + """
            </body>
            </html>
            """;
    }

    /**
     * Send email with recipient in BCC field (sender email in To)
     */
    private void sendEmailWithBccSingle(String recipient, String subject, String htmlContent) throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail, fromName);
        helper.setTo(fromEmail); // Send to self
        helper.setBcc(recipient); // Actual recipient in BCC
        helper.setSubject(subject);
        helper.setText(convertToEmailSafeHtml(htmlContent), true);

        mailSender.send(message);
    }

    /**
     * Send email to multiple recipients using To field
     */
    private void sendEmailWithMultipleTo(List<String> recipients, String subject, String htmlContent) throws MessagingException, UnsupportedEncodingException {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail, fromName);
        helper.setTo(recipients.toArray(new String[0]));
        helper.setSubject(subject);
        helper.setText(convertToEmailSafeHtml(htmlContent), true);

        mailSender.send(message);
    }

    /**
     * Send email to multiple recipients using BCC field
     * Sender email goes to To field, all recipients go to BCC
     */
    private void sendEmailWithBcc(List<String> recipients, String subject, String htmlContent) throws MessagingException, UnsupportedEncodingException {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }

        log.info("sendEmailWithBcc: Sending to {} recipients via BCC, To field will be: {}", recipients.size(), fromEmail);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail, fromName);
        //helper.setTo(fromEmail); // Send to self
        helper.setBcc(recipients.toArray(new String[0])); // All recipients in BCC

        log.info("sendEmailWithBcc: BCC recipients set to: {}", recipients);

        helper.setSubject(subject);
        helper.setText(convertToEmailSafeHtml(htmlContent), true);

        mailSender.send(message);
        log.info("sendEmailWithBcc: Email sent successfully via BCC");
    }

    public boolean testConnection() {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // Just creating a message tests the connection
            return true;
        } catch (Exception e) {
            log.error("Mail server connection test failed: {}", e.getMessage());
            return false;
        }
    }

    public void sendTestEmail(String to, String subject, String htmlContent) throws Exception {
        try {
            sendEmail(to, subject, htmlContent);
            log.info("Test email sent successfully to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send test email: {}", e.getMessage());
            throw e;
        }
    }
}
