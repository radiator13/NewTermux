//! Terminal emulator core implementation.
//!
//! Manages the screen buffer, cursor, colors, and processes bytes from the PTY.
//! This is the Rust port of the Kotlin `TerminalEmulator` class.

use crate::terminal_buffer::TerminalBuffer;
use crate::text_style;

/// Cursor style constants (matching Kotlin TerminalEmulator).
pub const CURSOR_STYLE_BLOCK: i32 = 0;
pub const CURSOR_STYLE_UNDERLINE: i32 = 1;
pub const CURSOR_STYLE_BAR: i32 = 2;

/// Default scroll transcript rows.
const DEFAULT_TRANSCRIPT_ROWS: i32 = 10000;

/// Callback flags returned by `get_flags()`.
const FLAG_BELL: u32 = 1;
const FLAG_TITLE_CHANGED: u32 = 2;
const FLAG_COLORS_CHANGED: u32 = 4;
const FLAG_CURSOR_STATE_CHANGED: u32 = 8;

/// Parser states for ANSI escape sequence processing.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum EscapeState {
    /// Normal ground state — processing printable characters.
    Normal,
    /// Seen ESC (0x1b), waiting for next byte.
    Escape,
    /// Seen ESC [ — collecting CSI parameters.
    Csi,
    /// Seen ESC ] — operating system command.
    Osc,
}

/// A terminal emulator that processes bytes from a PTY and maintains a screen buffer.
pub struct TerminalEmulator {
    /// The main screen buffer.
    screen: TerminalBuffer,
    /// The alternate screen buffer (used by programs like vim, less).
    alt_screen: Option<TerminalBuffer>,
    /// Whether the alternate buffer is currently active.
    alt_buffer_active: bool,

    /// Cursor row (0-based, from top of visible screen).
    cursor_row: i32,
    /// Cursor column (0-based, from left of screen).
    cursor_col: i32,
    /// Cursor style (block, underline, bar).
    cursor_style: i32,
    /// Whether the cursor is visible (DECSET 25).
    cursor_visible: bool,

    /// Number of columns.
    columns: i32,
    /// Number of visible rows.
    rows: i32,
    /// Cell width in pixels (for mouse coordinate calculation).
    cell_width: i32,
    /// Cell height in pixels.
    cell_height: i32,

    /// ANSI color palette (256 indexed + foreground + background + cursor = 259).
    colors: [i32; 259],
    /// Cursor color.
    #[allow(dead_code)]
    cursor_color: i32,

    /// Reverse video mode (DECSET 5).
    reverse_video: bool,

    /// Mouse tracking active (DECSET 1000/1002).
    mouse_tracking: bool,

    /// Terminal window title.
    title: String,

    /// Output buffer: bytes to write back to the PTY (e.g., cursor position reports).
    output_buffer: Vec<u8>,

    /// Callback flags (cleared on read).
    flags: u32,

    /// Scroll counter for tracking scroll events.
    scroll_counter: i32,

    /// Current text style (foreground, background, effects).
    current_style: u64,

    /// Top margin for scroll region (0-based).
    scroll_region_top: i32,
    /// Bottom margin for scroll region (exclusive, 0-based).
    scroll_region_bottom: i32,

    /// Current escape sequence parser state.
    escape_state: EscapeState,
    /// CSI parameter accumulator (digits collected so far).
    csi_params: Vec<i32>,
    /// Whether we're currently collecting a CSI sequence.
    csi_intermediate: Vec<u8>,
}

