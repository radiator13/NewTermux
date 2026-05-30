/// A circular buffer of `TerminalRow`s which keeps notes about what is visible on a logical screen and the scroll
/// history.
///
/// See `external_to_internal_row` for how to map from logical screen rows to array indices.
use crate::terminal_row::TerminalRow;
use crate::text_style;
use crate::wcwidth;

/// A snapshot of a TerminalRow's text and style data, used to avoid borrow conflicts
/// when copying between rows in the same `Vec<Option<TerminalRow>>`.
pub struct RowSnapshot {
    pub text: Vec<u32>,
    pub styles: Vec<u64>,
    pub space_used: usize,
    pub has_non_one_width_chars: bool,
}

pub struct TerminalBuffer {
    pub lines: Vec<Option<TerminalRow>>,
    pub total_rows: i32,
    pub screen_rows: i32,
    pub columns: i32,
    active_transcript_rows: i32,
    screen_first_row: i32,
}

impl TerminalBuffer {
    pub fn new(columns: i32, total_rows: i32, screen_rows: i32) -> Self {
        let mut buf = TerminalBuffer {
            lines: Vec::with_capacity(total_rows as usize),
            total_rows,
            screen_rows,
            columns,
            active_transcript_rows: 0,
            screen_first_row: 0,
        };
        // Initialize with None entries (equivalent to arrayOfNulls)
        for _ in 0..total_rows {
            buf.lines.push(None);
        }
        buf.block_set(0, 0, columns, screen_rows, ' ' as u32, text_style::NORMAL);
        buf
    }

    // ────────────────────── coordinate mapping ──────────────────────

    /// Convert a row value from the public external coordinate system to our internal private
    /// coordinate system.
    ///
    /// ```text
    /// - External coordinate system: -active_transcript_rows to screen_rows-1,
    ///   with the screen being 0..screen_rows-1.
    /// - Internal coordinate system: the screen_rows lines starting at screen_first_row
    ///   comprise the screen, while the active_transcript_rows lines ending at
    ///   screen_first_row-1 form the transcript (as a circular buffer).
    ///
    /// External ↔ Internal:
    ///
    /// [ ...                            ]     [ ...                                     ]
    /// [ -active_transcript_rows        ]     [ screen_first_row - active_transcript_rows ]
    /// [ ...                            ]     [ ...                                     ]
    /// [ 0 (visible screen starts here) ]  ↔  [ screen_first_row                        ]
    /// [ ...                            ]     [ ...                                     ]
    /// [ screen_rows-1                  ]     [ screen_first_row + screen_rows-1        ]
    /// ```
    pub fn external_to_internal_row(&self, external_row: i32) -> i32 {
        if external_row < -self.active_transcript_rows || external_row > self.screen_rows {
            panic!(
                "extRow={}, mScreenRows={}, mActiveTranscriptRows={}",
                external_row, self.screen_rows, self.active_transcript_rows
            );
        }
        let internal_row = self.screen_first_row + external_row;
        if internal_row < 0 {
            self.total_rows + internal_row
        } else {
            internal_row % self.total_rows
        }
    }

    // ────────────────────── line-wrap helpers ──────────────────────

    pub fn set_line_wrap(&mut self, row: i32) {
        let idx = self.external_to_internal_row(row) as usize;
        self.lines[idx].as_mut().unwrap().line_wrap = true;
    }

    pub fn get_line_wrap(&self, row: i32) -> bool {
        let idx = self.external_to_internal_row(row) as usize;
        self.lines[idx].as_ref().unwrap().line_wrap
    }

    pub fn clear_line_wrap(&mut self, row: i32) {
        let idx = self.external_to_internal_row(row) as usize;
        self.lines[idx].as_mut().unwrap().line_wrap = false;
    }

    // ────────────────────── allocate / access helpers ──────────────────────

