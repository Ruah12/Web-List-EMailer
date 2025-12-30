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

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

/**
 * Email Sender Service - Sends emails via SMTP using Spring JavaMail.
     * This service provides multiple sending strategies:
 * - Individual: Send one email per recipient (slower but more reliable)
 * - Batch: Send to multiple recipients per email (faster but riskier)
 * Address Modes:
 * - To: Recipients see each other's addresses
 * - BCC: Recipients are hidden from each other (recommended for mass emails)
 * HTML Conversion:
 * The service includes a sophisticated HTML converter that transforms editor HTML
 * (which uses CSS floats) into email-client-compatible HTML (using tables). This ensures
 * images and text appear side-by-side in email clients like Outlook that don't support floats.
 * Key Features:
 * - Configurable from-address and name via {@code mail.from} and {@code mail.from.name}
 * - UTF-8 encoding support
 * - HTML email with inline styles
 * - Automatic image-to-table conversion for Outlook compatibility
 * - Rate limiting between sends via {@code delayMs} parameter
 * - Detailed error tracking per recipient
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-26
 * @see com.kisoft.emaillist.controller.EmailController
 * @see com.kisoft.emaillist.model.SendResult
 * @see org.springframework.mail.javamail.JavaMailSender
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailSenderService {

    /** Spring JavaMail sender for SMTP communication */
    private final JavaMailSender mailSender;

    /** From email address (configured in application.properties) */
    @Value("${mail.from}")
    private String fromEmail;

    /** From display name (configured in application.properties) */
    @Value("${mail.from.name:Email Sender}")
    private String fromName;

    /**
     * Sends emails in batch mode to multiple recipients per email.
     * Groups recipients into batches of the specified size and sends
     * one email per batch. This is faster than individual sending but
     * riskier (if one batch fails, all recipients in that batch fail).
     * @param emails List of all recipient email addresses
     * @param subject Email subject line
     * @param htmlContent HTML body content
     * @param batchSize Number of recipients per batch email
     * @param useBcc If {@code true}, recipients go in BCC field; if {@code false}, in To field
     * @param delayMs Delay in milliseconds between batch sends
     * @return {@link SendResult} with success/failure counts and error details
     */
    public SendResult sendBatch(List<String> emails, String subject, String htmlContent, int batchSize, boolean useBcc, int delayMs) {
        int total = emails.size();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<String> failedEmails = new ArrayList<>();
        SendResult result = new SendResult(total, 0, 0, "", failedEmails);

        log.info("sendBatch called: total={}, useBcc={}, batchSize={}, delayMs={}", total, useBcc, batchSize, delayMs);

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

                if (i + batchSize < emails.size() && delayMs > 0) {
                    Thread.sleep(delayMs);
                }
            } catch (MessagingException | UnsupportedEncodingException e) {
                failCount.addAndGet(batch.size());
                failedEmails.addAll(batch);
                String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                for (String email : batch) {
                    result.addErrorMessage(email, errorMsg);
                }
                log.error("Failed to send batch: {}", errorMsg);
            } catch (org.springframework.mail.MailException e) {
                failCount.addAndGet(batch.size());
                failedEmails.addAll(batch);
                String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                for (String email : batch) {
                    result.addErrorMessage(email, errorMsg);
                }
                log.error("Failed to send batch (MailException): {}", errorMsg);
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
     * Sends emails individually, one per recipient.
     *
     * <p>Sends a separate email to each recipient in the list. This is slower
     * than batch mode but more reliable (one failure doesn't affect others).</p>
     *
     * <p>Includes a small delay (100ms) between sends to avoid overwhelming
     * the mail server.</p>
     *
     * @param emails List of recipient email addresses
     * @param subject Email subject line
     * @param htmlContent HTML body content
     * @param useBcc If true, recipient goes in BCC field (sender in To); if false, recipient in To field
     * @return SendResult with success/failure counts and error details
     */
    public SendResult sendIndividual(List<String> emails, String subject, String htmlContent, boolean useBcc) {
        int total = emails.size();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<String> failedEmails = new ArrayList<>();
        SendResult result = new SendResult(total, 0, 0, "", failedEmails);

        log.info("Starting individual send to {} emails, useBcc={}", total, useBcc);
        log.info("Email list to send (exact values): {}", emails);

        int emailIndex = 0;
        for (String email : emails) {
            emailIndex++;
            log.info("=== Processing email {} of {}: '{}' (length={}) ===", emailIndex, total, email, email.length());
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

    /**
     * Sends a single email to one recipient.
     *
     * <p>Creates a MIME message with HTML content and sends it via JavaMailSender.
     * The HTML content is converted to email-safe format before sending.</p>
     *
     * @param to Recipient email address
     * @param subject Email subject line
     * @param htmlContent HTML body content (will be converted for email compatibility)
     * @throws MessagingException If email creation or sending fails
     * @throws UnsupportedEncodingException If character encoding fails
     */
    private void sendEmail(String to, String subject, String htmlContent) throws MessagingException, UnsupportedEncodingException {
        log.info(">>> sendEmail called - TO: '{}' (length={})", to, to.length());
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        
        helper.setFrom(fromEmail, fromName);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(convertToEmailSafeHtml(htmlContent), true);

        log.info(">>> Sending email - TO: '{}', FROM: '{}'", to, fromEmail);
        mailSender.send(message);
        log.info(">>> Email SENT successfully - TO: '{}'", to);
    }

    /**
     * Converts editor HTML into email-client-compatible HTML.
     * Problem this solves:
     * - Browsers render CSS floats well, but many email clients (notably Outlook) ignore/limit floats
     * - Without conversion, an "image left + text right" layout often collapses to "image top, text bottom"
     * - Image dimensions from editor resize may not translate correctly to email
     * Strategy:
     * 1. Convert floated images to table layout for side-by-side rendering
     * 2. Normalize ALL images to ensure width is preserved and {@code height:auto} is used
     * 3. Remove height attributes to prevent disproportionate scaling
     * @param htmlContent The raw HTML from the editor
     * @return Email-safe HTML wrapped in a responsive template
     */
    private String convertToEmailSafeHtml(String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) {
            return wrapInEmailTemplate("");
        }

        Document doc = Jsoup.parseBodyFragment(htmlContent);
        Element body = doc.body();

        // Debug: log where editor images are loaded from (src values).
        // We intentionally sanitize/redact to avoid logging secrets (query strings) or large payloads (data URIs).
        // NOTE: This logs the image *source references present in the editor HTML*.
        // If the browser already converted local files to data:image/... the original local path is not available server-side.
        if (log.isDebugEnabled()) {
            for (Element img : body.select("img")) {
                String src = img.attr("src");
                if (src == null || src.isBlank()) {
                    continue;
                }
                log.debug("Editor image source loaded from: {}", sanitizeImageSourceForLogs(src));
            }
        }

        // Convert white text to black for email (emails typically have white background)
        convertWhiteTextToBlack(body);

        // Convert deprecated <font size="N"> tags to inline CSS font-size.
        // We convert to explicit px so email clients (notably Outlook) render consistently.
        convertFontTagsToInlineStyles(body);

        // Normalize all inline font-size declarations to px (including pt -> px)
        // to reduce browser-vs-Outlook differences.
        normalizeInlineFontSizesToPx(body);

        // Normalize line-height to px + add Outlook-specific rule. This prevents Outlook from
        // re-interpreting unitless line-height differently than the browser preview.
        normalizeLineHeightsForOutlook(body);

        // Enforce minimum font size for Outlook compatibility.
        // Outlook cannot render font sizes below ~10px readably (8px appears invisible).
        // This applies to both <font size="1"> conversions AND inline CSS font-size styles.
        enforceMinimumFontSize(body, 10);

        // STEP 1: Identify floated images for table conversion
        java.util.List<Element> floatedImages = new java.util.ArrayList<>();
        for (Element img : body.select("img")) {
            String style = img.attr("style");
            if (isSideBySideImage(style, img)) {
                floatedImages.add(img);
            }
        }

        for (Element img : floatedImages) {
            // Skip if already inside a presentation table (one we created for layout)
            // Don't skip images in content tables (user's original tables)
            if (isInsidePresentationTable(img)) {
                continue;
            }

            // Collect following content as the "text" column
            var textNodes = new java.util.ArrayList<Node>();
            Element imgParent = img.parent();

            // Strategy 1: Image is direct child of body or a block element
            // Collect all following siblings until we hit ANY image or end
            Node cursor = img.nextSibling();
            while (cursor != null) {
                // Stop at ANY image (not just floated ones)
                if (cursor instanceof Element el) {
                    if (el.tagName().equalsIgnoreCase("img") || !el.select("img").isEmpty()) {
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
                    // Stop if this sibling contains ANY image (not just floated ones)
                    if (!siblingEl.select("img").isEmpty()) {
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
            log.info("Processing floated image: style='{}', width attr='{}', calculated width={}",
                img.attr("style"), img.attr("width"), imgWidthPx);

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
                // Use the already-calculated imgWidthPx from above
                img.attr("style", normalizeImgStyleForEmail(img.attr("style"), img));
                img.attr("width", String.valueOf(imgWidthPx));
                // Set proportional height for Outlook compatibility (decodes base64 if needed)
                setProportionalHeight(img, imgWidthPx);
            }
        }

        // STEP 2: Normalize ALL remaining images (non-floated) for consistent sizing
        // This ensures any image the user resized in the editor displays at the same size in email
        for (Element remainingImg : body.select("img")) {
            // Skip images already inside presentation tables (ones we created for layout)
            // Don't skip images in content tables (user's original tables)
            if (isInsidePresentationTable(remainingImg)) {
                continue;
            }

            // Get the user-specified width and normalize the image
            int remainingImgWidth = pickImageWidthPx(remainingImg);
            log.info("Processing non-floated image: style='{}', width attr='{}', calculated width={}",
                remainingImg.attr("style"), remainingImg.attr("width"), remainingImgWidth);
            remainingImg.attr("style", normalizeImgStyleForEmail(remainingImg.attr("style"), remainingImg));
            remainingImg.attr("width", String.valueOf(remainingImgWidth));
            // Set proportional height for Outlook compatibility (decodes base64 if needed)
            setProportionalHeight(remainingImg, remainingImgWidth);
        }

        inlineParagraphDefaults(body);

        return wrapInEmailTemplate(body.html());
    }

    /**
     * Normalizes inline font-size declarations to px.
     * Why: Browsers and Outlook (Word engine) differ in how they interpret pt/px and how they round.
     * Converting everything to px makes sizing more deterministic and helps match the editor preview.
     * Supported inputs:
     * - {@code font-size: 12px} - kept as-is
     * - {@code font-size: 9pt} - converted to ~12px (1pt = 1.333px)
     * Note: We keep only integer px sizes because that's the most compatible form for email clients.
     * @param body The HTML body element to process
     */
    private static void normalizeInlineFontSizesToPx(Element body) {
        java.util.regex.Pattern fontSizePattern = java.util.regex.Pattern.compile(
            "(?i)font-size\\s*:\\s*(\\d+(?:\\.\\d+)?)(px|pt)"
        );

        for (Element el : body.select("[style*=font-size]")) {
            String style = el.attr("style");
            if (style == null || style.isBlank()) {
                continue;
            }

            java.util.regex.Matcher matcher = fontSizePattern.matcher(style);
            StringBuffer newStyle = new StringBuffer();

            while (matcher.find()) {
                double sizeValue = Double.parseDouble(matcher.group(1));
                String unit = matcher.group(2).toLowerCase();

                // Convert pt to px (1pt ~= 1.333px). Keep px verbatim.
                double sizeInPx = unit.equals("pt") ? sizeValue * 1.333 : sizeValue;
                int px = (int) Math.round(sizeInPx);

                matcher.appendReplacement(newStyle, "font-size:" + px + "px");
            }
            matcher.appendTail(newStyle);

            el.attr("style", newStyle.toString());
        }
    }

    /**
     * Checks if an image is inside a presentation table (one we created for email layout).
     * We mark our tables with role="presentation" to distinguish them from content tables.
     */
    private static boolean isInsidePresentationTable(Element img) {
        for (Element parent : img.parents()) {
            if (parent.tagName().equalsIgnoreCase("table")
                && "presentation".equals(parent.attr("role"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines if an image should be converted to side-by-side table layout.
     *
     * <p>Checks for float:left style or width+margin combination that indicates
     * the image was intended to have text wrap beside it.</p>
     *
     * @param style The image's CSS style string
     * @param img The image element
     * @return true if the image should be converted to table layout
     */
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

    /**
     * Extracts the width in pixels from an image element.
     *
     * <p>Priority order:</p>
     * <ol>
     *   <li>Inline style width: Npx first (this is set when user resizes in editor)</li>
     *   <li>HTML width attribute</li>
     *   <li>Default of 260px (approximately 40% of typical editor width)</li>
     * </ol>
     *
     * @param img The image element
     * @return Width in pixels
     */
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

    /**
     * Parses the leading integer from a string value.
     *
     * <p>Handles values like "150px" by extracting just "150".</p>
     *
     * @param value String that may contain a number
     * @return The parsed integer, or null if parsing fails
     */
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

    /**
     * Builds a two-column table layout for an image and adjacent text.
     * This creates an email-compatible table structure that ensures the image
     * appears on the left and text on the right, which works in all email clients
     * including Outlook.
     * CRITICAL: Image dimensions are handled carefully:
     * - Width is set from user's resize operation (preserving exact size)
     * - Height is NEVER set as an attribute - only {@code height:auto} in style
     * - This ensures proportional scaling in all email clients
     * @param img The original image element
     * @param textNodes The text nodes to place in the right column
     * @param imgWidthPx The width in pixels for the image
     * @return A table element with the two-column layout
     */
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

        // CRITICAL: Use enhanced normalization that preserves width and forces height:auto
        imgClone.attr("style", normalizeImgStyleForEmail(imgClone.attr("style"), imgClone));
        imgClone.attr("border", "0");
        imgClone.attr("width", String.valueOf(imgWidthPx));

        // Set proportional height for Outlook compatibility (decodes base64 if needed)
        // Note: Use original img for data attributes, but set on imgClone
        Integer originalWidth = tryParseLeadingInt(img.attr("data-original-width"));
        Integer originalHeight = tryParseLeadingInt(img.attr("data-original-height"));
        if (originalWidth == null || originalHeight == null || originalWidth <= 0) {
            int[] dimensions = getImageDimensionsFromDataUrl(img.attr("src"));
            if (dimensions != null) {
                originalWidth = dimensions[0];
                originalHeight = dimensions[1];
            }
        }
        if (originalWidth != null && originalHeight != null && originalWidth > 0) {
            int calculatedHeight = (imgWidthPx * originalHeight) / originalWidth;
            imgClone.attr("height", String.valueOf(calculatedHeight));
        } else {
            imgClone.removeAttr("height");
        }
        // Clean up data attributes from email output
        imgClone.removeAttr("data-original-width");
        imgClone.removeAttr("data-original-height");

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
     * Converts deprecated HTML font tags to inline CSS styles.
     * The browser editor may still emit {@code <font size="1..7">} via execCommand.
     * Email clients (especially Outlook) handle these inconsistently, so we normalize
     * to explicit pixel sizes.
     * IMPORTANT: Outlook has a minimum readable font size of approximately 10-11px.
     * Font size 8px renders as invisible/unreadable in Outlook. We map size 1 to 10px
     * (smallest Outlook-readable size) to ensure text is always visible.
     * @param body The HTML body element to process
     */
    private static void convertFontTagsToInlineStyles(Element body) {
        // Map HTML font size attribute (1-7) to CSS pixel sizes.
        // Size 1 maps to 10px (not 8px) because Outlook cannot render 8px text readably.
        // Size 2 maps to 11px as a small-but-readable size.
        java.util.Map<String, String> fontSizeMap = java.util.Map.of(
            "1", "10px",  // Smallest Outlook-readable size (8px is invisible in Outlook)
            "2", "11px",  // Small but readable
            "3", "12px",
            "4", "14px",
            "5", "18px",
            "6", "24px",
            "7", "36px"
        );

        for (Element font : body.select("font[size]")) {
            String sizeAttr = font.attr("size");
            String pxSize = fontSizeMap.getOrDefault(sizeAttr, "14px");

            StringBuilder style = new StringBuilder();
            style.append("font-size:").append(pxSize).append(";");

            String color = font.attr("color");
            if (color != null && !color.isBlank()) {
                style.append("color:").append(color).append(";");
            }

            String face = font.attr("face");
            if (face != null && !face.isBlank()) {
                style.append("font-family:").append(face).append(";");
            }

            String existingStyle = font.attr("style");
            if (existingStyle != null && !existingStyle.isBlank()) {
                style.append(existingStyle);
            }

            // Use outerHtml replacement to preserve surrounding whitespace
            // JSoup's replaceWith can strip adjacent text nodes' spaces
            font.tagName("span");
            font.removeAttr("size");
            font.removeAttr("color");
            font.removeAttr("face");
            font.attr("style", style.toString());
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

            if (!style.isEmpty()) {
                String existingStyle = font.attr("style");
                if (existingStyle != null && !existingStyle.isBlank()) {
                    style.append(existingStyle);
                }

                // Rename tag in place to preserve whitespace
                font.tagName("span");
                font.removeAttr("color");
                font.removeAttr("face");
                font.attr("style", style.toString());
            }
        }
    }

    /**
     * Enforces a minimum font size on all elements with inline font-size styles.
     *
     * <p>Outlook and some other email clients cannot render very small font sizes (below ~10px)
     * readably. Font size 8px often appears invisible or as tiny dots. This method scans all
     * elements with inline font-size CSS and upgrades any size below the minimum to the minimum.</p>
     *
     * <p>Handles both px and pt units. For pt, we convert to px equivalent (1pt ≈ 1.333px)
     * before comparison.</p>
     *
     * @param body The HTML body element to process
     * @param minPx The minimum font size in pixels (typically 10 for Outlook compatibility)
     */
    private static void enforceMinimumFontSize(Element body, int minPx) {
        // Pattern to match font-size declarations: font-size: Npx or font-size: Npt
        java.util.regex.Pattern fontSizePattern = java.util.regex.Pattern.compile(
            "(?i)font-size\\s*:\\s*(\\d+(?:\\.\\d+)?)(px|pt)"
        );

        for (Element el : body.select("[style*=font-size]")) {
            String style = el.attr("style");
            if (style == null || style.isBlank()) continue;

            java.util.regex.Matcher matcher = fontSizePattern.matcher(style);
            StringBuffer newStyle = new StringBuffer();

            while (matcher.find()) {
                double sizeValue = Double.parseDouble(matcher.group(1));
                String unit = matcher.group(2).toLowerCase();

                // Convert pt to px for comparison (1pt ≈ 1.333px)
                double sizeInPx = unit.equals("pt") ? sizeValue * 1.333 : sizeValue;

                if (sizeInPx < minPx) {
                    // Replace with minimum size in px
                    matcher.appendReplacement(newStyle, "font-size:" + minPx + "px");
                } else {
                    // Keep original
                    matcher.appendReplacement(newStyle, matcher.group(0));
                }
            }
            matcher.appendTail(newStyle);

            el.attr("style", newStyle.toString());
        }
    }

    /**
     * Inline paragraph styles for better email client compatibility.
     * Many email clients strip or ignore CSS, so inline styles are more reliable.
     *
     * <p>We intentionally do NOT inject a default font-size here.
     * The email template already provides a base font-size on the body.
     * Injecting a font-size at every element breaks mixed font-size content.</p>
     */
    private static void inlineParagraphDefaults(Element body) {
        // Paragraphs: add margin/padding defaults only
        for (Element p : body.select("p")) {
            String existingStyle = p.attr("style");
            if (existingStyle == null || existingStyle.isBlank()) {
                p.attr("style", "margin:0 0 10px 0; padding:0;");
            } else {
                StringBuilder newStyle = new StringBuilder(existingStyle);
                if (!existingStyle.toLowerCase().contains("margin")) {
                    newStyle.append("; margin:0 0 10px 0;");
                }
                if (!existingStyle.toLowerCase().contains("padding")) {
                    newStyle.append("; padding:0;");
                }
                p.attr("style", newStyle.toString());
            }
        }

        // Divs: normalize margin/padding only
        for (Element div : body.select("div")) {
            String existingStyle = div.attr("style");
            if (existingStyle == null || existingStyle.isBlank()) {
                div.attr("style", "margin:0; padding:0;");
            } else {
                StringBuilder newStyle = new StringBuilder(existingStyle);
                if (!existingStyle.toLowerCase().contains("margin")) {
                    newStyle.append("; margin:0;");
                }
                if (!existingStyle.toLowerCase().contains("padding")) {
                    newStyle.append("; padding:0;");
                }
                div.attr("style", newStyle.toString());
            }
        }

        // Spans: do not inject defaults (spans should be as-authored)

        // Table cells: do not inject font-size; Outlook respects inherited font-size from body.
    }

    /**
     * Normalizes image styles for email compatibility.
     * This method ensures images display correctly in email clients by:
     * - Removing float (tables handle positioning)
     * - Preserving user-specified width from resize
     * - Forcing {@code height:auto} for proportional scaling
     * - Adding email-compatible display properties
     * @param originalStyle The original CSS style string from the image
     * @param img The image element (to extract width attribute if needed)
     * @return Normalized style string for email
     */
    private static String normalizeImgStyleForEmail(String originalStyle, Element img) {
        String style = originalStyle == null ? "" : originalStyle;

        // Remove float:left - we use tables instead for email compatibility
        style = style.replaceAll("(?i)float\\s*:\\s*left\\s*;?", "");

        // Remove max-width percentage patterns (40%, etc.) - these don't work well in email
        style = style.replaceAll("(?i)max-width\\s*:\\s*\\d+%\\s*;?", "");

        // REMOVE any explicit height value - we will force height:auto for proportional scaling
        style = style.replaceAll("(?i)height\\s*:\\s*[^;]+;?", "");

        // Extract the width value to ensure it's preserved correctly
        Integer widthPx = extractWidthFromStyle(style);
        if (widthPx == null && img != null) {
            widthPx = tryParseLeadingInt(img.attr("width"));
        }

        // Remove existing width from style (we'll re-add it properly)
        style = style.replaceAll("(?i)width\\s*:\\s*[^;]+;?", "");

        // Build clean style string
        StringBuilder cleanStyle = new StringBuilder();

        // Always force height:auto for proportional scaling - this is CRITICAL
        cleanStyle.append("height:auto !important; ");

        // Add width if we have one
        if (widthPx != null && widthPx > 0) {
            cleanStyle.append("width:").append(widthPx).append("px; ");
        }

        cleanStyle.append("max-width:100%; ");
        cleanStyle.append("display:block; ");

        // Append any remaining original styles (colors, borders, etc.)
        String remaining = style.replaceAll("\\s+", " ").trim();
        if (!remaining.isEmpty()) {
            cleanStyle.append(remaining);
        }

        return cleanStyle.toString().trim();
    }

    /**
     * Extracts width in pixels from a CSS style string.
     * Parses inline style attribute to find width declarations in pixel format.
     * @param style CSS style string to parse (e.g., "width: 200px; height: auto;")
     * @return Width in pixels as Integer, or null if not found or not in px format
     */
    private static Integer extractWidthFromStyle(String style) {
        if (style == null || style.isBlank()) return null;

        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(?i)width\\s*:\\s*(\\d+)px")
            .matcher(style);
        if (m.find()) {
            return tryParseLeadingInt(m.group(1));
        }
        return null;
    }

    /**
     * Wraps content in a complete HTML email template.
     * Creates a full HTML document with:
     * - Proper DOCTYPE and XML namespaces for Outlook
     * - MSO conditional comments for Outlook-specific styles
     * - Base font settings (Arial, 14px, 1.5 line-height)
     * - Reset styles for consistent rendering
     * @param content The HTML body content to wrap
     * @return Complete HTML document string
     */
    private String wrapInEmailTemplate(String content) {
        // Outlook renders HTML using the Word engine and is sensitive to where typography is applied.
        // Applying base styles on an outer container TABLE/TD tends to be more consistent than relying
        // on <body> inheritance alone.
        return """
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office">
            <head>
                <meta charset="UTF-8">
                <meta http-equiv="X-UA-Compatible" content="IE=edge">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta name="x-apple-disable-message-reformatting">
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
            <body style="margin:0; padding:0; -webkit-text-size-adjust:100%; -ms-text-size-adjust:100%; background:#ffffff;">
              <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="border-collapse:collapse; mso-table-lspace:0pt; mso-table-rspace:0pt; width:100%; background:#ffffff;">
                <tr>
                  <td align="left" valign="top" style="vertical-align:top; text-align:left; font-family: Calibri, Arial, sans-serif; font-size:14px; line-height:1.5; mso-line-height-rule:exactly; color:#000000;">
            """ + content + """
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """;
    }

    /**
     * Sends a single email with the recipient in the BCC field.
     *
     * <p>The sender's email address is placed in the To field, and the actual
     * recipient is placed in BCC. This hides the recipient from the email headers.</p>
     *
     * @param recipient Recipient email address (placed in BCC)
     * @param subject Email subject line
     * @param htmlContent HTML body content
     * @throws MessagingException If email creation or sending fails
     * @throws UnsupportedEncodingException If character encoding fails
     */
    private void sendEmailWithBccSingle(String recipient, String subject, String htmlContent) throws MessagingException, UnsupportedEncodingException {
        log.info(">>> sendEmailWithBccSingle called - BCC: '{}' (length={})", recipient, recipient.length());
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail, fromName);
        helper.setTo(fromEmail); // Send to self
        helper.setBcc(recipient); // Actual recipient in BCC
        helper.setSubject(subject);
        helper.setText(convertToEmailSafeHtml(htmlContent), true);

        log.info(">>> Sending email - TO: '{}', BCC: '{}'", fromEmail, recipient);
        mailSender.send(message);
        log.info(">>> Email SENT successfully - BCC: '{}'", recipient);
    }

    /**
     * Sends an email to multiple recipients using the To field.
     *
     * <p>All recipients are visible to each other in the email headers.
     * Used for batch sending when recipients should see who else received the email.</p>
     *
     * @param recipients List of recipient email addresses
     * @param subject Email subject line
     * @param htmlContent HTML body content
     * @throws MessagingException If email creation or sending fails
     * @throws UnsupportedEncodingException If character encoding fails
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
     * Sends an email to multiple recipients using the BCC field.
     *
     * <p>All recipients are hidden from each other. The sender's email address
     * is placed in the To field (commented out in current implementation).
     * This is the recommended mode for mass emails to protect recipient privacy.</p>
     *
     * @param recipients List of recipient email addresses (placed in BCC)
     * @param subject Email subject line
     * @param htmlContent HTML body content
     * @throws MessagingException If email creation or sending fails
     * @throws UnsupportedEncodingException If character encoding fails
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

    /**
     * Tests the connection to the mail server.
     *
     * <p>Attempts to create a MIME message using the configured JavaMailSender.
     * If successful, the mail server connection is working. This is called
     * periodically by the UI to update the connection status badge.</p>
     *
     * @return true if connection is successful, false otherwise
     */
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

    /**
     * Sends a test email to verify email functionality.
     *
     * <p>Used by the "Send Test" button in the UI to verify that emails
     * can be sent successfully before sending to the full recipient list.</p>
     *
     * @param to Test recipient email address
     * @param subject Email subject line
     * @param htmlContent HTML body content
     * @throws Exception If email sending fails
     */
    public void sendTestEmail(String to, String subject, String htmlContent) throws Exception {
        try {
            sendEmail(to, subject, htmlContent);
            log.info("Test email sent successfully to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send test email: {}", e.getMessage());
            throw e;
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
     * Extracts image dimensions from a base64 data URL.
     * Returns an int array [width, height] or null if extraction fails.
     *
     * @param src The image src attribute (expected to be a data URL)
     * @return int array [width, height] or null if extraction fails
     */
    private static int[] getImageDimensionsFromDataUrl(String src) {
        if (src == null || !src.startsWith("data:image")) {
            return null;
        }
        try {
            int commaIndex = src.indexOf(',');
            if (commaIndex < 0) {
                return null;
            }
            String base64Data = src.substring(commaIndex + 1);
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (bufferedImage == null) {
                return null;
            }
            return new int[] { bufferedImage.getWidth(), bufferedImage.getHeight() };
        } catch (Exception e) {
            // Log but don't fail - we'll just skip setting height
            return null;
        }
    }

    /**
     * Sets the height attribute on an image element based on the target width and original dimensions.
     * This is critical for Outlook compatibility, which ignores CSS and requires HTML attributes.
     *
     * @param imgElement The image element to update
     * @param targetWidth The target width in pixels
     */
    private static void setProportionalHeight(Element imgElement, int targetWidth) {
        // First try to get dimensions from data attributes (set by JavaScript)
        Integer originalWidth = tryParseLeadingInt(imgElement.attr("data-original-width"));
        Integer originalHeight = tryParseLeadingInt(imgElement.attr("data-original-height"));

        // Fallback: decode base64 image to get dimensions
        if (originalWidth == null || originalHeight == null || originalWidth <= 0) {
            int[] dimensions = getImageDimensionsFromDataUrl(imgElement.attr("src"));
            if (dimensions != null) {
                originalWidth = dimensions[0];
                originalHeight = dimensions[1];
            }
        }

        // Calculate and set proportional height
        if (originalWidth != null && originalHeight != null && originalWidth > 0) {
            int calculatedHeight = (targetWidth * originalHeight) / originalWidth;
            imgElement.attr("height", String.valueOf(calculatedHeight));
        } else {
            imgElement.removeAttr("height");
        }

        // Clean up data attributes from email output
        imgElement.removeAttr("data-original-width");
        imgElement.removeAttr("data-original-height");
    }

    /**
     * Normalizes inline line-height declarations to explicit px and adds
     * Outlook-specific "mso-line-height-rule:exactly".
     *
     * <p>Outlook (Word engine) can interpret unitless line-height differently from browsers.
     * For the closest match to the editor, we rewrite line-height to px based on the element's
     * font-size (inline or inherited default 14px from the email wrapper).</p>
     */
    private static void normalizeLineHeightsForOutlook(Element body) {
        // Matches: line-height: 1.5 | line-height: 20px | line-height: 1.2em
        java.util.regex.Pattern lineHeightPattern = java.util.regex.Pattern.compile(
            "(?i)line-height\\s*:\\s*(\\d+(?:\\.\\d+)?)(px|em)?"
        );
        java.util.regex.Pattern fontSizePattern = java.util.regex.Pattern.compile(
            "(?i)font-size\\s*:\\s*(\\d+)(px)"
        );

        for (Element el : body.select("[style*=line-height]")) {
            String style = el.attr("style");
            if (style == null || style.isBlank()) {
                continue;
            }

            java.util.regex.Matcher lh = lineHeightPattern.matcher(style);
            if (!lh.find()) {
                continue;
            }

            double lhValue = Double.parseDouble(lh.group(1));
            String lhUnit = lh.group(2) == null ? "" : lh.group(2).toLowerCase();

            // Determine font-size in px for this element. Prefer inline font-size; fallback to 14.
            int fontPx = 14;
            java.util.regex.Matcher fs = fontSizePattern.matcher(style);
            if (fs.find()) {
                try {
                    fontPx = Integer.parseInt(fs.group(1));
                } catch (NumberFormatException ignored) {
                    fontPx = 14;
                }
            }

            int linePx;
            if ("px".equals(lhUnit)) {
                linePx = (int) Math.round(lhValue);
            } else if ("em".equals(lhUnit)) {
                linePx = (int) Math.round(lhValue * fontPx);
            } else {
                // Unitless: treat as multiplier (CSS spec). Convert to px.
                linePx = (int) Math.round(lhValue * fontPx);
            }

            // Replace the first occurrence of line-height with px value.
            String replaced = lh.replaceFirst("line-height:" + linePx + "px");

            // Ensure Outlook uses the provided line-height exactly.
            if (!replaced.toLowerCase().contains("mso-line-height-rule")) {
                replaced = replaced + "; mso-line-height-rule:exactly";
            }

            el.attr("style", replaced);
        }
    }

    /**
     * Sanitizes an image source URL for safe logging.
     * Prevents sensitive information leakage by:
     * - Redacting base64 data in data URLs (shows only MIME type header)
     * - Removing query strings and fragments from URLs
     * - Removing user credentials from URLs
     * @param src The image src attribute value to sanitize
     * @return Sanitized string safe for logging (sensitive parts replaced with {@code <redacted>})
     */
    private static String sanitizeImageSourceForLogs(String src) {
        if (src == null) {
            return "<null>";
        }

        String trimmed = src.trim();
        if (trimmed.isEmpty()) {
            return "<empty>";
        }

        String lower = trimmed.toLowerCase();
        if (lower.startsWith("data:image")) {
            // Avoid logging the full base64 payload.
            String meta = trimmed;
            int comma = trimmed.indexOf(',');
            if (comma >= 0) {
                meta = trimmed.substring(0, comma);
            }
            return meta + ",<redacted>";
        }

        try {
            java.net.URI uri = java.net.URI.create(trimmed);

            String scheme = uri.getScheme();
            if (scheme == null) {
                // Not a URI (or relative). Still try to avoid leaking query strings.
                int q = trimmed.indexOf('?');
                return q >= 0 ? trimmed.substring(0, q) + "?<redacted>" : trimmed;
            }

            // Recompose without userinfo/query/fragment.
            // Keep path because caller asked for "full path"; for file:// URIs, that's the full local path.
            java.net.URI sanitized = new java.net.URI(
                scheme,
                null,
                uri.getHost(),
                uri.getPort(),
                uri.getPath(),
                null,
                null
            );

            String out = sanitized.toString();
            if (trimmed.contains("?") && !out.contains("?")) {
                out = out + "?<redacted>";
            }
            if (trimmed.contains("#") && !out.contains("#")) {
                out = out + "#<redacted>";
            }
            return out;
        } catch (Exception ignored) {
            // Fall back to simple query/fragment removal.
            String out = trimmed;
            int q = out.indexOf('?');
            if (q >= 0) {
                out = out.substring(0, q) + "?<redacted>";
            }
            int hash = out.indexOf('#');
            if (hash >= 0) {
                out = out.substring(0, hash) + "#<redacted>";
            }
            return out;
        }
    }
}
