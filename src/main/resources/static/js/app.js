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
 * Current theme - 'light' (default) or 'dark'
 * @type {string}
 */
let currentTheme = 'light';

/**
 * Invisible symbols visibility state - tracks whether invisible symbols are shown
 * @type {boolean}
 */
let showInvisibleSymbols = false;

/* ============================================================================
   2. DOM READY INITIALIZATION
   ============================================================================
   Main initialization when DOM is fully loaded.
   Sets up event listeners for toolbar, editor, and form elements.
   ============================================================================ */

document.addEventListener('DOMContentLoaded', function() {
    const editor = document.getElementById('editor');

    // -----------------------------------------
    // Initialize Column Resizer
    // -----------------------------------------
    initColumnResizer();

    // -----------------------------------------
    // Set Default Editor Text Color from Server Config
    // -----------------------------------------
    const defaultTextColor = (window.editorConfig && window.editorConfig.defaultTextColor) || '#000000';
    if (editor) {
        // Set the editor's default text color via CSS
        editor.style.color = defaultTextColor;

        // Set initial content with the correct color
        if (editor.innerHTML.trim() === '' || editor.innerHTML === '<p>Type your email here...</p>') {
            editor.innerHTML = '<p style="color:' + defaultTextColor + ';">Type your email here...</p>';
        }

        // When user starts typing, ensure new text uses the default color
        editor.addEventListener('focus', function() {
            // Select all and set color only if empty or default text
            if (this.innerText.trim() === '' || this.innerText.trim() === 'Type your email here...') {
                document.execCommand('selectAll', false, null);
                document.execCommand('foreColor', false, defaultTextColor);
                // Collapse selection to end
                const sel = window.getSelection();
                if (sel.rangeCount > 0) {
                    sel.collapseToEnd();
                }
            }
        }, { once: true });
    }

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
                    insertImageAtCaret(editor, String(event.target.result || ''), maybeImage.name || 'pasted-from-clipboard', null);
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
                insertImageAtCaret(editor, String(event.target.result || ''), blob.name || 'pasted-image', null);
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
                    if (dataUrl) {
                        // Extract filename from path
                        const fileName = src.split(/[/\\]/).pop() || 'pasted-from-file';
                        // Store filepath for logging
                        const filePath = src.startsWith('file:') ? src.replace(/^file:\/\/\//, '') : src;
                        converted.push({ dataUrl, fileName, filePath });
                    }
                }

                if (converted.length === 0) return;

                e.preventDefault();
                for (const item of converted) {
                    insertImageAtCaret(editor, item.dataUrl, item.fileName, item.filePath || null);
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
                const fileName = t.split(/[/\\]/).pop() || 'pasted-path';

                const dataUrl = await fileUrlToDataUrl(src);
                if (!dataUrl) return;

                e.preventDefault();
                insertImageAtCaret(editor, dataUrl, fileName, t); // Use original path as filepath
                enableSendButton();
            });
        }
    });

    // -----------------------------------------
    // Enable send button on content change
    // -----------------------------------------
    editor.addEventListener('input', function() {
        enableSendButton();
        // Initialize table resizing for any newly added tables
        initTableResizing();
    });
    document.getElementById('subject').addEventListener('input', enableSendButton);

    // -----------------------------------------
    // Selection Change Handler - Update Toolbar State (like MS Word)
    // -----------------------------------------
    document.addEventListener('selectionchange', function() {
        const sel = window.getSelection();
        if (sel && sel.rangeCount > 0) {
            const range = sel.getRangeAt(0);
            // Only update if selection is within the editor
            if (editor.contains(range.commonAncestorContainer)) {
                updateToolbarState();
            }
        }
    });

    // Also update on mouseup and keyup within editor
    editor.addEventListener('mouseup', updateToolbarState);
    editor.addEventListener('keyup', updateToolbarState);

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

    // Update template button labels (don’t break the app if template helpers aren’t available)
    try {
        if (typeof updateLoadButtonLabels === 'function') {
            updateLoadButtonLabels();
        } else {
            console.warn('[Template] updateLoadButtonLabels() not available (template buttons may show default numbers)');
        }
    } catch (e) {
        console.warn('[Template] updateLoadButtonLabels() failed:', e);
    }

    loadThemePreference();     // Load saved theme preference
    loadInvisiblesPreference(); // Load saved invisible symbols preference
    initTablePicker();         // Initialize table size picker grid

    // Bind template buttons (avoid relying on inline onclick/global scope)
    try {
        document.querySelectorAll('.js-template-save[data-template-slot]').forEach((btn) => {
            btn.addEventListener('click', () => {
                const slot = Number(btn.getAttribute('data-template-slot'));
                saveTemplate(slot);
            });
        });

        document.querySelectorAll('.js-template-load[data-template-slot]').forEach((btn) => {
            btn.addEventListener('click', () => {
                const slot = Number(btn.getAttribute('data-template-slot'));
                loadTemplate(slot);
            });
        });
    } catch (e) {
        console.warn('[Template] Failed to bind template button handlers:', e);
    }
});

/* ============================================================================
   2.1 TOOLBAR STATE UPDATE (like MS Word)
   ============================================================================
   Updates toolbar buttons/dropdowns to reflect current selection formatting.
   ============================================================================ */

/**
 * Updates toolbar state to reflect the formatting of the current selection.
 * This mimics MS Word behavior where toolbar shows current text properties.
 */