    /// Lazily allocate a full `TerminalRow` if the slot is `None`.
    pub fn allocate_full_line_if_necessary(&mut self, row: i32) -> &mut TerminalRow {
        let idx = row as usize;
        if self.lines[idx].is_none() {
            let columns = self.columns;
            self.lines[idx] = Some(TerminalRow::new(columns, 0));
        }
        self.lines[idx].as_mut().unwrap()
    }

    pub fn set_char(&mut self, column: i32, row: i32, code_point: u32, style: u64) {
        if row < 0 || row >= self.screen_rows || column < 0 || column >= self.columns {
            panic!(
                "TerminalBuffer.setChar(): row={}, column={}, mScreenRows={}, mColumns={}",
                row, column, self.screen_rows, self.columns
            );
        }
        let internal_row = self.external_to_internal_row(row);
        self.allocate_full_line_if_necessary(internal_row)
            .set_char(column, code_point, style);
    }

    pub fn get_style_at(&self, external_row: i32, column: i32) -> u64 {
        let idx = self.external_to_internal_row(external_row) as usize;
        match &self.lines[idx] {
            Some(row) => row.get_style(column),
            None => text_style::NORMAL,
        }
    }

    // ────────────────────── scroll down one line ──────────────────────

    /// Block copy lines and associated metadata from one location to another in the circular
    /// buffer, taking wraparound into account.
    ///
    /// Copies `len` lines starting at `src_internal` **down** by one slot (indices increase).
    fn block_copy_lines_down(&mut self, src_internal: i32, len: i32) {
        if len == 0 {
            return;
        }
        let total_rows = self.total_rows as usize;
        let start = (len - 1) as usize;

        // Save away line to be overwritten:
        let line_to_be_overwritten =
            self.lines[((src_internal as usize) + start + 1) % total_rows].take();

        // Do the copy from bottom to top.
        for i in (0..=start).rev() {
            let src_idx = ((src_internal as usize) + i) % total_rows;
            let dst_idx = ((src_internal as usize) + i + 1) % total_rows;
            self.lines[dst_idx] = self.lines[src_idx].take();
        }

        // Put back overwritten line, now above the block:
        self.lines[(src_internal as usize) % total_rows] = line_to_be_overwritten;
    }

    /// Scroll the screen down one line. To scroll the whole screen of a 24 line screen, the
    /// arguments would be `(0, 24)`.
    pub fn scroll_down_one_line(&mut self, top_margin: i32, bottom_margin: i32, style: u64) {
        if top_margin > bottom_margin - 1 || top_margin < 0 || bottom_margin > self.screen_rows {
            panic!(
                "topMargin={}, bottomMargin={}, mScreenRows={}",
                top_margin, bottom_margin, self.screen_rows
            );
        }

        // Copy the fixed topMargin lines one line down so that they remain on screen
        // in same position:
        let sfr = self.screen_first_row;
        self.block_copy_lines_down(sfr, top_margin);

        // Copy the fixed screen_rows-bottomMargin lines one line down so that they remain
        // on screen in same position:
        let external_bottom = self.external_to_internal_row(bottom_margin);
        self.block_copy_lines_down(external_bottom, self.screen_rows - bottom_margin);

        // Update the screen location in the ring buffer:
        self.screen_first_row = (self.screen_first_row + 1) % self.total_rows;

        // Note that the history has grown if not already full:
        if self.active_transcript_rows < self.total_rows - self.screen_rows {
            self.active_transcript_rows += 1;
        }

        // Blank the newly revealed line above the bottom margin:
        let blank_row = self.external_to_internal_row(bottom_margin - 1);
        if self.lines[blank_row as usize].is_none() {
            let columns = self.columns;
            self.lines[blank_row as usize] = Some(TerminalRow::new(columns, style));
        } else {
            self.lines[blank_row as usize]
                .as_mut()
                .unwrap()
                .clear(style);
        }
    }

    // ────────────────────── block copy / block set ──────────────────────

