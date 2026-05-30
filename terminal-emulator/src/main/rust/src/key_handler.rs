//! Key handler: maps Android KeyEvent keycodes + modifiers to terminal escape sequences.
//! Ported from KeyHandler.java — pure logic, no Android dependencies.

/// Modifier bit flags (must match Java KeyHandler.KEYMOD_* constants)
pub const KEYMOD_ALT: i32 = 0x80000000u32 as i32;
pub const KEYMOD_CTRL: i32 = 0x40000000u32 as i32;
pub const KEYMOD_SHIFT: i32 = 0x20000000u32 as i32;
pub const KEYMOD_NUM_LOCK: i32 = 0x10000000u32 as i32;

// Android KeyEvent keycodes
const KEYCODE_DPAD_CENTER: i32 = 23;
const KEYCODE_DPAD_UP: i32 = 19;
const KEYCODE_DPAD_DOWN: i32 = 20;
const KEYCODE_DPAD_LEFT: i32 = 21;
const KEYCODE_DPAD_RIGHT: i32 = 22;
const KEYCODE_MOVE_HOME: i32 = 122;
const KEYCODE_MOVE_END: i32 = 123;
const KEYCODE_F1: i32 = 131;
const KEYCODE_F2: i32 = 132;
const KEYCODE_F3: i32 = 133;
const KEYCODE_F4: i32 = 134;
const KEYCODE_F5: i32 = 135;
const KEYCODE_F6: i32 = 136;
const KEYCODE_F7: i32 = 137;
const KEYCODE_F8: i32 = 138;
const KEYCODE_F9: i32 = 139;
const KEYCODE_F10: i32 = 140;
const KEYCODE_F11: i32 = 141;
const KEYCODE_F12: i32 = 142;
const KEYCODE_SYSRQ: i32 = 120;
const KEYCODE_BREAK: i32 = 121;
const KEYCODE_ESCAPE: i32 = 111;
const KEYCODE_BACK: i32 = 4;
const KEYCODE_INSERT: i32 = 124;
const KEYCODE_FORWARD_DEL: i32 = 112;
const KEYCODE_PAGE_UP: i32 = 92;
const KEYCODE_PAGE_DOWN: i32 = 93;
const KEYCODE_DEL: i32 = 67;
const KEYCODE_NUM_LOCK: i32 = 143;
const KEYCODE_SPACE: i32 = 62;
const KEYCODE_TAB: i32 = 61;
const KEYCODE_ENTER: i32 = 66;
const KEYCODE_NUMPAD_ENTER: i32 = 160;
const KEYCODE_NUMPAD_MULTIPLY: i32 = 155;
const KEYCODE_NUMPAD_ADD: i32 = 157;
const KEYCODE_NUMPAD_COMMA: i32 = 159;
const KEYCODE_NUMPAD_DOT: i32 = 158;
const KEYCODE_NUMPAD_SUBTRACT: i32 = 156;
const KEYCODE_NUMPAD_DIVIDE: i32 = 154;
const KEYCODE_NUMPAD_0: i32 = 144;
const KEYCODE_NUMPAD_1: i32 = 145;
const KEYCODE_NUMPAD_2: i32 = 146;
const KEYCODE_NUMPAD_3: i32 = 147;
const KEYCODE_NUMPAD_4: i32 = 148;
const KEYCODE_NUMPAD_5: i32 = 149;
const KEYCODE_NUMPAD_6: i32 = 150;
const KEYCODE_NUMPAD_7: i32 = 151;
const KEYCODE_NUMPAD_8: i32 = 152;
const KEYCODE_NUMPAD_9: i32 = 153;
const KEYCODE_NUMPAD_EQUALS: i32 = 161;