impl TerminalEmulator {
    /// Create a new terminal emulator with the given dimensions.
    pub fn new(columns: i32, rows: i32, cell_width: i32, cell_height: i32, transcript_rows: i32) -> Box<Self> {
        let total_rows = rows + transcript_rows.max(0);
        let screen = TerminalBuffer::new(columns, total_rows, rows);

        // Initialize default ANSI colors (first 16 are the standard palette).
        let mut colors = [0i32; 259];
        // Standard 16 ANSI colors
        colors[0] = 0xFF000000u32 as i32; // black
        colors[1] = 0xFFFF0000u32 as i32; // red
        colors[2] = 0xFF00FF00u32 as i32; // green
        colors[3] = 0xFFFFFF00u32 as i32; // yellow
        colors[4] = 0xFF0000FFu32 as i32; // blue
        colors[5] = 0xFFFF00FFu32 as i32; // magenta
        colors[6] = 0xFF00FFFFu32 as i32; // cyan
        colors[7] = 0xFFC0C0C0u32 as i32; // white
        colors[8] = 0xFF808080u32 as i32; // bright black
        colors[9] = 0xFFFF8080u32 as i32; // bright red
        colors[10] = 0xFF80FF80u32 as i32; // bright green
        colors[11] = 0xFFFFFF80u32 as i32; // bright yellow
        colors[12] = 0xFF8080FFu32 as i32; // bright blue
        colors[13] = 0xFFFF80FFu32 as i32; // bright magenta
        colors[14] = 0xFF80FFFFu32 as i32; // bright cyan
        colors[15] = 0xFFFFFFFFu32 as i32; // bright white
        // Default foreground (index 256)
        colors[text_style::COLOR_INDEX_FOREGROUND as usize] = 0xFFC0C0C0u32 as i32;
        // Default background (index 257)
        colors[text_style::COLOR_INDEX_BACKGROUND as usize] = 0xFF000000u32 as i32;
        // Cursor color (index 258)
        colors[text_style::COLOR_INDEX_CURSOR as usize] = 0xFFFFFFFFu32 as i32;

        Box::new(Self {
            screen,
            alt_screen: None,
            alt_buffer_active: false,
            cursor_row: 0,
            cursor_col: 0,
            cursor_style: CURSOR_STYLE_BLOCK,
            cursor_visible: true,
            columns,
            rows,
            cell_width,
            cell_height,
            colors,
            cursor_color: 0xFFFFFFFFu32 as i32,
            reverse_video: false,
            mouse_tracking: false,
            title: String::new(),
            output_buffer: Vec::with_capacity(256),
            flags: 0,
            scroll_counter: 0,
            current_style: text_style::NORMAL,
            scroll_region_top: 0,
            scroll_region_bottom: rows,
            escape_state: EscapeState::Normal,
            csi_params: Vec::new(),
            csi_intermediate: Vec::new(),
        })
    }

    /// Get a reference to the active screen buffer (main or alternate).
    pub fn screen(&self) -> &TerminalBuffer {
        if self.alt_buffer_active {
            self.alt_screen.as_ref().unwrap_or(&self.screen)
        } else {
            &self.screen
        }
    }

    /// Get a mutable reference to the active screen buffer.
    fn screen_mut(&mut self) -> &mut TerminalBuffer {
        if self.alt_buffer_active {
            self.alt_screen.as_mut().unwrap_or(&mut self.screen)
        } else {
            &mut self.screen
        }
    }

    /// Process raw bytes from the PTY.
    pub fn process_bytes(&mut self, bytes: &[u8]) {
        for &byte in bytes {
            self.process_byte(byte);
        }
    }

    /// Process a single byte through the escape sequence state machine.
    fn process_byte(&mut self, byte: u8) {
        match self.escape_state {
            EscapeState::Normal => self.process_normal(byte),
            EscapeState::Escape => self.process_escape(byte),
            EscapeState::Csi => self.process_csi(byte),
            EscapeState::Osc => self.process_osc(byte),
        }
    }

    /// Process a byte in normal (ground) state.
    fn process_normal(&mut self, byte: u8) {
        match byte {
            // ESC
            0x1b => {
                self.escape_state = EscapeState::Escape;
            }
            // BEL (bell)
            0x07 => {
                self.flags |= FLAG_BELL;
            }
            // BS (backspace)
            0x08 => {
                if self.cursor_col > 0 {
                    self.cursor_col -= 1;
                }
            }
            // HT (horizontal tab)
            0x09 => {
                let next_tab = (self.cursor_col / 8 + 1) * 8;
                self.cursor_col = next_tab.min(self.columns - 1);
            }
            // LF (line feed), VT (vertical tab), FF (form feed)
            0x0a | 0x0b | 0x0c => {
                self.line_feed();
            }
            // CR (carriage return)
            0x0d => {
                self.cursor_col = 0;
            }
            // SO (shift out) / SI (shift in) — ignored for now
            0x0e | 0x0f => {}
            // Printable ASCII and UTF-8 lead bytes
            0x20..=0x7e => {
                self.put_char(byte as u32);
            }
            // Ignore other control characters
            _ => {}
        }
    }

