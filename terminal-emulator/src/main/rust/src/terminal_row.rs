/// A row in a terminal, composed of a fixed number of cells.
///
/// The text in the row is stored in a `Vec<u16>` array (UTF-16, matching Java's `char[]`)
/// for quick access during rendering.
///
/// Ported from Kotlin: TerminalRow.kt
use crate::wcwidth;

/// An immutable snapshot of a row's text and style data, used to avoid borrow conflicts
/// when copying between rows that may overlap.
pub struct RowSnapshot {
    text: Vec<u16>,
    style: Vec<u64>,
    space_used: i32,
}

/// Spare capacity factor - initial text buffer is 1.5x columns.
const SPARE_CAPACITY_FACTOR: f32 = 1.5;

/// Max combining characters that can exist in a column, separate from the base character.
/// Any additional combining characters will be ignored and not added to the column.
const MAX_COMBINING_CHARACTERS_PER_COLUMN: i32 = 15;

pub struct TerminalRow {
    /// The text filling this terminal row, stored as UTF-16 code units.
    pub text: Vec<u16>,
    /// The style bits of each cell in the row. See `crate::text_style`.
    pub style: Vec<u64>,
    /// The number of u16 chars used in `text`.
    space_used: i32,
    /// If this row has been line wrapped due to text output at the end of line.
    pub line_wrap: bool,
    /// If this row might contain chars with width != 1, used for deactivating fast path.
    pub has_non_one_width_or_surrogate: bool,
    /// Dirty flag for optimized rendering - set to true when row content changes.
    pub dirty: bool,
    /// The number of columns in this terminal row.
    columns: i32,
}

/// Check if a u16 code unit is a UTF-16 high surrogate.
#[inline]
fn is_high_surrogate(c: u16) -> bool {
    (c & 0xFC00) == 0xD800
}

/// Check if a u16 code unit is a UTF-16 low surrogate.
#[inline]
fn is_low_surrogate(c: u16) -> bool {
    (c & 0xFC00) == 0xDC00
}

/// Convert a surrogate pair (high, low) to a Unicode code point.
#[inline]
fn to_code_point(high: u16, low: u16) -> u32 {
    ((high as u32 - 0xD800) << 10) | (low as u32 - 0xDC00) + 0x10000
}

/// Return the number of UTF-16 code units needed to represent a code point.
#[inline]
fn char_count(code_point: u32) -> i32 {
    if code_point >= 0x10000 {
        2
    } else {
        1
    }
}

/// Write a code point as UTF-16 code units into `buf` at `offset`.
/// Returns the number of code units written (1 or 2).
fn to_chars(code_point: u32, buf: &mut [u16], offset: usize) -> i32 {
    if code_point < 0x10000 {
        buf[offset] = code_point as u16;
        1
    } else {
        let cp = code_point - 0x10000;
        buf[offset] = (0xD800 + (cp >> 10)) as u16;
        buf[offset + 1] = (0xDC00 + (cp & 0x3FF)) as u16;
        2
    }
}

/// Read a code point from a UTF-16 buffer at the given index, advancing the index past it.
/// Returns (code_point, new_index).
fn read_code_point(text: &[u16], index: usize) -> (u32, usize) {
    let c = text[index];
    if is_high_surrogate(c) && index + 1 < text.len() && is_low_surrogate(text[index + 1]) {
        (to_code_point(c, text[index + 1]), index + 2)
    } else {
        (c as u32, index + 1)
    }
}

impl RowSnapshot {
    /// Get the text data as a slice.
    pub fn text(&self) -> &[u16] {
        &self.text
    }

    /// Get the style for a given column.
    pub fn get_style(&self, column: i32) -> u64 {
        self.style[column as usize]
    }

    /// Find the char index where a given column starts (delegates to stored data).
    pub fn find_start_of_column(&self, column: i32, columns: i32) -> i32 {
        if column == columns {
            return self.space_used;
        }

        let mut current_column: i32 = 0;
        let mut current_char_index: usize = 0;

        loop {
            let mut new_char_index = current_char_index;
            let c = self.text[new_char_index];
            new_char_index += 1;
            let is_high = is_high_surrogate(c);
            let (code_point, updated_idx) = if is_high {
                read_code_point(&self.text, current_char_index)
            } else {
                (c as u32, new_char_index)
            };
            new_char_index = updated_idx;

            let ww = wcwidth::width(code_point);
            if ww > 0 {
                current_column += ww;
                if current_column == column {
                    while (new_char_index as i32) < self.space_used {
                        let ch = self.text[new_char_index as usize];
                        if is_high_surrogate(ch) {
                            let (cp, _) = read_code_point(&self.text, new_char_index as usize);
                            if wcwidth::width(cp) <= 0 {
                                new_char_index += 2;
                            } else {
                                break;
                            }
                        } else if wcwidth::width(ch as u32) <= 0 {
                            new_char_index += 1;
                        } else {
                            break;
                        }
                    }
                    return new_char_index as i32;
                } else if current_column > column {
                    return current_char_index as i32;
                }
            }
            current_char_index = new_char_index;
        }
    }