fn transform_for_modifiers(start: &[u8], keymod: i32, last_char: u8) -> Vec<u8> {
    let modifier = match keymod {
        k if k == KEYMOD_SHIFT => 2,
        k if k == KEYMOD_ALT => 3,
        k if k == KEYMOD_SHIFT | KEYMOD_ALT => 4,
        k if k == KEYMOD_CTRL => 5,
        k if k == KEYMOD_SHIFT | KEYMOD_CTRL => 6,
        k if k == KEYMOD_ALT | KEYMOD_CTRL => 7,
        k if k == KEYMOD_SHIFT | KEYMOD_ALT | KEYMOD_CTRL => 8,
        _ => {
            let mut result = start.to_vec();
            result.push(last_char);
            return result;
        }
    };
    let mut result = start.to_vec();
    result.extend_from_slice(format!(";{}", modifier).as_bytes());
    result.push(last_char);
    result
}

/// Returns escape sequence bytes for the given keycode + modifiers.
/// Returns empty Vec if no mapping exists (caller should handle via normal input).
pub fn get_code(key_code: i32, key_mode: i32, cursor_app: bool, keypad_app: bool) -> Vec<u8> {
    let num_lock_on = (key_mode & KEYMOD_NUM_LOCK) != 0;
    let key_mode = key_mode & !KEYMOD_NUM_LOCK;

    match key_code {
        KEYCODE_DPAD_CENTER => b"\r".to_vec(),

        KEYCODE_DPAD_UP => {
            if key_mode == 0 {
                if cursor_app {
                    b"\x1bOA".to_vec()
                } else {
                    b"\x1b[A".to_vec()
                }
            } else {
                transform_for_modifiers(b"\x1b[1", key_mode, b'A')
            }
        }
        KEYCODE_DPAD_DOWN => {
            if key_mode == 0 {
                if cursor_app {
                    b"\x1bOB".to_vec()
                } else {
                    b"\x1b[B".to_vec()
                }
            } else {
                transform_for_modifiers(b"\x1b[1", key_mode, b'B')
            }
        }
        KEYCODE_DPAD_RIGHT => {
            if key_mode == 0 {
                if cursor_app {
                    b"\x1bOC".to_vec()
                } else {
                    b"\x1b[C".to_vec()
                }
            } else {
                transform_for_modifiers(b"\x1b[1", key_mode, b'C')
            }
        }
        KEYCODE_DPAD_LEFT => {
            if key_mode == 0 {
                if cursor_app {
                    b"\x1bOD".to_vec()
                } else {
                    b"\x1b[D".to_vec()
                }
            } else {
                transform_for_modifiers(b"\x1b[1", key_mode, b'D')
            }
        }

        KEYCODE_MOVE_HOME => {
            if key_mode == 0 {
                if cursor_app {
                    b"\x1bOH".to_vec()
                } else {
                    b"\x1b[H".to_vec()
                }
            } else {
                transform_for_modifiers(b"\x1b[1", key_mode, b'H')
            }
        }
        KEYCODE_MOVE_END => {
            if key_mode == 0 {
                if cursor_app {
                    b"\x1bOF".to_vec()
                } else {
                    b"\x1b[F".to_vec()
                }
            } else {
                transform_for_modifiers(b"\x1b[1", key_mode, b'F')
            }
        }

        KEYCODE_F1 => {
            if key_mode == 0 {
                b"\x1bOP".to_vec()
            } else {
                transform_for_modifiers(b"\x1b[1", key_mode, b'P')
            }
        }
        KEYCODE_F2 => {
            if key_mode == 0 {
                b"\x1bOQ".to_vec()
            } else {
                transform_for_modifiers(b"\x1b[1", key_mode, b'Q')
            }
        }
        KEYCODE_F3 => {
            if key_mode == 0 {
                b"\x1bOR".to_vec()
            } else {
                transform_for_modifiers(b"\x1b[1", key_mode, b'R')
            }
        }
        KEYCODE_F4 => {
            if key_mode == 0 {
                b"\x1bOS".to_vec()
            } else {
                transform_for_modifiers(b"\x1b[1", key_mode, b'S')
            }
        }
        KEYCODE_F5 => transform_for_modifiers(b"\x1b[15", key_mode, b'~'),
        KEYCODE_F6 => transform_for_modifiers(b"\x1b[17", key_mode, b'~'),
        KEYCODE_F7 => transform_for_modifiers(b"\x1b[18", key_mode, b'~'),
        KEYCODE_F8 => transform_for_modifiers(b"\x1b[19", key_mode, b'~'),
        KEYCODE_F9 => transform_for_modifiers(b"\x1b[20", key_mode, b'~'),
        KEYCODE_F10 => transform_for_modifiers(b"\x1b[21", key_mode, b'~'),
        KEYCODE_F11 => transform_for_modifiers(b"\x1b[23", key_mode, b'~'),
        KEYCODE_F12 => transform_for_modifiers(b"\x1b[24", key_mode, b'~'),

        KEYCODE_SYSRQ => b"\x1b[32~".to_vec(),
        KEYCODE_BREAK => b"\x1b[34~".to_vec(),

        KEYCODE_ESCAPE | KEYCODE_BACK => b"\x1b".to_vec(),

        KEYCODE_INSERT => transform_for_modifiers(b"\x1b[2", key_mode, b'~'),
        KEYCODE_FORWARD_DEL => transform_for_modifiers(b"\x1b[3", key_mode, b'~'),

        KEYCODE_PAGE_UP => transform_for_modifiers(b"\x1b[5", key_mode, b'~'),
        KEYCODE_PAGE_DOWN => transform_for_modifiers(b"\x1b[6", key_mode, b'~'),

        KEYCODE_DEL => {
            let mut result = Vec::new();
            if (key_mode & KEYMOD_ALT) != 0 {
                result.push(0x1b);
            }
            if (key_mode & KEYMOD_CTRL) != 0 {
                result.push(0x08);
            } else {
                result.push(0x7f);
            }
            result
        }

        KEYCODE_NUM_LOCK => {
            if keypad_app {
                b"\x1bOP".to_vec()
            } else {
                return vec![];
            }
        }

        KEYCODE_SPACE => {
            if (key_mode & KEYMOD_CTRL) == 0 {
                vec![]
            } else {
                b"\0".to_vec()
            }
        }

        KEYCODE_TAB => {
            if (key_mode & KEYMOD_SHIFT) == 0 {
                b"\x09".to_vec()
            } else {
                b"\x1b[Z".to_vec()
            }
        }

        KEYCODE_ENTER => {
            if (key_mode & KEYMOD_ALT) == 0 {
                b"\r".to_vec()
            } else {
                b"\x1b\r".to_vec()
            }
        }

        KEYCODE_NUMPAD_ENTER => {
            if keypad_app {
                transform_for_modifiers(b"\x1bO", key_mode, b'M')
            } else {
                b"\n".to_vec()
            }
        }
        KEYCODE_NUMPAD_MULTIPLY => {
            if keypad_app {
                transform_for_modifiers(b"\x1bO", key_mode, b'j')
            } else {
                b"*".to_vec()
            }
        }
        KEYCODE_NUMPAD_ADD => {
            if keypad_app {
                transform_for_modifiers(b"\x1bO", key_mode, b'k')
            } else {
                b"+".to_vec()
            }
        }
        KEYCODE_NUMPAD_COMMA => b",".to_vec(),
        KEYCODE_NUMPAD_DOT => {
            if num_lock_on {
                if keypad_app {
                    b"\x1bOn".to_vec()
                } else {
                    b".".to_vec()
                }
            } else {
                transform_for_modifiers(b"\x1b[3", key_mode, b'~')
            }
        }
        KEYCODE_NUMPAD_SUBTRACT => {
            if keypad_app {
                transform_for_modifiers(b"\x1bO", key_mode, b'm')
            } else {
                b"-".to_vec()
            }
        }
        KEYCODE_NUMPAD_DIVIDE => {
            if keypad_app {
                transform_for_modifiers(b"\x1bO", key_mode, b'o')
            } else {
                b"/".to_vec()
            }
        }
        KEYCODE_NUMPAD_0 => {
            if num_lock_on {
                if keypad_app {
                    transform_for_modifiers(b"\x1bO", key_mode, b'p')
                } else {
                    b"0".to_vec()
                }
            } else {
                transform_for_modifiers(b"\x1b[2", key_mode, b'~')
            }
        }
        KEYCODE_NUMPAD_1 => {
            if num_lock_on {
                if keypad_app {
                    transform_for_modifiers(b"\x1bO", key_mode, b'q')
                } else {
                    b"1".to_vec()
                }
            } else {
                if key_mode == 0 {
                    if cursor_app {
                        b"\x1bOF".to_vec()
                    } else {
                        b"\x1b[F".to_vec()
                    }
                } else {
                    transform_for_modifiers(b"\x1b[1", key_mode, b'F')
                }
            }
        }
        KEYCODE_NUMPAD_2 => {
            if num_lock_on {
                if keypad_app {
                    transform_for_modifiers(b"\x1bO", key_mode, b'r')
                } else {
                    b"2".to_vec()
                }
            } else {
                if key_mode == 0 {
                    if cursor_app {
                        b"\x1bOB".to_vec()
                    } else {
                        b"\x1b[B".to_vec()
                    }
                } else {
                    transform_for_modifiers(b"\x1b[1", key_mode, b'B')
                }
            }
        }
        KEYCODE_NUMPAD_3 => {
            if num_lock_on {
                if keypad_app {
                    transform_for_modifiers(b"\x1bO", key_mode, b's')
                } else {
                    b"3".to_vec()
                }
            } else {
                b"\x1b[6~".to_vec()
            }
        }
        KEYCODE_NUMPAD_4 => {
            if num_lock_on {
                if keypad_app {
                    transform_for_modifiers(b"\x1bO", key_mode, b't')
                } else {
                    b"4".to_vec()
                }
            } else {
                if key_mode == 0 {
                    if cursor_app {
                        b"\x1bOD".to_vec()
                    } else {
                        b"\x1b[D".to_vec()
                    }
                } else {
                    transform_for_modifiers(b"\x1b[1", key_mode, b'D')
                }
            }
        }
        KEYCODE_NUMPAD_5 => {
            if keypad_app {
                transform_for_modifiers(b"\x1bO", key_mode, b'u')
            } else {
                b"5".to_vec()
            }
        }
        KEYCODE_NUMPAD_6 => {
            if num_lock_on {
                if keypad_app {
                    transform_for_modifiers(b"\x1bO", key_mode, b'v')
                } else {
                    b"6".to_vec()
                }
            } else {
                if key_mode == 0 {
                    if cursor_app {
                        b"\x1bOC".to_vec()
                    } else {
                        b"\x1b[C".to_vec()
                    }
                } else {
                    transform_for_modifiers(b"\x1b[1", key_mode, b'C')
                }
            }
        }
        KEYCODE_NUMPAD_7 => {
            if num_lock_on {
                if keypad_app {
                    transform_for_modifiers(b"\x1bO", key_mode, b'w')
                } else {
                    b"7".to_vec()
                }
            } else {
                if key_mode == 0 {
                    if cursor_app {
                        b"\x1bOH".to_vec()
                    } else {
                        b"\x1b[H".to_vec()
                    }
                } else {
                    transform_for_modifiers(b"\x1b[1", key_mode, b'H')
                }
            }
        }
        KEYCODE_NUMPAD_8 => {
            if num_lock_on {
                if keypad_app {
                    transform_for_modifiers(b"\x1bO", key_mode, b'x')
                } else {
                    b"8".to_vec()
                }
            } else {
                if key_mode == 0 {
                    if cursor_app {
                        b"\x1bOA".to_vec()
                    } else {
                        b"\x1b[A".to_vec()
                    }
                } else {
                    transform_for_modifiers(b"\x1b[1", key_mode, b'A')
                }
            }
        }
        KEYCODE_NUMPAD_9 => {
            if num_lock_on {
                if keypad_app {
                    transform_for_modifiers(b"\x1bO", key_mode, b'y')
                } else {
                    b"9".to_vec()
                }
            } else {
                b"\x1b[5~".to_vec()
            }
        }
        KEYCODE_NUMPAD_EQUALS => {
            if keypad_app {
                transform_for_modifiers(b"\x1bO", key_mode, b'X')
            } else {
                b"=".to_vec()
            }
        }

        _ => vec![],
    }
}