function updateToolbarState() {
    // Update Bold button
    const boldBtn = document.querySelector('[data-cmd="bold"]');
    if (boldBtn) {
        if (document.queryCommandState('bold')) {
            boldBtn.classList.add('active');
        } else {
            boldBtn.classList.remove('active');
        }
    }

    // Update Italic button
    const italicBtn = document.querySelector('[data-cmd="italic"]');
    if (italicBtn) {
        if (document.queryCommandState('italic')) {
            italicBtn.classList.add('active');
        } else {
            italicBtn.classList.remove('active');
        }
    }

    // Update Underline button
    const underlineBtn = document.querySelector('[data-cmd="underline"]');
    if (underlineBtn) {
        if (document.queryCommandState('underline')) {
            underlineBtn.classList.add('active');
        } else {
            underlineBtn.classList.remove('active');
        }
    }

    // Update Strikethrough button
    const strikeBtn = document.querySelector('[data-cmd="strikeThrough"]');
    if (strikeBtn) {
        if (document.queryCommandState('strikeThrough')) {
            strikeBtn.classList.add('active');
        } else {
            strikeBtn.classList.remove('active');
        }
    }

    // Update Subscript button
    const subBtn = document.querySelector('[data-cmd="subscript"]');
    if (subBtn) {
        if (document.queryCommandState('subscript')) {
            subBtn.classList.add('active');
        } else {
            subBtn.classList.remove('active');
        }
    }

    // Update Superscript button
    const supBtn = document.querySelector('[data-cmd="superscript"]');
    if (supBtn) {
        if (document.queryCommandState('superscript')) {
            supBtn.classList.add('active');
        } else {
            supBtn.classList.remove('active');
        }
    }

    // Update alignment buttons
    const alignLeftBtn = document.querySelector('[data-cmd="justifyLeft"]');
    const alignCenterBtn = document.querySelector('[data-cmd="justifyCenter"]');
    const alignRightBtn = document.querySelector('[data-cmd="justifyRight"]');
    const alignFullBtn = document.querySelector('[data-cmd="justifyFull"]');

    if (alignLeftBtn) alignLeftBtn.classList.toggle('active', document.queryCommandState('justifyLeft'));
    if (alignCenterBtn) alignCenterBtn.classList.toggle('active', document.queryCommandState('justifyCenter'));
    if (alignRightBtn) alignRightBtn.classList.toggle('active', document.queryCommandState('justifyRight'));
    if (alignFullBtn) alignFullBtn.classList.toggle('active', document.queryCommandState('justifyFull'));

    // Update list buttons
    const ulBtn = document.querySelector('[data-cmd="insertUnorderedList"]');
    const olBtn = document.querySelector('[data-cmd="insertOrderedList"]');
    if (ulBtn) ulBtn.classList.toggle('active', document.queryCommandState('insertUnorderedList'));
    if (olBtn) olBtn.classList.toggle('active', document.queryCommandState('insertOrderedList'));

    // Update Font Size dropdown
    const fontSizeSelect = document.getElementById('fontSize');
    if (fontSizeSelect) {
        const fontSize = document.queryCommandValue('fontSize');
        if (fontSize && fontSize !== '') {
            fontSizeSelect.value = fontSize;
        }
    }

    // Update Font Family dropdown
    const fontFamilySelect = document.getElementById('fontFamily');
    if (fontFamilySelect) {
        let fontName = document.queryCommandValue('fontName');
        if (fontName) {
            // Remove quotes if present
            fontName = fontName.replace(/['"]/g, '');
            // Try to match with available options
            const options = Array.from(fontFamilySelect.options);
            const match = options.find(opt =>
                opt.value.toLowerCase() === fontName.toLowerCase() ||
                fontName.toLowerCase().includes(opt.value.toLowerCase())
            );
            if (match) {
                fontFamilySelect.value = match.value;
            }
        }
    }

    // Update Font Color indicator
    const fontColorBar = document.getElementById('fontColorBar');
    if (fontColorBar) {
        const color = document.queryCommandValue('foreColor');
        if (color) {
            fontColorBar.style.background = color;
        }
    }

    // Update Highlight Color indicator
    const hiliteColorBar = document.getElementById('hiliteColorBar');
    if (hiliteColorBar) {
        const color = document.queryCommandValue('hiliteColor');
        if (color && color !== 'transparent' && color !== 'rgba(0, 0, 0, 0)') {
            hiliteColorBar.style.background = color;
        }
    }
}

/* ============================================================================
   2.2 THEME TOGGLE FUNCTIONS
   ============================================================================
   Functions for switching between dark and light themes.
   ============================================================================ */

/**
 * Toggles between light and dark themes
 * Light theme is default. Dark theme adds 'theme-dark' class to body.
 */
function toggleTheme() {
    const body = document.body;
    const themeIcon = document.getElementById('themeIcon');

    if (currentTheme === 'light') {
        // Switch to dark theme
        body.classList.add('theme-dark');
        currentTheme = 'dark';
        if (themeIcon) {
            themeIcon.classList.remove('fa-moon');
            themeIcon.classList.add('fa-sun');
        }
    } else {
        // Switch to light theme
        body.classList.remove('theme-dark');
        currentTheme = 'light';
        if (themeIcon) {
            themeIcon.classList.remove('fa-sun');
            themeIcon.classList.add('fa-moon');
        }
    }

    // Save preference
    localStorage.setItem('editorTheme', currentTheme);
}

/**
 * Loads saved theme preference from localStorage
 * Light is default, so only switch if 'dark' was saved
 */
function loadThemePreference() {
    const savedTheme = localStorage.getItem('editorTheme');
    if (savedTheme === 'dark') {
        currentTheme = 'light'; // Set to opposite so toggle switches correctly
        toggleTheme();
    } else {
        // Ensure icon is correct for light theme (moon icon to switch to dark)
        const themeIcon = document.getElementById('themeIcon');
        if (themeIcon) {
            themeIcon.classList.remove('fa-sun');
            themeIcon.classList.add('fa-moon');
        }
    }
}

/* ============================================================================
   2.3 INVISIBLE SYMBOLS TOGGLE
   ============================================================================
   Functions for showing/hiding invisible symbols in the editor.
   Shows: paragraph markers, line breaks, table borders, image frames, etc.
   ============================================================================ */

/**
 * Toggles visibility of invisible symbols in the editor.
 * When enabled, shows:
 * - Paragraph markers (¶) at end of paragraphs/divs
 * - Line break markers (↵) after <br> tags
 * - Dashed borders around tables, images, links
 * - Highlighted horizontal rules
 */
function toggleInvisibleSymbols() {
    const editor = document.getElementById('editor');
    const editorContainer = editor?.closest('.editor-container') || document.querySelector('.editor-container');
    const btn = document.getElementById('toggleInvisiblesBtn');
    const icon = document.getElementById('invisiblesIcon');

    if (!editorContainer) {
        console.error('[InvisibleSymbols] Editor container not found');
        return;
    }

    showInvisibleSymbols = !showInvisibleSymbols;
    console.log('[InvisibleSymbols] Toggled to:', showInvisibleSymbols);

    if (showInvisibleSymbols) {
        // Enable invisible symbols display
        editorContainer.classList.add('show-invisibles');
        if (btn) btn.classList.add('active');
        if (icon) {
            icon.classList.remove('fa-paragraph');
            icon.classList.add('fa-eye');
        }
        console.log('[InvisibleSymbols] Showing invisible symbols (¶ ↵ borders)');
    } else {
        // Disable invisible symbols display
        editorContainer.classList.remove('show-invisibles');
        if (btn) btn.classList.remove('active');
        if (icon) {
            icon.classList.remove('fa-eye');
            icon.classList.add('fa-paragraph');
        }
        console.log('[InvisibleSymbols] Hiding invisible symbols');
    }

    // Save preference
    localStorage.setItem('showInvisibles', showInvisibleSymbols ? 'true' : 'false');
}

/**
 * Loads saved invisible symbols preference from localStorage
 */
function loadInvisiblesPreference() {
    const savedPref = localStorage.getItem('showInvisibles');
    if (savedPref === 'true') {
        showInvisibleSymbols = false; // Set to opposite so toggle switches correctly
        toggleInvisibleSymbols();
    }
}

/* ============================================================================
   2.4 TABLE PICKER AND TABLE MANIPULATION
   ============================================================================
   Functions for creating tables with a visual picker grid and
   manipulating table rows/columns.
   ============================================================================ */

/**
 * Initializes the table picker grid (6x6 grid for table size selection)
 */
function initTablePicker() {
    const picker = document.getElementById('tablePicker');
    const sizeLabel = document.getElementById('tablePickerSize');

    if (!picker) {
        console.warn('[TablePicker] Table picker element not found');
        return;
    }

    // Save selection when dropdown button is clicked (before focus is lost)
    const dropdownBtn = picker.closest('.dropdown-menu')?.previousElementSibling;
    if (dropdownBtn) {
        dropdownBtn.addEventListener('mousedown', function() {
            saveEditorSelection();
        });
    }

    // Create 6x6 grid of cells
    picker.innerHTML = '';
    for (let row = 1; row <= 6; row++) {
        for (let col = 1; col <= 6; col++) {
            const cell = document.createElement('div');
            cell.className = 'table-picker-cell';
            cell.dataset.row = row;
            cell.dataset.col = col;

            // Mouseover: highlight cells up to current position
            cell.addEventListener('mouseover', function() {
                const targetRow = parseInt(this.dataset.row);
                const targetCol = parseInt(this.dataset.col);
                highlightTablePickerCells(picker, targetRow, targetCol);
                if (sizeLabel) {
                    sizeLabel.textContent = `${targetCol} × ${targetRow}`;
                }
            });

            // Click: insert table with selected size
            cell.addEventListener('click', function() {
                const rows = parseInt(this.dataset.row);
                const cols = parseInt(this.dataset.col);

                // Restore saved selection before inserting
                restoreEditorSelection();

                insertTable(rows, cols);
                // Close dropdown
                const dropdown = picker.closest('.dropdown-menu');
                if (dropdown) {
                    const bsDropdown = bootstrap.Dropdown.getInstance(dropdown.previousElementSibling);
                    if (bsDropdown) bsDropdown.hide();
                }
            });

            picker.appendChild(cell);
        }
    }

    // Reset on mouse leave
    picker.addEventListener('mouseleave', function() {
        highlightTablePickerCells(picker, 0, 0);
        if (sizeLabel) {
            sizeLabel.textContent = '0 × 0';
        }
    });

    console.log('[TablePicker] Initialized 6x6 table picker grid');
}

// Saved selection for restoring after dropdown interactions
let savedEditorSelection = null;

/**
 * Saves the current editor selection for later restoration
 */
function saveEditorSelection() {
    const editor = document.getElementById('editor');
    const sel = window.getSelection();

    if (sel && sel.rangeCount > 0) {
        const range = sel.getRangeAt(0);
        if (editor && editor.contains(range.commonAncestorContainer)) {
            savedEditorSelection = range.cloneRange();
            console.log('[Selection] Saved editor selection');
        }
    }
}

/**
 * Restores a previously saved editor selection
 */
function restoreEditorSelection() {
    const editor = document.getElementById('editor');

    if (savedEditorSelection && editor) {
        editor.focus();
        const sel = window.getSelection();
        sel.removeAllRanges();
        sel.addRange(savedEditorSelection);
        console.log('[Selection] Restored editor selection');
    } else if (editor) {
        // No saved selection, just focus editor
        editor.focus();
    }
}

/**
 * Highlights cells in the table picker up to the specified row/col
 * @param {HTMLElement} picker - The picker container
 * @param {number} maxRow - Maximum row to highlight
 * @param {number} maxCol - Maximum column to highlight
 */
function highlightTablePickerCells(picker, maxRow, maxCol) {
    const cells = picker.querySelectorAll('.table-picker-cell');
    cells.forEach(cell => {
        const row = parseInt(cell.dataset.row);
        const col = parseInt(cell.dataset.col);
        if (row <= maxRow && col <= maxCol) {
            cell.classList.add('highlight');
        } else {
            cell.classList.remove('highlight');
        }
    });
}

/**
 * Inserts a table with the specified dimensions at the cursor position
 * @param {number} rows - Number of rows
 * @param {number} cols - Number of columns
 */
function insertTable(rows, cols) {
    const editor = document.getElementById('editor');
    if (!editor) return;

    // Determine the text color based on current theme
    const isDarkTheme = document.body.classList.contains('theme-dark');
    const textColor = isDarkTheme ? '#ffffff' : '#000000';

    // Calculate initial table width based on columns (approx 100px per column, min 200px)
    const initialWidth = Math.max(200, cols * 100);

    // Build table HTML with resizable width (not 100%)
    let tableHtml = `<table class="editor-table" style="width: ${initialWidth}px; border-collapse: collapse; margin: 10px 0; table-layout: fixed;">`;
    for (let r = 0; r < rows; r++) {
        tableHtml += '<tr>';
        for (let c = 0; c < cols; c++) {
            tableHtml += `<td style="border: 1px solid #ccc; padding: 8px; min-width: 50px; color: ${textColor};" contenteditable="true">&nbsp;</td>`;
        }
        tableHtml += '</tr>';
    }
    tableHtml += '</table><p><br></p>';

    // Ensure editor has focus and get current selection
    editor.focus();

    const sel = window.getSelection();
    if (sel && sel.rangeCount > 0) {
        // Insert at current cursor position
        const range = sel.getRangeAt(0);

        // Make sure we're inside the editor
        if (editor.contains(range.commonAncestorContainer)) {
            // Use insertHTML to insert at cursor
            document.execCommand('insertHTML', false, tableHtml);
        } else {
            // Cursor not in editor, append at end
            editor.focus();
            document.execCommand('insertHTML', false, tableHtml);
        }
    } else {
        // No selection, try to insert at cursor or append
        document.execCommand('insertHTML', false, tableHtml);
    }

    console.log(`[Table] Inserted ${rows}x${cols} table at cursor position (initial width: ${initialWidth}px)`);

    // Initialize table resizing for the new table
    initTableResizing();
}

/**
 * Gets the currently selected table cell or the table containing the selection
 * @returns {{table: HTMLTableElement|null, cell: HTMLTableCellElement|null, row: HTMLTableRowElement|null}}
 */
function getSelectedTableContext() {
    const selection = window.getSelection();
    if (!selection.rangeCount) return { table: null, cell: null, row: null };

    let node = selection.anchorNode;
    if (node.nodeType === Node.TEXT_NODE) {
        node = node.parentNode;
    }

    const cell = node.closest('td, th');
    const row = node.closest('tr');
    const table = node.closest('table');

    return { table, cell, row };
}

/**
 * Inserts a table row above or below the current row
 * @param {string} position - 'above' or 'below'
 */
function insertTableRow(position) {
    const { table, row } = getSelectedTableContext();
    if (!table || !row) {
        console.warn('[Table] No table row selected');
        return;
    }

    const colCount = row.cells.length;
    const newRow = table.insertRow(position === 'above' ? row.rowIndex : row.rowIndex + 1);

    for (let i = 0; i < colCount; i++) {
        const cell = newRow.insertCell(i);
        cell.className = 'table-cell-default';
        cell.contentEditable = 'true';
        cell.innerHTML = '&nbsp;';
    }

    console.log(`[Table] Inserted row ${position}`);
}

/**
 * Inserts a table column to the left or right of the current column
 * @param {string} position - 'left' or 'right'
 */
function insertTableColumn(position) {
    const { table, cell } = getSelectedTableContext();
    if (!table || !cell) {
        console.warn('[Table] No table cell selected');
        return;
    }

    const colIndex = cell.cellIndex;
    const insertIndex = position === 'left' ? colIndex : colIndex + 1;

    for (let i = 0; i < table.rows.length; i++) {
        const row = table.rows[i];
        const newCell = row.insertCell(insertIndex);
        newCell.className = 'table-cell-default';
        newCell.contentEditable = 'true';
        newCell.innerHTML = '&nbsp;';
    }

    console.log(`[Table] Inserted column ${position}`);
}

/**
 * Deletes the currently selected table row
 */
function deleteTableRow() {
    const { table, row } = getSelectedTableContext();
    if (!table || !row) {
        console.warn('[Table] No table row selected');
        return;
    }

    if (table.rows.length <= 1) {
        // If only one row left, delete the whole table
        table.remove();
        console.log('[Table] Deleted last row and table');
    } else {
        row.remove();
        console.log('[Table] Deleted row');
    }
}

/**
 * Deletes the currently selected table column
 */
function deleteTableColumn() {
    const { table, cell } = getSelectedTableContext();
    if (!table || !cell) {
        console.warn('[Table] No table cell selected');
        return;
    }

    const colIndex = cell.cellIndex;

    // Check if this is the last column
    if (table.rows[0].cells.length <= 1) {
        // Delete the whole table
        table.remove();
        console.log('[Table] Deleted last column and table');
    } else {
        // Delete the column from each row
        for (let i = 0; i < table.rows.length; i++) {
            if (table.rows[i].cells.length > colIndex) {
                table.rows[i].deleteCell(colIndex);
            }
        }
        console.log('[Table] Deleted column');
    }
}

/**
 * Initializes resizable borders for tables in the editor.
 * Handles nested tables by processing each table independently.
 * Call this after loading content or inserting tables.
 */
function initTableResizing() {
    const editor = document.getElementById('editor');
    if (!editor) return;

    // Get ALL tables including nested ones - process from innermost to outermost
    const allTables = Array.from(editor.querySelectorAll('table'));

    // Sort tables by depth (deepest first) so nested tables get processed first
    allTables.sort((a, b) => {
        const depthA = getTableDepth(a);
        const depthB = getTableDepth(b);
        return depthB - depthA; // Deepest first
    });

    let initializedCount = 0;

    allTables.forEach(table => {
        // Skip if already initialized
        if (table.dataset.resizeInit === 'true') return;
        table.dataset.resizeInit = 'true';
        initializedCount++;

        // Get direct rows of this table (not from nested tables)
        const directRows = getDirectRows(table);

        // Process each direct row
        directRows.forEach(row => {
            // Get direct cells of this row
            const directCells = Array.from(row.cells);

            directCells.forEach(cell => {
                // Skip if cell already has resize handle
                if (cell.querySelector('.table-resize-col')) return;

                cell.style.position = 'relative';

                // Create right edge resize handle for column width
                const colHandle = document.createElement('div');
                colHandle.className = 'table-col-handle';
                colHandle.style.right = '-3px';
                colHandle.style.top = '0';
                colHandle.style.bottom = '0';
                cell.appendChild(colHandle);

                // Column resize logic
                let startX, startWidth;
                colHandle.addEventListener('mousedown', function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    startX = e.pageX;
                    startWidth = cell.offsetWidth;

                    const onMouseMove = function(e) {
                        const diff = e.pageX - startX;
                        const newWidth = Math.max(30, startWidth + diff);
                        cell.style.width = newWidth + 'px';
                        cell.style.minWidth = newWidth + 'px';
                    };

                    const onMouseUp = function() {
                        document.removeEventListener('mousemove', onMouseMove);
                        document.removeEventListener('mouseup', onMouseUp);
                    };

                    document.addEventListener('mousemove', onMouseMove);
                    document.addEventListener('mouseup', onMouseUp);
                });
            });

            // Add row height resize handle to first cell of each row
            const firstCell = row.cells[0];
            if (firstCell && !firstCell.querySelector('.table-resize-row')) {
                const rowHandle = document.createElement('div');
                rowHandle.className = 'table-row-handle table-resize-row';
                rowHandle.style.left = '0';
                rowHandle.style.right = '0';
                rowHandle.style.bottom = '-3px';
                firstCell.appendChild(rowHandle);

                // Row resize logic
                let startY, startHeight;
                rowHandle.addEventListener('mousedown', function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    startY = e.pageY;
                    startHeight = row.offsetHeight;

                    const onMouseMove = function(e) {
                        const diff = e.pageY - startY;
                        const newHeight = Math.max(20, startHeight + diff);
                        row.style.height = newHeight + 'px';
                    };

                    const onMouseUp = function() {
                        document.removeEventListener('mousemove', onMouseMove);
                        document.removeEventListener('mouseup', onMouseUp);
                    };

                    document.addEventListener('mousemove', onMouseMove);
                    document.addEventListener('mouseup', onMouseUp);
                });
            }
        });

        // Add table-wide resize handle at bottom-right corner
        if (!table.querySelector('.table-resize-corner')) {
            table.style.position = 'relative';
            const tableHandle = document.createElement('div');
            tableHandle.className = 'table-resize-handle table-resize-corner';
            tableHandle.style.right = '-6px';
            tableHandle.style.bottom = '-6px';
            table.appendChild(tableHandle);

            // Table resize logic (resize whole table)
            let tableStartX, tableStartWidth;
            tableHandle.addEventListener('mousedown', function(e) {
                e.preventDefault();
                e.stopPropagation();
                tableStartX = e.pageX;
                tableStartWidth = table.offsetWidth;

                const onMouseMove = function(e) {
                    const diffX = e.pageX - tableStartX;
                    const newWidth = Math.max(100, tableStartWidth + diffX);
                    table.style.width = newWidth + 'px';
                };

                const onMouseUp = function() {
                    document.removeEventListener('mousemove', onMouseMove);
                    document.removeEventListener('mouseup', onMouseUp);
                    console.log(`[Table] Resized table to ${table.offsetWidth}px width`);
                };

                document.addEventListener('mousemove', onMouseMove);
                document.addEventListener('mouseup', onMouseUp);
            });
        }
    });

    if (initializedCount > 0) {
        console.log(`[Table] Initialized resize handles for ${initializedCount} table(s)`);
    }
}