    /// Check if there's a wide display character (width=2) starting at the given column.
    fn wide_display_character_starting_at(&self, column: i32, _columns: i32) -> bool {
        let mut current_char_index: usize = 0;
        let mut current_column: i32 = 0;
        let su = self.space_used as usize;
        while current_char_index < su {
            let (code_point, new_idx) = read_code_point(&self.text, current_char_index);
            let ww = wcwidth::width(code_point);
            current_char_index = new_idx;
            if ww > 0 {
                if current_column == column && ww == 2 {
                    return true;
                }
                current_column += ww;
                if current_column > column {
                    return false;
                }
            }
        }
        false
    }
}

impl TerminalRow {
    /// Create a new terminal row with the given number of columns and initial style.
    pub fn new(columns: i32, style: u64) -> Self {
        let text_capacity = (SPARE_CAPACITY_FACTOR * columns as f32) as usize;
        let mut text = vec![0u16; text_capacity];
        let mut style_vec = vec![0u64; columns as usize];

        // fill text with spaces and style with the given style (same as clear())
        for ch in text.iter_mut() {
            *ch = b' ' as u16;
        }
        for s in style_vec.iter_mut() {
            *s = style;
        }

        Self {
            text,
            style: style_vec,
            space_used: columns,
            line_wrap: false,
            has_non_one_width_or_surrogate: false,
            dirty: true,
            columns,
        }
    }

    /// Clear the row: fill text with spaces, reset all styles, set space_used = columns.
    pub fn clear(&mut self, style: u64) {
        for ch in self.text.iter_mut() {
            *ch = b' ' as u16;
        }
        for s in self.style.iter_mut() {
            *s = style;
        }
        self.space_used = self.columns;
        self.has_non_one_width_or_surrogate = false;
        self.dirty = true;
    }

    /// Return how many u16 chars are used.
    pub fn space_used(&self) -> i32 {
        self.space_used
    }

    /// Get the style for a given column.
    pub fn get_style(&self, column: i32) -> u64 {
        self.style[column as usize]
    }

    /// Get the number of columns.
    pub fn columns(&self) -> i32 {
        self.columns
    }

    /// Get the text as a slice (compatibility accessor for terminal_buffer).
    pub fn text(&self) -> &[u16] {
        &self.text
    }

    /// Create an immutable snapshot of this row's data for safe cross-row copies.
    pub fn snapshot(&self) -> RowSnapshot {
        RowSnapshot {
            text: self.text.clone(),
            style: self.style.clone(),
            space_used: self.space_used,
        }
    }

    /// Copy characters from a snapshot into this row.
    ///
    /// `source_x2` is exclusive.
    pub fn copy_interval_from_snapshot(
        &mut self,
        source: &RowSnapshot,
        source_x1: i32,
        source_x2: i32,
        dest_x: i32,
    ) {
        let mut src_x1 = source_x1;
        let mut dest_x = dest_x;

        let x1 = source.find_start_of_column(src_x1, self.columns) as usize;
        let x2 = source.find_start_of_column(source_x2, self.columns) as usize;

        let mut starting_from_second_half_of_wide_char =
            src_x1 > 0 && source.wide_display_character_starting_at(src_x1 - 1, self.columns);

        let source_chars = &source.text;

        let mut latest_non_combining_width: i32 = 0;
        let mut i = x1;
        let su = source.space_used as usize;

        while i < x2 && i < su {
            let source_char = source_chars[i];
            let (mut code_point, new_i) = if is_high_surrogate(source_char) && i + 1 < su {
                read_code_point(source_chars, i)
            } else {
                (source_char as u32, i + 1)
            };

            if starting_from_second_half_of_wide_char {
                code_point = ' ' as u32;
                starting_from_second_half_of_wide_char = false;
            }

            let w = wcwidth::width(code_point);
            if w > 0 {
                dest_x += latest_non_combining_width;
                src_x1 += latest_non_combining_width;
                latest_non_combining_width = w;
            }

            self.set_char(dest_x, code_point, source.get_style(src_x1));
            i = new_i;
        }
    }

