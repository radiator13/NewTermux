//! C FFI bridge for the terminal emulator.
//!
//! Provides `extern "C"` functions that Kotlin/JNI can call via JNI external
//! declarations. All functions use C-compatible types and handle null pointers
//! safely.
//!
//! Memory management follows the `Box::into_raw` / `Box::from_raw` pattern for
//! heap-allocated Rust types passed across the FFI boundary.

use std::panic::{self, AssertUnwindSafe};
use std::ptr;

use crate::byte_queue::ByteQueue;
use crate::terminal_emulator::TerminalEmulator;
use crate::wcwidth;

// ═══════════════════════════════════════════════════════════════════════════
// Internal helpers
// ═══════════════════════════════════════════════════════════════════════════

/// Run a closure inside `catch_unwind` so that Rust panics never cross the
/// FFI boundary. Returns `T::default()` if a panic occurs.
///
/// The closure is automatically wrapped in `AssertUnwindSafe` because all
/// FFI callers deal with raw pointers which are not `UnwindSafe` by default.
fn catch_unwind_or<T: Default>(f: impl FnOnce() -> T) -> T {
    panic::catch_unwind(AssertUnwindSafe(f)).unwrap_or_default()
}

/// Read a UTF-16 code point from `text` at `index`, handling surrogate pairs.
/// Returns `(code_point, next_index)`.
fn read_utf16_code_point(text: &[u16], index: usize) -> (u32, usize) {
    let c = text[index];
    if (c & 0xFC00) == 0xD800 && index + 1 < text.len() && (text[index + 1] & 0xFC00) == 0xDC00 {
        let cp = ((c as u32 - 0xD800) << 10) | (text[index + 1] as u32 - 0xDC00) + 0x10000;
        (cp, index + 2)
    } else {
        (c as u32, index + 1)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Terminal Emulator lifecycle
// ═══════════════════════════════════════════════════════════════════════════

/// Create a new terminal emulator.
///
/// Returns an opaque pointer that must be freed with [`term_emulator_free`].
/// Returns null on allocation failure.
#[no_mangle]
pub extern "C" fn term_emulator_new(
    cols: i32,
    rows: i32,
    cell_w: i32,
    cell_h: i32,
    transcript_rows: i32,
) -> *mut TerminalEmulator {
    catch_unwind_or(|| {
        if cols <= 0 || rows <= 0 {
            return ptr::null_mut();
        }
        let em = TerminalEmulator::new(cols, rows, cell_w, cell_h, transcript_rows);
        Box::into_raw(em)
    })
}

/// Free a terminal emulator previously created with [`term_emulator_new`].
///
/// Passing null is a safe no-op.
#[no_mangle]
pub extern "C" fn term_emulator_free(emulator: *mut TerminalEmulator) {
    if emulator.is_null() {
        return;
    }
    catch_unwind_or(|| {
        unsafe {
            let _ = Box::from_raw(emulator);
        }
    });
}

/// Process raw bytes from the PTY.
///
/// The emulator accumulates output internally; call [`term_emulator_get_output`]
/// afterwards to retrieve bytes destined for the PTY.
///
/// Passing null for `emulator` or `bytes` is a safe no-op.
#[no_mangle]
pub extern "C" fn term_emulator_process_bytes(
    emulator: *mut TerminalEmulator,
    bytes: *const u8,
    len: i32,
) {
    if emulator.is_null() || bytes.is_null() || len <= 0 {
        return;
    }
    catch_unwind_or(|| {
        unsafe {
            let slice = std::slice::from_raw_parts(bytes, len as usize);
            (*emulator).process_bytes(slice);
        }
    });
}

/// Retrieve output bytes that the emulator wants to write back to the PTY.
///
/// Writes up to `max_len` bytes into `buf`. Returns the number of bytes
/// written, or 0 if no output is pending.
///
/// Passing null for `emulator` or `buf` returns 0.
#[no_mangle]
pub extern "C" fn term_emulator_get_output(
    emulator: *mut TerminalEmulator,
    buf: *mut u8,
    max_len: i32,
) -> i32 {
    if emulator.is_null() || buf.is_null() || max_len <= 0 {
        return 0;
    }
    catch_unwind_or(|| {
        unsafe {
            let out_slice = std::slice::from_raw_parts_mut(buf, max_len as usize);
            (*emulator).get_output(out_slice)
        }
    })
}

/// Get and clear the callback flags.
///
/// Flags indicate what changed during the last `process_bytes` call:
/// - bit 0: bell
/// - bit 1: title changed
/// - bit 2: colors changed
/// - bit 3: cursor state changed
#[no_mangle]
pub extern "C" fn term_emulator_get_flags(emulator: *mut TerminalEmulator) -> u32 {
    if emulator.is_null() {
        return 0;
    }
    catch_unwind_or(|| unsafe { (*emulator).get_flags() })
}

// ═══════════════════════════════════════════════════════════════════════════
// Screen queries for rendering
// ═══════════════════════════════════════════════════════════════════════════

/// Get the character and style at a screen position.
///
/// Returns a packed `u64`: `(code_point << 32) | (style & 0xFFFFFFFF)`.
/// Returns 0 if the cell is empty or the position is out of bounds.
///
/// The screen pointer is resolved internally to whichever buffer is active
/// (main or alternate).
#[no_mangle]
pub extern "C" fn term_emulator_get_char_at(
    emulator: *mut TerminalEmulator,
    row: i32,
    col: i32,
) -> u64 {
    if emulator.is_null() {
        return 0;
    }
    catch_unwind_or(|| {
        unsafe {
            let em = &*emulator;
            let screen = em.screen();

            // Bounds check
            if row < 0 || row >= screen.screen_rows || col < 0 || col >= screen.columns {
                return 0u64;
            }

            let style = screen.get_style_at(row, col);

            // Get the internal row index and read the character
            let internal_row = screen.external_to_internal_row(row);
            let idx = internal_row as usize;

            match &screen.lines[idx] {
                Some(line) => {
                    let text_index = line.find_start_of_column(col) as usize;
                    if text_index >= line.text.len() {
                        return 0u64;
                    }
                    let (code_point, _next) = read_utf16_code_point(&line.text, text_index);
                    (code_point as u64) << 32 | (style & 0xFFFFFFFF)
                }
                None => 0u64,
            }
        }
    })
}

/// Get the cursor row (0-based, from the top of the screen).
#[no_mangle]
pub extern "C" fn term_emulator_get_cursor_row(emulator: *mut TerminalEmulator) -> i32 {
    if emulator.is_null() {
        return 0;
    }
    catch_unwind_or(|| unsafe { (*emulator).cursor_row() })
}

/// Get the cursor column (0-based, from the left of the screen).
#[no_mangle]
pub extern "C" fn term_emulator_get_cursor_col(emulator: *mut TerminalEmulator) -> i32 {
    if emulator.is_null() {
        return 0;
    }
    catch_unwind_or(|| unsafe { (*emulator).cursor_col() })
}

/// Get the cursor style.
///
/// Returns one of:
/// - 0: block
/// - 1: underline
/// - 2: bar
#[no_mangle]
pub extern "C" fn term_emulator_get_cursor_style(emulator: *mut TerminalEmulator) -> i32 {
    if emulator.is_null() {
        return 0;
    }
    catch_unwind_or(|| unsafe { (*emulator).cursor_style() })
}

/// Get the number of rows in the terminal screen.
#[no_mangle]
pub extern "C" fn term_emulator_get_rows(emulator: *mut TerminalEmulator) -> i32 {
    if emulator.is_null() {
        return 0;
    }
    catch_unwind_or(|| unsafe { (*emulator).rows() })
}

/// Get the number of columns in the terminal screen.
#[no_mangle]
pub extern "C" fn term_emulator_get_columns(emulator: *mut TerminalEmulator) -> i32 {
    if emulator.is_null() {
        return 0;
    }
    catch_unwind_or(|| unsafe { (*emulator).columns() })
}

// ═══════════════════════════════════════════════════════════════════════════
// Color queries
// ═══════════════════════════════════════════════════════════════════════════

/// Get a color value by index.
///
/// Index 0-255: standard ANSI colors.
/// Index 256: default foreground.
/// Index 257: default background.
/// Index 258: cursor color.
#[no_mangle]
pub extern "C" fn term_emulator_get_color(emulator: *mut TerminalEmulator, index: i32) -> i32 {
    if emulator.is_null() {
        return 0;
    }
    catch_unwind_or(|| unsafe { (*emulator).get_color(index) })
}

/// Check if reverse video mode is active (DECSET 5).
#[no_mangle]
pub extern "C" fn term_emulator_get_reverse_video(emulator: *mut TerminalEmulator) -> bool {
    if emulator.is_null() {
        return false;
    }
    catch_unwind_or(|| unsafe { (*emulator).is_reverse_video() })
}

// ═══════════════════════════════════════════════════════════════════════════
// Title and mouse
// ═══════════════════════════════════════════════════════════════════════════

/// Get the terminal window title as UTF-16 code units.
///
/// Copies up to `max_len` UTF-16 code units into `buf`. Returns the number
/// of code units written, or 0 if there is no title.
///
/// Passing null for `emulator` or `buf` returns 0.
#[no_mangle]
pub extern "C" fn term_emulator_get_title(
    emulator: *mut TerminalEmulator,
    buf: *mut u16,
    max_len: i32,
) -> i32 {
    if emulator.is_null() || buf.is_null() || max_len <= 0 {
        return 0;
    }
    catch_unwind_or(|| {
        unsafe {
            let title = (*emulator).title();
            if title.is_empty() {
                return 0;
            }

            // Encode to UTF-16 and copy to the buffer
            let max = max_len as usize;
            let mut count = 0usize;
            for ch in title.chars() {
                if count >= max {
                    break;
                }
                let cp = ch as u32;
                if cp < 0x10000 {
                    *buf.add(count) = cp as u16;
                    count += 1;
                } else if count + 1 < max {
                    // Supplementary plane: encode as surrogate pair
                    let cp_minus = cp - 0x10000;
                    *buf.add(count) = (0xD800 + (cp_minus >> 10)) as u16;
                    *buf.add(count + 1) = (0xDC00 + (cp_minus & 0x3FF)) as u16;
                    count += 2;
                } else {
                    break;
                }
            }
            count as i32
        }
    })
}

/// Check if mouse tracking is active (DECSET 1000 or 1002).
#[no_mangle]
pub extern "C" fn term_emulator_is_mouse_tracking_active(
    emulator: *mut TerminalEmulator,
) -> bool {
    if emulator.is_null() {
        return false;
    }
    catch_unwind_or(|| unsafe { (*emulator).is_mouse_tracking_active() })
}

// ═══════════════════════════════════════════════════════════════════════════
// WcWidth (exposed for Kotlin)
// ═══════════════════════════════════════════════════════════════════════════

/// Return the display width of a Unicode code point.
///
/// - 0 for non-spacing / combining characters
/// - 1 for most characters
/// - 2 for fullwidth / wide characters
/// - -1 for non-printable / control characters
#[no_mangle]
pub extern "C" fn term_wcwidth(code_point: u32) -> i32 {
    catch_unwind_or(|| wcwidth::width(code_point))
}

// ═══════════════════════════════════════════════════════════════════════════
// ByteQueue (for PTY I/O)
// ═══════════════════════════════════════════════════════════════════════════

/// Create a new byte queue with the given buffer capacity.
///
/// Returns an opaque pointer that must be freed with [`term_byte_queue_free`].
/// Returns null on allocation failure or if `size <= 0`.
#[no_mangle]
pub extern "C" fn term_byte_queue_new(size: i32) -> *mut ByteQueue {
    if size <= 0 {
        return ptr::null_mut();
    }
    catch_unwind_or(|| {
        let queue = ByteQueue::new(size as usize);
        Box::into_raw(Box::new(queue))
    })
}

/// Free a byte queue previously created with [`term_byte_queue_new`].
///
/// Passing null is a safe no-op.
#[no_mangle]
pub extern "C" fn term_byte_queue_free(queue: *mut ByteQueue) {
    if queue.is_null() {
        return;
    }
    catch_unwind_or(|| {
        unsafe {
            let _ = Box::from_raw(queue);
        }
    });
}

/// Write bytes into the queue.
///
/// Copies `length` bytes from `buf` starting at `offset` into the queue.
/// Returns `true` if all bytes were written, `false` if the queue was closed
/// or an error occurred.
///
/// If the buffer is full and the queue is open, this call blocks until space
/// becomes available.
///
/// Passing null for `queue` or `buf` returns false.
#[no_mangle]
pub extern "C" fn term_byte_queue_write(
    queue: *mut ByteQueue,
    buf: *const u8,
    offset: i32,
    length: i32,
) -> bool {
    if queue.is_null() || buf.is_null() || length <= 0 || offset < 0 {
        return false;
    }
    catch_unwind_or(|| {
        unsafe {
            let slice = std::slice::from_raw_parts(buf, (offset + length) as usize);
            (*queue).write(slice, offset as usize, length as usize)
        }
    })
}

/// Read bytes from the queue into `buf`.
///
/// Reads up to `max_len` bytes. When `block` is true, the call sleeps until
/// data is available or the queue is closed.
///
/// Returns:
/// - -1 if the queue has been closed
/// - 0 if the queue is empty and `block` is false
/// - the number of bytes actually read
///
/// Passing null for `queue` or `buf` returns 0.
#[no_mangle]
pub extern "C" fn term_byte_queue_read(
    queue: *mut ByteQueue,
    buf: *mut u8,
    max_len: i32,
    block: bool,
) -> i32 {
    if queue.is_null() || buf.is_null() || max_len <= 0 {
        return 0;
    }
    catch_unwind_or(|| {
        unsafe {
            let out = std::slice::from_raw_parts_mut(buf, max_len as usize);
            (*queue).read(out, block)
        }
    })
}

/// Close the queue.
///
/// After closing, [`term_byte_queue_read`] returns -1 and
/// [`term_byte_queue_write`] returns false. Any threads blocked on read or
/// write are woken up.
///
/// Passing null is a safe no-op.
#[no_mangle]
pub extern "C" fn term_byte_queue_close(queue: *mut ByteQueue) {
    if queue.is_null() {
        return;
    }
    catch_unwind_or(|| {
        unsafe {
            (*queue).close();
        }
    });
}

// ═══════════════════════════════════════════════════════════════════════════
// Draw run computation — offloads per-character iteration to Rust
// ═══════════════════════════════════════════════════════════════════════════

/// Compute draw runs for all dirty rows in the terminal screen.
///
/// Groups characters into runs by style, cursor position, and selection state.
/// Returns a flat i32 buffer encoding draw commands. Dirty rows are marked clean.
///
/// Output format: [count, cmd0_0..cmd0_9, cmd1_0..cmd1_9, ...]
/// Each command is 10 i32s:
///   [0] row (external coordinate)
///   [1] startColumn
///   [2] columnWidth (in terminal columns)
///   [3] startCharIndex (into the row's UTF-16 text array)
///   [4] charsCount (number of UTF-16 code units)
///   [5] style_high (upper 32 bits of u64 style)
///   [6] style_low (lower 32 bits of u64 style)
///   [7] cursorCol (-1 if no cursor in this run)
///   [8] cursorStyle
///   [9] flags: bit0=hasNonDefaultWidth, bit1=insideSelection
///
/// Returns an allocated i32 buffer. Caller must free with `term_free_i32_buffer`.
/// Returns null on error.
#[no_mangle]
pub extern "C" fn term_emulator_compute_draw_runs(
    emulator: *mut TerminalEmulator,
    top_row: i32,
    selection_y1: i32,
    selection_y2: i32,
    selection_x1: i32,
    selection_x2: i32,
    out_len: *mut i32,
) -> *mut i32 {
    if emulator.is_null() || out_len.is_null() {
        return ptr::null_mut();
    }
    catch_unwind_or(|| {
        unsafe {
            let em = &*emulator;
            let screen = em.screen();
            let columns = screen.columns;
            let rows = screen.screen_rows;
            let end_row = top_row + rows;
            let cursor_col = em.cursor_col();
            let cursor_row = em.cursor_row();
            let cursor_visible = em.is_cursor_visible();
            let cursor_style = em.cursor_style();

            // Estimate capacity: ~10 commands per row * 10 ints per command
            let mut commands: Vec<i32> = Vec::with_capacity((rows as usize * 100) + 1);
            commands.push(0); // placeholder for count

            for row in top_row..end_row {
                let internal_row = screen.external_to_internal_row(row);
                let line = match &screen.lines[internal_row as usize] {
                    Some(l) => l,
                    None => continue,
                };

                if !line.dirty {
                    continue;
                }

                // Mark as clean (we need mutable access — get it via the lines vec)
                // SAFETY: We're only setting the dirty flag to false, which doesn't
                // affect any data we're currently reading.
                let line_mut = screen.lines.as_ptr().add(internal_row as usize) as *mut Option<crate::terminal_row::TerminalRow>;
                if let Some(ref mut row_data) = *line_mut {
                    row_data.dirty = false;
                }

                let text = &line.text;
                let space_used = line.space_used() as usize;
                let line_cursor_x = if row == cursor_row && cursor_visible { cursor_col } else { -1 };

                let mut selx1 = -1i32;
                let mut selx2 = -1i32;
                if row >= selection_y1 && row <= selection_y2 {
                    if row == selection_y1 { selx1 = selection_x1; }
                    selx2 = if row == selection_y2 { selection_x2 } else { columns };
                }

                // Iterate characters and group into runs by style
                let mut last_run_style: u64 = 0;
                let mut last_run_inside_cursor = false;
                let mut last_run_inside_selection = false;
                let mut last_run_font_width_mismatch = false;
                let mut last_run_start_column = -1i32;
                let mut last_run_start_index = 0usize;
                let mut current_char_index = 0usize;
                let mut column = 0i32;
                let mut first = true;

                while column < columns && current_char_index < space_used {
                    let char_at_index = text[current_char_index];

                    // ASCII fast-path
                    let (code_point, chars_for_code_point, code_point_wc_width);
                    if char_at_index < 0x80 {
                        code_point = char_at_index as u32;
                        chars_for_code_point = 1;
                        code_point_wc_width = if char_at_index < 32 {
                            crate::wcwidth::width(code_point)
                        } else {
                            1
                        };
                    } else {
                        let is_high = (char_at_index & 0xFC00) == 0xD800;
                        chars_for_code_point = if is_high { 2 } else { 1 };
                        code_point = if is_high && current_char_index + 1 < space_used {
                            ((char_at_index as u32 - 0xD800) << 10) | (text[current_char_index + 1] as u32 - 0xDC00) + 0x10000
                        } else {
                            char_at_index as u32
                        };
                        code_point_wc_width = crate::wcwidth::width(code_point);
                    }

                    let inside_cursor = line_cursor_x == column || (code_point_wc_width == 2 && line_cursor_x == column + 1);
                    let inside_selection = column >= selx1 && column <= selx2;
                    let style = line.get_style(column);

                    // Font width mismatch detection
                    let font_width_mismatch = if code_point_wc_width == 1 {
                        false // ASCII and width-1 chars are always fine
                    } else {
                        // For wide chars, we still need the measured width — flag it
                        true
                    };

                    if style != last_run_style || inside_cursor != last_run_inside_cursor
                        || inside_selection != last_run_inside_selection
                        || font_width_mismatch != last_run_font_width_mismatch
                        || first
                    {
                        if !first {
                            // Emit previous run
                            let column_width = column - last_run_start_column;
                            let chars_count = current_char_index - last_run_start_index;
                            let style_high = (last_run_style >> 32) as i32;
                            let style_low = (last_run_style & 0xFFFFFFFF) as i32;
                            let cc = if last_run_inside_cursor { line_cursor_x } else { -1 };
                            let flags = (if last_run_font_width_mismatch { 1 } else { 0 })
                                | (if last_run_inside_selection { 2 } else { 0 });

                            commands.push(row);
                            commands.push(last_run_start_column);
                            commands.push(column_width);
                            commands.push(last_run_start_index as i32);
                            commands.push(chars_count as i32);
                            commands.push(style_high);
                            commands.push(style_low);
                            commands.push(cc);
                            commands.push(cursor_style);
                            commands.push(flags);
                        }
                        first = false;
                        last_run_style = style;
                        last_run_inside_cursor = inside_cursor;
                        last_run_inside_selection = inside_selection;
                        last_run_start_column = column;
                        last_run_start_index = current_char_index;
                        last_run_font_width_mismatch = font_width_mismatch;
                    }

                    column += code_point_wc_width;
                    current_char_index += chars_for_code_point;

                    // Eat combining characters
                    while current_char_index < space_used {
                        let ch = text[current_char_index];
                        let cp = if (ch & 0xFC00) == 0xD800 && current_char_index + 1 < space_used {
                            ((ch as u32 - 0xD800) << 10) | (text[current_char_index + 1] as u32 - 0xDC00) + 0x10000
                        } else {
                            ch as u32
                        };
                        if crate::wcwidth::width(cp) <= 0 {
                            current_char_index += if (ch & 0xFC00) == 0xD800 { 2 } else { 1 };
                        } else {
                            break;
                        }
                    }
                }

                // Emit final run for this row
                if !first {
                    let column_width = column - last_run_start_column;
                    let chars_count = current_char_index - last_run_start_index;
                    let style_high = (last_run_style >> 32) as i32;
                    let style_low = (last_run_style & 0xFFFFFFFF) as i32;
                    let cc = if last_run_inside_cursor { line_cursor_x } else { -1 };
                    let flags = (if last_run_font_width_mismatch { 1 } else { 0 })
                        | (if last_run_inside_selection { 2 } else { 0 });

                    commands.push(row);
                    commands.push(last_run_start_column);
                    commands.push(column_width);
                    commands.push(last_run_start_index as i32);
                    commands.push(chars_count as i32);
                    commands.push(style_high);
                    commands.push(style_low);
                    commands.push(cc);
                    commands.push(cursor_style);
                    commands.push(flags);
                }
            }

            let count = ((commands.len() - 1) / 10) as i32;
            commands[0] = count;
            *out_len = commands.len() as i32;

            let mut boxed = commands.into_boxed_slice();
            let ptr = boxed.as_mut_ptr();
            std::mem::forget(boxed);
            ptr
        }
    })
}

/// Free an i32 buffer allocated by `term_emulator_compute_draw_runs`.
#[no_mangle]
pub extern "C" fn term_free_i32_buffer(buf: *mut i32, len: i32) {
    if buf.is_null() || len <= 0 {
        return;
    }
    catch_unwind_or(|| {
        unsafe {
            let slice = std::slice::from_raw_parts_mut(buf, len as usize);
            let _ = Box::from_raw(slice as *mut [i32]);
        }
    });
}

// ═══════════════════════════════════════════════════════════════════════════
// JNI wrappers — map Kotlin external fun names to Rust FFI functions.
//
// Kotlin `@JvmStatic external fun` on `object RustJNI` in package
// `com.termux.terminal` produces JNI symbol names of the form:
//   Java_com_termux_terminal_RustJNI_<methodName>
//
// Each wrapper accepts (JNIEnv*, jclass, ...) and delegates to the
// corresponding short-named FFI function above.
// ═══════════════════════════════════════════════════════════════════════════

// We only provide wrappers for functions that are declared as `external fun`
// in RustJNI.kt. Unused parameters are prefixed with `_`.

type JniEnv = *mut core::ffi::c_void;
type Jclass = *mut core::ffi::c_void;
type Jlong = i64;
type Jint = i32;
type Jboolean = u8; // JNI jboolean is u8, not bool

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termEmulatorNew(
    _env: JniEnv,
    _clazz: Jclass,
    cols: Jint,
    rows: Jint,
    cell_width: Jint,
    cell_height: Jint,
    transcript_rows: Jint,
) -> Jlong {
    term_emulator_new(cols, rows, cell_width, cell_height, transcript_rows) as Jlong
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termEmulatorFree(
    _env: JniEnv,
    _clazz: Jclass,
    emulator: Jlong,
) {
    term_emulator_free(emulator as *mut TerminalEmulator);
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termEmulatorProcessBytes(
    _env: JniEnv,
    _clazz: Jclass,
    emulator: Jlong,
    bytes: *const u8,
    length: Jint,
) {
    term_emulator_process_bytes(emulator as *mut TerminalEmulator, bytes, length);
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termEmulatorGetOutput(
    _env: JniEnv,
    _clazz: Jclass,
    emulator: Jlong,
    buf: *mut u8,
    max_len: Jint,
) -> Jint {
    term_emulator_get_output(emulator as *mut TerminalEmulator, buf, max_len)
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termEmulatorGetFlags(
    _env: JniEnv,
    _clazz: Jclass,
    emulator: Jlong,
) -> Jint {
    term_emulator_get_flags(emulator as *mut TerminalEmulator) as Jint
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termEmulatorGetCharAt(
    _env: JniEnv,
    _clazz: Jclass,
    emulator: Jlong,
    row: Jint,
    col: Jint,
) -> Jlong {
    term_emulator_get_char_at(emulator as *mut TerminalEmulator, row, col) as Jlong
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termEmulatorGetCursorRow(
    _env: JniEnv,
    _clazz: Jclass,
    emulator: Jlong,
) -> Jint {
    term_emulator_get_cursor_row(emulator as *mut TerminalEmulator)
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termEmulatorGetCursorCol(
    _env: JniEnv,
    _clazz: Jclass,
    emulator: Jlong,
) -> Jint {
    term_emulator_get_cursor_col(emulator as *mut TerminalEmulator)
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termEmulatorGetCursorStyle(
    _env: JniEnv,
    _clazz: Jclass,
    emulator: Jlong,
) -> Jint {
    term_emulator_get_cursor_style(emulator as *mut TerminalEmulator)
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termEmulatorGetRows(
    _env: JniEnv,
    _clazz: Jclass,
    emulator: Jlong,
) -> Jint {
    term_emulator_get_rows(emulator as *mut TerminalEmulator)
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termEmulatorGetColumns(
    _env: JniEnv,
    _clazz: Jclass,
    emulator: Jlong,
) -> Jint {
    term_emulator_get_columns(emulator as *mut TerminalEmulator)
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termEmulatorGetColor(
    _env: JniEnv,
    _clazz: Jclass,
    emulator: Jlong,
    index: Jint,
) -> Jint {
    term_emulator_get_color(emulator as *mut TerminalEmulator, index)
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termEmulatorIsReverseVideo(
    _env: JniEnv,
    _clazz: Jclass,
    emulator: Jlong,
) -> Jboolean {
    term_emulator_get_reverse_video(emulator as *mut TerminalEmulator) as Jboolean
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termEmulatorIsAlternateBufferActive(
    _env: JniEnv,
    _clazz: Jclass,
    emulator: Jlong,
) -> Jboolean {
    let em = emulator as *mut TerminalEmulator;
    if em.is_null() {
        return 0;
    }
    catch_unwind_or(|| unsafe { (*em).is_alternate_buffer_active() }) as Jboolean
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termEmulatorIsMouseTrackingActive(
    _env: JniEnv,
    _clazz: Jclass,
    emulator: Jlong,
) -> Jboolean {
    term_emulator_is_mouse_tracking_active(emulator as *mut TerminalEmulator) as Jboolean
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termWcwidth(
    _env: JniEnv,
    _clazz: Jclass,
    code_point: Jint,
) -> Jint {
    term_wcwidth(code_point as u32)
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termByteQueueNew(
    _env: JniEnv,
    _clazz: Jclass,
    size: Jint,
) -> Jlong {
    term_byte_queue_new(size) as Jlong
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termByteQueueFree(
    _env: JniEnv,
    _clazz: Jclass,
    queue: Jlong,
) {
    term_byte_queue_free(queue as *mut ByteQueue);
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termByteQueueWrite(
    _env: JniEnv,
    _clazz: Jclass,
    queue: Jlong,
    buf: *const u8,
    offset: Jint,
    length: Jint,
) -> Jboolean {
    term_byte_queue_write(queue as *mut ByteQueue, buf, offset, length) as Jboolean
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termByteQueueRead(
    _env: JniEnv,
    _clazz: Jclass,
    queue: Jlong,
    buf: *mut u8,
    max_len: Jint,
    block: Jboolean,
) -> Jint {
    term_byte_queue_read(queue as *mut ByteQueue, buf, max_len, block != 0)
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termByteQueueClose(
    _env: JniEnv,
    _clazz: Jclass,
    queue: Jlong,
) {
    term_byte_queue_close(queue as *mut ByteQueue);
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termEmulatorResize(
    _env: JniEnv,
    _clazz: Jclass,
    emulator: Jlong,
    cols: Jint,
    rows: Jint,
    cell_width: Jint,
    cell_height: Jint,
) {
    let em = emulator as *mut TerminalEmulator;
    if em.is_null() {
        return;
    }
    catch_unwind_or(|| {
        unsafe {
            (*em).resize(cols, rows, cell_width, cell_height);
        }
    });
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termEmulatorPaste(
    _env: JniEnv,
    _clazz: Jclass,
    emulator: Jlong,
    text: *const u16,
    text_len: Jint,
) {
    let em = emulator as *mut TerminalEmulator;
    if em.is_null() || text.is_null() || text_len <= 0 {
        return;
    }
    catch_unwind_or(|| {
        unsafe {
            let slice = core::slice::from_raw_parts(text, text_len as usize);
            // Convert UTF-16 to String
            let s = String::from_utf16_lossy(slice);
            (*em).paste(&s);
        }
    });
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termEmulatorGetSelectedText(
    _env: JniEnv,
    _clazz: Jclass,
    emulator: Jlong,
    x1: Jint,
    y1: Jint,
    x2: Jint,
    y2: Jint,
    join_back: Jboolean,
    join_full: Jboolean,
) -> *mut u8 {
    // Return a malloc'd buffer with the selected text as UTF-8.
    // The caller (Kotlin) will need to read and free it.
    // For simplicity, we use a static buffer approach — but this is not ideal.
    // A real implementation would use JNI's NewStringUTF.
    let em = emulator as *mut TerminalEmulator;
    if em.is_null() {
        return core::ptr::null_mut();
    }
    catch_unwind_or(|| {
        unsafe {
            let text = (*em).get_selected_text(x1, y1, x2, y2, join_back != 0, join_full != 0);
            // We can't return a JNI string without JNIEnv, so we leak a buffer
            // and let the JNI wrapper handle it. This is a limitation.
            // For now, return null — the JNI wrapper will handle this case.
            let _ = text;
            core::ptr::null_mut()
        }
    })
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termEmulatorSendMouseEvent(
    _env: JniEnv,
    _clazz: Jclass,
    emulator: Jlong,
    _mouse_button: Jint,
    _column: Jint,
    _row: Jint,
    _pressed: Jboolean,
) {
    let em = emulator as *mut TerminalEmulator;
    if em.is_null() {
        return;
    }
    catch_unwind_or(|| {
        unsafe {
            (*em).send_mouse_event(_mouse_button, _column, _row, _pressed != 0);
        }
    });
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termEmulatorGetScrollCounter(
    _env: JniEnv,
    _clazz: Jclass,
    emulator: Jlong,
) -> Jint {
    let em = emulator as *mut TerminalEmulator;
    if em.is_null() {
        return 0;
    }
    catch_unwind_or(|| unsafe { (*em).get_scroll_counter() })
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termEmulatorClearScrollCounter(
    _env: JniEnv,
    _clazz: Jclass,
    emulator: Jlong,
) {
    let em = emulator as *mut TerminalEmulator;
    if em.is_null() {
        return;
    }
    catch_unwind_or(|| {
        unsafe {
            (*em).clear_scroll_counter();
        }
    });
}

// ═══════════════════════════════════════════════════════════════════════════
// Draw run computation (Rust → Kotlin render commands)
// ═══════════════════════════════════════════════════════════════════════════

// JNI struct for accessing JNIEnv function table.
// We need this to call NewIntArray/SetIntArrayRegion from Rust.
#[repr(C)]
struct JniNativeInterface {
    reserved0: *mut core::ffi::c_void,
    reserved1: *mut core::ffi::c_void,
    reserved2: *mut core::ffi::c_void,
    reserved3: *mut core::ffi::c_void,
    // ... we only need a few function pointers
}

#[repr(C)]
struct JniEnvInner {
    functions: *const JniNativeInterface,
}

/// Compute draw runs for dirty rows and return as a Java IntArray.
/// This is the main entry point for Rust-accelerated rendering.
///
/// Returns IntArray: [count, cmd0_0..cmd0_9, cmd1_0..cmd1_9, ...]
/// Each command is 10 ints encoding one draw run.
#[no_mangle]
pub unsafe extern "C" fn Java_com_termux_terminal_RustJNI_termEmulatorComputeDrawRuns(
    env: JniEnv,
    _clazz: Jclass,
    emulator: Jlong,
    top_row: Jint,
    sel_y1: Jint,
    sel_y2: Jint,
    sel_x1: Jint,
    sel_x2: Jint,
) -> *mut core::ffi::c_void {
    let em = emulator as *mut TerminalEmulator;
    if em.is_null() {
        return core::ptr::null_mut();
    }

    let mut out_len: i32 = 0;
    let buf = term_emulator_compute_draw_runs(em, top_row, sel_y1, sel_y2, sel_x1, sel_x2, &mut out_len);

    if buf.is_null() || out_len <= 0 {
        if !buf.is_null() {
            term_free_i32_buffer(buf, out_len);
        }
        return core::ptr::null_mut();
    }

    // Create Java IntArray and copy data into it.
    // We use the JNI function table directly.
    let env_inner = env as *mut JniEnvInner;
    let funcs = (*env_inner).functions;

    // JNI function offsets (from jni.h):
    // NewIntArray is at offset 173 (in pointer-sized units)
    // SetIntArrayRegion is at offset 183
    // These are standard JNI 1.6+ offsets.
    let func_table = funcs as *const *const core::ffi::c_void;

    // NewIntArray(JNIEnv*, jsize) -> jintArray
    let new_int_array: extern "C" fn(*mut core::ffi::c_void, i32) -> *mut core::ffi::c_void =
        core::mem::transmute(*func_table.add(173));
    // SetIntArrayRegion(JNIEnv*, jintArray, jsize, jsize, const jint*)
    let set_int_array_region: extern "C" fn(
        *mut core::ffi::c_void,
        *mut core::ffi::c_void,
        i32,
        i32,
        *const i32,
    ) = core::mem::transmute(*func_table.add(183));

    let java_array = new_int_array(env, out_len);
    if !java_array.is_null() {
        set_int_array_region(env, java_array, 0, out_len, buf as *const i32);
    }

    term_free_i32_buffer(buf, out_len);
    java_array
}