/**
 * Gets the nesting depth of a table (how many parent tables it has)
 */
function getTableDepth(table) {
    let depth = 0;
    let parent = table.parentElement;
    while (parent) {
        if (parent.tagName === 'TABLE') depth++;
        parent = parent.parentElement;
    }
    return depth;
}

/**
 * Gets direct rows of a table (not rows from nested tables)
 */
function getDirectRows(table) {
    const rows = [];
    // Check for tbody, thead, tfoot
    for (const child of table.children) {
        if (child.tagName === 'TBODY' || child.tagName === 'THEAD' || child.tagName === 'TFOOT') {
            for (const row of child.children) {
                if (row.tagName === 'TR') rows.push(row);
            }
        } else if (child.tagName === 'TR') {
            rows.push(child);
        }
    }
    return rows;
}

/**
 * Toggles table border visibility
 * Uses data attribute to track state and falls back to detecting actual border style
 * @param {HTMLTableElement} table - The table to toggle borders on
 */
function toggleTableBorders(table) {
    if (!table) return;

    const cells = table.querySelectorAll('td, th');
    if (cells.length === 0) return;

    // Use data attribute to track border state (more reliable than computed styles)
    // If not set, detect actual border state from first cell
    let currentState = table.getAttribute('data-borders-visible');
    let hasBorder;

    if (currentState === null) {
        // Attribute not set - detect actual border state from first cell
        const firstCell = cells[0];
        const computedStyle = window.getComputedStyle(firstCell);
        const borderWidth = computedStyle.borderWidth || computedStyle.borderTopWidth;
        // If border width is 0px or 'none', there are no borders
        hasBorder = borderWidth && borderWidth !== '0px' && borderWidth !== '0';
        console.log('[Table] Detected border state from computed style:', hasBorder, 'borderWidth:', borderWidth);
    } else {
        hasBorder = currentState !== 'false';
    }

    cells.forEach(cell => {
        if (hasBorder) {
            // Hide borders - remove all border styling
            cell.style.border = 'none';
            cell.style.borderWidth = '0';
            cell.style.borderStyle = 'none';
            cell.style.borderColor = '';
        } else {
            // Show borders - explicitly set all border properties
            cell.style.border = '1px solid #ccc';
            cell.style.borderWidth = '1px';
            cell.style.borderStyle = 'solid';
            cell.style.borderColor = '#ccc';
        }
    });

    // Also toggle the table border attribute for older formats
    if (hasBorder) {
        table.removeAttribute('border');
        table.style.border = 'none';
        table.style.borderCollapse = 'collapse';
        table.setAttribute('data-borders-visible', 'false');
    } else {
        table.setAttribute('border', '1');
        table.style.border = '1px solid #ccc';
        table.style.borderCollapse = 'collapse';
        table.setAttribute('data-borders-visible', 'true');
    }
    console.log('[Table] Toggled borders:', hasBorder ? 'hidden' : 'shown');
}

/**
 * Toggles border visibility for the currently selected table
 * Searches for table in selection or nearest table in editor
 */
