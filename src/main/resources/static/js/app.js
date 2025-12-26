/**
 * ============================================================================
 * Email Mass Sender - Main JavaScript Application
 * ============================================================================
 *
 * This JavaScript file provides all client-side functionality for the Email
 * Mass Sender web application. It handles the rich text editor, formatting
 * toolbar, email sending, recipient management, and UI interactions.
 *
 * File Structure:
 * ---------------
 * 1. Global State Variables
 * 2. DOM Ready Initialization
 * 3. Editor Command Handler (Outlook-style toolbar)
 * 4. Format Painter Functions
 * 5. Text Formatting Functions (font size, line spacing, clear formatting)
 * 6. Settings Persistence (localStorage)
 * 7. Subject Line Defaults
 * 8. Image Handling (select, resize, delete, insert)
 * 9. Template Management (save/load)
 * 10. Email List Management (select, filter, add, remove)
 * 11. Error Console Functions
 * 12. Email Sending Functions (individual and batch modes)
 * 13. Connection Status Monitoring
 * 14. Modal Dialog Functions
 * 15. Image Paste Helpers
 *
 * Dependencies:
 * -------------
 * - Bootstrap 5.3.2 (modal, dropdown functionality)
 * - Font Awesome 6.5.1 (icons)
 * - Browser's contenteditable and execCommand APIs
 *
 * API Endpoints Used:
 * -------------------
 * - POST /api/send         - Send emails (individual or batch)
 * - POST /api/send-test    - Send test email
 * - GET  /api/test-connection - Check mail server connection
 * - GET  /api/emails       - Get email list
 * - POST /api/emails       - Add single email
 * - DELETE /api/emails     - Remove single email
 * - POST /api/emails/bulk  - Update entire email list
 *
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-26
 * ============================================================================
 */

/* ============================================================================
   1. GLOBAL STATE VARIABLES
   ============================================================================
   Global variables for tracking application state across functions.
   ============================================================================ */

/**
 * Format Painter state - tracks whether format painter is active
 * @type {boolean}
 */
let formatPainterActive = false;

/**
 * Stored format from Format Painter - contains style properties to apply
 * @type {Object|null}
 */
let storedFormat = null;

/**
 * Current font color for the color picker button
 * @type {string}
 */
let currentFontColor = '#ff0000';

/**
 * Current highlight color for the highlight picker button
 * @type {string}
 */
let currentHiliteColor = '#ffff00';

/**
 * Flag to stop email sending process
 * @type {boolean}
 */
let stopSendingRequested = false;

/**
 * Current theme - 'dark' (default) or 'light'
 * @type {string}
 */
let currentTheme = 'dark';

/* ============================================================================
   2. DOM READY INITIALIZATION
   ============================================================================
   Main initialization when DOM is fully loaded.
   Sets up event listeners for toolbar, editor, and form elements.
   ============================================================================ */

document.addEventListener('DOMContentLoaded', function() {
    const editor = document.getElementById('editor');

    // -----------------------------------------
    // Toolbar Button Click Handlers
    // -----------------------------------------
    // Support both legacy .editor-toolbar and new .editor-ribbon-compact
    document.querySelectorAll('.editor-toolbar [data-cmd], .editor-ribbon-compact [data-cmd]').forEach(btn => {
        btn.addEventListener('click', function(e) {
            e.preventDefault();
            const cmd = this.getAttribute('data-cmd');
            handleEditorCommand(cmd, editor);
            editor.focus();
        });
    });

    // -----------------------------------------
    // Font Family Dropdown
    // -----------------------------------------
    const fontFamilySelect = document.getElementById('fontFamily');
    if (fontFamilySelect) {
        fontFamilySelect.addEventListener('change', function() {
            document.execCommand('fontName', false, this.value);
            editor.focus();
        });
    }

    // -----------------------------------------
    // Font Size Dropdown
    // -----------------------------------------
    const fontSizeSelect = document.getElementById('fontSize');
    if (fontSizeSelect) {
        fontSizeSelect.addEventListener('change', function() {
            document.execCommand('fontSize', false, this.value);
            editor.focus();
        });
    }

    // -----------------------------------------
    // Font Color Picker
    // -----------------------------------------
    const fontColorPicker = document.getElementById('fontColorPicker');
    if (fontColorPicker) {
        fontColorPicker.addEventListener('input', function() {
            currentFontColor = this.value;
            document.execCommand('foreColor', false, this.value);
            // Update the color bar indicator below the button
            const colorBar = document.getElementById('fontColorBar');
            if (colorBar) colorBar.style.background = this.value;
            editor.focus();
        });
    }

    // -----------------------------------------
    // Highlight Color Picker
    // -----------------------------------------
    const hiliteColorPicker = document.getElementById('hiliteColorPicker');
    if (hiliteColorPicker) {
        hiliteColorPicker.addEventListener('input', function() {
            currentHiliteColor = this.value;
            document.execCommand('hiliteColor', false, this.value);
            // Update the color bar indicator below the button
            const colorBar = document.getElementById('hiliteColorBar');
            if (colorBar) colorBar.style.background = this.value;
            editor.focus();
        });
    }

    // -----------------------------------------
    // Format Painter Application
    // -----------------------------------------
    // When format painter is active, apply stored format on mouseup
    editor.addEventListener('mouseup', function() {
        if (formatPainterActive && storedFormat) {
            applyStoredFormat();
            formatPainterActive = false;
            storedFormat = null;
            document.body.style.cursor = 'default';
            // Remove active state from format painter button
            const fpBtn = document.querySelector('[data-cmd="formatPainter"]');
            if (fpBtn) fpBtn.classList.remove('active');
        }
    });

    // -----------------------------------------
    // Clipboard Paste Handler (for images)
    // -----------------------------------------
    // Intercepts paste events to handle image pasting from various sources
    editor.addEventListener('paste', function(e) {
        const dt = e.clipboardData;
        if (!dt) return;

        // Some apps provide pasted images only via clipboardData.files (not items).
        if (dt.files && dt.files.length > 0) {
            const maybeImage = Array.from(dt.files).find(f => (f.type || '').startsWith('image/'));
            if (maybeImage) {
                e.preventDefault();
                const reader = new FileReader();
                reader.onload = function(event) {
                    insertImageAtCaret(editor, String(event.target.result || ''));
                    enableSendButton();
                };
                reader.readAsDataURL(maybeImage);
                return;
            }
        }

        // We only intercept paste when we can confidently handle an image.
        // Otherwise, we let the browser do the normal paste.
        const items = Array.from(dt.items || []);

        // 1) Best case: clipboard contains an actual image blob.
        const imageItem = items.find(it => (it.type || '').startsWith('image/'));
        if (imageItem) {
            e.preventDefault();
            const blob = imageItem.getAsFile();
            if (!blob) return;

            const reader = new FileReader();
            reader.onload = function(event) {
                insertImageAtCaret(editor, String(event.target.result || ''));
                enableSendButton();
            };
            reader.readAsDataURL(blob);
            return;
        }

        // 2) Next: clipboard contains HTML (Office/Outlook often provide <img src="file:///...">).
        const htmlItem = items.find(it => it.type === 'text/html');
        if (htmlItem) {
            htmlItem.getAsString(async (html) => {
                if (!html) return;

                const doc = new DOMParser().parseFromString(html, 'text/html');
                const imgs = Array.from(doc.querySelectorAll('img'));
                const fileImgs = imgs
                    .map(img => img.getAttribute('src') || '')
                    .filter(src => src.toLowerCase().startsWith('file:'));

                if (fileImgs.length === 0) return;

                // If we can convert at least one file:// image to a data URL, we take over paste.
                // Otherwise, we let the default paste happen.
                const converted = [];
                for (const src of fileImgs) {
                    const dataUrl = await fileUrlToDataUrl(src);
                    if (dataUrl) converted.push(dataUrl);
                }

                if (converted.length === 0) return;

                e.preventDefault();
                for (const dataUrl of converted) {
                    insertImageAtCaret(editor, dataUrl);
                }
                enableSendButton();
            });
            return;
        }

        // 3) Last-chance: plain text is a local path or file URL.
        const textItem = items.find(it => it.type === 'text/plain');
        if (textItem) {
            textItem.getAsString(async (text) => {
                const t = (text || '').trim();
                if (!t) return;

                // Accept common forms: file:///C:/..., C:\path\to\img.png
                const looksLikeFileUrl = /^file:\/\//i.test(t);
                const looksLikeWinPath = /^[a-zA-Z]:\\/.test(t);
                if (!looksLikeFileUrl && !looksLikeWinPath) return;

                const src = looksLikeWinPath ? ('file:///' + t.replace(/\\/g, '/')) : t;

                const dataUrl = await fileUrlToDataUrl(src);
                if (!dataUrl) return;

                e.preventDefault();
                insertImageAtCaret(editor, dataUrl);
                enableSendButton();
            });
        }
    });

    // -----------------------------------------
    // Enable send button on content change
    // -----------------------------------------
    editor.addEventListener('input', enableSendButton);
    document.getElementById('subject').addEventListener('input', enableSendButton);

    // -----------------------------------------
    // Image Selection Handler
    // -----------------------------------------
    // Click on image to select it for resizing/deletion
    editor.addEventListener('click', function(e) {
        if (e.target.tagName === 'IMG') {
            e.preventDefault();
            selectImage(e.target);
        } else if (!e.target.closest('.resize-handle')) {
            deselectImage();
        }
    });

    // -----------------------------------------
    // Initialize Application State
    // -----------------------------------------
    initDefaultSubject();      // Set default subject line
    updateSelectedCount();     // Update recipient count display
    loadSettings();            // Load saved settings from localStorage
    setupSettingsPersistence(); // Set up auto-save for settings
    updateLoadButtonLabels();  // Update template button labels
    loadThemePreference();     // Load saved theme preference
});