    /// Check if the row is blank (all spaces).
    pub fn is_blank(&self) -> bool {
        let su = self.space_used as usize;
        for i in 0..su {
            if self.text[i] != b' ' as u16 {
                return false;
            }
        }
        true
    }

    /// Check if there's a wide display character (width=2) starting at the given column.
    fn wide_display_character_starting_at(&self, column: i32) -> bool {
        let mut current_char_index: usize = 0;
        let mut current_column: i32 = 0;
        let su = self.space_used as usize;
        while current_char_index < su {
            let (code_point, new_idx) = read_code_point(&self.text, current_char_index);
            let ww = wcwidth::width(code_point);
            current_char_index = new_idx;
            if ww > 0 {
                if current_column == column && ww == 2 {
                    return true;
                }
                current_column += ww;
                if current_column > column {
                    return false;
                }
            }
        }
        false
    }

    /// Find the char index where a given column starts.
    ///
    /// Note that the column may end in the second half of a wide character.
    pub fn find_start_of_column(&self, column: i32) -> i32 {
        if column == self.columns {
            return self.space_used;
        }

        let mut current_column: i32 = 0;
        let mut current_char_index: usize = 0;

        loop {
            let mut new_char_index = current_char_index;
            let c = self.text[new_char_index];
            new_char_index += 1;
            let is_high = is_high_surrogate(c);
            let (code_point, updated_idx) = if is_high {
                read_code_point(&self.text, current_char_index)
            } else {
                (c as u32, new_char_index)
            };
            new_char_index = updated_idx;

            let ww = wcwidth::width(code_point);
            if ww > 0 {
                current_column += ww;
                if current_column == column {
                    // Skip combining chars after this column's base character.
                    while (new_char_index as i32) < self.space_used {
                        let ch = self.text[new_char_index as usize];
                        if is_high_surrogate(ch) {
                            let (cp, _) = read_code_point(&self.text, new_char_index as usize);
                            if wcwidth::width(cp) <= 0 {
                                new_char_index += 2;
                            } else {
                                break;
                            }
                        } else if wcwidth::width(ch as u32) <= 0 {
                            new_char_index += 1;
                        } else {
                            break;
                        }
                    }
                    return new_char_index as i32;
                } else if current_column > column {
                    // Wide column going past end.
                    return current_char_index as i32;
                }
            }
            current_char_index = new_char_index;
        }
    }

    /// Copy characters from another row's column range into this row at `dest_x`.
    ///
    /// `source_x2` is exclusive.
    pub fn copy_interval(
        &mut self,
        source: &TerminalRow,
        source_x1: i32,
        source_x2: i32,
        dest_x: i32,
    ) {
        let mut src_x1 = source_x1;
        let mut dest_x = dest_x;

        self.has_non_one_width_or_surrogate =
            self.has_non_one_width_or_surrogate | source.has_non_one_width_or_surrogate;

        let x1 = source.find_start_of_column(src_x1) as usize;
        let x2 = source.find_start_of_column(source_x2) as usize;

        let mut starting_from_second_half_of_wide_char =
            src_x1 > 0 && source.wide_display_character_starting_at(src_x1 - 1);

        // If self and source are the same row, copy the text first to avoid aliasing.
        let source_chars: Vec<u16> = if std::ptr::eq(self as *const _, source as *const _) {
            source.text.clone()
        } else {
            // We can't borrow source.text while we also call self.set_char().
            // So we always clone when we need to iterate.
            source.text.clone()
        };

        let mut latest_non_combining_width: i32 = 0;
        let mut i = x1;
        let su = source.space_used as usize;

        while i < x2 && i < su {
            let source_char = source_chars[i];
            let (mut code_point, new_i) = if is_high_surrogate(source_char) && i + 1 < su {
                read_code_point(&source_chars, i)
            } else {
                (source_char as u32, i + 1)
            };

            if starting_from_second_half_of_wide_char {
                // Treat copying second half of wide char as copying whitespace.
                code_point = ' ' as u32;
                starting_from_second_half_of_wide_char = false;
            }

            let w = wcwidth::width(code_point);
            if w > 0 {
                dest_x += latest_non_combining_width;
                src_x1 += latest_non_combining_width;
                latest_non_combining_width = w;
            }

            self.set_char(dest_x, code_point, source.get_style(src_x1));
            i = new_i;
        }
    }

