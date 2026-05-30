//! Termux Terminal Emulator - Rust Implementation
//!
//! High-performance terminal emulator core ported from Kotlin.
//! Designed to be called via C FFI from Android/Kotlin.

pub mod byte_queue;
pub mod ffi;
pub mod key_handler;
pub mod terminal_buffer;
pub mod terminal_emulator;
pub mod terminal_row;
pub mod text_style;
pub mod wcwidth;