function toggleSelectedTableBorders() {
    let { table } = getSelectedTableContext();
    // If no table from selection, try to find any table in editor that contains cursor
    if (!table) {
        const editor = document.getElementById('editor');
        if (editor) {
            // Check if there's only one table - use it
            const tables = editor.querySelectorAll('table');
            if (tables.length === 1) {
                table = tables[0];
            } else if (tables.length > 1) {
                // Show message that user needs to click inside a table
                console.warn('[Table] Multiple tables found, please click inside the table to toggle borders');
                showInfoModal('Toggle Borders', 'Please click inside a table cell first, then click the border toggle button.');
                return;
            }
        }
    }
    if (!table) {
        console.warn('[Table] No table selected for border toggle');
        showInfoModal('Toggle Borders', 'No table found. Please create a table or click inside an existing table.');
        return;
    }
    toggleTableBorders(table);
    console.log('[Table] Toggled borders for selected table');
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
    updateToolbarState(); // Update toolbar to reflect change
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
        // Use an inline wrapper to avoid introducing block-level layout differences
        // between the browser editor and Outlook (Word engine).
        const wrapper = document.createElement('span');
        wrapper.style.lineHeight = value.toString();
        try {
            range.surroundContents(wrapper);
        } catch (e) {
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
   All images are resizable regardless of their position or float status.
   ============================================================================ */

/**
 * Inserts an image at the current cursor position with template tracking
 * @param {HTMLElement} editor - The editor element
 * @param {string} dataUrl - The image data URL
 * @param {string} filename - The filename of the image
 * @param {string|null} filepath - The full path to the image file (null for pasted/embedded)
 */
function insertImageAtCaret(editor, dataUrl, filename, filepath) {
    const img = document.createElement('img');
    img.src = dataUrl;
    img.setAttribute('data-original-filename', filename || 'unknown');
    if (filepath) {
        img.setAttribute('data-original-filepath', filepath);
        console.log(`[Image] Inserted with path: ${filepath}`);
    } else {
        console.log(`[Image] Inserted embedded/pasted: ${filename}`);
    }
    img.style.maxWidth = '100%';
    img.style.height = 'auto';

    editor.focus();
    const sel = window.getSelection();
    if (sel && sel.rangeCount > 0) {
        const range = sel.getRangeAt(0);
        range.deleteContents();
        range.insertNode(img);
        range.setStartAfter(img);
        range.collapse(true);
        sel.removeAllRanges();
        sel.addRange(range);
    } else {
        editor.appendChild(img);
    }
}

/**
 * Opens file picker for image insertion with filepath tracking
 * @param {HTMLElement} editor - The editor element
 */
function openImagePickerAndInsert(editor) {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'image/*';
    input.onchange = function(e) {
        const file = e.target.files[0];
        if (file) {
            const reader = new FileReader();
            reader.onload = function(event) {
                // Extract full path if available (Note: modern browsers restrict this for security)
                const filepath = file.path || null; // file.path is available in Electron, null in browsers
                insertImageAtCaret(editor, String(event.target.result), file.name, filepath);
                enableSendButton();
            };
            reader.readAsDataURL(file);
        }
    };
    input.click();
}

/** Currently selected image element */
let selectedImg = null;

/** Resize handle element for selected image */
let resizeHandle = null;

/** Flag indicating if resize operation is in progress */
let isResizing = false;

/** Starting coordinates for resize operation */
let startX, startY, startWidth, startHeight;

/** Original aspect ratio for proportional resizing */
let originalAspectRatio = 1;

/**
 * Selects an image for editing (resize/delete)
 * Works with ALL images regardless of float status.
 * @param {HTMLImageElement} img - The image element to select
 */
function selectImage(img) {
    if (selectedImg) selectedImg.classList.remove('selected');
    if (resizeHandle) resizeHandle.remove();

    selectedImg = img;
    img.classList.add('selected');

    // Store original aspect ratio for proportional resizing
    // Use naturalWidth/naturalHeight if available, otherwise use current dimensions
    if (img.naturalWidth && img.naturalHeight) {
        originalAspectRatio = img.naturalHeight / img.naturalWidth;
    } else {
        originalAspectRatio = img.offsetHeight / img.offsetWidth;
    }

    // Ensure the image has explicit width style for resizing to work
    // This is critical for images that don't have float:left
    if (!img.style.width || !img.style.width.includes('px')) {
        const currentWidth = img.offsetWidth;
        img.style.width = currentWidth + 'px';
        img.style.height = 'auto';
    }

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
 * Performs the resize operation as mouse moves.
 * Maintains aspect ratio using height:auto for proportional scaling.
 * Always sets explicit width in pixels for email compatibility.
 * @param {MouseEvent} e - The mousemove event
 */
function doResize(e) {
    if (!isResizing || !selectedImg) return;

    // Calculate new width based on mouse movement (minimum 10px for small icons/emojis)
    const newWidth = Math.max(10, startWidth + (e.clientX - startX));

    // Set explicit width in pixels - this is what gets preserved in the email
    selectedImg.style.width = newWidth + 'px';

    // Always use height:auto for proportional scaling
    // This ensures the image maintains its aspect ratio
    selectedImg.style.height = 'auto';

    // Remove any max-width that might interfere with resizing
    if (selectedImg.style.maxWidth && selectedImg.style.maxWidth.includes('%')) {
        selectedImg.style.maxWidth = '';
    }

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
 * Applies float-left styling to the last image in the editor.
 * Sets explicit pixel width for email compatibility.
 */
function floatImageLeft() {
    const editor = document.getElementById('editor');
    const images = editor.querySelectorAll('img');
    if (images.length > 0) {
        const img = images[images.length - 1];
        // Calculate 40% of editor width as initial size
        const editorWidth = editor.offsetWidth;
        const targetWidth = Math.round(editorWidth * 0.4);
        // Set explicit pixel width for email compatibility
        img.style.cssText = 'float:left; margin:0 15px 10px 0; width:' + targetWidth + 'px; height:auto;';
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
   8.1 IMAGE DRAG AND DROP
   ============================================================================
   Allows dragging images to reposition them within the editor text.
   ============================================================================ */

/** Flag indicating if drag operation is in progress */
let isDragging = false;

/** The image being dragged */
let draggedImg = null;

/** Drop indicator element */
let dropIndicator = null;

/**
 * Starts dragging an image
 * @param {MouseEvent} e - The mousedown event
 */
function startDrag(e) {
    // Don't start drag if clicking on resize handle
    if (e.target.classList.contains('resize-handle')) return;
    if (!selectedImg) return;

    // Only start drag on left mouse button
    if (e.button !== 0) return;

    e.preventDefault();
    isDragging = true;
    draggedImg = selectedImg;
    draggedImg.classList.add('dragging');

    // Create drop indicator
    dropIndicator = document.createElement('span');
    dropIndicator.className = 'drop-indicator';
    dropIndicator.innerHTML = '|';

    document.addEventListener('mousemove', doDrag);
    document.addEventListener('mouseup', stopDrag);
}

/**
 * Handles drag movement - shows drop indicator at cursor position
 * @param {MouseEvent} e - The mousemove event
 */
function doDrag(e) {
    if (!isDragging || !draggedImg) return;

    const editor = document.getElementById('editor');

    // Get the position in the document where we'd drop
    const range = document.caretRangeFromPoint(e.clientX, e.clientY);

    if (range && editor.contains(range.startContainer)) {
        // Remove existing indicator
        if (dropIndicator.parentNode) {
            dropIndicator.remove();
        }

        // Insert indicator at caret position
        range.insertNode(dropIndicator);
    }
}

/**
 * Ends the drag operation and moves the image
 */
function stopDrag(e) {
    if (!isDragging || !draggedImg) {
        isDragging = false;
        return;
    }

    document.removeEventListener('mousemove', doDrag);
    document.removeEventListener('mouseup', stopDrag);

    const editor = document.getElementById('editor');

    // Get drop position from indicator
    if (dropIndicator && dropIndicator.parentNode) {
        // Store image properties before moving
        const imgWidth = draggedImg.style.width;
        const origWidth = draggedImg.getAttribute('data-original-width');
        const origHeight = draggedImg.getAttribute('data-original-height');

        // Insert image at drop indicator position
        dropIndicator.parentNode.insertBefore(draggedImg, dropIndicator);

        // Pure inline positioning - image stays exactly where dropped
        // No float, no block - just inline element in text flow
        draggedImg.className = 'img-inline';
        draggedImg.style.width = imgWidth;

        // Restore original dimension data
        if (origWidth) draggedImg.setAttribute('data-original-width', origWidth);
        if (origHeight) draggedImg.setAttribute('data-original-height', origHeight);

        // Remove indicator
        dropIndicator.remove();
    }

    draggedImg.classList.remove('dragging');

    // Re-select the moved image
    const movedImg = draggedImg;
    isDragging = false;
    draggedImg = null;
    dropIndicator = null;

    // Re-select and reposition handle
    selectImage(movedImg);

    enableSendButton();
}

// Add drag start listener to editor for selected images
document.addEventListener('DOMContentLoaded', function() {
    const editor = document.getElementById('editor');
    if (editor) {
        editor.addEventListener('mousedown', function(e) {
            // If clicking on a selected image (not the resize handle), start drag
            if (e.target === selectedImg && !e.target.classList.contains('resize-handle')) {
                startDrag(e);
            }
        });
    }
});

/* ============================================================================
   8.1 EDITOR CONTENT HELPERS
   ============================================================================
   Functions to get and set the editor's HTML content.
   ============================================================================ */

/**
 * Gets the current HTML content from the editor.
 * @returns {string} The editor's innerHTML
 */
function getEditorHtml() {
    const editor = document.getElementById('editor');
    if (!editor) return '';

    // Check if editor only contains the placeholder text
    const text = editor.innerText.trim();
    if (text === 'Type your email here...' || text === '') {
        return '';
    }

    return editor.innerHTML || '';
}

/**
 * Sets the HTML content in the editor.
 * @param {string} html - The HTML content to set
 */
function setEditorHtml(html) {
    const editor = document.getElementById('editor');
    if (!editor) return;

    if (!html || html.trim() === '') {
        // Set placeholder if empty
        const defaultTextColor = (window.editorConfig && window.editorConfig.defaultTextColor) || '#000000';
        editor.innerHTML = '<p style="color:' + defaultTextColor + ';">Type your email here...</p>';
    } else {
        editor.innerHTML = html;
    }

    // Reset table resize initialization flags so tables get new resize handles
    const tables = editor.querySelectorAll('table');
    tables.forEach(table => {
        // Remove the init flag
        delete table.dataset.resizeInit;
        // Remove old resize handles that may have been serialized
        table.querySelectorAll('.table-col-handle, .table-row-handle, .table-resize-handle, .table-resize-corner, .table-resize-row').forEach(h => h.remove());
        // Ensure table has position relative for handles
        table.style.position = 'relative';
        // Ensure cells have position relative
        table.querySelectorAll('td, th').forEach(cell => {
            cell.style.position = 'relative';
        });
    });

    // Initialize table resizing for any tables in the loaded content
    // Use longer timeout to ensure DOM is fully updated and rendered
    setTimeout(() => {
        console.log('[EDITOR] Initializing resize handles for', tables.length, 'table(s)');
        initTableResizing();
        // Verify initialization worked
        const uninitializedTables = Array.from(editor.querySelectorAll('table')).filter(t => t.dataset.resizeInit !== 'true');
        if (uninitializedTables.length > 0) {
            console.warn('[EDITOR] Warning:', uninitializedTables.length, 'table(s) still not initialized, retrying...');
            setTimeout(() => initTableResizing(), 100);
        } else {
            console.log('[EDITOR] All tables initialized successfully');
        }
    }, 100);

    // Log any console warnings/errors from setting HTML (for debugging template loads)
    const errors = [];
    const originalError = console.error;
    const originalWarn = console.warn;
    console.error = function(...args) { errors.push(args.join(' ')); originalError.apply(console, args); };
    console.warn = function(...args) { errors.push(args.join(' ')); originalWarn.apply(console, args); };

    // Restore after a tick
    setTimeout(() => {
        console.error = originalError;
        console.warn = originalWarn;
    }, 100);
}

/* ============================================================================
   9. TEMPLATE MANAGEMENT
   ============================================================================
   Functions for saving and loading email templates to/from the server.

   NOTE:
   - The UI buttons in index.html call global saveTemplate(slot) and loadTemplate(slot).
   - We use POST requests to avoid GET retries/caching and to keep request shape predictable.
   ============================================================================ */

/**
 * Saves current subject + editor HTML into the given template slot.
 * Shows visual feedback by flashing the menu item green (success) or red (failure).
 * @param {number} slot
 */
function saveTemplate(slot) {
    const n = Number(slot);
    if (!Number.isFinite(n) || n < 1) {
        flashSaveSlotFeedback(n, false);
        setStatus('Invalid template slot: ' + slot, false);
        return;
    }

    const subjectEl = document.getElementById('subject');
    const subject = subjectEl ? (subjectEl.value || '') : '';
    const htmlContent = getEditorHtml();

    if (!htmlContent || !htmlContent.trim()) {
        flashSaveSlotFeedback(n, false);
        setStatus('Cannot save an empty message.', false);
        return;
    }

    console.log(`[TEMPLATE] Saving template slot ${n}...`);
    setStatus(`Saving template ${n}...`, true);

    fetch('/api/template/save', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ slot: n, subject, htmlContent })
    })
        .then(async (res) => {
            const data = await safeReadJson(res);
            if (!res.ok || !data || data.status !== 'ok') {
                const msg = (data && data.message) ? data.message : `Save failed (${res.status})`;
                throw new Error(msg);
            }

            setStatus(`Template ${n} saved.`, false);
            flashSaveSlotFeedback(n, true);

            // Refresh label for this slot (e.g., show subject snippet)
            updateLoadButtonLabel(n);

            // Best-effort audit log (safe + small)
            sendTemplateLogToServer('SAVE', n, subject, htmlContent);
        })
        .catch((err) => {
            console.error('[Template] Save failed:', err);
            setStatus('Template save failed: ' + (err.message || String(err)), false);
            flashSaveSlotFeedback(n, false);
        });
}

/**
 * Flashes the save template menu item green (success) or red (failure).
 * @param {number} slot - The slot number
 * @param {boolean} success - True for success (green), false for failure (red)
 */
function flashSaveSlotFeedback(slot, success) {
    const menuItem = document.getElementById('saveTemplateSlot' + slot);
    if (!menuItem) return;

    const color = success ? '#28a745' : '#dc3545'; // Bootstrap success/danger colors
    const originalBg = menuItem.style.backgroundColor;
    const originalColor = menuItem.style.color;

    menuItem.style.backgroundColor = color;
    menuItem.style.color = 'white';
    menuItem.style.transition = 'background-color 0.3s, color 0.3s';

    setTimeout(() => {
        menuItem.style.backgroundColor = originalBg || '';
        menuItem.style.color = originalColor || '';
    }, 1500);
}

/**
 * Loads a template from the given slot and replaces subject + editor content.
 * @param {number} slot
 */
function loadTemplate(slot) {
    const n = Number(slot);
    if (!Number.isFinite(n) || n < 1) {
        showResult('error', 'Template Error', 'Invalid template slot: ' + slot);
        return;
    }

    console.log(`[TEMPLATE] Loading template slot ${n}...`);
    setStatus(`Loading template ${n}...`, true);

    fetch('/api/template/load', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ slot: n })
    })
        .then(async (res) => {
            const payload = await safeReadJson(res);
            if (!res.ok || !payload) {
                throw new Error(`Load failed (${res.status})`);
            }
            if (payload.status !== 'ok') {
                throw new Error(payload.message || 'Failed to load template');
            }

            const data = payload.data || {};
            const subject = (typeof data.subject === 'string') ? data.subject : '';
            const htmlContent = (typeof data.htmlContent === 'string') ? data.htmlContent : '';

            const subjectEl = document.getElementById('subject');
            if (subjectEl) subjectEl.value = subject;
            setEditorHtml(htmlContent);
            enableSendButton();

            setStatus(`Template ${n} loaded.`, false);
            console.log(`[TEMPLATE] Template ${n} loaded successfully`);

            // Best-effort audit log (safe + small)
            sendTemplateLogToServer('LOAD', n, subject, htmlContent);
        })
        .catch((err) => {
            console.error('[Template] Load failed:', err);
            setStatus('Template load failed', false);
            showResult('error', 'Template Load Failed', err.message || String(err));
        });
}

// Explicitly export template functions to global scope
// (ensures they're available for legacy inline onclick handlers)
window.saveTemplate = saveTemplate;
window.loadTemplate = loadTemplate;
window.updateLoadButtonLabels = updateLoadButtonLabels;
window.updateLoadButtonLabel = updateLoadButtonLabel;
window.sendTemplateLogToServer = sendTemplateLogToServer;
window.safeReadJson = safeReadJson;
window.getEditorHtml = getEditorHtml;
window.setEditorHtml = setEditorHtml;
window.setStatus = setStatus;
window.showResult = showResult;
window.enableSendButton;
window.toggleSelectedTableBorders = toggleSelectedTableBorders;
window.toggleTableBorders = toggleTableBorders;
window.exportToPdf = exportToPdf;
window.exportToDocx = exportToDocx;
window.exportToTxt = exportToTxt;
window.exportToHtml = exportToHtml;
window.rebuildTemplateButtons = rebuildTemplateButtons;
window.rebuildTemplateMenuItems = rebuildTemplateMenuItems;

/**
 * Updates all load button labels based on server-side stored subject.
 */
function updateLoadButtonLabels() {
    const maxSlots = (() => {
        const cfg = window.editorConfig;
        const raw = cfg && cfg.templateSlots;
        const n = Number(raw);
        return (Number.isFinite(n) && n > 0) ? n : 5;
    })();

    for (let i = 1; i <= maxSlots; i++) {
        updateLoadButtonLabel(i);
    }
}

function updateLoadButtonLabel(slot) {
    const btn = document.getElementById('loadBtn' + slot);
    if (!btn) return;

    fetch('/api/template/label/' + slot, { method: 'GET' })
        .then(async (res) => {
            const payload = await safeReadJson(res);
            if (!res.ok || !payload || payload.status !== 'ok') {
                return;
            }
            if (typeof payload.label === 'string' && payload.label.trim()) {
                btn.textContent = payload.label;
            }
        })
        .catch(() => {
            // ignore label refresh failures
        });
}

/**
 * Reads JSON safely even if the response is empty or not JSON.
 */
async function safeReadJson(res) {
    try {
        const text = await res.text();
        if (!text) return null;
        return JSON.parse(text);
    } catch {
        return null;
    }
}

/**
 * Sends a small template audit log to the server.
 * IMPORTANT: Never send the full HTML or base64 payload; only metadata.
 */
function sendTemplateLogToServer(operation, slot, subject, htmlContent) {
    try {
        const safeSubject = (subject || '').toString().substring(0, 200);
        const html = (htmlContent || '').toString();
        const analyzer = analyzeTemplateHtml(html);

        const body = {
            operation: (operation || 'UNKNOWN').toString().substring(0, 12),
            slot: Number(slot),
            subject: safeSubject,
            contentLength: Math.min(html.length, 2_000_000),
            imageCount: analyzer.imageCount,
            tableCount: analyzer.tableCount,
            linkCount: analyzer.linkCount,
            imageDetails: analyzer.imageDetails
        };

        fetch('/api/template/log', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        }).catch(() => {
            // ignore audit logging failures
        });
    } catch (e) {
        console.warn('[Template] Failed to prepare server log:', e);
    }
}

function analyzeTemplateHtml(html) {
    const result = {
        imageCount: 0,
        tableCount: 0,
        linkCount: 0,
        imageDetails: []
    };

    if (!html) return result;

    try {
        const parser = new DOMParser();
        const doc = parser.parseFromString(html, 'text/html');

        const imgs = Array.from(doc.querySelectorAll('img'));
        const links = Array.from(doc.querySelectorAll('a[href]'));
        const tables = Array.from(doc.querySelectorAll('table'));

        result.imageCount = imgs.length;
        result.linkCount = links.length;
        result.tableCount = tables.length;

        // Limit details to avoid large payloads.
        imgs.slice(0, 20).forEach((img) => {
            const src = (img.getAttribute('src') || '').toString();
            const isData = src.startsWith('data:');

            // Never include raw data URLs; only summarize.
            const safeSrc = isData ? summarizeDataUrl(src) : truncateSafe(src, 180);

            result.imageDetails.push({
                type: isData ? 'data-url' : 'url',
                filePath: safeSrc,
                filename: truncateSafe((img.getAttribute('data-original-filename') || '').toString(), 120),
                size: isData ? summarizeDataUrlSize(src) : truncateSafe((img.getAttribute('data-size') || '').toString(), 32),
                dimensions: truncateSafe(((img.getAttribute('width') || '') + 'x' + (img.getAttribute('height') || '')).toString(), 50)
            });
        });

        return result;
    } catch {
        return result;
    }
}