/* ============================================================================
   2.1 THEME TOGGLE FUNCTIONS
   ============================================================================
   Functions for switching between dark and light themes.
   ============================================================================ */

/**
 * Toggles between dark and light themes
 */
function toggleTheme() {
    const body = document.body;
    const themeIcon = document.getElementById('themeIcon');

    if (currentTheme === 'dark') {
        // Switch to light theme
        body.classList.add('theme-light');
        currentTheme = 'light';
        if (themeIcon) {
            themeIcon.classList.remove('fa-sun');
            themeIcon.classList.add('fa-moon');
        }
    } else {
        // Switch to dark theme
        body.classList.remove('theme-light');
        currentTheme = 'dark';
        if (themeIcon) {
            themeIcon.classList.remove('fa-moon');
            themeIcon.classList.add('fa-sun');
        }
    }

    // Save preference
    localStorage.setItem('editorTheme', currentTheme);
}

/**
 * Loads saved theme preference from localStorage
 */
function loadThemePreference() {
    const savedTheme = localStorage.getItem('editorTheme');
    if (savedTheme === 'light') {
        currentTheme = 'dark'; // Set to opposite so toggle switches correctly
        toggleTheme();
    }
}

/* ============================================================================
   3. EDITOR COMMAND HANDLER
   ============================================================================
   Central command dispatcher for all toolbar actions.
   Maps command names to appropriate document.execCommand calls or custom functions.
   ============================================================================ */

/**
 * Handles editor formatting commands from the toolbar
 * @param {string} cmd - The command name (e.g., 'bold', 'italic', 'createLink')
 * @param {HTMLElement} editor - The editor element
 */