    /// Block copy characters from one position in the screen to another. The two positions can
    /// overlap. All characters of the source and destination must be within the bounds of the
    /// screen, or else a panic will occur.
    pub fn block_copy(&mut self, sx: i32, sy: i32, w: i32, h: i32, dx: i32, dy: i32) {
        if w == 0 {
            return;
        }
        if sx < 0
            || sx + w > self.columns
            || sy < 0
            || sy + h > self.screen_rows
            || dx < 0
            || dx + w > self.columns
            || dy < 0
            || dy + h > self.screen_rows
        {
            panic!("blockCopy: out of bounds");
        }
        let copying_up = sy > dy;
        for y in 0..h {
            let y2 = if copying_up { y } else { h - (y + 1) };
            let source_row_internal = self.external_to_internal_row(sy + y2);
            let dest_row_internal = self.external_to_internal_row(dy + y2);

            // Take a snapshot of the source row to avoid borrow conflicts when source
            // and destination are in the same Vec<Option<TerminalRow>>.
            // This mirrors Kotlin's copyInterval which clones text when `this === line`.
            let source_snapshot = {
                match &self.lines[source_row_internal as usize] {
                    Some(row) => row.snapshot(),
                    None => continue,
                }
            };
            // Immutable borrow of self.lines is released here (block scope).

            let dest_row = self.allocate_full_line_if_necessary(dest_row_internal);
            dest_row.copy_interval_from_snapshot(&source_snapshot, sx, sx + w, dx);
        }
    }

    /// Block set characters. All characters must be within the bounds of the screen, or else a
    /// panic will occur. Typically this is called with a `val` argument of 32 (space) to clear a
    /// block of characters.
    pub fn block_set(&mut self, sx: i32, sy: i32, w: i32, h: i32, val: u32, style: u64) {
        if sx < 0 || sx + w > self.columns || sy < 0 || sy + h > self.screen_rows {
            panic!(
                "Illegal arguments! blockSet({}, {}, {}, {}, {}, {}, {})",
                sx, sy, w, h, val, self.columns, self.screen_rows
            );
        }
        for y in 0..h {
            for x in 0..w {
                self.set_char(sx + x, sy + y, val, style);
            }
        }
    }

    // ────────────────────── getSelectedText ──────────────────────

    pub fn get_selected_text(
        &self,
        sel_x1: i32,
        sel_y1: i32,
        sel_x2: i32,
        sel_y2: i32,
        join_back_lines: bool,
        join_full_lines: bool,
    ) -> String {
        let mut builder = String::new();
        let columns = self.columns;

        let mut y1 = sel_y1;
        let mut y2 = sel_y2;
        if y1 < -self.active_transcript_rows {
            y1 = -self.active_transcript_rows;
        }
        if y2 >= self.screen_rows {
            y2 = self.screen_rows - 1;
        }

        for row in y1..=y2 {
            let x1 = if row == y1 { sel_x1 } else { 0 };
            let x2 = if row == y2 {
                let mut v = sel_x2 + 1;
                if v > columns {
                    v = columns;
                }
                v
            } else {
                columns
            };

            let internal_row = self.external_to_internal_row(row) as usize;
            let line_object = self.lines[internal_row].as_ref().unwrap();

            let x1_index = line_object.find_start_of_column(x1);
            let x2_index = if x2 < self.columns {
                line_object.find_start_of_column(x2)
            } else {
                line_object.space_used()
            };
            let final_x2_index = if x2_index == x1_index {
                // Selected the start of a wide character.
                line_object.find_start_of_column(x2 + 1)
            } else {
                x2_index
            };

            let text = line_object.text();
            let mut last_printing_char_index: i32 = -1;
            let row_line_wrap = self.get_line_wrap(row);
            if row_line_wrap && x2 == columns {
                // If the line was wrapped, we shouldn't lose trailing space:
                last_printing_char_index = final_x2_index - 1;
            } else {
                for i in x1_index..final_x2_index {
                    let idx = i as usize;
                    if idx < text.len() && text[idx] != b' ' as u16 {
                        last_printing_char_index = i;
                    }
                }
            }

            let len = last_printing_char_index - x1_index + 1;
            if last_printing_char_index != -1 && len > 0 {
                let start = x1_index as usize;
                let end = start + len as usize;
                for i in start..end {
                    if i < text.len() {
                        let cp = text[i] as u32;
                        if let Some(c) = char::from_u32(cp) {
                            builder.push(c);
                        }
                    }
                }
            }

            let line_fills_width =
                last_printing_char_index as usize == (final_x2_index as usize).wrapping_sub(1);
            if (!join_back_lines || !row_line_wrap)
                && (!join_full_lines || !line_fills_width)
                && row < y2
                && row < self.screen_rows - 1
            {
                builder.push('\n');
            }
        }
        builder
    }