    /// Process a byte in ESC state.
    fn process_escape(&mut self, byte: u8) {
        match byte {
            // CSI: ESC [
            b'[' => {
                self.escape_state = EscapeState::Csi;
                self.csi_params.clear();
                self.csi_intermediate.clear();
                self.csi_params.push(0);
            }
            // OSC: ESC ]
            b']' => {
                self.escape_state = EscapeState::Osc;
            }
            // DECSC: ESC 7 — save cursor
            b'7' => {
                // TODO: save cursor state
                self.escape_state = EscapeState::Normal;
            }
            // DECRC: ESC 8 — restore cursor
            b'8' => {
                // TODO: restore cursor state
                self.escape_state = EscapeState::Normal;
            }
            // RIS: ESC c — reset
            b'c' => {
                self.reset();
                self.escape_state = EscapeState::Normal;
            }
            // ESC E — next line (NEL)
            b'E' => {
                self.cursor_col = 0;
                self.line_feed();
                self.escape_state = EscapeState::Normal;
            }
            // ESC D — index (IND) — move cursor down
            b'D' => {
                self.line_feed();
                self.escape_state = EscapeState::Normal;
            }
            // ESC M — reverse index (RI) — move cursor up
            b'M' => {
                self.reverse_line_feed();
                self.escape_state = EscapeState::Normal;
            }
            // Unknown ESC sequence — return to normal
            _ => {
                self.escape_state = EscapeState::Normal;
            }
        }
    }

    /// Process a byte in CSI state.
    fn process_csi(&mut self, byte: u8) {
        match byte {
            // Parameter bytes: 0x30-0x3f (0-9, ;, :, <, =, >, ?)
            0x30..=0x3f => {
                match byte {
                    b'0'..=b'9' => {
                        let last = self.csi_params.last_mut().unwrap();
                        *last = *last * 10 + (byte - b'0') as i32;
                    }
                    b';' => {
                        self.csi_params.push(0);
                    }
                    b'?' => {
                        // DEC private mode prefix
                        self.csi_intermediate.push(byte);
                    }
                    _ => {
                        self.csi_intermediate.push(byte);
                    }
                }
            }
            // Intermediate bytes: 0x20-0x2f
            0x20..=0x2f => {
                self.csi_intermediate.push(byte);
            }
            // Final byte: 0x40-0x7e
            0x40..=0x7e => {
                self.dispatch_csi(byte);
                self.escape_state = EscapeState::Normal;
            }
            // Abort on anything else
            _ => {
                self.escape_state = EscapeState::Normal;
            }
        }
    }

    /// Dispatch a completed CSI sequence.
    fn dispatch_csi(&mut self, final_byte: u8) {
        let params: Vec<i32> = self.csi_params.clone();
        let is_dec = !self.csi_intermediate.is_empty() && self.csi_intermediate[0] == b'?';

        match final_byte {
            // CUU: Cursor Up
            b'A' => {
                let n = params.first().copied().unwrap_or(1).max(1);
                self.cursor_row = (self.cursor_row - n).max(0);
            }
            // CUD: Cursor Down
            b'B' => {
                let n = params.first().copied().unwrap_or(1).max(1);
                self.cursor_row = (self.cursor_row + n).min(self.rows - 1);
            }
            // CUF: Cursor Forward
            b'C' => {
                let n = params.first().copied().unwrap_or(1).max(1);
                self.cursor_col = (self.cursor_col + n).min(self.columns - 1);
            }
            // CUB: Cursor Back
            b'D' => {
                let n = params.first().copied().unwrap_or(1).max(1);
                self.cursor_col = (self.cursor_col - n).max(0);
            }
            // CUP: Cursor Position (row;col)
            b'H' | b'f' => {
                let r = params.first().copied().unwrap_or(1).max(1) - 1;
                let c = params.get(1).copied().unwrap_or(1).max(1) - 1;
                self.cursor_row = r.min(self.rows - 1);
                self.cursor_col = c.min(self.columns - 1);
            }
            // ED: Erase in Display
            b'J' => {
                let mode = params.first().copied().unwrap_or(0);
                self.erase_display(mode);
            }
            // EL: Erase in Line
            b'K' => {
                let mode = params.first().copied().unwrap_or(0);
                self.erase_line(mode);
            }
            // SGR: Select Graphic Rendition
            b'm' => {
                self.process_sgr(&params);
            }
            // DECSET/DECRST: private mode set/reset
            b'h' if is_dec => {
                for mode in params.iter().copied() {
                    self.dec_set(mode, true);
                }
            }
            b'l' if is_dec => {
                for mode in params.iter().copied() {
                    self.dec_set(mode, false);
                }
            }
            // DSR: Device Status Report
            b'n' => {
                let mode = params.first().copied().unwrap_or(0);
                if mode == 6 {
                    // CPR: Cursor Position Report
                    let report = format!("\x1b[{};{}R", self.cursor_row + 1, self.cursor_col + 1);
                    self.output_buffer.extend_from_slice(report.as_bytes());
                }
            }
            // DECSTBM: Set Top and Bottom Margins (scroll region)
            b'r' => {
                let top = params.first().copied().unwrap_or(1).max(1) - 1;
                let bottom = params.get(1).copied().unwrap_or(self.rows).max(1);
                if top < bottom && bottom <= self.rows {
                    self.scroll_region_top = top;
                    self.scroll_region_bottom = bottom;
                    self.cursor_row = 0;
                    self.cursor_col = 0;
                }
            }
            // ICH: Insert Character(s)
            b'@' => {
                // TODO: implement
            }
            // DA: Device Attributes
            b'c' => {
                // Report VT102
                self.output_buffer.extend_from_slice(b"\x1b[?6c");
            }
            _ => {
                // Unhandled CSI sequence — ignore
            }
        }
    }