function handleEditorCommand(cmd, editor) {
    switch(cmd) {
        // -----------------------------------------
        // Link Commands
        // -----------------------------------------
        case 'createLink':
            const url = prompt('Enter URL:', 'https://');
            if (url) document.execCommand('createLink', false, url);
            break;
        case 'unlink':
            document.execCommand('unlink', false, null);
            break;

        // -----------------------------------------
        // Formatting Commands
        // -----------------------------------------
        case 'removeFormat':
            clearAllFormatting();
            break;

        // -----------------------------------------
        // Image Commands
        // -----------------------------------------
        case 'pasteImage':
            openImagePickerAndInsert(editor);
            break;

        // -----------------------------------------
        // Format Painter
        // -----------------------------------------
        case 'formatPainter':
            activateFormatPainter();
            break;

        // -----------------------------------------
        // Color Commands
        // -----------------------------------------
        case 'foreColor':
            // Apply current color immediately, then open picker for new color
            document.execCommand('foreColor', false, currentFontColor);
            document.getElementById('fontColorPicker').click();
            break;
        case 'hiliteColor':
            // Apply current highlight color, then open picker
            document.execCommand('hiliteColor', false, currentHiliteColor);
            document.getElementById('hiliteColorPicker').click();
            break;

        // -----------------------------------------
        // Font Size Commands
        // -----------------------------------------
        case 'increaseFontSize':
            changeFontSize(1);
            break;
        case 'decreaseFontSize':
            changeFontSize(-1);
            break;

        // -----------------------------------------
        // Clipboard Commands
        // -----------------------------------------
        case 'cut':
            document.execCommand('cut', false, null);
            break;
        case 'copy':
            document.execCommand('copy', false, null);
            break;
        case 'paste':
            // Note: May not work due to browser security restrictions
            document.execCommand('paste', false, null);
            break;

        // -----------------------------------------
        // Standard Formatting Commands
        // -----------------------------------------
        case 'bold':
        case 'italic':
        case 'underline':
        case 'strikeThrough':
        case 'subscript':
        case 'superscript':
        case 'insertUnorderedList':
        case 'insertOrderedList':
        case 'indent':
        case 'outdent':
        case 'justifyLeft':
        case 'justifyCenter':
        case 'justifyRight':
        case 'justifyFull':
        case 'insertHorizontalRule':
            document.execCommand(cmd, false, null);
            break;

        // -----------------------------------------
        // Default: Pass through to execCommand
        // -----------------------------------------
        default:
            document.execCommand(cmd, false, null);
    }
    enableSendButton();
}

/* ============================================================================
   4. FORMAT PAINTER FUNCTIONS
   ============================================================================
   Implements Outlook-style Format Painter functionality.
   Copies formatting from selected text and applies to new selection.
   ============================================================================ */

/**
 * Activates Format Painter - stores the formatting of currently selected text
 * User must have text selected; cursor changes to indicate format painter mode
 */
function activateFormatPainter() {
    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0 || selection.isCollapsed) {
        alert('Please select some text first to copy its formatting.');
        return;
    }

    const range = selection.getRangeAt(0);
    const container = range.commonAncestorContainer;
    const element = container.nodeType === Node.TEXT_NODE ? container.parentElement : container;

    // Store computed styles
    const computedStyle = window.getComputedStyle(element);
    storedFormat = {
        fontFamily: computedStyle.fontFamily,
        fontSize: computedStyle.fontSize,
        fontWeight: computedStyle.fontWeight,
        fontStyle: computedStyle.fontStyle,
        textDecoration: computedStyle.textDecoration,
        color: computedStyle.color,
        backgroundColor: computedStyle.backgroundColor
    };

    formatPainterActive = true;
    document.body.style.cursor = 'copy';

    // Add active state to format painter button
    const fpBtn = document.querySelector('[data-cmd="formatPainter"]');
    if (fpBtn) fpBtn.classList.add('active');
}

/**
 * Applies the stored format to the currently selected text
 * Called automatically when user makes a selection while format painter is active
 */
function applyStoredFormat() {
    if (!storedFormat) return;

    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0 || selection.isCollapsed) return;

    const range = selection.getRangeAt(0);
    const span = document.createElement('span');
    span.style.fontFamily = storedFormat.fontFamily;
    span.style.fontSize = storedFormat.fontSize;
    span.style.fontWeight = storedFormat.fontWeight;
    span.style.fontStyle = storedFormat.fontStyle;
    span.style.textDecoration = storedFormat.textDecoration;
    span.style.color = storedFormat.color;
    if (storedFormat.backgroundColor !== 'rgba(0, 0, 0, 0)' && storedFormat.backgroundColor !== 'transparent') {
        span.style.backgroundColor = storedFormat.backgroundColor;
    }

    try {
        range.surroundContents(span);
    } catch(e) {
        const fragment = range.extractContents();
        span.appendChild(fragment);
        range.insertNode(span);
    }

    enableSendButton();
}

/* ============================================================================
   5. TEXT FORMATTING FUNCTIONS
   ============================================================================
   Functions for manipulating text formatting: font size, line spacing, etc.
   ============================================================================ */

/**
 * Incrementally changes the font size
 * @param {number} direction - Positive to increase, negative to decrease
 */
function changeFontSize(direction) {
    const fontSizeSelect = document.getElementById('fontSize');
    if (!fontSizeSelect) {
        // Fallback: just use built-in commands
        if (direction > 0) {
            document.execCommand('fontSize', false, '5');
        } else {
            document.execCommand('fontSize', false, '2');
        }
        return;
    }

    let currentValue = parseInt(fontSizeSelect.value) || 4;
    let newValue = currentValue + direction;

    // Clamp to valid range (1-7)
    newValue = Math.max(1, Math.min(7, newValue));

    fontSizeSelect.value = newValue;
    document.execCommand('fontSize', false, newValue.toString());
}

/**
 * Sets line spacing for selected text or entire editor
 * @param {number} value - Line height value (e.g., 1.0, 1.5, 2.0)
 */
function setLineSpacing(value) {
    const editor = document.getElementById('editor');
    const selection = window.getSelection();

    if (selection && selection.rangeCount > 0 && !selection.isCollapsed) {
        const range = selection.getRangeAt(0);
        const wrapper = document.createElement('div');
        wrapper.style.lineHeight = value.toString();
        try {
            range.surroundContents(wrapper);
        } catch(e) {
            const fragment = range.extractContents();
            wrapper.appendChild(fragment);
            range.insertNode(wrapper);
        }
    } else {
        // Apply to entire editor
        editor.style.lineHeight = value.toString();
    }
    editor.focus();
    enableSendButton();
}

/**
 * Clears all formatting from selected text or entire editor
 * Preserves line breaks and paragraph structure
 */
function clearAllFormatting() {
    const editor = document.getElementById('editor');
    const selection = window.getSelection();

    // First, use the built-in removeFormat command for basic formatting
    document.execCommand('removeFormat', false, null);

    if (selection.rangeCount > 0 && !selection.isCollapsed) {
        // Get selected content and strip formatting but keep line breaks
        const range = selection.getRangeAt(0);
        const container = document.createElement('div');
        container.appendChild(range.cloneContents());

        // Replace <br> and block elements with newlines, then get text
        container.querySelectorAll('br').forEach(br => br.replaceWith('\n'));
        container.querySelectorAll('p, div').forEach(el => {
            el.insertAdjacentText('afterend', '\n');
        });
        const selectedText = container.innerText;

        // Delete the selected content and insert as paragraphs preserving line breaks
        range.deleteContents();
        const lines = selectedText.split('\n');
        const fragment = document.createDocumentFragment();
        lines.forEach((line, i) => {
            if (line.trim() || i < lines.length - 1) {
                const p = document.createElement('p');
                p.textContent = line || '\u00A0'; // non-breaking space for empty lines
                fragment.appendChild(p);
            }
        });
        range.insertNode(fragment);
    } else {
        // No selection - clear all formatting from entire editor but preserve structure
        editor.style.lineHeight = '';
        editor.querySelectorAll('*').forEach(el => {
            el.removeAttribute('style');
        });
    }

    editor.focus();
}

