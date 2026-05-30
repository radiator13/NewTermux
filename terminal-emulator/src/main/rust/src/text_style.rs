/// Encodes effects, foreground and background colors into a 64 bit integer,
/// which is stored for each cell in a terminal row.
///
/// The bit layout is:
/// - 16 flags (11 currently used).
/// - 24 for foreground color (only 9 first bits if a color index).
/// - 24 for background color (only 9 first bits if a color index).

pub const CHARACTER_ATTRIBUTE_BOLD: i32 = 1;
pub const CHARACTER_ATTRIBUTE_ITALIC: i32 = 1 << 1;
pub const CHARACTER_ATTRIBUTE_UNDERLINE: i32 = 1 << 2;
pub const CHARACTER_ATTRIBUTE_BLINK: i32 = 1 << 3;
pub const CHARACTER_ATTRIBUTE_INVERSE: i32 = 1 << 4;
pub const CHARACTER_ATTRIBUTE_INVISIBLE: i32 = 1 << 5;
pub const CHARACTER_ATTRIBUTE_STRIKETHROUGH: i32 = 1 << 6;
/// The selective erase control functions (DECSED and DECSEL) can only erase characters defined as erasable.
///
/// This bit is set if DECSCA (Select Character Protection Attribute) has been used to define the characters that
/// come after it as erasable from the screen.
pub const CHARACTER_ATTRIBUTE_PROTECTED: i32 = 1 << 7;
/// Dim colors. Also known as faint or half intensity.
pub const CHARACTER_ATTRIBUTE_DIM: i32 = 1 << 8;
/// If true (24-bit) color is used for the cell for foreground.
pub const CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND: i32 = 1 << 9;
/// If true (24-bit) color is used for the cell for background.
pub const CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND: i32 = 1 << 10;

pub const COLOR_INDEX_FOREGROUND: i32 = 256;
pub const COLOR_INDEX_BACKGROUND: i32 = 257;
pub const COLOR_INDEX_CURSOR: i32 = 258;

/// The 256 standard color entries and the three special (foreground, background and cursor) ones.
pub const NUM_INDEXED_COLORS: i32 = 259;

/// 0xff000000 as a signed i32 (-16777216). Used to detect 24-bit truecolor values.
/// In Kotlin: `0xff000000.toInt()` which wraps to this value.
const TRUECOLOR_MARKER: i32 = -16_777_216;

/// Normal foreground and background colors and no effects.
pub const NORMAL: u64 = encode(COLOR_INDEX_FOREGROUND, COLOR_INDEX_BACKGROUND, 0);

pub const fn encode(fore_color: i32, back_color: i32, effect: i32) -> u64 {
    let mut result = (effect & 0b111111111) as u64;
    if (TRUECOLOR_MARKER & fore_color) == TRUECOLOR_MARKER {
        // 24-bit color.
        result |= CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND as u64
            | ((fore_color as u64 & 0x00ffffff) << 40);
    } else {
        // Indexed color.
        result |= (fore_color as u64 & 0b111111111) << 40;
    }
    if (TRUECOLOR_MARKER & back_color) == TRUECOLOR_MARKER {
        // 24-bit color.
        result |= CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND as u64
            | ((back_color as u64 & 0x00ffffff) << 16);
    } else {
        // Indexed color.
        result |= (back_color as u64 & 0b111111111) << 16;
    }
    result
}

pub const fn decode_fore_color(style: u64) -> i32 {
    if (style & CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND as u64) == 0 {
        ((style >> 40) & 0b111111111) as i32
    } else {
        TRUECOLOR_MARKER | ((style >> 40) & 0x00ffffff) as i32
    }
}

pub const fn decode_back_color(style: u64) -> i32 {
    if (style & CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND as u64) == 0 {
        ((style >> 16) & 0b111111111) as i32
    } else {
        TRUECOLOR_MARKER | ((style >> 16) & 0x00ffffff) as i32
    }
}

pub const fn decode_effect(style: u64) -> i32 {
    (style & 0b11111111111) as i32
}