    /// Process an OSC (Operating System Command) sequence.
    fn process_osc(&mut self, byte: u8) {
        match byte {
            0x07 => {
                // BEL terminates OSC
                self.finish_osc();
                self.escape_state = EscapeState::Normal;
            }
            0x1b => {
                // Might be ST (ESC \) — for now just finish
                self.finish_osc();
                self.escape_state = EscapeState::Escape;
            }
            _ => {
                // Accumulate
                // We use a simple approach: store in the CSI params vector (reused)
                self.csi_intermediate.push(byte);
            }
        }
    }

    /// Finish processing an OSC sequence.
    fn finish_osc(&mut self) {
        let data = &self.csi_intermediate;
        // OSC sequences are typically: Ps ; Pt BEL
        // Where Ps = 0 (icon name + title), 1 (icon name), 2 (window title)
        if let Some(pos) = data.iter().position(|&b| b == b';') {
            let ps_str = std::str::from_utf8(&data[..pos]).unwrap_or("");
            let pt = &data[pos + 1..];
            if let Ok(ps) = ps_str.parse::<i32>() {
                if ps == 0 || ps == 2 {
                    if let Ok(new_title) = std::str::from_utf8(pt) {
                        self.title = new_title.to_string();
                        self.flags |= FLAG_TITLE_CHANGED;
                    }
                }
            }
        }
        self.csi_intermediate.clear();
    }