/* ============================================================================
   6. SETTINGS PERSISTENCE
   ============================================================================
   Saves and loads user preferences to/from localStorage.
   Settings include: send mode, address mode, batch size, delay.
   ============================================================================ */

/** Settings version - increment to reset user settings on breaking changes */
const SETTINGS_VERSION = '3';

/**
 * Loads saved settings from localStorage
 * Falls back to HTML defaults if no settings or version mismatch
 */
function loadSettings() {
    const settings = JSON.parse(localStorage.getItem('emailSenderSettings') || '{}');

    // Clear old settings if version changed
    if (settings.version !== SETTINGS_VERSION) {
        localStorage.removeItem('emailSenderSettings');
        console.log('Cleared old settings, using new defaults');
        return; // Use HTML defaults
    }

    if (settings.sendMode) {
        document.getElementById('sendMode').value = settings.sendMode;
        // Update radio buttons
        if (settings.sendMode === 'batch') {
            document.getElementById('sendModeBatch').checked = true;
        } else {
            document.getElementById('sendModeIndividual').checked = true;
        }
    }
    if (settings.addressMode) document.getElementById('addressMode').value = settings.addressMode;
    if (settings.batchSize) document.getElementById('batchSize').value = settings.batchSize;
    if (settings.sendDelay) document.getElementById('sendDelay').value = settings.sendDelay;
    if (settings.batchAddressMode) document.getElementById('batchAddressMode').value = settings.batchAddressMode;
}

/**
 * Saves current settings to localStorage
 */
function saveSettings() {
    const settings = {
        version: SETTINGS_VERSION,
        sendMode: document.getElementById('sendMode').value,
        addressMode: document.getElementById('addressMode').value,
        batchSize: document.getElementById('batchSize').value,
        sendDelay: document.getElementById('sendDelay').value,
        batchAddressMode: document.getElementById('batchAddressMode').value
    };
    localStorage.setItem('emailSenderSettings', JSON.stringify(settings));
}

/**
 * Sets up event listeners to auto-save settings on change
 */
function setupSettingsPersistence() {
    ['sendMode', 'addressMode', 'batchSize', 'sendDelay', 'batchAddressMode'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.addEventListener('change', saveSettings);
    });
    // Also listen to radio buttons
    document.querySelectorAll('input[name="sendModeRadio"]').forEach(radio => {
        radio.addEventListener('change', function() {
            document.getElementById('sendMode').value = this.value;
            saveSettings();
        });
    });
}

/* ============================================================================
   7. SUBJECT LINE DEFAULTS
   ============================================================================
   Functions for generating default subject lines with date formatting.
   ============================================================================ */

/**
 * Generates default subject line with next Sunday's date
 * @returns {string} Formatted subject line
 */
function getDefaultSubject() {
    const nextSunday = getNextSunday();
    const options = { year: 'numeric', month: 'short', day: 'numeric' };
    const formatted = nextSunday.toLocaleDateString('en-US', options);
    return `Next Sound Therapy/Sound Meditation at ${formatted} at 5pm Sunday`;
}

/**
 * Calculates the date of the next Sunday
 * @returns {Date} Next Sunday's date
 */
function getNextSunday() {
    const now = new Date();
    const day = now.getDay();
    const diff = day === 0 ? 7 : 7 - day;
    return new Date(now.getFullYear(), now.getMonth(), now.getDate() + diff);
}

/**
 * Initializes the subject input with default value if empty
 */
function initDefaultSubject() {
    const subjectInput = document.getElementById('subject');
    if (!subjectInput.value) {
        subjectInput.value = getDefaultSubject();
    }
}

/* ============================================================================
   8. IMAGE HANDLING
   ============================================================================
   Functions for selecting, resizing, and managing images in the editor.
   ============================================================================ */

/** Currently selected image element */
let selectedImg = null;

/** Resize handle element for selected image */
let resizeHandle = null;

/** Flag indicating if resize operation is in progress */
let isResizing = false;

/** Starting coordinates for resize operation */
let startX, startY, startWidth, startHeight;

/**
 * Selects an image for editing (resize/delete)
 * @param {HTMLImageElement} img - The image element to select
 */
function selectImage(img) {
    if (selectedImg) selectedImg.classList.remove('selected');
    if (resizeHandle) resizeHandle.remove();

    selectedImg = img;
    img.classList.add('selected');

    resizeHandle = document.createElement('div');
    resizeHandle.className = 'resize-handle';
    document.body.appendChild(resizeHandle);
    positionResizeHandle();
    resizeHandle.addEventListener('mousedown', startResize);
}

/**
 * Positions the resize handle at the corner of selected image
 */
function positionResizeHandle() {
    if (selectedImg && resizeHandle) {
        const rect = selectedImg.getBoundingClientRect();
        resizeHandle.style.left = (rect.right - 7) + 'px';
        resizeHandle.style.top = (rect.bottom - 7) + 'px';
    }
}

/**
 * Starts the image resize operation
 * @param {MouseEvent} e - The mousedown event
 */
function startResize(e) {
    e.preventDefault();
    e.stopPropagation();
    isResizing = true;
    startX = e.clientX;
    startY = e.clientY;
    startWidth = selectedImg.offsetWidth;
    startHeight = selectedImg.offsetHeight;
    document.addEventListener('mousemove', doResize);
    document.addEventListener('mouseup', stopResize);
}

/**
 * Performs the resize operation as mouse moves
 * @param {MouseEvent} e - The mousemove event
 */
function doResize(e) {
    if (!isResizing || !selectedImg) return;
    const newWidth = Math.max(50, startWidth + (e.clientX - startX));
    selectedImg.style.width = newWidth + 'px';
    selectedImg.style.height = 'auto';
    positionResizeHandle();
    enableSendButton();
}

/**
 * Ends the resize operation
 */
function stopResize() {
    isResizing = false;
    document.removeEventListener('mousemove', doResize);
    document.removeEventListener('mouseup', stopResize);
}