    /// Set a character at the given column position with the given style.
    ///
    /// This is the core method that handles normal chars, combining chars, and wide (2-column)
    /// characters with proper array management.
    pub fn set_char(&mut self, column_to_set: i32, code_point: u32, style: u64) {
        if column_to_set < 0 || column_to_set >= self.columns {
            panic!(
                "TerminalRow.setChar(): column_to_set={}, code_point={:#x}, style={}",
                column_to_set, code_point, style
            );
        }

        self.dirty = true;
        self.style[column_to_set as usize] = style;

        let new_code_point_display_width = wcwidth::width(code_point);

        // Fast path when we don't have any chars with width != 1
        if !self.has_non_one_width_or_surrogate {
            if code_point >= 0x10000 || new_code_point_display_width != 1 {
                self.has_non_one_width_or_surrogate = true;
            } else {
                self.text[column_to_set as usize] = code_point as u16;
                return;
            }
        }

        let new_is_combining = new_code_point_display_width <= 0;
        let mut actual_column_to_set = column_to_set;

        let was_extra_col_for_wide_char =
            column_to_set > 0 && self.wide_display_character_starting_at(column_to_set - 1);

        if new_is_combining {
            // When standing at second half of wide character and inserting combining:
            if was_extra_col_for_wide_char {
                actual_column_to_set -= 1;
            }
        } else {
            // Check if we are overwriting the second half of a wide char starting at prev column:
            if was_extra_col_for_wide_char {
                self.set_char(column_to_set - 1, ' ' as u32, style);
            }
            // Check if we are overwriting the first half of a wide char starting at next column:
            let overwriting_wide_char_in_next_column = new_code_point_display_width == 2
                && self.wide_display_character_starting_at(column_to_set + 1);
            if overwriting_wide_char_in_next_column {
                self.set_char(column_to_set + 1, ' ' as u32, style);
            }
        }

        let old_start_of_column_index = self.find_start_of_column(actual_column_to_set) as usize;
        let old_code_point_display_width =
            wcwidth::width_from_chars(&self.text, old_start_of_column_index);

        // Get the number of elements in the text array this column uses now
        let old_characters_used_for_column: usize;
        if actual_column_to_set + old_code_point_display_width < self.columns {
            let old_end_of_column_index = self
                .find_start_of_column(actual_column_to_set + old_code_point_display_width)
                as usize;
            old_characters_used_for_column = old_end_of_column_index - old_start_of_column_index;
        } else {
            // Last character.
            old_characters_used_for_column = self.space_used as usize - old_start_of_column_index;
        }

        // If MAX_COMBINING_CHARACTERS_PER_COLUMN already exist, ignore additional combining chars.
        if new_is_combining {
            let combining_chars_count = wcwidth::zero_width_count(
                &self.text,
                old_start_of_column_index,
                old_start_of_column_index + old_characters_used_for_column,
            );
            if combining_chars_count >= MAX_COMBINING_CHARACTERS_PER_COLUMN {
                return;
            }
        }

        // Find how many chars this column will need
        let mut new_characters_used_for_column = char_count(code_point) as usize;
        if new_is_combining {
            // Combining characters are added to the contents of the column instead of
            // overwriting them, so that they modify the existing contents.
            new_characters_used_for_column += old_characters_used_for_column;
        }

        let old_next_column_index = old_start_of_column_index + old_characters_used_for_column;
        let new_next_column_index = old_start_of_column_index + new_characters_used_for_column;

        let java_char_difference =
            new_characters_used_for_column as i32 - old_characters_used_for_column as i32;

        if java_char_difference > 0 {
            // Shift the rest of the line right.
            let old_characters_after_column = self.space_used as usize - old_next_column_index;
            if self.space_used as usize + java_char_difference as usize > self.text.len() {
                // We need to grow the array
                let mut new_text = vec![0u16; self.text.len() + self.columns as usize];
                // copy [0, old_next_column_index) -> [0, old_next_column_index)
                new_text[..old_next_column_index]
                    .copy_from_slice(&self.text[..old_next_column_index]);
                // copy [old_next_column_index, space_used) -> [new_next_column_index, ...)
                new_text
                    [new_next_column_index..new_next_column_index + old_characters_after_column]
                    .copy_from_slice(
                        &self.text[old_next_column_index
                            ..old_next_column_index + old_characters_after_column],
                    );
                self.text = new_text;
            } else {
                // Shift right in-place (must use copy_within for overlapping)
                self.text.copy_within(
                    old_next_column_index..old_next_column_index + old_characters_after_column,
                    new_next_column_index,
                );
            }
        } else if java_char_difference < 0 {
            // Shift the rest of the line left.
            let remaining = self.space_used as usize - old_next_column_index;
            self.text.copy_within(
                old_next_column_index..old_next_column_index + remaining,
                new_next_column_index,
            );
        }
        self.space_used += java_char_difference;

        // Store char. A combining character is stored at the end of the existing contents
        // so that it modifies them:
        let write_offset = old_start_of_column_index
            + if new_is_combining {
                old_characters_used_for_column
            } else {
                0
            };
        to_chars(code_point, &mut self.text, write_offset);

        if old_code_point_display_width == 2 && new_code_point_display_width == 1 {
            // Replace second half of wide char with a space. Which means we actually add
            // a ' ' u16 character.
            if self.space_used as usize + 1 > self.text.len() {
                let mut new_text = vec![0u16; self.text.len() + self.columns as usize];
                new_text[..new_next_column_index]
                    .copy_from_slice(&self.text[..new_next_column_index]);
                let remaining = self.space_used as usize - new_next_column_index;
                new_text[new_next_column_index + 1..new_next_column_index + 1 + remaining]
                    .copy_from_slice(
                        &self.text[new_next_column_index..new_next_column_index + remaining],
                    );
                self.text = new_text;
            } else {
                let remaining = self.space_used as usize - new_next_column_index;
                self.text.copy_within(
                    new_next_column_index..new_next_column_index + remaining,
                    new_next_column_index + 1,
                );
            }
            self.text[new_next_column_index] = ' ' as u16;
            self.space_used += 1;
        } else if old_code_point_display_width == 1 && new_code_point_display_width == 2 {
            if actual_column_to_set == self.columns - 1 {
                panic!("Cannot put wide character in last column");
            } else if actual_column_to_set == self.columns - 2 {
                // Truncate the line to the second part of this wide char:
                self.space_used = new_next_column_index as i32;
            } else {
                // Overwrite the contents of the next column, which means we actually
                // remove u16 characters. Due to the check at the beginning of this method
                // we know that we are not overwriting a wide char.
                let next_char_count = if is_high_surrogate(self.text[new_next_column_index]) {
                    2
                } else {
                    1
                };
                let new_next_next_column_index = new_next_column_index + next_char_count;
                let next_len = new_next_next_column_index - new_next_column_index;

                // Shift the array leftwards.
                let remaining = self.space_used as usize - new_next_next_column_index;
                self.text.copy_within(
                    new_next_next_column_index..new_next_next_column_index + remaining,
                    new_next_column_index,
                );
                self.space_used -= next_len as i32;
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::text_style;

    #[test]
    fn test_new_row() {
        let row = TerminalRow::new(80, text_style::NORMAL);
        assert_eq!(row.space_used, 80);
        assert_eq!(row.text.len(), (80.0 * SPARE_CAPACITY_FACTOR) as usize);
        assert_eq!(row.style.len(), 80);
        assert!(!row.line_wrap);
        assert!(!row.has_non_one_width_or_surrogate);
        assert!(row.dirty);
    }

    #[test]
    fn test_clear() {
        let mut row = TerminalRow::new(10, text_style::NORMAL);
        row.clear(text_style::NORMAL);
        assert_eq!(row.space_used, 10);
        assert!(!row.has_non_one_width_or_surrogate);
        assert!(row.dirty);
        // All spaces
        for i in 0..10 {
            assert_eq!(row.text[i], b' ' as u16);
        }
    }

    #[test]
    fn test_set_ascii_char() {
        let mut row = TerminalRow::new(10, text_style::NORMAL);
        row.set_char(0, 'A' as u32, text_style::NORMAL);
        assert_eq!(row.text[0], 'A' as u16);
    }

    #[test]
    fn test_find_start_of_column() {
        let mut row = TerminalRow::new(10, text_style::NORMAL);
        // For ASCII text, each column starts at its own index
        for i in 0..10 {
            assert_eq!(row.find_start_of_column(i), i);
        }
        // Last column (columns) returns space_used
        assert_eq!(row.find_start_of_column(10), 10);
    }

    #[test]
    fn test_is_blank() {
        let mut row = TerminalRow::new(5, text_style::NORMAL);
        assert!(row.is_blank());
        row.set_char(2, 'X' as u32, text_style::NORMAL);
        assert!(!row.is_blank());
    }

    #[test]
    fn test_get_style() {
        let mut row = TerminalRow::new(5, text_style::NORMAL);
        row.set_char(0, 'A' as u32, 0x42);
        assert_eq!(row.get_style(0), 0x42);
    }
}