    /// Process SGR (Select Graphic Rendition) parameters.
    fn process_sgr(&mut self, params: &[i32]) {
        if params.is_empty() {
            self.current_style = text_style::NORMAL;
            return;
        }

        let mut i = 0;
        while i < params.len() {
            match params[i] {
                0 => self.current_style = text_style::NORMAL,
                1 => self.current_style |= text_style::CHARACTER_ATTRIBUTE_BOLD as u64,
                2 => self.current_style |= text_style::CHARACTER_ATTRIBUTE_DIM as u64,
                3 => self.current_style |= text_style::CHARACTER_ATTRIBUTE_ITALIC as u64,
                4 => self.current_style |= text_style::CHARACTER_ATTRIBUTE_UNDERLINE as u64,
                5 | 6 => self.current_style |= text_style::CHARACTER_ATTRIBUTE_BLINK as u64,
                7 => self.current_style |= text_style::CHARACTER_ATTRIBUTE_INVERSE as u64,
                8 => self.current_style |= text_style::CHARACTER_ATTRIBUTE_INVISIBLE as u64,
                9 => self.current_style |= text_style::CHARACTER_ATTRIBUTE_STRIKETHROUGH as u64,
                22 => {
                    self.current_style &= !(text_style::CHARACTER_ATTRIBUTE_BOLD as u64);
                    self.current_style &= !(text_style::CHARACTER_ATTRIBUTE_DIM as u64);
                }
                23 => self.current_style &= !(text_style::CHARACTER_ATTRIBUTE_ITALIC as u64),
                24 => self.current_style &= !(text_style::CHARACTER_ATTRIBUTE_UNDERLINE as u64),
                25 => self.current_style &= !(text_style::CHARACTER_ATTRIBUTE_BLINK as u64),
                27 => self.current_style &= !(text_style::CHARACTER_ATTRIBUTE_INVERSE as u64),
                28 => self.current_style &= !(text_style::CHARACTER_ATTRIBUTE_INVISIBLE as u64),
                29 => self.current_style &= !(text_style::CHARACTER_ATTRIBUTE_STRIKETHROUGH as u64),
                // Foreground colors
                30..=37 => {
                    let color_idx = params[i] - 30;
                    self.current_style = (self.current_style & !(0x1FFu64 << 40))
                        | ((color_idx as u64) << 40);
                }
                38 => {
                    // Extended foreground: 38;5;n (256-color) or 38;2;r;g;b (truecolor)
                    if i + 1 < params.len() && params[i + 1] == 5 && i + 2 < params.len() {
                        let color_idx = params[i + 2];
                        self.current_style = (self.current_style & !(0x1FFu64 << 40))
                            | ((color_idx as u64 & 0x1FF) << 40);
                        i += 2;
                    } else if i + 1 < params.len() && params[i + 1] == 2 && i + 4 < params.len() {
                        let r = params[i + 2] as u32;
                        let g = params[i + 3] as u32;
                        let b = params[i + 4] as u32;
                        let color = (0xFF000000u32 | (r << 16) | (g << 8) | b) as i32;
                        self.current_style = (self.current_style & !(0x1FFu64 << 40))
                            | ((color as u64 & 0x00FFFFFF) << 40)
                            | (text_style::CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND as u64);
                        i += 4;
                    }
                }
                39 => {
                    // Default foreground
                    self.current_style = (self.current_style & !(0x1FFu64 << 40))
                        | ((text_style::COLOR_INDEX_FOREGROUND as u64) << 40);
                }
                // Background colors
                40..=47 => {
                    let color_idx = params[i] - 40;
                    self.current_style = (self.current_style & !(0x1FFu64 << 16))
                        | ((color_idx as u64) << 16);
                }
                48 => {
                    // Extended background: 48;5;n or 48;2;r;g;b
                    if i + 1 < params.len() && params[i + 1] == 5 && i + 2 < params.len() {
                        let color_idx = params[i + 2];
                        self.current_style = (self.current_style & !(0x1FFu64 << 16))
                            | ((color_idx as u64 & 0x1FF) << 16);
                        i += 2;
                    } else if i + 1 < params.len() && params[i + 1] == 2 && i + 4 < params.len() {
                        let r = params[i + 2] as u32;
                        let g = params[i + 3] as u32;
                        let b = params[i + 4] as u32;
                        let color = (0xFF000000u32 | (r << 16) | (g << 8) | b) as i32;
                        self.current_style = (self.current_style & !(0x1FFu64 << 16))
                            | ((color as u64 & 0x00FFFFFF) << 16)
                            | (text_style::CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND as u64);
                        i += 4;
                    }
                }
                49 => {
                    // Default background
                    self.current_style = (self.current_style & !(0x1FFu64 << 16))
                        | ((text_style::COLOR_INDEX_BACKGROUND as u64) << 16);
                }
                // Bright foreground
                90..=97 => {
                    let color_idx = params[i] - 90 + 8;
                    self.current_style = (self.current_style & !(0x1FFu64 << 40))
                        | ((color_idx as u64) << 40);
                }
                // Bright background
                100..=107 => {
                    let color_idx = params[i] - 100 + 8;
                    self.current_style = (self.current_style & !(0x1FFu64 << 16))
                        | ((color_idx as u64) << 16);
                }
                _ => {}
            }
            i += 1;
        }
    }