/**
 * Deselects the currently selected image
 */
function deselectImage() {
    if (selectedImg) {
        selectedImg.classList.remove('selected');
        selectedImg = null;
    }
    if (resizeHandle) {
        resizeHandle.remove();
        resizeHandle = null;
    }
}

/**
 * Applies float-left styling to the last image in the editor
 */
function floatImageLeft() {
    const editor = document.getElementById('editor');
    const images = editor.querySelectorAll('img');
    if (images.length > 0) {
        const img = images[images.length - 1];
        img.style.cssText = 'float:left; margin:0 15px 10px 0; max-width:40%; height:auto;';
    } else {
        alert('No image found. Please paste an image first.');
    }
    editor.focus();
}

// Keyboard handler for deleting selected images with Del/Backspace
document.addEventListener('keydown', function(e) {
    if ((e.key === 'Delete' || e.key === 'Backspace') && selectedImg) {
        const activeEl = document.activeElement;
        if (activeEl.tagName === 'INPUT' || activeEl.tagName === 'TEXTAREA') return;
        e.preventDefault();
        selectedImg.remove();
        deselectImage();
        enableSendButton();
    }
});

// Reposition resize handle on scroll/resize
window.addEventListener('scroll', positionResizeHandle);
window.addEventListener('resize', positionResizeHandle);

/* ============================================================================
   9. TEMPLATE MANAGEMENT
   ============================================================================
   Functions for saving and loading email templates to/from localStorage.
   ============================================================================ */

/**
 * Gets the button label for a template slot (numbered + first 22 chars of subject)
 * @param {number} index - Template slot number (1-5)
 * @returns {string} Button label text with format "N.<title>"
 */
function getTemplateButtonLabel(index) {
    try {
        const raw = localStorage.getItem('emailTemplate' + index);
        if (raw) {
            const template = JSON.parse(raw);
            if (template.subject) {
                const label = template.subject.substring(0, 22);
                return index + '.' + label + (template.subject.length > 22 ? '…' : '');
            }
        }
    } catch (e) {}
    return index.toString();
}

/**
 * Updates all load button labels to show template previews
 */
function updateLoadButtonLabels() {
    for (let i = 1; i <= 5; i++) {
        const btn = document.getElementById('loadBtn' + i);
        if (btn) {
            btn.textContent = getTemplateButtonLabel(i);
        }
    }
}

/**
 * Saves current subject and message to a template slot
 * @param {number} index - Template slot number (1-3)
 */
function saveTemplate(index) {
    const subject = document.getElementById('subject').value;
    const htmlContent = getEditorHtml();

    if (!subject && !htmlContent) {
        alert('Nothing to save - subject and message are empty.');
        return;
    }

    const template = { subject, htmlContent };
    try {
        localStorage.setItem('emailTemplate' + index, JSON.stringify(template));
        // Update the load button label
        updateLoadButtonLabels();
        // Brief visual feedback
        const btn = event.target.closest('button');
        const originalText = btn.innerHTML;
        btn.innerHTML = '<i class="fas fa-check"></i> Saved!';
        btn.classList.remove('btn-outline-primary');
        btn.classList.add('btn-success');
        setTimeout(() => {
            btn.innerHTML = originalText;
            btn.classList.remove('btn-success');
            btn.classList.add('btn-outline-primary');
        }, 1500);
    } catch (e) {
        alert('Failed to save: ' + e.message);
    }
}

/**
 * Loads a template from localStorage into the editor
 * @param {number} index - Template slot number (1-3)
 */
function loadTemplate(index) {
    try {
        const raw = localStorage.getItem('emailTemplate' + index);
        if (!raw) {
            alert('Template ' + index + ' is empty. Save something first.');
            return;
        }
        const template = JSON.parse(raw);
        if (template.subject) document.getElementById('subject').value = template.subject;
        if (template.htmlContent) setEditorHtml(template.htmlContent);
        enableSendButton();
        // Brief visual feedback
        const btn = event.target.closest('button');
        const originalText = btn.innerHTML;
        btn.innerHTML = '<i class="fas fa-check"></i> Loaded!';
        btn.classList.remove('btn-outline-secondary');
        btn.classList.add('btn-success');
        setTimeout(() => {
            btn.innerHTML = originalText;
            btn.classList.remove('btn-success');
            btn.classList.add('btn-outline-secondary');
        }, 1500);
    } catch (e) {
        alert('Failed to load: ' + e.message);
    }
}

/* ============================================================================
   10. EMAIL LIST MANAGEMENT
   ============================================================================
   Functions for managing the recipient email list.
   ============================================================================ */

/**
 * Updates the selected email count display
 */
function updateSelectedCount() {
    const checked = document.querySelectorAll('.email-checkbox:checked');
    const el = document.getElementById('selectedCount');
    if (el) el.textContent = checked.length;
}

/**
 * Selects all email checkboxes
 */
function selectAll() {
    document.querySelectorAll('.email-checkbox').forEach(cb => cb.checked = true);
    updateSelectedCount();
}

/**
 * Deselects all email checkboxes
 */
function deselectAll() {
    document.querySelectorAll('.email-checkbox').forEach(cb => cb.checked = false);
    updateSelectedCount();
}

/**
 * Filters the email list based on search input
 */
function filterEmails() {
    const search = document.getElementById('searchEmail').value.toLowerCase();
    document.querySelectorAll('.email-item').forEach(item => {
        const email = item.querySelector('.email-text').textContent.toLowerCase();
        item.style.display = email.includes(search) ? 'flex' : 'none';
    });
}

/**
 * Gets array of currently selected email addresses
 * @returns {string[]} Array of selected email addresses
 */
function getSelectedEmails() {
    const selected = [];
    document.querySelectorAll('.email-checkbox:checked').forEach(cb => selected.push(cb.value));
    return selected;
}

/**
 * Unchecks an email checkbox and scrolls it into view
 * @param {string} email - Email address to uncheck
 */