/// Helper: construct a 24-bit truecolor value (0xffRRGGBB as i32).
#[allow(dead_code)]
const fn truecolor(r: u32, g: u32, b: u32) -> i32 {
    let value = 0xff000000u32 | (r << 16) | (g << 8) | b;
    value as i32
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_normal_constant() {
        let normal = encode(COLOR_INDEX_FOREGROUND, COLOR_INDEX_BACKGROUND, 0);
        assert_eq!(NORMAL, normal);
        assert_eq!(decode_fore_color(NORMAL), COLOR_INDEX_FOREGROUND);
        assert_eq!(decode_back_color(NORMAL), COLOR_INDEX_BACKGROUND);
        assert_eq!(decode_effect(NORMAL), 0);
    }

    #[test]
    fn test_single_effects() {
        let effects = [
            (CHARACTER_ATTRIBUTE_BOLD, "BOLD"),
            (CHARACTER_ATTRIBUTE_ITALIC, "ITALIC"),
            (CHARACTER_ATTRIBUTE_UNDERLINE, "UNDERLINE"),
            (CHARACTER_ATTRIBUTE_BLINK, "BLINK"),
            (CHARACTER_ATTRIBUTE_INVERSE, "INVERSE"),
            (CHARACTER_ATTRIBUTE_INVISIBLE, "INVISIBLE"),
            (CHARACTER_ATTRIBUTE_STRIKETHROUGH, "STRIKETHROUGH"),
            (CHARACTER_ATTRIBUTE_PROTECTED, "PROTECTED"),
            (CHARACTER_ATTRIBUTE_DIM, "DIM"),
        ];
        for (effect, name) in effects {
            let style = encode(0, 0, effect);
            assert_eq!(
                decode_effect(style),
                effect,
                "Failed roundtrip for effect: {}",
                name
            );
        }
    }

    #[test]
    fn test_combined_effects() {
        let combined = CHARACTER_ATTRIBUTE_BOLD
            | CHARACTER_ATTRIBUTE_ITALIC
            | CHARACTER_ATTRIBUTE_UNDERLINE;
        let style = encode(1, 2, combined);
        assert_eq!(decode_effect(style), combined);
        assert_eq!(decode_fore_color(style), 1);
        assert_eq!(decode_back_color(style), 2);
    }

    #[test]
    fn test_all_effects_roundtrip() {
        let all_flags = CHARACTER_ATTRIBUTE_BOLD
            | CHARACTER_ATTRIBUTE_ITALIC
            | CHARACTER_ATTRIBUTE_UNDERLINE
            | CHARACTER_ATTRIBUTE_BLINK
            | CHARACTER_ATTRIBUTE_INVERSE
            | CHARACTER_ATTRIBUTE_INVISIBLE
            | CHARACTER_ATTRIBUTE_STRIKETHROUGH
            | CHARACTER_ATTRIBUTE_PROTECTED
            | CHARACTER_ATTRIBUTE_DIM;
        let style = encode(100, 200, all_flags);
        assert_eq!(decode_effect(style), all_flags);
        assert_eq!(decode_fore_color(style), 100);
        assert_eq!(decode_back_color(style), 200);
    }

    #[test]
    fn test_indexed_color_roundtrip() {
        let style = encode(5, 10, 0);
        assert_eq!(decode_fore_color(style), 5);
        assert_eq!(decode_back_color(style), 10);
        assert_eq!(decode_effect(style), 0);
    }

    #[test]
    fn test_indexed_color_max() {
        // Max 9-bit indexed value is 511 (0b111111111)
        let style = encode(511, 511, 0);
        assert_eq!(decode_fore_color(style), 511);
        assert_eq!(decode_back_color(style), 511);
    }

    #[test]
    fn test_special_color_indices() {
        let style = encode(COLOR_INDEX_FOREGROUND, COLOR_INDEX_BACKGROUND, 0);
        assert_eq!(decode_fore_color(style), COLOR_INDEX_FOREGROUND);
        assert_eq!(decode_back_color(style), COLOR_INDEX_BACKGROUND);

        let style2 = encode(
            COLOR_INDEX_CURSOR,
            COLOR_INDEX_FOREGROUND,
            CHARACTER_ATTRIBUTE_BOLD,
        );
        assert_eq!(decode_fore_color(style2), COLOR_INDEX_CURSOR);
        assert_eq!(decode_back_color(style2), COLOR_INDEX_FOREGROUND);
        assert_eq!(decode_effect(style2), CHARACTER_ATTRIBUTE_BOLD);
    }

    #[test]
    fn test_truecolor_foreground() {
        // 24-bit truecolor: 0xffRRGGBB format — high byte must be 0xff
        let truecolor_red = truecolor(0xff, 0x00, 0x00);
        let style = encode(truecolor_red, 0, 0);
        assert_eq!(decode_fore_color(style), truecolor_red);
    }

    #[test]
    fn test_truecolor_background() {
        let truecolor_green = truecolor(0x00, 0xff, 0x00);
        let style = encode(0, truecolor_green, 0);
        assert_eq!(decode_back_color(style), truecolor_green);
    }

    #[test]
    fn test_truecolor_both() {
        let truecolor_blue = truecolor(0x00, 0x00, 0xff);
        let truecolor_yellow = truecolor(0xff, 0xff, 0x00);
        let style = encode(truecolor_blue, truecolor_yellow, CHARACTER_ATTRIBUTE_BOLD);
        assert_eq!(decode_fore_color(style), truecolor_blue);
        assert_eq!(decode_back_color(style), truecolor_yellow);
        // decode_effect returns all 11 lower bits: 9 effect flags + 2 truecolor flags
        assert_eq!(
            decode_effect(style),
            CHARACTER_ATTRIBUTE_BOLD | CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND | CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND
        );
    }

    #[test]
    fn test_truecolor_with_all_flags() {
        let all_flags = CHARACTER_ATTRIBUTE_BOLD
            | CHARACTER_ATTRIBUTE_ITALIC
            | CHARACTER_ATTRIBUTE_UNDERLINE
            | CHARACTER_ATTRIBUTE_BLINK
            | CHARACTER_ATTRIBUTE_INVERSE
            | CHARACTER_ATTRIBUTE_INVISIBLE
            | CHARACTER_ATTRIBUTE_STRIKETHROUGH
            | CHARACTER_ATTRIBUTE_PROTECTED
            | CHARACTER_ATTRIBUTE_DIM;
        let fg = truecolor(0x12, 0x34, 0x56);
        let bg = truecolor(0x78, 0x9a, 0xbc);
        let style = encode(fg, bg, all_flags);
        assert_eq!(decode_fore_color(style), fg);
        assert_eq!(decode_back_color(style), bg);
        // decode_effect includes the truecolor bits for fg+bg
        assert_eq!(
            decode_effect(style),
            all_flags | CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND | CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND
        );
    }

    #[test]
    fn test_effect_mask_limits_to_9_bits_during_encode() {
        // Encode masks the effect to 9 bits (0b111111111 = 511)
        // So passing 0x7ff (11 bits) truncates to 0x1ff (511)
        let style = encode(0, 0, 0x7ff);
        let decoded_effect = decode_effect(style);
        // Only 9 bits survive encoding; no truecolor flags (both colors are 0/indexed)
        assert_eq!(decoded_effect & 0b111111111, 0x1ff);
        // No truecolor flags for indexed colors
        assert_eq!(decoded_effect, 0x1ff);
    }

    #[test]
    fn test_effect_decode_includes_truecolor_bits() {
        // decode_effect returns 11 bits — the 9 user effect bits plus
        // bits 9 (TRUECOLOR_FOREGROUND) and 10 (TRUECOLOR_BACKGROUND)
        let style_tc_fg = encode(0xff000000_u32 as i32, 0, 0);
        assert_eq!(
            decode_effect(style_tc_fg),
            CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND,
            "Truecolor FG bit should appear in decoded effect"
        );

        let style_tc_bg = encode(0, 0xff000000_u32 as i32, 0);
        assert_eq!(
            decode_effect(style_tc_bg),
            CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND,
            "Truecolor BG bit should appear in decoded effect"
        );

        let style_both = encode(0xff000000_u32 as i32, 0xff000000_u32 as i32, 0);
        assert_eq!(
            decode_effect(style_both),
            CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND | CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND,
            "Both truecolor bits should appear in decoded effect"
        );
    }

    #[test]
    fn test_color_index_masking() {
        // Indexed colors are masked to 9 bits (0-511)
        let style = encode(0x1ff, 0x1ff, 0); // max 9-bit value
        assert_eq!(decode_fore_color(style), 0x1ff);
        assert_eq!(decode_back_color(style), 0x1ff);

        // Values beyond 9 bits get truncated
        let style2 = encode(0x200, 0x200, 0);
        assert_eq!(decode_fore_color(style2), 0);
        assert_eq!(decode_back_color(style2), 0);
    }

    #[test]
    fn test_truecolor_detection() {
        // 0xff000000 triggers truecolor path
        let tc = encode(
            TRUECOLOR_MARKER | 0x123456,
            TRUECOLOR_MARKER | 0x789abc,
            0,
        );
        // Should decode as truecolor
        assert_eq!(decode_fore_color(tc), TRUECOLOR_MARKER | 0x123456);
        assert_eq!(decode_back_color(tc), TRUECOLOR_MARKER | 0x789abc);

        // 0xfe000000 does NOT trigger truecolor (high byte != 0xff)
        // 0xfe000000 as i32 = -33554432
        let non_tc = -33_554_432_i32; // 0xfe000000
        let idx = encode(non_tc, non_tc, 0);
        // Should be indexed (only 9 bits preserved)
        assert_eq!(decode_fore_color(idx), 0); // 0xfe000000 as i32 & 0x1ff = 0
        assert_eq!(decode_back_color(idx), 0);
    }

    #[test]
    fn test_encode_is_const() {
        // Verify const evaluation works at compile time
        const S: u64 = encode(1, 2, CHARACTER_ATTRIBUTE_BOLD);
        assert_eq!(decode_fore_color(S), 1);
        assert_eq!(decode_back_color(S), 2);
        assert_eq!(decode_effect(S), CHARACTER_ATTRIBUTE_BOLD);
    }

    #[test]
    fn test_normal_is_const() {
        // NORMAL is a const, verify it encodes correctly
        const FG: i32 = decode_fore_color(NORMAL);
        const BG: i32 = decode_back_color(NORMAL);
        const EFF: i32 = decode_effect(NORMAL);
        assert_eq!(FG, COLOR_INDEX_FOREGROUND);
        assert_eq!(BG, COLOR_INDEX_BACKGROUND);
        assert_eq!(EFF, 0);
    }
}