    /// Set/reset DEC private modes.
    fn dec_set(&mut self, mode: i32, set: bool) {
        match mode {
            // DECSCNM: Reverse video
            5 => {
                self.reverse_video = set;
                self.flags |= FLAG_COLORS_CHANGED;
            }
            // DECTCEM: Cursor visibility
            25 => {
                self.cursor_visible = set;
                self.flags |= FLAG_CURSOR_STATE_CHANGED;
            }
            // DECCKM: Cursor keys mode (application vs normal)
            1 => {
                // TODO: track cursor key mode
            }
            // DECOM: Origin mode
            6 => {
                // TODO: track origin mode
            }
            // Mouse tracking (X10)
            9 => {
                if !set {
                    self.mouse_tracking = false;
                }
            }
            // Mouse tracking (normal)
            1000 => {
                self.mouse_tracking = set;
            }
            // Mouse tracking (button events)
            1002 => {
                self.mouse_tracking = set;
            }
            // Alternate screen buffer
            47 | 1049 => {
                if set && !self.alt_buffer_active {
                    // Switch to alternate screen
                    if self.alt_screen.is_none() {
                        let total_rows = self.rows + DEFAULT_TRANSCRIPT_ROWS;
                        self.alt_screen = Some(TerminalBuffer::new(self.columns, total_rows, self.rows));
                    }
                    self.alt_buffer_active = true;
                    self.cursor_row = 0;
                    self.cursor_col = 0;
                } else if !set && self.alt_buffer_active {
                    // Switch back to main screen
                    self.alt_buffer_active = false;
                }
            }
            // Bracketed paste mode
            2004 => {
                // TODO: track bracketed paste
            }
            _ => {}
        }
    }

    /// Write a printable character at the current cursor position and advance.
    fn put_char(&mut self, code_point: u32) {
        if self.cursor_col >= self.columns {
            self.cursor_col = 0;
            self.line_feed();
        }
        let col = self.cursor_col;
        let row = self.cursor_row;
        let style = self.current_style;
        self.screen_mut().set_char(col, row, code_point, style);
        self.cursor_col += 1;
    }

    /// Move cursor down one line, scrolling if at bottom of scroll region.
    fn line_feed(&mut self) {
        if self.cursor_row >= self.scroll_region_bottom - 1 {
            let style = text_style::encode(
                text_style::COLOR_INDEX_FOREGROUND,
                text_style::COLOR_INDEX_BACKGROUND,
                0,
            );
            let top = self.scroll_region_top;
            let bottom = self.scroll_region_bottom;
            self.screen_mut().scroll_down_one_line(top, bottom, style);
            self.scroll_counter += 1;
        } else {
            self.cursor_row += 1;
        }
    }

    /// Move cursor up one line, scrolling if at top of scroll region.
    fn reverse_line_feed(&mut self) {
        if self.cursor_row <= self.scroll_region_top {
            // TODO: scroll down
        } else {
            self.cursor_row -= 1;
        }
    }

    /// Erase in display.
    fn erase_display(&mut self, mode: i32) {
        let style = self.current_style;
        let cols = self.columns;
        let rows = self.rows;
        let cursor_col = self.cursor_col;
        let cursor_row = self.cursor_row;
        match mode {
            0 => {
                // Erase from cursor to end of screen
                // Erase rest of current line
                self.screen_mut().block_set(cursor_col, cursor_row, cols - cursor_col, 1, b' ' as u32, style);
                // Erase lines below
                if cursor_row + 1 < rows {
                    self.screen_mut().block_set(0, cursor_row + 1, cols, rows - cursor_row - 1, b' ' as u32, style);
                }
            }
            1 => {
                // Erase from start to cursor
                if cursor_row > 0 {
                    self.screen_mut().block_set(0, 0, cols, cursor_row, b' ' as u32, style);
                }
                self.screen_mut().block_set(0, cursor_row, cursor_col + 1, 1, b' ' as u32, style);
            }
            2 => {
                // Erase entire screen
                self.screen_mut().block_set(0, 0, cols, rows, b' ' as u32, style);
            }
            3 => {
                // Erase scrollback
                // TODO: clear transcript
            }
            _ => {}
        }
    }

    /// Erase in line.
    fn erase_line(&mut self, mode: i32) {
        let style = self.current_style;
        let cols = self.columns;
        let cursor_col = self.cursor_col;
        let cursor_row = self.cursor_row;
        match mode {
            0 => {
                // Erase from cursor to end of line
                self.screen_mut().block_set(cursor_col, cursor_row, cols - cursor_col, 1, b' ' as u32, style);
            }
            1 => {
                // Erase from start to cursor
                self.screen_mut().block_set(0, cursor_row, cursor_col + 1, 1, b' ' as u32, style);
            }
            2 => {
                // Erase entire line
                self.screen_mut().block_set(0, cursor_row, cols, 1, b' ' as u32, style);
            }
            _ => {}
        }
    }