function uncheckEmail(email) {
    const checkbox = document.querySelector(`.email-checkbox[value="${email}"]`);
    if (checkbox) {
        checkbox.checked = false;

        // Scroll the email item into view
        const emailItem = checkbox.closest('.email-item');
        if (emailItem) {
            const container = document.getElementById('emailListContainer');
            if (container) {
                // Calculate position to scroll
                const itemTop = emailItem.offsetTop;
                const containerHeight = container.clientHeight;
                const scrollTarget = itemTop - containerHeight / 2 + emailItem.clientHeight / 2;

                container.scrollTo({
                    top: Math.max(0, scrollTarget),
                    behavior: 'smooth'
                });

                // Briefly highlight the item
                emailItem.style.backgroundColor = '#d4edda';
                setTimeout(() => {
                    emailItem.style.backgroundColor = '';
                }, 500);
            }
        }
    }
    updateSelectedCount();
}

/* ============================================================================
   11. ERROR CONSOLE FUNCTIONS
   ============================================================================
   Functions for displaying send errors in the error console panel.
   ============================================================================ */

/**
 * Shows the error console with failed email details
 * @param {Array<{email: string, message: string}>} errors - Array of error objects
 */
function showErrorConsole(errors) {
    const console = document.getElementById('errorConsole');
    const body = document.getElementById('errorConsoleBody');
    const count = document.getElementById('errorCount');

    if (!errors || errors.length === 0) {
        console.style.display = 'none';
        return;
    }

    count.textContent = errors.length;
    body.innerHTML = errors.map(err =>
        `<div class="error-item">
            <span class="error-email">${err.email}</span>
            <span class="error-message">${err.message || 'Send failed'}</span>
        </div>`
    ).join('');

    console.style.display = 'block';
}

/**
 * Toggles error console visibility
 */
function toggleErrorConsole() {
    const console = document.getElementById('errorConsole');
    console.style.display = console.style.display === 'none' ? 'block' : 'none';
}

/**
 * Hides the error console
 */
function hideErrorConsole() {
    document.getElementById('errorConsole').style.display = 'none';
}

/**
 * Updates the send status line with progress
 * @param {number} current - Current email number being processed
 * @param {number} total - Total emails to send
 * @param {number} success - Number of successful sends
 * @param {number} failed - Number of failed sends
 */
function updateSendStatusLine(current, total, success, failed) {
    const statusLine = document.getElementById('sendStatusLine');
    statusLine.style.display = 'inline';
    statusLine.innerHTML = `${current} of ${total} sent: <span class="success-count">${success} success</span>, <span class="failed-count">${failed} failed</span>`;
}

/**
 * Hides the send status line
 */
function hideSendStatusLine() {
    document.getElementById('sendStatusLine').style.display = 'none';
}

/**
 * Updates the send button to show progress
 * @param {number} current - Current email number
 * @param {number} total - Total emails
 * @param {number} success - Successful sends
 * @param {number} failed - Failed sends
 */
function updateSendButtonProgress(current, total, success, failed) {
    const sendBtn = document.querySelector('.btn-send');
    if (!sendBtn) return;

    const curr = Math.min(Math.max(0, Number(current) || 0), Number(total) || 0);
    const tot = Math.max(0, Number(total) || 0);
    const ok = Math.max(0, Number(success) || 0);
    const bad = Math.max(0, Number(failed) || 0);

    sendBtn.innerHTML = `<i class="fas fa-mail-bulk"></i> Send to All Selected — ${curr}/${tot}, ${ok} succeed, ${bad} failed`;
}

/**
 * Enables the stop button during email sending
 */
function enableStopButton() {
    const stopBtn = document.getElementById('stopBtn');
    if (stopBtn) {
        stopBtn.disabled = false;
    }
    stopSendingRequested = false;
}

/**
 * Disables the stop button after sending completes or is stopped
 */
function disableStopButton() {
    const stopBtn = document.getElementById('stopBtn');
    if (stopBtn) {
        stopBtn.disabled = true;
    }
}

/**
 * Stops the email sending process
 */
function stopSending() {
    stopSendingRequested = true;
    setStatus('Stopping...', false);
    const stopBtn = document.getElementById('stopBtn');
    if (stopBtn) {
        stopBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Stopping...';
        stopBtn.disabled = true;
    }
}

/**
 * Resets the stop button to its original state
 */
function resetStopButton() {
    const stopBtn = document.getElementById('stopBtn');
    if (stopBtn) {
        stopBtn.innerHTML = '<i class="fas fa-stop"></i> Stop';
    }
    stopSendingRequested = false;
}

/* ============================================================================
   12. EMAIL SENDING FUNCTIONS
   ============================================================================
   Core functions for sending emails via the API.
   Supports both individual (one-by-one) and batch sending modes.
   ============================================================================ */

/**
 * Main function to send emails to selected recipients
 * Handles both individual and batch sending modes
 * Shows progress and handles errors
 */