function truncateSafe(value, maxLen) {
    const v = (value || '').toString();
    if (v.length <= maxLen) return v;
    return v.substring(0, maxLen) + '…';
}

function summarizeDataUrl(value) {
    if (!value || !value.startsWith('data:')) return truncateSafe(value, 180);
    const mime = extractDataUrlMime(value);
    const size = summarizeDataUrlSize(value);
    return `(embedded data-url${mime ? ';' + mime : ''}${size ? ';' + size : ''})`;
}

function summarizeDataUrlSize(value) {
    if (!value || !value.startsWith('data:')) return '';
    const comma = value.indexOf(',');
    if (comma < 0) return '';
    const header = value.substring(0, comma);
    const base64 = header.includes(';base64');
    const payloadLen = value.length - (comma + 1);
    if (payloadLen <= 0) return '';
    const bytes = base64 ? Math.floor((payloadLen / 4) * 3) : payloadLen;
    if (bytes < 1024) return `${bytes}B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)}MB`;
}

function extractDataUrlMime(value) {
    try {
        const start = 'data:'.length;
        const semi = value.indexOf(';', start);
        const comma = value.indexOf(',', start);
        const end = semi > -1 ? semi : comma;
        if (end <= start) return '';
        return value.substring(start, end);
    } catch (e) {
        return '';
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
    document.querySelectorAll('.email-checkbox:checked').forEach(cb => {
        console.log('[getSelectedEmails] Found checked checkbox with value:', cb.value);
        selected.push(cb.value);
    });
    console.log('[getSelectedEmails] Total selected:', selected.length, 'emails:', selected);
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
 * Sets the status bar text and optionally shows/hides the progress bar.
 * @param {string} message - Status message to display
 * @param {boolean} showProgress - Whether to show the progress bar
 */
function setStatus(message, showProgress) {
    const statusText = document.getElementById('statusText');
    const progressContainer = document.getElementById('progressContainer');

    if (statusText) {
        statusText.textContent = message || 'Ready';
    }

    if (progressContainer) {
        progressContainer.style.visibility = showProgress ? 'visible' : 'hidden';
    }
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
 * Also handles publishing to Facebook/Instagram if selected
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

    // Get publishing options
    const publishFacebook = document.getElementById('publishFacebook')?.checked || false;
    const publishInstagram = document.getElementById('publishInstagram')?.checked || false;
    const sendEmailRecipients = document.getElementById('sendEmailRecipients')?.checked ?? true;

    if (!subject.trim()) { showResult('error', 'Error', 'Please enter a subject'); return; }
    if (!htmlContent.trim()) { showResult('error', 'Error', 'Please enter a message'); return; }

    // Check if any action is selected
    if (!publishFacebook && !publishInstagram && !sendEmailRecipients) {
        showResult('error', 'Error', 'Please select at least one action (Facebook, Instagram, or Send Emails)');
        return;
    }

    // If sending emails, require at least one recipient
    if (sendEmailRecipients && selectedEmails.length === 0) {
        showResult('error', 'Error', 'Please select at least one email recipient');
        return;
    }

    // Build confirmation message
    let confirmParts = [];
    if (publishFacebook) confirmParts.push('• Post to Facebook');
    if (publishInstagram) confirmParts.push('• Post to Instagram');
    if (sendEmailRecipients && selectedEmails.length > 0) {
        confirmParts.push('• Send emails to ' + selectedEmails.length + ' recipient(s)');
    }

    // Show confirmation dialog
    const confirmMsg = 'You are about to:\n\n' + confirmParts.join('\n') + '\n\nContinue?';
    if (!confirm(confirmMsg)) {
        console.log('Send operation cancelled by user');
        return;
    }

    // Handle Facebook posting (with skipConfirmation=true since we already confirmed)
    if (publishFacebook) {
        console.log('[SendEmails] Triggering Facebook post...');
        postToFacebook(subject, htmlContent, true); // true = skip confirmation
    }

    // Handle Instagram posting (placeholder)
    if (publishInstagram) {
        console.log('[SendEmails] Instagram posting not yet implemented');
        showResult('partial', 'Instagram', 'Instagram posting is not yet implemented');
    }

    // If not sending emails, we're done after social posts
    if (!sendEmailRecipients || selectedEmails.length === 0) {
        console.log('[SendEmails] No email recipients selected, skipping email send');
        return;
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
        console.log('Batch mode - addressMode:', batchAddressMode, 'batchSize:', batchSize, 'delayMs:', sendDelay);
        fetch('/api/send', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                subject, htmlContent, sendToAll: false, selectedEmails,
                sendMode: 'batch', addressMode: batchAddressMode, batchSize, delayMs: sendDelay
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
            console.log('[Individual Send] idx=' + idx + ' of ' + selectedEmails.length + ', sending to email: "' + email + '"');
            console.log('[Individual Send] Full selectedEmails array:', JSON.stringify(selectedEmails));
            setStatus(`Sending ${idx + 1}/${selectedEmails.length}: ${email}`, true);
            updateSendStatusLine(idx, selectedEmails.length, ok, fail);
            updateSendButtonProgress(idx, selectedEmails.length, ok, fail);

            const requestBody = {
                subject, htmlContent, sendToAll: false, selectedEmails: [email],
                sendMode: 'individual', addressMode
            };
            console.log('[Individual Send] Request body:', JSON.stringify(requestBody, null, 2));

            fetch('/api/send', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestBody)
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
 * Posts content to Facebook with confirmation dialog
 * @param {string} subject - The subject/title of the post
 * @param {string} htmlContent - The HTML content to post
 * @param {boolean} skipConfirmation - If true, skip the confirmation dialog (used when called from sendEmails)
 */
function postToFacebook(subject, htmlContent, skipConfirmation = false) {
    // Show confirmation dialog unless skipped
    if (!skipConfirmation) {
        const confirmMsg = 'Are you sure you want to post to Facebook?\n\n' +
            'Subject: ' + subject + '\n\n' +
            'This will be visible to your Facebook followers.';
        if (!confirm(confirmMsg)) {
            console.log('Facebook posting cancelled by user');
            setStatus('Facebook posting cancelled', false);
            return;
        }
    }

    console.log('[Facebook] Starting post - Subject:', subject, ', Content length:', htmlContent.length);
    setStatus('Posting to Facebook...', true);

    // Update status on Facebook checkbox
    const fbCheckbox = document.getElementById('publishFacebook');
    const fbLabel = fbCheckbox?.parentElement?.querySelector('label');
    const originalLabelHtml = fbLabel?.innerHTML;
    if (fbLabel) {
        fbLabel.innerHTML = '<i class="fas fa-spinner fa-spin text-primary"></i> Posting...';
    }

    fetch('/api/facebook/post', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ subject, htmlContent })
    })
    .then(r => {
        console.log('[Facebook] Response status:', r.status);
        return r.json();
    })
    .then(data => {
        console.log('[Facebook] Response data:', data);

        if (data.success) {
            console.log('[Facebook] Post successful:', data.message);
            setStatus('Facebook: Posted successfully!', false);

            // Show success notification
            if (fbLabel) {
                fbLabel.innerHTML = '<i class="fas fa-check-circle text-success"></i> Posted!';
                setTimeout(() => {
                    fbLabel.innerHTML = originalLabelHtml;
                }, 5000);
            }

            // Show success alert
            showResult('success', 'Facebook Success', 'Your message has been posted to Facebook successfully!\n\n' + (data.message || ''));
        } else {
            console.error('[Facebook] Post failed:', data.message);
            setStatus('Facebook: Post failed', false);

            // Restore label and show error
            if (fbLabel) {
                fbLabel.innerHTML = '<i class="fas fa-times-circle text-danger"></i> Failed';
                setTimeout(() => {
                    fbLabel.innerHTML = originalLabelHtml;
                }, 5000);
            }

            showResult('error', 'Facebook Error', 'Failed to post to Facebook:\n\n' + data.message);
        }
    })
    .catch(err => {
        console.error('[Facebook] API error:', err);
        setStatus('Facebook: Connection error', false);

        // Restore label and show error
        if (fbLabel) {
            fbLabel.innerHTML = '<i class="fas fa-exclamation-triangle text-warning"></i> Error';
            setTimeout(() => {
                fbLabel.innerHTML = originalLabelHtml;
            }, 5000);
        }

        showResult('error', 'Facebook Error', 'Failed to connect to Facebook:\n\n' + err.message);
    });
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
   14. RESULT MODAL & DIALOG FUNCTIONS
   ============================================================================
   Functions for showing modals and handling dialog interactions.
   ============================================================================ */

/**
 * Shows a result modal with status-based styling.
 * @param {string} status - 'success', 'error', or 'partial'
 * @param {string} title - Modal title
 * @param {string} message - Modal message body
 */
function showResult(status, title, message) {
    const modalEl = document.getElementById('resultModal');
    if (!modalEl) {
        alert(title + '\n\n' + message);
        return;
    }

    const titleEl = document.getElementById('resultModalTitle');
    const bodyEl = document.getElementById('resultModalBody');
    const headerEl = document.getElementById('resultModalHeader');

    if (titleEl) titleEl.innerText = title;
    if (bodyEl) bodyEl.innerText = message;

    if (headerEl) {
        headerEl.className = 'modal-header';
        if (status === 'success') {
            headerEl.classList.add('bg-success', 'text-white');
        } else if (status === 'error') {
            headerEl.classList.add('bg-danger', 'text-white');
        } else if (status === 'partial') {
            headerEl.classList.add('bg-warning', 'text-dark');
        } else {
            headerEl.classList.add('bg-info', 'text-white');
        }
    }

    const modal = new bootstrap.Modal(modalEl);
    modal.show();
}

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
 * Opens the Configuration dialog.
 * Loads current configuration values and displays the modal.
 */
function openConfiguration() {
    console.log('[CONFIG] Opening Configuration dialog');
    fetch('/api/config')
        .then(r => r.json())
        .then(cfg => {
            console.log('[CONFIG] Configuration loaded successfully');
            const f = document.getElementById('configForm');
            // SMTP settings
            f.elements['spring.mail.host'].value = cfg['spring.mail.host'] || '';
            f.elements['spring.mail.port'].value = cfg['spring.mail.port'] || '';
            f.elements['spring.mail.username'].value = cfg['spring.mail.username'] || '';
            f.elements['spring.mail.password'].value = '';
            f.elements['spring.mail.password'].placeholder = cfg['spring.mail.password.encrypted'] ? '••••••• (encrypted)' : '(not set)';
            // Sender settings
            f.elements['mail.from'].value = cfg['mail.from'] || '';
            f.elements['mail.from.name'].value = cfg['mail.from.name'] || '';
            // Editor settings
            f.elements['app.editor.default.text.color'].value = cfg['app.editor.default.text.color'] || 'white';
            f.elements['app.template.slots'].value = cfg['app.template.slots'] || '5';
            // Facebook settings
            document.getElementById('facebookEnabledSwitch').checked = (String(cfg['facebook.enabled']) === 'true');
            f.elements['facebook.email'].value = cfg['facebook.email'] || '';
            f.elements['facebook.password'].value = '';
            f.elements['facebook.password'].placeholder = cfg['facebook.password.encrypted'] ? '••••••• (encrypted)' : '(not set)';
            f.elements['facebook.page.id'].value = cfg['facebook.page.id'] || '';
            f.elements['facebook.access.token'].value = '';
            f.elements['facebook.access.token'].placeholder = cfg['facebook.access.token.encrypted'] ? '••••••• (encrypted)' : '(not set)';
            // Reset button states
            const saveBtn = document.getElementById('configSaveBtn');
            const saveCloseBtn = document.getElementById('configSaveCloseBtn');
            if (saveBtn) {
                saveBtn.className = 'btn btn-primary';
                saveBtn.innerHTML = 'Save';
                saveBtn.disabled = false;
            }
            if (saveCloseBtn) {
                saveCloseBtn.className = 'btn btn-outline-primary';
                saveCloseBtn.innerHTML = 'Save &amp; Close';
                saveCloseBtn.disabled = false;
            }
            // Clear status message
            document.getElementById('configStatus').textContent = '';
            // Show modal
            const modal = new bootstrap.Modal(document.getElementById('configModal'));
            modal.show();
        })
        .catch(err => {
            console.error('[CONFIG] Failed to load configuration:', err);
            document.getElementById('configStatus').textContent = 'Failed to load config: ' + err.message;
            new bootstrap.Modal(document.getElementById('configModal')).show();
        });
}

/**
 * Saves the configuration settings to the server.
 * Stores ALL values, updates button states, and optionally closes the modal.
 * On success: Save button turns green briefly, configuration is stored to localStorage.
 * On failure: Save button turns red briefly with error message.
 * @param {boolean} [closeOnSave=false] - If true, close modal after save
 */
function saveConfiguration(closeOnSave = false) {
    console.log('[CONFIG] Saving configuration (closeOnSave=' + closeOnSave + ')');
    const f = document.getElementById('configForm');
    const saveBtn = closeOnSave
        ? document.getElementById('configSaveCloseBtn')
        : document.getElementById('configSaveBtn');
    const originalBtnClass = saveBtn ? saveBtn.className : '';
    const originalBtnText = saveBtn ? saveBtn.innerHTML : '';
    // Collect ALL values from form
    const payload = {
        'spring.mail.host': f.elements['spring.mail.host'].value.trim(),
        'spring.mail.port': f.elements['spring.mail.port'].value.trim(),
        'spring.mail.username': f.elements['spring.mail.username'].value.trim(),
        'mail.from': f.elements['mail.from'].value.trim(),
        'mail.from.name': f.elements['mail.from.name'].value.trim(),
        'app.editor.default.text.color': f.elements['app.editor.default.text.color'].value,
        'app.template.slots': f.elements['app.template.slots'].value.trim(),
        'facebook.enabled': document.getElementById('facebookEnabledSwitch').checked ? 'true' : 'false',
        'facebook.email': f.elements['facebook.email'].value.trim(),
        'facebook.page.id': f.elements['facebook.page.id'].value.trim()
    };
    // Always send password fields if user entered a value (even if empty string to clear)
    const mailPassword = f.elements['spring.mail.password'].value;
    if (mailPassword !== '') {
        payload['spring.mail.password'] = mailPassword;
        console.log('[CONFIG] Mail password provided');
    }
    const fbPassword = f.elements['facebook.password'].value;
    if (fbPassword !== '') {
        payload['facebook.password'] = fbPassword;
        console.log('[CONFIG] Facebook password provided');
    }
    const fbToken = f.elements['facebook.access.token'].value;
    if (fbToken !== '') {
        payload['facebook.access.token'] = fbToken;
        console.log('[CONFIG] Facebook access token provided');
    }
    console.log('[CONFIG] Payload to save:', Object.keys(payload).length + ' keys');
    document.getElementById('configStatus').textContent = 'Saving…';
    // Disable button during save
    if (saveBtn) {
        saveBtn.disabled = true;
        saveBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Saving...';
    }
    fetch('/api/config', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
    .then(r => r.json())
    .then(d => {
        if (d.status === 'ok') {
            console.log('[CONFIG] Configuration saved successfully');
            // Store saved config in localStorage for immediate UI updates
            localStorage.setItem('savedConfig', JSON.stringify(payload));
            localStorage.setItem('configSavedAt', new Date().toISOString());
            // Show success - green button
            if (saveBtn) {
                saveBtn.disabled = false;
                saveBtn.className = 'btn btn-success';
                saveBtn.innerHTML = '<i class="fas fa-check"></i> Saved!';
            }
            document.getElementById('configStatus').innerHTML =
                '<strong style="color: #28a745;">✓ Saved successfully!</strong> ' +
                '<small style="color: #666;">(Restart required for full effect)</small>';
            // Apply immediate UI updates (without clearing editor)
            applyConfigToUI(payload);
            if (closeOnSave) {
                console.log('[CONFIG] Closing modal (no page reload to preserve editor content)');
                setTimeout(() => {
                    // Close modal
                    const modalEl = document.getElementById('configModal');
                    if (modalEl) {
                        const modal = bootstrap.Modal.getInstance(modalEl);
                        if (modal) modal.hide();
                    }
                }, 500);
            } else {
                // Reset button after 2 seconds
                setTimeout(() => {
                    if (saveBtn) {
                        saveBtn.className = originalBtnClass;
                        saveBtn.innerHTML = originalBtnText;
                    }
                }, 2000);
            }
        } else {
            console.error('[CONFIG] Save failed:', d.message);
            // Show failure - red button
            if (saveBtn) {
                saveBtn.disabled = false;
                saveBtn.className = 'btn btn-danger';
                saveBtn.innerHTML = '<i class="fas fa-times"></i> Failed';
            }
            document.getElementById('configStatus').innerHTML =
                '<strong style="color: #dc3545;">✗ Failed:</strong> ' + (d.message || 'Unknown error');
            // Reset button after 3 seconds
            setTimeout(() => {
                if (saveBtn) {
                    saveBtn.className = originalBtnClass;
                    saveBtn.innerHTML = originalBtnText;
                }
            }, 3000);
        }
    })
    .catch(err => {
        console.error('[CONFIG] Save error:', err);
        // Show error - red button
        if (saveBtn) {
            saveBtn.disabled = false;
            saveBtn.className = 'btn btn-danger';
            saveBtn.innerHTML = '<i class="fas fa-exclamation-triangle"></i> Error';
        }
        document.getElementById('configStatus').innerHTML =
            '<strong style="color: #dc3545;">✗ Error:</strong> ' + err.message;
        // Reset button after 3 seconds
        setTimeout(() => {
            if (saveBtn) {
                saveBtn.className = originalBtnClass;
                saveBtn.innerHTML = originalBtnText;
            }
        }, 3000);
    });
}

/**
 * Applies saved configuration values to the UI immediately.
 * Updates visual elements that can change without restart.
 * Does NOT clear or modify the editor content.
 * @param {Object} config - Configuration values to apply
 */
function applyConfigToUI(config) {
    console.log('[CONFIG] Applying config to UI');
    // Update editor default text color if changed
    if (config['app.editor.default.text.color'] && window.editorConfig) {
        const colorMap = { 'white': '#ffffff', 'black': '#000000' };
        window.editorConfig.defaultTextColor = colorMap[config['app.editor.default.text.color']] ||
            config['app.editor.default.text.color'];
        console.log('[CONFIG] Updated editor default text color to:', window.editorConfig.defaultTextColor);
    }
    // Update template slots count and rebuild buttons
    if (config['app.template.slots']) {
        const newSlots = parseInt(config['app.template.slots'], 10) || 5;
        const oldSlots = (window.editorConfig && window.editorConfig.templateSlots) || 5;
        if (!window.editorConfig) {
            window.editorConfig = {};
        }
        window.editorConfig.templateSlots = newSlots;
        console.log('[CONFIG] Updated template slots from', oldSlots, 'to:', newSlots);
        // Always rebuild template buttons to ensure UI is in sync
        rebuildTemplateButtons(newSlots);
        rebuildTemplateMenuItems(newSlots);
        // Refresh load button labels
        if (typeof updateLoadButtonLabels === 'function') {
            updateLoadButtonLabels();
        }
    }
}

/**
 * Rebuilds the template Save/Load buttons in the footer based on new slot count
 * @param {number} slotCount - Number of template slots
 */
function rebuildTemplateButtons(slotCount) {
    const templateGroup = document.querySelector('.template-group');
    if (!templateGroup) {
        console.warn('[CONFIG] Template group not found, cannot rebuild buttons');
        return;
    }

    // Remove old save and load buttons
    templateGroup.querySelectorAll('.js-template-save').forEach(btn => btn.remove());
    templateGroup.querySelectorAll('.js-template-load').forEach(btn => btn.remove());

    // Find the label elements
    const saveLabel = templateGroup.querySelector('strong[title*="Save"]');
    const loadLabel = templateGroup.querySelector('strong[title*="Load"]');

    if (!saveLabel || !loadLabel) {
        console.warn('[CONFIG] Template labels not found, cannot rebuild buttons');
        return;
    }

    // Create DocumentFragment for efficient DOM manipulation
    const saveFragment = document.createDocumentFragment();
    const loadFragment = document.createDocumentFragment();

    // Create new save buttons
    for (let i = 1; i <= slotCount; i++) {
        const saveBtn = document.createElement('button');
        saveBtn.type = 'button';
        saveBtn.className = 'btn btn-sm btn-outline-primary js-template-save';
        saveBtn.setAttribute('data-template-slot', i);
        saveBtn.title = 'Save to template slot ' + i;
        saveBtn.textContent = i;
        const slot = i; // Capture for closure
        saveBtn.onclick = function() { saveTemplate(slot); };
        saveFragment.appendChild(saveBtn);
    }

    // Create new load buttons
    for (let i = 1; i <= slotCount; i++) {
        const loadBtn = document.createElement('button');
        loadBtn.type = 'button';
        loadBtn.className = 'btn btn-sm btn-outline-secondary js-template-load';
        loadBtn.id = 'loadBtn' + i;
        loadBtn.setAttribute('data-template-slot', i);
        loadBtn.title = 'Load template from slot ' + i;
        loadBtn.textContent = i;
        const slot = i; // Capture for closure
        loadBtn.onclick = function() { loadTemplate(slot); };
        loadFragment.appendChild(loadBtn);
    }

    // Insert save buttons after save label, before load label
    let insertPoint = saveLabel.nextSibling;
    while (insertPoint && insertPoint !== loadLabel) {
        const next = insertPoint.nextSibling;
        if (insertPoint.nodeType === Node.TEXT_NODE && insertPoint.textContent.trim() === '') {
            insertPoint = next;
            continue;
        }
        insertPoint = next;
    }

    // Insert save buttons right after save label
    if (saveLabel.nextSibling) {
        templateGroup.insertBefore(saveFragment, loadLabel);
    } else {
        templateGroup.appendChild(saveFragment);
    }

    // Append load buttons at the end (after load label)
    templateGroup.appendChild(loadFragment);

    console.log('[CONFIG] Rebuilt template buttons for', slotCount, 'slots');
}

/**
 * Rebuilds the template menu items in File menu based on new slot count
 * @param {number} slotCount - Number of template slots
 */
function rebuildTemplateMenuItems(slotCount) {
    // Update Save Template submenu
    const saveSubmenu = document.querySelector('#saveTemplateSlot1')?.closest('ul');
    if (saveSubmenu) {
        saveSubmenu.innerHTML = '';
        for (let i = 1; i <= slotCount; i++) {
            const li = document.createElement('li');
            const a = document.createElement('a');
            a.className = 'dropdown-item';
            a.href = '#';
            a.id = 'saveTemplateSlot' + i;
            a.textContent = 'Save to Slot ' + i;
            a.onclick = function(e) { e.preventDefault(); saveTemplate(i); return false; };
            li.appendChild(a);
            saveSubmenu.appendChild(li);
        }
    }
    // Update Load Template submenu
    const loadSubmenus = document.querySelectorAll('.dropdown-menu .dropdown-item[onclick*="loadTemplate"]');
    const loadSubmenu = loadSubmenus[0]?.closest('ul');
    if (loadSubmenu) {
        loadSubmenu.innerHTML = '';
        for (let i = 1; i <= slotCount; i++) {
            const li = document.createElement('li');
            const a = document.createElement('a');
            a.className = 'dropdown-item';
            a.href = '#';
            a.textContent = 'Load from Slot ' + i;
            a.onclick = function(e) { e.preventDefault(); loadTemplate(i); return false; };
            li.appendChild(a);
            loadSubmenu.appendChild(li);
        }
    }
    console.log('[CONFIG] Rebuilt template menu items for', slotCount, 'slots');
}


/* ============================================================================
   NEW TOP MENU ACTIONS
   ============================================================================
   Stubs for new top menu actions: save/load template dialogs, export to TXT/HTML,
   configuration/about, theme setters.
   ============================================================================ */

/**
 * Initializes the column resizer to allow dragging between compose and recipients columns.
 */
function initColumnResizer() {
    const resizer = document.getElementById('columnResizer');
    const leftColumn = document.querySelector('.col-compose');
    const rightColumn = document.querySelector('.col-recipients');

    if (!resizer || !leftColumn || !rightColumn) return;

    let isResizing = false;
    let startX = 0;
    let startLeftWidth = 0;
    let startRightWidth = 0;

    resizer.addEventListener('mousedown', function(e) {
        isResizing = true;
        startX = e.clientX;
        startLeftWidth = leftColumn.offsetWidth;
        startRightWidth = rightColumn.offsetWidth;

        // Add visual feedback
        document.body.style.cursor = 'col-resize';
        resizer.style.background = '#667eea';

        e.preventDefault();
    });

    document.addEventListener('mousemove', function(e) {
        if (!isResizing) return;

        const deltaX = e.clientX - startX;
        const newLeftWidth = startLeftWidth + deltaX;
        const newRightWidth = startRightWidth - deltaX;

        // Enforce minimum widths
        const minLeftWidth = 400;
        const minRightWidth = 300;

        if (newLeftWidth >= minLeftWidth && newRightWidth >= minRightWidth) {
            leftColumn.style.flex = `0 0 ${newLeftWidth}px`;
            rightColumn.style.flex = `0 0 ${newRightWidth}px`;
        }

        e.preventDefault();
    });

    document.addEventListener('mouseup', function() {
        if (isResizing) {
            isResizing = false;
            document.body.style.cursor = '';
            resizer.style.background = '';
        }
    });
}

/**
 * Executes a command using document.execCommand
 * @param {string} cmd - The command to execute
 */
function execCmd(cmd) { try { document.execCommand(cmd); } catch(e){} }

/**
 * Opens the Save Template dialog
 */
function openSaveTemplateDialog(){ showInfoModal('Save Template','Use the numbered Save as Template buttons below to save.'); }

/**
 * Opens the Load Template dialog
 */
function openLoadTemplateDialog(){ showInfoModal('Load Template','Use the numbered Load buttons below to load.'); }

/**
 * Exports the current editor content to a TXT file
 */
function exportToTxt(){ const editor=document.getElementById('editor'); const blob=new Blob([editor.innerText||''],{type:'text/plain;charset=utf-8'}); downloadBlob(blob, (document.getElementById('subject').value||'message')+'.txt'); }

/**
 * Exports the current editor content to an HTML file
 */
function exportToHtml(){ const editor=document.getElementById('editor'); const html='<!DOCTYPE html><html><head><meta charset="UTF-8"><title>'+(document.getElementById('subject').value||'Message')+'</title></head><body>'+editor.innerHTML+'</body></html>'; const blob=new Blob([html],{type:'text/html;charset=utf-8'}); downloadBlob(blob, (document.getElementById('subject').value||'message')+'.html'); }

/**
 * Exports the current editor content to PDF file.
 * First tries server-side PDF generation for better quality and consistency.
 * Falls back to html2pdf.js client-side, then browser print dialog as last resort.
 * Creates a properly styled document that preserves formatting.
 */
function exportToPdf() {
    const editor = document.getElementById('editor');
    if (!editor) {
        showInfoModal('Export PDF', 'Editor not found.');
        return;
    }
    const subject = document.getElementById('subject').value || 'Message';
    const cleanContent = editor.cloneNode(true);
    cleanContent.querySelectorAll('.table-col-handle, .table-row-handle, .table-resize-handle, .table-resize-corner, .table-resize-row').forEach(el => el.remove());

    console.log('[Export] Starting PDF export, subject:', subject);

    // Try server-side PDF generation first (best quality)
    fetch('/api/export/pdf', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ subject: subject, htmlContent: cleanContent.innerHTML })
    })
    .then(response => {
        if (!response.ok) {
            throw new Error('Server PDF generation failed: ' + response.status);
        }
        return response.blob();
    })
    .then(blob => {
        console.log('[Export] PDF generated server-side successfully');
        downloadBlob(blob, (subject || 'message') + '.pdf');
    })
    .catch(serverError => {
        console.warn('[Export] Server-side PDF failed, trying html2pdf.js:', serverError.message);

        // Fallback: Try html2pdf.js if available
        if (typeof html2pdf !== 'undefined') {
            try {
                const opt = {
                    margin: 10,
                    filename: (subject || 'message') + '.pdf',
                    image: { type: 'jpeg', quality: 0.98 },
                    html2canvas: { scale: 2, useCORS: true },
                    jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' }
                };
                html2pdf().set(opt).from(cleanContent).save();
                console.log('[Export] PDF generated using html2pdf.js');
                return;
            } catch (html2pdfError) {
                console.warn('[Export] html2pdf.js failed:', html2pdfError.message);
            }
        } else {
            console.warn('[Export] html2pdf.js not available');
        }

        // Last resort: Browser print dialog
        console.log('[Export] Falling back to browser print dialog');
        const printWindow = window.open('', '_blank', 'width=800,height=600');
        if (!printWindow) {
            showInfoModal('Export PDF', 'Please allow pop-ups to export to PDF. Use browser Print dialog and select "Save as PDF".');
            return;
        }
        const htmlContent = `<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>${subject}</title>
    <style>
        body {
            font-family: Calibri, Arial, sans-serif;
            padding: 20px;
            margin: 0;
            color: #000;
        }
        table {
            border-collapse: collapse;
            margin: 10px 0;
        }
        td, th {
            border: 1px solid #ccc;
            padding: 8px;
        }
        img {
            max-width: 100%;
            height: auto;
        }
        @media print {
            body { padding: 0; }
        }
    </style>
</head>
<body>${cleanContent.innerHTML}</body>
</html>`;
        printWindow.document.write(htmlContent);
        printWindow.document.close();
        printWindow.onload = function() {
            setTimeout(() => {
                printWindow.focus();
                printWindow.print();
            }, 100);
        };
        setTimeout(() => {
            if (printWindow && !printWindow.closed) {
                printWindow.focus();
                printWindow.print();
            }
        }, 500);
    });
}

/**
 * Exports the current editor content to Microsoft Word (DOCX) format
 * Uses HTML-to-Word conversion via Blob with MS Word MIME type
 */
function exportToDocx() {
    const editor = document.getElementById('editor');
    const subject = document.getElementById('subject').value || 'Message';
    const htmlContent = editor.innerHTML || '';
    const docHtml = `<!DOCTYPE html><html xmlns:o='urn:schemas-microsoft-com:office:office' xmlns:w='urn:schemas-microsoft-com:office:word' xmlns='http://www.w3.org/TR/REC-html40'><head><meta charset="UTF-8"><title>${subject}</title><!--[if gte mso 9]><xml><w:WordDocument><w:View>Print</w:View><w:Zoom>100</w:Zoom></w:WordDocument></xml><![endif]--><style>body{font-family:Calibri,Arial,sans-serif;} table{border-collapse:collapse;} td,th{border:1px solid #ccc;padding:4px;}</style></head><body>${htmlContent}</body></html>`;
    const blob = new Blob(['\ufeff', docHtml], { type: 'application/msword' });
    downloadBlob(blob, subject + '.doc');
}


/**
 * Shows the About dialog with version information from the server.
 * Fetches application name, version (from pom.xml), and copyright from the API.
 * Falls back to basic info if the API call fails.
 */
function showAbout() {
    fetch('/api/config/version')
        .then(response => response.json())
        .then(data => {
            const content = `
                <div class="about-dialog text-center">
                    <h4>${data.name || 'Web-List-EMailer'}</h4>
                    <p class="text-muted">${data.description || 'Email Mass Sender Application'}</p>
                    <hr>
                    <p><strong>Version:</strong> ${data.version || 'Unknown'}</p>
                    <p class="small text-muted mt-3">${data.copyright || '© 2025 KiSoft. All rights reserved.'}</p>
                </div>
            `;
            showInfoModal('About', content);
        })
        .catch(error => {
            console.error('[ABOUT] Failed to fetch version info:', error);
            showInfoModal('About', `
                <div class="about-dialog text-center">
                    <h4>Web-List-EMailer</h4>
                    <p class="text-muted">Email Mass Sender Application</p>
                    <hr>
                    <p class="small text-muted mt-3">© 2025 KiSoft. All rights reserved.</p>
                </div>
            `);
        });
}

/**
 * Shows the Detail Description dialog with comprehensive help documentation
 */
function showDetailDescription() {
    const helpContent = `
        <div class="help-documentation">
            <h4>Web-List-EMailer - Detailed User Guide</h4>
            
            <div class="help-toc">
                <h5>Table of Contents</h5>
                <ol>
                    <li><a href="#help-overview">Overview</a></li>
                    <li><a href="#help-interface">User Interface</a></li>
                    <li><a href="#help-composing">Composing Messages</a></li>
                    <li><a href="#help-formatting">Text Formatting</a></li>
                    <li><a href="#help-images">Working with Images</a></li>
                    <li><a href="#help-tables">Creating Tables</a></li>
                    <li><a href="#help-templates">Template Management</a></li>
                    <li><a href="#help-recipients">Managing Recipients</a></li>
                    <li><a href="#help-sending">Sending Emails</a></li>
                    <li><a href="#help-export">Export Options</a></li>
                    <li><a href="#help-troubleshooting">Troubleshooting</a></li>
                </ol>
            </div>

            <div id="help-overview" class="help-section">
                <h5>1. Overview</h5>
                <p><strong>Web-List-EMailer</strong> is a powerful email mass sender application that allows you to:</p>
                <ul>
                    <li>Compose rich-formatted HTML emails with images and tables</li>
                    <li>Manage recipient lists with up to thousands of email addresses</li>
                    <li>Send emails individually or in batch mode</li>
                    <li>Save and load message templates for reuse</li>
                    <li>Export emails to PDF, Word, HTML, and TXT formats</li>
                    <li>Integrate with Facebook for social media posting</li>
                </ul>
            </div>

            <div id="help-interface" class="help-section">
                <h5>2. User Interface</h5>
                <p>The interface is divided into two main panels:</p>
                <ul>
                    <li><strong>Left Panel - Compose Email:</strong> Subject line, rich text editor, formatting toolbar, template buttons, send options</li>
                    <li><strong>Right Panel - Recipients:</strong> Email list with search, select all/none, add/edit buttons</li>
                    <li><strong>Resizable Divider:</strong> Drag the purple vertical line between panels to adjust their widths</li>
                    <li><strong>Menu Bar:</strong> File, Edit, View, Help menus in the Compose Email header</li>
                    <li><strong>Status Bar:</strong> Fixed at bottom showing connection status and progress</li>
                </ul>
            </div>

            <div id="help-composing" class="help-section">
                <h5>3. Composing Messages</h5>
                <p><strong>Subject Line:</strong></p>
                <ul>
                    <li>Auto-filled with next Sunday's date on page load</li>
                    <li>Edit to customize your message subject</li>
                    <li>Required for sending emails</li>
                </ul>
                <p><strong>Message Editor:</strong></p>
                <ul>
                    <li>Rich text editing with full HTML support</li>
                    <li>Click in the editor and start typing</li>
                    <li>Paste formatted content from Word, web pages, or other sources</li>
                    <li>Use toolbar buttons or keyboard shortcuts for formatting</li>
                </ul>
            </div>

            <div id="help-formatting" class="help-section">
                <h5>4. Text Formatting</h5>
                <p><strong>Font Section:</strong></p>
                <ul>
                    <li><strong>Font Family:</strong> Arial, Calibri, Times New Roman, Verdana, Georgia, etc.</li>
                    <li><strong>Font Size:</strong> 8pt to 36pt (default: 14pt)</li>
                    <li><strong>Grow/Shrink:</strong> Increase or decrease font size incrementally</li>
                </ul>
                <p><strong>Format Section:</strong></p>
                <ul>
                    <li><strong>Bold (Ctrl+B):</strong> Make text bold</li>
                    <li><strong>Italic (Ctrl+I):</strong> Make text italic</li>
                    <li><strong>Underline (Ctrl+U):</strong> Underline text</li>
                    <li><strong>Strikethrough:</strong> Strike through text</li>
                    <li><strong>Subscript/Superscript:</strong> For chemical formulas or footnotes</li>
                    <li><strong>Clear Formatting:</strong> Remove all formatting from selected text</li>
                </ul>
                <p><strong>Colors:</strong></p>
                <ul>
                    <li><strong>Text Color:</strong> Click color picker to choose text color</li>
                    <li><strong>Background Color:</strong> Highlight text with background color</li>
                </ul>
                <p><strong>Paragraph:</strong></p>
                <ul>
                    <li><strong>Alignment:</strong> Left, Center, Right, Justify</li>
                    <li><strong>Lists:</strong> Bullet list or numbered list</li>
                    <li><strong>Indentation:</strong> Increase or decrease indent</li>
                    <li><strong>Line Spacing:</strong> Single (1.0), 1.15, 1.5, Double (2.0)</li>
                </ul>
            </div>

            <div id="help-images" class="help-section">
                <h5>5. Working with Images</h5>
                <p><strong>Inserting Images:</strong></p>
                <ul>
                    <li><strong>Insert Image Button:</strong> Click to browse and select an image file</li>
                    <li><strong>Paste:</strong> Copy image from anywhere and paste (Ctrl+V) into editor</li>
                    <li><strong>Drag & Drop:</strong> Drag image files directly into the editor</li>
                </ul>
                <p><strong>Image Formatting:</strong></p>
                <ul>
                    <li><strong>Resize:</strong> Click image to select, drag corner handles to resize</li>
                    <li><strong>Float Left:</strong> Text wraps around image on right side</li>
                    <li><strong>Float Right:</strong> Text wraps around image on left side</li>
                    <li><strong>Inline:</strong> Image stays inline with text (no wrapping)</li>
                    <li><strong>Delete:</strong> Select image and press Delete key</li>
                </ul>
                <p><strong>Supported Formats:</strong> JPG, PNG, GIF, BMP, WebP</p>
            </div>

            <div id="help-tables" class="help-section">
                <h5>6. Creating Tables</h5>
                <p><strong>Insert Table:</strong></p>
                <ul>
                    <li>Click <strong>Insert Table</strong> button in toolbar</li>
                    <li>Enter number of rows and columns</li>
                    <li>Table is inserted at cursor position</li>
                    <li>Default width: 100% of editor width</li>
                </ul>
                <p><strong>Editing Tables:</strong></p>
                <ul>
                    <li><strong>Resize Table:</strong> Drag table borders to adjust width/height</li>
                    <li><strong>Resize Columns:</strong> Drag vertical borders between cells</li>
                    <li><strong>Resize Rows:</strong> Drag horizontal borders between cells</li>
                    <li><strong>Add Content:</strong> Click in cell and type or paste</li>
                    <li><strong>Format Cells:</strong> Use formatting toolbar on cell content</li>
                    <li><strong>Nested Tables:</strong> Insert a table inside another table cell</li>
                </ul>
                <p><strong>Table Borders:</strong></p>
                <ul>
                    <li>Default: 1px solid black border</li>
                    <li>Toggle visibility with "Show/Hide Invisible Symbols" (View menu)</li>
                </ul>
            </div>

            <div id="help-templates" class="help-section">
                <h5>7. Template Management</h5>
                <p><strong>Saving Templates:</strong></p>
                <ul>
                    <li>Compose your message (subject and content)</li>
                    <li>Click <strong>File → Save Template → Save to Slot 1-5</strong></li>
                    <li>Or use <strong>Save</strong> buttons at bottom of editor</li>
                    <li>Templates saved to browser localStorage (persistent)</li>
                    <li>Up to 5 template slots available</li>
                </ul>
                <p><strong>Loading Templates:</strong></p>
                <ul>
                    <li>Click <strong>File → Load Template → Load from Slot 1-5</strong></li>
                    <li>Or use <strong>Load</strong> buttons at bottom of editor</li>
                    <li>Previous content is replaced with template</li>
                    <li>All formatting, images, and tables are preserved</li>
                </ul>
                <p><strong>Template Details Logged:</strong></p>
                <ul>
                    <li>Check browser console (F12) for detailed template information</li>
                    <li>Server logs include timestamp, subject, image count, table count</li>
                </ul>
            </div>

            <div id="help-recipients" class="help-section">
                <h5>8. Managing Recipients</h5>
                <p><strong>Email List:</strong></p>
                <ul>
                    <li>List loaded from <code>email-list.txt</code> file on server</li>
                    <li>Each line = one email address</li>
                    <li>Total count shown in header badge</li>
                </ul>
                <p><strong>Search & Filter:</strong></p>
                <ul>
                    <li>Type in search box to filter email list</li>
                    <li>Matches any part of email address</li>
                    <li>Case-insensitive search</li>
                </ul>
                <p><strong>Selection:</strong></p>
                <ul>
                    <li><strong>Select All:</strong> Check all visible emails</li>
                    <li><strong>Select None:</strong> Uncheck all emails</li>
                    <li><strong>Individual:</strong> Click checkbox next to email address</li>
                    <li>Selected count shown in status bar</li>
                </ul>
                <p><strong>Add Email:</strong></p>
                <ul>
                    <li>Click <strong>Add Email</strong> button</li>
                    <li>Enter email address in dialog</li>
                    <li>Email is added to list and file</li>
                </ul>
                <p><strong>Bulk Edit:</strong></p>
                <ul>
                    <li>Click <strong>Bulk Edit</strong> button</li>
                    <li>Edit entire list in text area (one email per line)</li>
                    <li>Click Save to update list</li>
                </ul>
            </div>

            <div id="help-sending" class="help-section">
                <h5>9. Sending Emails</h5>
                <p><strong>Send Modes:</strong></p>
                <ul>
                    <li><strong>One by One:</strong> Sends emails individually with delay between each (prevents spam detection)</li>
                    <li><strong>Batch:</strong> Sends emails in batches (faster but may trigger spam filters)</li>
                    <li><strong> Delay (applied for both):</strong> Configurable delay in seconds between sends (default: 2s)</li>
                </ul>
                <p><strong>Test Email:</strong></p>
                <ul>
                    <li>Click <strong>Send Test</strong> button</li>
                    <li>Sent to first email in list</li>
                    <li>Verify message looks correct before sending to all</li>
                </ul>
                <p><strong>Send to All:</strong></p>
                <ul>
                    <li>Select recipients using checkboxes</li>
                    <li>Click <strong>Send to All Selected</strong> button</li>
                    <li>Progress shown in status line (sent/success/failed counts)</li>
                    <li>Click <strong>Stop</strong> to cancel in-progress send</li>
                </ul>
                <p><strong>Connection Status:</strong></p>
                <ul>
                    <li><strong>Green:</strong> Connected to mail server</li>
                    <li><strong>Red:</strong> Disconnected or error</li>
                    <li>Automatically checked every 20 seconds</li>
                </ul>
                <p><strong>Social Media:</strong></p>
                <ul>
                    <li>Check <strong>Publish to Facebook</strong> to post message to Facebook</li>
                    <li>Requires Facebook configuration in application.properties</li>
                </ul>
            </div>

            <div id="help-export" class="help-section">
                <h5>10. Export Options</h5>
                <p><strong>File → Export Menu:</strong></p>
                <ul>
                    <li><strong>Export to TXT:</strong> Plain text without formatting</li>
                    <li><strong>Export to PDF:</strong> Professional PDF document with images and formatting</li>
                    <li><strong>Export to HTML:</strong> Complete HTML file with embedded images</li>
                    <li><strong>Export to Microsoft Word:</strong> DOCX file compatible with Word 2007+</li>
                </ul>
                <p><strong>Print:</strong></p>
                <ul>
                    <li>Click <strong>File → Print</strong></li>
                    <li>Prints only the message editor content (not the entire page)</li>
                    <li>Use browser print dialog for options (pages, copies, etc.)</li>
                </ul>
            </div>

            <div id="help-troubleshooting" class="help-section">
                <h5>11. Troubleshooting</h5>
                <p><strong>Connection Failed:</strong></p>
                <ul>
                    <li>Check SMTP server configuration in application.properties</li>
                    <li>Verify username/password are correct</li>
                    <li>Check firewall/antivirus settings</li>
                    <li>Try test connection button</li>
                </ul>
                <p><strong>Images Not Showing:</strong></p>
                <ul>
                    <li>Images are embedded as base64 (no external URLs needed)</li>
                    <li>Large images may take time to load</li>
                    <li>Check browser console (F12) for errors</li>
                </ul>
                <p><strong>Template Not Saving:</strong></p>
                <ul>
                    <li>Templates saved to browser localStorage</li>
                    <li>Check browser storage quota (may be full)</li>
                    <li>Try incognito/private mode to test</li>
                </ul>
                <p><strong>Emails Marked as Spam:</strong></p>
                <ul>
                    <li>Use "One by One" mode with 2+ second delay</li>
                    <li>Add SPF/DKIM records to your domain</li>
                    <li>Avoid spam trigger words in subject/content</li>
                    <li>Test with small batches first</li>
                </ul>
                <p><strong>Getting Help:</strong></p>
                <ul>
                    <li>Check application logs in logs/ directory</li>
                    <li>Browser console (F12) shows client-side errors</li>
                    <li>Server console shows server-side errors</li>
                </ul>
            </div>

            <div class="help-footer">
                <hr>
                <p><strong>Version:</strong> 0.0.13-SNAPSHOT</p>
                <p><strong>Java:</strong> 21 | <strong>Spring Boot:</strong> 4.0.0</p>
                <p><strong>© 2025 KiSoft</strong></p>
            </div>
        </div>
    `;

    const modalEl = document.getElementById('resultModal');
    if (!modalEl) {
        alert('Help documentation is not available. Please check the browser console.');
        return;
    }

    document.getElementById('resultModalTitle').innerHTML = '<i class="fas fa-book"></i> Detailed User Guide';
    document.getElementById('resultModalBody').innerHTML = helpContent;

    const header = document.getElementById('resultModalHeader');
    header.className = 'modal-header bg-primary text-white';

    const modal = new bootstrap.Modal(modalEl);
    modal.show();
}

/**
 * Prints only the message editor content
 */
function printMessageEditor() {
    const subject = document.getElementById('subject').value || 'Untitled';
    const editorContent = document.getElementById('editor').innerHTML;

    // Create a new window for printing
    const printWindow = window.open('', '_blank');

    const printContent = `
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>${subject}</title>
            <style>
                @media print {
                    body { margin: 0; padding: 20px; }
                    @page { margin: 1cm; }
                }
                body {
                    font-family: Calibri, Arial, sans-serif;
                    font-size: 14pt;
                    line-height: 1.6;
                    color: #000;
                    background: white;
                }
                h1 {
                    font-size: 18pt;
                    margin-bottom: 20px;
                    border-bottom: 2px solid #667eea;
                    padding-bottom: 10px;
                }
                img {
                    max-width: 100%;
                    height: auto;
                    page-break-inside: avoid;
                }
                table {
                    border-collapse: collapse;
                    width: 100%;
                    margin: 10px 0;
                    page-break-inside: avoid;
                }
                table, th, td {
                    border: 1px solid #000;
                }
                th, td {
                    padding: 8px;
                    text-align: left;
                }
            </style>
        </head>
        <body>
            <h1>${subject}</h1>
            <div class="content">
                ${editorContent}
            </div>
        </body>
        </html>
    `;

    printWindow.document.write(printContent);
    printWindow.document.close();

    // Wait for images to load before printing
    printWindow.onload = function() {
        setTimeout(() => {
            printWindow.print();
            printWindow.close();
        }, 250);
    };
}

/**
 * Sets the theme
 * @param {string} t - 'dark' or 'light'
 */
function setTheme(t){ if(t==='dark'){ toggleTheme('dark'); } else { toggleTheme('light'); } }

/**
 * Downloads a Blob object as a file
 * @param {Blob} blob - The Blob to download
 * @param {string} filename - The name of the file to download as
 */
function downloadBlob(blob, filename){ const a=document.createElement('a'); a.href=URL.createObjectURL(blob); a.download=filename; document.body.appendChild(a); a.click(); setTimeout(()=>{URL.revokeObjectURL(a.href); a.remove();},0); }

/**
 * Shows an info modal with title and body text
 * @param {string} title - The modal title
 * @param {string} body - The modal body text
 */
function showInfoModal(title, body){ const modalEl=document.getElementById('resultModal'); if(!modalEl){ alert(title+"\n\n"+body); return; } document.getElementById('resultModalTitle').innerText=title; document.getElementById('resultModalBody').innerText=body; const header=document.getElementById('resultModalHeader'); header.className='modal-header bg-info text-white'; const m=new bootstrap.Modal(modalEl); m.show(); }

/**
 * Copies the selected photo (image) to the clipboard
 */
function photoCopy(){ const img=getSelectedImage(); if(img){ navigator.clipboard.writeText(img.src||''); showInfoModal('Copy Photo','Image source copied to clipboard.'); } }

/**
 * Cuts the selected photo (image) from the editor
 */
function photoCut(){ const img=getSelectedImage(); if(img){ img.remove(); showInfoModal('Cut Photo','Image removed from editor.'); } }

/**
 * Pastes a photo (image) from the clipboard into the editor
 */
function photoPaste(){ /* rely on normal paste handler for images */ showInfoModal('Paste Photo','Use Ctrl+V to paste images.'); }

/**
 * Gets the currently selected image element
 * @returns {HTMLImageElement|null} The selected image element, or null if none
 */
function getSelectedImage(){ const sel=window.getSelection(); if(!sel||sel.rangeCount===0) return null; const node=sel.anchorNode; if(!node) return null; return node.nodeType===1 && node.tagName==='IMG' ? node : (node.parentElement && node.parentElement.tagName==='IMG' ? node.parentElement : null); }