    /// Reset the terminal to initial state.
    fn reset(&mut self) {
        let total_rows = self.rows + DEFAULT_TRANSCRIPT_ROWS;
        self.screen = TerminalBuffer::new(self.columns, total_rows, self.rows);
        self.alt_screen = None;
        self.alt_buffer_active = false;
        self.cursor_row = 0;
        self.cursor_col = 0;
        self.cursor_style = CURSOR_STYLE_BLOCK;
        self.cursor_visible = true;
        self.reverse_video = false;
        self.mouse_tracking = false;
        self.title.clear();
        self.current_style = text_style::NORMAL;
        self.scroll_region_top = 0;
        self.scroll_region_bottom = self.rows;
        self.escape_state = EscapeState::Normal;
        self.flags = 0;
    }

    // ────────────────────── Public query methods ──────────────────────

    /// Get and clear callback flags.
    pub fn get_flags(&mut self) -> u32 {
        let f = self.flags;
        self.flags = 0;
        f
    }

    /// Get the cursor row.
    pub fn cursor_row(&self) -> i32 {
        self.cursor_row
    }

    /// Get the cursor column.
    pub fn cursor_col(&self) -> i32 {
        self.cursor_col
    }

    /// Get the cursor style.
    pub fn cursor_style(&self) -> i32 {
        self.cursor_style
    }

    /// Whether the cursor is currently visible (DECSET 25).
    pub fn is_cursor_visible(&self) -> bool {
        self.cursor_visible
    }

    /// Get the number of visible rows.
    pub fn rows(&self) -> i32 {
        self.rows
    }

    /// Get the number of columns.
    pub fn columns(&self) -> i32 {
        self.columns
    }

    /// Get a color value by index.
    pub fn get_color(&self, index: i32) -> i32 {
        if index >= 0 && index < 259 {
            self.colors[index as usize]
        } else {
            0
        }
    }

    /// Check if reverse video mode is active.
    pub fn is_reverse_video(&self) -> bool {
        self.reverse_video
    }

    /// Get the terminal title.
    pub fn title(&self) -> &str {
        &self.title
    }

    /// Check if mouse tracking is active.
    pub fn is_mouse_tracking_active(&self) -> bool {
        self.mouse_tracking
    }

    /// Check if the alternate buffer is active.
    pub fn is_alternate_buffer_active(&self) -> bool {
        self.alt_buffer_active
    }

    /// Get and clear the output buffer (bytes to write back to PTY).
    pub fn get_output(&mut self, buf: &mut [u8]) -> i32 {
        let len = self.output_buffer.len().min(buf.len());
        buf[..len].copy_from_slice(&self.output_buffer[..len]);
        self.output_buffer.drain(..len);
        len as i32
    }

    /// Get the scroll counter.
    pub fn get_scroll_counter(&self) -> i32 {
        self.scroll_counter
    }

    /// Clear the scroll counter.
    pub fn clear_scroll_counter(&mut self) {
        self.scroll_counter = 0;
    }

    /// Resize the terminal.
    pub fn resize(&mut self, new_cols: i32, new_rows: i32, cell_width: i32, cell_height: i32) {
        if new_cols == self.columns && new_rows == self.rows {
            return;
        }

        let total_rows = new_rows + DEFAULT_TRANSCRIPT_ROWS;
        let mut cursor = [self.cursor_col, self.cursor_row];
        let current_style = self.current_style;

        self.screen.resize(new_cols, new_rows, total_rows, &mut cursor, current_style, false);
        self.cursor_col = cursor[0];
        self.cursor_row = cursor[1];

        if let Some(ref mut alt) = self.alt_screen {
            let mut alt_cursor = [0, 0];
            alt.resize(new_cols, new_rows, total_rows, &mut alt_cursor, current_style, true);
        }

        self.columns = new_cols;
        self.rows = new_rows;
        self.cell_width = cell_width;
        self.cell_height = cell_height;
        self.scroll_region_top = 0;
        self.scroll_region_bottom = new_rows;
    }

    /// Paste text into the terminal.
    pub fn paste(&mut self, text: &str) {
        for ch in text.chars() {
            if ch.is_control() {
                continue;
            }
            self.put_char(ch as u32);
        }
    }

    /// Send a mouse event.
    pub fn send_mouse_event(&mut self, _button: i32, _column: i32, _row: i32, _pressed: bool) {
        // TODO: generate mouse escape sequence and add to output_buffer
    }

    /// Get selected text from the screen buffer.
    pub fn get_selected_text(&self, x1: i32, y1: i32, x2: i32, y2: i32, join_back: bool, join_full: bool) -> String {
        self.screen().get_selected_text(x1, y1, x2, y2, join_back, join_full)
    }
}