function sendEmails() {
    const subject = document.getElementById('subject').value;
    const htmlContent = getEditorHtml();
    const selectedEmails = getSelectedEmails();
    const sendMode = document.getElementById('sendMode').value;
    const addressMode = document.getElementById('addressMode').value;
    const batchAddressMode = document.getElementById('batchAddressMode').value;
    const batchSize = parseInt(document.getElementById('batchSize').value) || 10;
    const sendDelay = parseInt(document.getElementById('sendDelay').value) || 500;

    if (!subject.trim()) { showResult('error', 'Error', 'Please enter a subject'); return; }
    if (!htmlContent.trim()) { showResult('error', 'Error', 'Please enter a message'); return; }
    if (selectedEmails.length === 0) { showResult('error', 'Error', 'Please select at least one recipient'); return; }

    if (selectedEmails.length > 1) {
        if (!confirm('Send to ' + selectedEmails.length + ' recipients?')) return;
    }

    // Reset stop flag and enable stop button
    stopSendingRequested = false;
    enableStopButton();

    const sendBtn = document.querySelector('.btn-send');
    sendBtn.disabled = true;
    sendBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Sending...';
    setStatus('Sending emails...', true);
    hideErrorConsole();
    hideSendStatusLine();

    // Initialize progress in the button.
    updateSendButtonProgress(0, selectedEmails.length, 0, 0);

    if (sendMode === 'batch') {
        console.log('Batch mode - addressMode:', batchAddressMode, 'batchSize:', batchSize);
        fetch('/api/send', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                subject, htmlContent, sendToAll: false, selectedEmails,
                sendMode: 'batch', addressMode: batchAddressMode, batchSize
            })
        })
        .then(r => {
            if (!r.ok) {
                return r.text().then(text => {
                    throw new Error(text || 'Server error: ' + r.status);
                });
            }
            return r.json();
        })
        .then(data => {
            setStatus('Ready', false);
            const total = data.totalEmails || selectedEmails.length;
            const success = data.successCount || 0;
            const failed = data.failCount || 0;

            // In batch mode server returns final counts only.
            updateSendStatusLine(total, total, success, failed);
            updateSendButtonProgress(total, total, success, failed);

            // Uncheck successful emails (all except failed)
            const failedSet = new Set(data.failedEmails || []);
            selectedEmails.forEach(email => {
                if (!failedSet.has(email)) {
                    uncheckEmail(email);
                }
            });

            // Show error console if there are failures
            if (data.failedEmails?.length) {
                const errors = data.failedEmails.map(email => ({
                    email: email,
                    message: data.errorMessages?.[email] || 'Send failed'
                }));
                showErrorConsole(errors);
            }

            enableSendButton();
            disableStopButton();
            resetStopButton();
        })
        .catch(err => {
            setStatus('Error', false);
            updateSendStatusLine(selectedEmails.length, selectedEmails.length, 0, selectedEmails.length);
            updateSendButtonProgress(selectedEmails.length, selectedEmails.length, 0, selectedEmails.length);
            showErrorConsole([{email: 'System', message: err.message || 'Unknown error'}]);
            enableSendButton();
            disableStopButton();
            resetStopButton();
        });
    } else {
        let idx = 0, ok = 0, fail = 0;
        let errors = [];
        let successEmails = [];

        function next() {
            // Check if stop was requested
            if (stopSendingRequested) {
                setStatus('Stopped by user', false);
                updateSendStatusLine(idx, selectedEmails.length, ok, fail);
                updateSendButtonProgress(idx, selectedEmails.length, ok, fail);

                // Uncheck successful emails sent so far
                successEmails.forEach(email => uncheckEmail(email));

                if (errors.length > 0) {
                    showErrorConsole(errors);
                }

                enableSendButton();
                disableStopButton();
                resetStopButton();
                showResult('partial', 'Stopped', `Sending stopped. Sent ${ok} of ${selectedEmails.length} emails.`);
                return;
            }

            if (idx >= selectedEmails.length) {
                setStatus('Ready', false);
                updateSendStatusLine(selectedEmails.length, selectedEmails.length, ok, fail);
                updateSendButtonProgress(selectedEmails.length, selectedEmails.length, ok, fail);

                // Uncheck all successful emails
                successEmails.forEach(email => uncheckEmail(email));

                // Show error console if there are failures
                if (errors.length > 0) {
                    showErrorConsole(errors);
                }

                enableSendButton();
                disableStopButton();
                resetStopButton();
                return;
            }

            const email = selectedEmails[idx];
            setStatus(`Sending ${idx + 1}/${selectedEmails.length}: ${email}`, true);
            updateSendStatusLine(idx, selectedEmails.length, ok, fail);
            updateSendButtonProgress(idx, selectedEmails.length, ok, fail);

            fetch('/api/send', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    subject, htmlContent, sendToAll: false, selectedEmails: [email],
                    sendMode: 'individual', addressMode
                })
            })
            .then(r => r.json())
            .then(d => {
                if (d.successCount > 0) {
                    ok++;
                    successEmails.push(email);
                    uncheckEmail(email); // Uncheck immediately on success
                } else {
                    fail++;
                    // Get full error message from errorMessages map
                    const errorMsg = d.errorMessages?.[email] || d.message || 'Send failed';
                    errors.push({
                        email: email,
                        message: errorMsg
                    });
                }
                idx++;
                document.getElementById('progressBar').style.width = (idx / selectedEmails.length * 100) + '%';
                updateSendStatusLine(idx, selectedEmails.length, ok, fail);
                updateSendButtonProgress(idx, selectedEmails.length, ok, fail);

                // Update error console in real-time if there are errors
                if (errors.length > 0) {
                    showErrorConsole(errors);
                }

                setTimeout(next, sendDelay);
            })
            .catch(err => {
                fail++;
                errors.push({email: email, message: err.message || 'Network error'});
                idx++;
                updateSendStatusLine(idx, selectedEmails.length, ok, fail);
                updateSendButtonProgress(idx, selectedEmails.length, ok, fail);
                showErrorConsole(errors);
                setTimeout(next, sendDelay);
            });
        }
        next();
    }
}

/**
 * Re-enables the send button after sending completes
 */
function enableSendButton() {
    const sendBtn = document.querySelector('.btn-send');
    if (sendBtn) {
        sendBtn.disabled = false;
        sendBtn.innerHTML = '<i class="fas fa-mail-bulk"></i> Send to All Selected';
    }
}

/**
 * Sends a test email to the first email address in the list
 * Shows success/error message with the target email address
 */
function sendTestEmail() {
    // Get the first email from the list
    const firstEmailCheckbox = document.querySelector('.email-checkbox');
    const testEmail = firstEmailCheckbox ? firstEmailCheckbox.value : null;

    if (!testEmail) {
        showResult('error', 'Error', 'No email addresses in the list.');
        return;
    }

    const subject = document.getElementById('subject').value || 'Test Email';
    const htmlContent = getEditorHtml() || '<p>Test email.</p>';
    setStatus('Sending test to ' + testEmail + '...', false);

    fetch('/api/send-test', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ subject: '[TEST] ' + subject, htmlContent, selectedEmails: [testEmail] })
    })
    .then(r => r.json())
    .then(d => {
        if (d.success) {
            setStatus('Email successfully sent to: ' + testEmail, false);
        } else {
            setStatus('Ready', false);
            showResult('error', 'Error', d.message);
        }
    })
    .catch(err => { setStatus('Error', false); showResult('error', 'Error', err.message); });
}

/* ============================================================================
   13. CONNECTION STATUS MONITORING
   ============================================================================
   Functions for checking and displaying mail server connection status.
   ============================================================================ */

/**
 * Pings the mail server to check connection status
 * Updates the connection status badge
 */
function pingConnection() {
    const statusEl = document.getElementById('connectionStatus');
    if (!statusEl) return;

    statusEl.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Checking...';
    statusEl.className = 'ms-2 badge bg-secondary';

    fetch('/api/test-connection')
    .then(r => r.json())
    .then(d => {
        if (d.connected) {
            statusEl.innerHTML = '<i class="fas fa-check-circle"></i> Connection is healthy';
            statusEl.className = 'ms-2 badge bg-success';
        } else {
            statusEl.innerHTML = '<i class="fas fa-exclamation-circle"></i> Connection error';
            statusEl.className = 'ms-2 badge bg-danger';
        }
    })
    .catch(err => {
        statusEl.innerHTML = '<i class="fas fa-times-circle"></i> Connection failed';
        statusEl.className = 'ms-2 badge bg-danger';
    });
}