    // ────────────────────── clearTranscript ──────────────────────

    pub fn clear_transcript(&mut self) {
        let sfr = self.screen_first_row as usize;
        let atr = self.active_transcript_rows as usize;
        let total = self.total_rows as usize;

        if sfr < atr {
            // Indices: [total + sfr - atr .. total)  and  [0 .. sfr)
            let start1 = total + sfr - atr;
            for i in start1..total {
                self.lines[i] = None;
            }
            for i in 0..sfr {
                self.lines[i] = None;
            }
        } else {
            // Indices: [sfr - atr .. sfr)
            for i in (sfr - atr)..sfr {
                self.lines[i] = None;
            }
        }
        self.active_transcript_rows = 0;
    }

    // ────────────────────── activeTranscriptRows getter ──────────────────────

    pub fn active_transcript_rows(&self) -> i32 {
        self.active_transcript_rows
    }

    /// The total number of active rows (transcript + screen).
    pub fn active_rows(&self) -> i32 {
        self.active_transcript_rows + self.screen_rows
    }

    // ────────────────────── resize ──────────────────────

    /// Resize the screen which this transcript backs. Currently, this only works if the number of
    /// columns does not change or the rows expand (that is, it only works when shrinking the
    /// number of rows).
    pub fn resize(
        &mut self,
        new_columns: i32,
        new_rows: i32,
        new_total_rows: i32,
        cursor: &mut [i32; 2],
        current_style: u64,
        alt_screen: bool,
    ) {
        // new_rows > total_rows should not normally happen since total_rows is TRANSCRIPT_ROWS
        // (10000):
        if new_columns == self.columns && new_rows <= self.total_rows {
            // Fast resize where just the rows changed.
            let mut shift_down_of_top_row = self.screen_rows - new_rows;
            if shift_down_of_top_row > 0 && shift_down_of_top_row < self.screen_rows {
                // Shrinking. Check if we can skip blank rows at bottom below cursor.
                for i in (1..self.screen_rows).rev() {
                    if cursor[1] >= i {
                        break;
                    }
                    let r = self.external_to_internal_row(i) as usize;
                    if self.lines[r].is_none()
                        || self.lines[r].as_ref().unwrap().is_blank()
                    {
                        shift_down_of_top_row -= 1;
                        if shift_down_of_top_row == 0 {
                            break;
                        }
                    }
                }
            } else if shift_down_of_top_row < 0 {
                // Negative shift down = expanding. Only move screen up if there is
                // transcript to show:
                let actual_shift = shift_down_of_top_row.max(-self.active_transcript_rows);
                if shift_down_of_top_row != actual_shift {
                    // The new lines revealed by the resizing are not all from the
                    // transcript. Blank the below ones.
                    for i in 0..(actual_shift - shift_down_of_top_row) {
                        let row_idx =
                            (self.screen_first_row + self.screen_rows + i) % self.total_rows;
                        let columns = self.columns;
                        let row = self
                            .lines
                            .get_mut(row_idx as usize)
                            .unwrap()
                            .get_or_insert_with(|| TerminalRow::new(columns, 0));
                        row.clear(current_style);
                    }
                    shift_down_of_top_row = actual_shift;
                }
            }
            self.screen_first_row += shift_down_of_top_row;
            if self.screen_first_row < 0 {
                self.screen_first_row += self.total_rows;
            } else {
                self.screen_first_row %= self.total_rows;
            }
            self.total_rows = new_total_rows;
            self.active_transcript_rows = if alt_screen {
                0
            } else {
                0.max(self.active_transcript_rows + shift_down_of_top_row)
            };
            cursor[1] -= shift_down_of_top_row;
            self.screen_rows = new_rows;
        } else {
            // Copy away old state and update new:
            let old_lines = std::mem::replace(
                &mut self.lines,
                (0..new_total_rows)
                    .map(|_| None)
                    .collect(),
            );
            // Allocate new rows
            for i in 0..new_total_rows {
                self.lines[i as usize] = Some(TerminalRow::new(new_columns, current_style));
            }

            let old_active_transcript_rows = self.active_transcript_rows;
            let old_screen_first_row = self.screen_first_row;
            let old_screen_rows = self.screen_rows;
            let old_total_rows = self.total_rows;
            self.total_rows = new_total_rows;
            self.screen_rows = new_rows;
            self.active_transcript_rows = 0;
            self.screen_first_row = 0;
            self.columns = new_columns;

            let mut new_cursor_row: i32 = -1;
            let mut new_cursor_column: i32 = -1;
            let old_cursor_row = cursor[1];
            let old_cursor_column = cursor[0];
            let mut new_cursor_placed = false;

            let mut current_output_external_row: i32 = 0;
            let mut current_output_external_column: i32 = 0;

            // Loop over every character in the initial state.
            // Blank lines should be skipped only if at end of transcript (just as is done in the
            // "fast" resize), so we keep track how many blank lines we have skipped if we later
            // on find a non-blank line.
            let mut skipped_blank_lines: i32 = 0;
            for external_old_row in -old_active_transcript_rows..old_screen_rows {
                // Do what external_to_internal_row() does but for the old state:
                let mut internal_old_row = old_screen_first_row + external_old_row;
                internal_old_row = if internal_old_row < 0 {
                    old_total_rows + internal_old_row
                } else {
                    internal_old_row % old_total_rows
                };

                let old_line = old_lines[internal_old_row as usize].as_ref();
                let cursor_at_this_row = external_old_row == old_cursor_row;
                // The cursor may only be on a non-null line, which we should not skip:
                let should_skip = match old_line {
                    None => true,
                    Some(line) => {
                        !(!new_cursor_placed && cursor_at_this_row) && line.is_blank()
                    }
                };
                if should_skip {
                    skipped_blank_lines += 1;
                    continue;
                } else if skipped_blank_lines > 0 {
                    // After skipping some blank lines we encounter a non-blank line.
                    // Insert the skipped blank lines.
                    for _ in 0..skipped_blank_lines {
                        if current_output_external_row == self.screen_rows - 1 {
                            if new_cursor_placed {
                                new_cursor_row -= 1;
                            }
                            self.scroll_down_one_line(0, self.screen_rows, current_style);
                        } else {
                            current_output_external_row += 1;
                        }
                        current_output_external_column = 0;
                    }
                    skipped_blank_lines = 0;
                }

                let old_line = old_line.unwrap();

                let mut last_non_space_index: i32 = 0;
                let mut just_to_cursor = false;
                if cursor_at_this_row || old_line.line_wrap {
                    // Take the whole line, either because of cursor on it, or if line wrapping.
                    last_non_space_index = old_line.space_used() as i32;
                    if cursor_at_this_row {
                        just_to_cursor = true;
                    }
                } else {
                    let text = old_line.text();
                    for i in 0..old_line.space_used() {
                        let idx = i as usize;
                        if idx < text.len() && text[idx] != b' ' as u16 {
                            last_non_space_index = i + 1;
                        }
                    }
                }

                let mut current_old_col: i32 = 0;
                let mut style_at_col: u64 = 0;
                let mut i: i32 = 0;
                // Clone text data as u32 code points so we can release the borrow on old_line
                let text_u16 = old_line.text();
                let text: Vec<u32> = text_u16.iter().map(|&c| c as u32).collect();
                let space_used = old_line.space_used();
                // Pre-fetch styles for the line so we don't need to borrow old_line in the loop
                let styles: Vec<u64> = (0..space_used).map(|c| old_line.get_style(c)).collect();
                let line_wrap = old_line.line_wrap;

                while i < last_non_space_index {
                    // Note that looping over java character, not cells.
                    let c = text[i as usize];
                    // Port the EXACT surrogate handling from Kotlin even though Rust's u32
                    // code points don't produce surrogates - kept for faithful port.
                    let code_point = if is_high_surrogate(c) {
                        i += 1;
                        to_code_point(c, text[i as usize])
                    } else {
                        c
                    };
                    let display_width = wcwidth::width(code_point);
                    // Use the last style if this is a zero-width character:
                    if display_width > 0 {
                        if (current_old_col as usize) < styles.len() {
                            style_at_col = styles[current_old_col as usize];
                        }
                    }

                    // Line wrap as necessary:
                    if current_output_external_column + display_width > self.columns {
                        self.set_line_wrap(current_output_external_row);
                        if current_output_external_row == self.screen_rows - 1 {
                            if new_cursor_placed {
                                new_cursor_row -= 1;
                            }
                            self.scroll_down_one_line(0, self.screen_rows, current_style);
                        } else {
                            current_output_external_row += 1;
                        }
                        current_output_external_column = 0;
                    }

                    let offset_due_to_combining_char =
                        if display_width <= 0 && current_output_external_column > 0 {
                            1
                        } else {
                            0
                        };
                    let output_column =
                        current_output_external_column - offset_due_to_combining_char;
                    self.set_char(
                        output_column,
                        current_output_external_row,
                        code_point,
                        style_at_col,
                    );

                    if display_width > 0 {
                        if old_cursor_row == external_old_row
                            && old_cursor_column == current_old_col
                        {
                            new_cursor_column = current_output_external_column;
                            new_cursor_row = current_output_external_row;
                            new_cursor_placed = true;
                        }
                        current_old_col += display_width;
                        current_output_external_column += display_width;
                        if just_to_cursor && new_cursor_placed {
                            break;
                        }
                    }
                    i += 1;
                }
                // Old row has been copied. Check if we need to insert newline if old line was not
                // wrapping:
                if external_old_row != (old_screen_rows - 1) && !line_wrap {
                    if current_output_external_row == self.screen_rows - 1 {
                        if new_cursor_placed {
                            new_cursor_row -= 1;
                        }
                        self.scroll_down_one_line(0, self.screen_rows, current_style);
                    } else {
                        current_output_external_row += 1;
                    }
                    current_output_external_column = 0;
                }
            }

            cursor[0] = new_cursor_column;
            cursor[1] = new_cursor_row;
        }

        // Handle cursor scrolling off screen:
        if cursor[0] < 0 || cursor[1] < 0 {
            cursor[0] = 0;
            cursor[1] = 0;
        }
    }
}

// ────────────────────── UTF-16 helpers (Java char compat) ──────────────────────

/// Check if a u32 value is in the UTF-16 high surrogate range (0xD800..0xDC00).
/// Ported from Kotlin `Character.isHighSurrogate()`.
fn is_high_surrogate(c: u32) -> bool {
    (0xD800..0xDC00).contains(&c)
}

/// Combine a high and low surrogate into a full Unicode code point.
/// Ported from Kotlin `Character.toCodePoint(high, low)`.
fn to_code_point(high: u32, low: u32) -> u32 {
    ((high - 0xD800) * 0x400 + (low - 0xDC00)) + 0x10000
}