// Start connection monitoring on load, repeat every 20 seconds
pingConnection();
setInterval(pingConnection, 20000);

/* ============================================================================
   14. MODAL DIALOG FUNCTIONS
   ============================================================================
   Functions for showing and handling modal dialogs.
   ============================================================================ */

/**
 * Shows the Add Email modal dialog
 */
function showAddEmailModal() {
    document.getElementById('newEmail').value = '';
    new bootstrap.Modal(document.getElementById('addEmailModal')).show();
}

/**
 * Adds a new email via API and reloads page
 */
function addEmail() {
    const email = document.getElementById('newEmail').value;
    if (!email || !email.includes('@')) { alert('Enter valid email'); return; }
    fetch('/api/emails', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email }) })
    .then(r => r.json())
    .then(d => { if (d.success) location.reload(); else alert(d.message); });
}

/**
 * Removes an email via API and reloads page
 * @param {string} email - Email address to remove
 */
function removeEmail(email) {
    if (!confirm('Remove ' + email + '?')) return;
    fetch('/api/emails', { method: 'DELETE', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email }) })
    .then(r => r.json())
    .then(d => { if (d.success) location.reload(); else alert(d.message); });
}

/**
 * Shows the Bulk Edit modal with current email list
 */
function showBulkEditModal() {
    fetch('/api/emails')
        .then(r => r.json())
        .then(emails => {
            document.getElementById('bulkEmails').value = emails.join('\n');
            new bootstrap.Modal(document.getElementById('bulkEditModal')).show();
        });
}

/**
 * Saves bulk edited emails via API and reloads page
 */
function saveBulkEmails() {
    const text = document.getElementById('bulkEmails').value;
    const emails = text.split(/[\n;,]/).map(e => e.trim()).filter(e => e && e.includes('@'));
    fetch('/api/emails/bulk', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ emails }) })
    .then(r => r.json())
    .then(d => { if (d.success) location.reload(); else alert(d.message); });
}

/**
 * Updates the status bar text and progress visibility
 * @param {string} text - Status text to display
 * @param {boolean} showProgress - Whether to show progress bar
 */
function setStatus(text, showProgress) {
    document.getElementById('statusText').textContent = text;
    document.getElementById('progressContainer').style.display = showProgress ? 'block' : 'none';
}

/**
 * Shows a result modal with specified type, title, and body
 * @param {string} type - Modal type ('success', 'error', 'partial')
 * @param {string} title - Modal title
 * @param {string} body - Modal body content (HTML)
 */
function showResult(type, title, body) {
    const header = document.getElementById('resultModalHeader');
    header.className = 'modal-header ' + type;
    document.getElementById('resultModalTitle').textContent = title;
    document.getElementById('resultModalBody').innerHTML = body;
    new bootstrap.Modal(document.getElementById('resultModal')).show();
}

/* ============================================================================
   15. IMAGE PASTE HELPERS
   ============================================================================
   Utility functions for handling image paste operations.
   ============================================================================ */

/**
 * Opens a file picker and inserts selected image into editor
 * @param {HTMLElement} editor - The editor element
 */
function openImagePickerAndInsert(editor) {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'image/*';

    input.addEventListener('change', () => {
        const file = input.files && input.files[0];
        if (!file) return;

        const reader = new FileReader();
        reader.onload = function(event) {
            insertImageAtCaret(editor, String(event.target.result || ''));
            enableSendButton();
        };
        reader.readAsDataURL(file);

        // Clear selection so choosing the same file twice still triggers change.
        input.value = '';
    });

    // Trigger file picker
    input.click();
}

/**
 * Inserts an image at the current caret position
 * @param {HTMLElement} editor - The editor element
 * @param {string} dataUrl - Base64 data URL of the image
 */
function insertImageAtCaret(editor, dataUrl) {
    if (!dataUrl) return;

    const img = document.createElement('img');
    img.src = dataUrl;
    // Float for multi-line wrap + keep sizing safe
    img.style.cssText = 'float:left; margin:0 15px 10px 0; max-width:40%; height:auto; display:block;';

    const sel = window.getSelection();
    if (sel && sel.rangeCount > 0) {
        const range = sel.getRangeAt(0);
        range.deleteContents();
        range.insertNode(img);

        // Add a spacer after for typing
        const textNode = document.createTextNode('\u00A0');
        range.setStartAfter(img);
        range.insertNode(textNode);
        range.setStartAfter(textNode);
        range.collapse(true);
        sel.removeAllRanges();
        sel.addRange(range);
    } else {
        editor.appendChild(img);
    }

    editor.focus();
}

/**
 * Converts a file:// URL to a data URL
 * @param {string} fileUrl - The file:// URL to convert
 * @returns {Promise<string|null>} Data URL or null if conversion fails
 */
async function fileUrlToDataUrl(fileUrl) {
    try {
        // Browser security: fetch(file://) is often blocked.
        // We try, but if blocked we return null. Use the toolbar "Insert Image" button instead.
        let url = String(fileUrl || '');
        url = url.replace(/\r|\n/g, '');

        const res = await fetch(url);
        if (!res.ok) return null;
        const blob = await res.blob();
        return await blobToDataUrl(blob);
    } catch (e) {
        return null;
    }
}

/**
 * Converts a Blob to a data URL
 * @param {Blob} blob - The blob to convert
 * @returns {Promise<string|null>} Data URL or null on error
 */
function blobToDataUrl(blob) {
    return new Promise((resolve) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result || ''));
        reader.onerror = () => resolve(null);
        reader.readAsDataURL(blob);
    });
}

/**
 * Gets the editor's HTML content
 * @returns {string} HTML content of the editor
 */
function getEditorHtml() {
    return document.getElementById('editor').innerHTML;
}

/**
 * Sets the editor's HTML content
 * @param {string} html - HTML content to set
 */
function setEditorHtml(html) {
    document.getElementById('editor').innerHTML = html;
}

/**
 * Legacy function for reducing line spacing
 * @deprecated Use setLineSpacing(1.0) instead
 */
function reduceLineSpacing() {
    setLineSpacing(1.0);
}

/* ============================================================================
   END OF FILE
   ============================================================================ */

