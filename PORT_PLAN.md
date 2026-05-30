# NewTermux Codebase Review & Port Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Port NewTermux Java codebase to Kotlin (UI/app layer) and Rust (pure logic), reducing Java LOC while preserving all public APIs.

**Architecture:** Kotlin for Android-facing code (Activities, Views, Settings, Features), Rust for CPU-bound pure logic (data processing, parsing, file ops utilities). Java thin wrappers remain only where Android framework APIs are deeply coupled.

**Tech Stack:** Kotlin 2.0+, Rust 2021 edition (cdylib), existing RustJNI bridge pattern

---

## Current State

| Module | Java LOC | Rust LOC | Kotlin | Files |
|--------|----------|----------|--------|-------|
| terminal-emulator | 1,739 | 5,223 | 0 | 14 Java, 10 Rust |
| terminal-view | 2,849 | 0 | 0 | 8 Java |
| termux-shared | 26,743 | 0 | 0 | ~100 Java |
| app (com.termux) | 12,166 | 0 | 0 | ~50 Java |
| app (com.newtermux) | 1,866 | 0 | 0 | 14 Java |
| **Total** | **45,363** | **5,223** | **0** | ~180 Java |

**Rust ratio:** 5,223 / 50,586 = **10.3%** of codebase

### What's Already Rust (terminal-emulator)
- `terminal_emulator.rs` (934 lines) — full escape sequence parser + state machine
- `terminal_buffer.rs` (658 lines) — screen buffer + scrollback
- `terminal_row.rs` (692 lines) — row storage + style encoding
- `key_handler.rs` (464 lines) — keycode → escape sequence mapping
- `wcwidth.rs` (646 lines) — Unicode width tables
- `text_style.rs` (329 lines) — style bit packing
- `byte_queue.rs` (312 lines) — lock-free byte queue
- `ffi.rs` (1,170 lines) — JNI bridge (28 exports)

### What's Still Java (terminal-emulator thin wrappers)
- `TerminalSession.java` (385 lines) — process lifecycle + I/O threads
- `TerminalEmulator.java` (278 lines) — thin wrapper, all logic in Rust
- `RustJNI.java` (258 lines) — native method declarations
- `TerminalBuffer.java` (189 lines) — thin wrapper, fetches from Rust
- `TerminalColorScheme.java` (126 lines) — default color palette constants
- `TerminalColors.java` (96 lines) — mutable color state
- `Logger.java` (80 lines) — Android Log wrapper
- `TextStyle.java` (71 lines) — constant aliases
- `TerminalRow.java` (59 lines) — data holder for renderer
- `TerminalSessionClient.java` (51 lines) — callback interface
- `WcWidth.java` (47 lines) — delegates to Rust
- `JNI.java` (41 lines) — PTY subprocess creation (C native)
- `TerminalOutput.java` (32 lines) — abstract output class
- `KeyHandler.java` (26 lines) — delegates to Rust

---

## Phase 1: Kotlin Setup (infrastructure)

### Task 1.1: Add Kotlin plugin to build.gradle files
**Files:** `build.gradle`, `app/build.gradle`, `terminal-emulator/build.gradle`, `termux-shared/build.gradle`, `terminal-view/build.gradle`

Add to root `build.gradle`:
```groovy
buildscript {
    repositories {
        mavenCentral()
        google()
    }
    dependencies {
        classpath "com.android.tools.build:gradle:8.13.2"
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21"
    }
}
```

Add to each module's `build.gradle` after the android plugin:
```groovy
apply plugin: 'kotlin-android'
```

Add `kotlin-stdlib` dependency:
```groovy
implementation "org.jetbrains.kotlin:kotlin-stdlib:2.0.21"
```

**Verify:** `./gradlew assembleDebug` passes

---

## Phase 2: terminal-emulator → Kotlin (thin wrappers)

These files are already thin wrappers around Rust. Converting to Kotlin is mechanical.

### Task 2.1: Convert TextStyle.java → TextStyle.kt (71 lines)
**File:** `terminal-emulator/src/main/java/com/termux/terminal/TextStyle.kt`

```kotlin
package com.termux.terminal

/**
 * Text style constants. The actual encoding/decoding lives in Rust (text_style.rs).
 * These constants are used by TerminalColorScheme, TerminalColors, and TerminalRenderer.
 */
object TextStyle {
    // Effect flags
    const val CHARACTER_ATTRIBUTE_BOLD = 1
    const val CHARACTER_ATTRIBUTE_ITALIC = 1 shl 1
    const val CHARACTER_ATTRIBUTE_UNDERLINE = 1 shl 2
    const val CHARACTER_ATTRIBUTE_BLINK = 1 shl 3
    const val CHARACTER_ATTRIBUTE_INVERSE = 1 shl 4
    const val CHARACTER_ATTRIBUTE_INVISIBLE = 1 shl 5
    const val CHARACTER_ATTRIBUTE_STRIKETHROUGH = 1 shl 6
    const val CHARACTER_ATTRIBUTE_DIM = 1 shl 7
    const val CHARACTER_ATTRIBUTE_HIDDEN = 1 shl 8
    const val CHARACTER_ATTRIBUTE_OVERLINE = 1 shl 9
    const val CHARACTER_ATTRIBUTE_UNDERLINE_DOUBLE = 1 shl 10

    // Aliases
    const val FX_BOLD = CHARACTER_ATTRIBUTE_BOLD
    const val FX_ITALIC = CHARACTER_ATTRIBUTE_ITALIC
    const val FX_UNDERLINE = CHARACTER_ATTRIBUTE_UNDERLINE
    const val FX_BLINK = CHARACTER_ATTRIBUTE_BLINK
    const val FX_INVERSE = CHARACTER_ATTRIBUTE_INVERSE
    const val FX_INVISIBLE = CHARACTER_ATTRIBUTE_INVISIBLE
    const val FX_STRIKETHROUGH = CHARACTER_ATTRIBUTE_STRIKETHROUGH
    const val FX_DIM = CHARACTER_ATTRIBUTE_DIM

    // Color index constants
    const val COLOR_INDEX_FOREGROUND = 256
    const val COLOR_INDEX_BACKGROUND = 257
    const val COLOR_INDEX_CURSOR = 258
    const val NUM_INDEXED_COLORS = 260

    // Effect mask
    const val CHARACTER_ATTRIBUTE_EFFECT_MASK = 0x7FF
}
```
**Delete:** `TextStyle.java`
**Verify:** `./gradlew :terminal-emulator:compileDebugKotlin`

### Task 2.2: Convert TerminalRow.java → TerminalRow.kt (59 lines)
**File:** `terminal-emulator/src/main/java/com/termux/terminal/TerminalRow.kt`

```kotlin
package com.termux.terminal

/**
 * Single terminal row — thin data holder populated from Rust.
 * The renderer reads mText and mStyle directly.
 */
class TerminalRow(val mColumns: Int) {
    val mText = CharArray(mColumns)
    val mStyle = LongArray(mColumns)
    var mSpaceUsed = mColumns

    constructor(columns: Int, style: Long) : this(columns) {
        mStyle.fill(style)
    }

    fun getStyle(column: Int): Long =
        if (column in 0 until mStyle.size) mStyle[column] else 0L

    fun isBlank(): Boolean {
        for (i in 0 until mSpaceUsed) {
            if (mText[i] != ' ') return false
        }
        return true
    }

    fun getSpaceUsed(): Int = mSpaceUsed
}
```
**Delete:** `TerminalRow.java`

### Task 2.3: Convert TerminalOutput.java → TerminalOutput.kt (32 lines)
**File:** `terminal-emulator/src/main/java/com/termux/terminal/TerminalOutput.kt`

```kotlin
package com.termux.terminal

import java.nio.charset.StandardCharsets

/** Client which receives callbacks from events triggered by feeding input to a TerminalEmulator. */
abstract class TerminalOutput {
    fun write(data: String?) {
        data ?: return
        val bytes = data.toByteArray(StandardCharsets.UTF_8)
        write(bytes, 0, bytes.size)
    }

    abstract fun write(data: ByteArray, offset: Int, count: Int)

    abstract fun onTitleChanged(title: String?)
    abstract fun onTextChanged()
    abstract fun onBell()
    abstract fun onColorsChanged()
    abstract fun onCursorChanged(style: Int)
}
```
**Delete:** `TerminalOutput.java`

### Task 2.4: Convert TerminalSessionClient.java → TerminalSessionClient.kt (51 lines)
**File:** `terminal-emulator/src/main/java/com/termux/terminal/TerminalSessionClient.kt`

```kotlin
package com.termux.terminal

/** Interface for communication between TerminalSession and its client. */
interface TerminalSessionClient {
    fun onTextChanged(changedSession: TerminalSession)
    fun onTitleChanged(changedSession: TerminalSession)
    fun onSessionFinished(finishedSession: TerminalSession)
    fun onCopyTextToClipboard(session: TerminalSession, text: String?)
    fun onPasteText(session: TerminalSession, text: String?)
    fun onBell(session: TerminalSession)
    fun onColorsChanged(changedSession: TerminalSession)
    fun onCursorChanged(changedSession: TerminalSession, style: Int)
    fun logError(tag: String?, message: String?)
    fun logWarn(tag: String?, message: String?)
    fun logInfo(tag: String?, message: String?)
    fun logDebug(tag: String?, message: String?)
    fun logVerbose(tag: String?, message: String?)
}
```
**Delete:** `TerminalSessionClient.java`

### Task 2.5: Convert Logger.java → Logger.kt (80 lines)
**File:** `terminal-emulator/src/main/java/com/termux/terminal/Logger.kt`

```kotlin
package com.termux.terminal

import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

object Logger {
    fun logError(client: TerminalSessionClient?, logTag: String?, message: String?) {
        client?.logError(logTag, message) ?: Log.e(logTag, message ?: "")
    }

    fun logWarn(client: TerminalSessionClient?, logTag: String?, message: String?) {
        client?.logWarn(logTag, message) ?: Log.w(logTag, message ?: "")
    }

    fun logInfo(client: TerminalSessionClient?, logTag: String?, message: String?) {
        client?.logInfo(logTag, message) ?: Log.i(logTag, message ?: "")
    }

    fun logDebug(client: TerminalSessionClient?, logTag: String?, message: String?) {
        client?.logDebug(logTag, message) ?: Log.d(logTag, message ?: "")
    }

    fun logVerbose(client: TerminalSessionClient?, logTag: String?, message: String?) {
        client?.logVerbose(logTag, message) ?: Log.v(logTag, message ?: "")
    }

    fun getStackTraceString(throwable: Throwable?): String {
        if (throwable == null) return ""
        val sw = StringWriter(256)
        throwable.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }
}
```
**Delete:** `Logger.java`

### Task 2.6: Convert KeyHandler.java → KeyHandler.kt (26 lines)
**File:** `terminal-emulator/src/main/java/com/termux/terminal/KeyHandler.kt`

```kotlin
package com.termux.terminal

import java.nio.charset.StandardCharsets

/** Key handler: maps Android keycodes to terminal escape sequences via Rust. */
object KeyHandler {
    const val KEYMOD_ALT = -0x80000000  // 0x80000000 as Int
    const val KEYMOD_CTRL = 0x40000000
    const val KEYMOD_SHIFT = 0x20000000
    const val KEYMOD_NUM_LOCK = 0x10000000

    fun getCode(keyCode: Int, keyMode: Int, cursorApp: Boolean, keypadApp: Boolean): String? {
        val bytes = RustJNI.termKeyHandlerGetCode(keyCode, keyMode, cursorApp, keypadApp)
        return if (bytes == null || bytes.isEmpty()) null
        else String(bytes, StandardCharsets.UTF_8)
    }
}
```
**Delete:** `KeyHandler.java`

### Task 2.7: Convert WcWidth.java → WcWidth.kt (47 lines)
**File:** `terminal-emulator/src/main/java/com/termux/terminal/WcWidth.kt`

```kotlin
package com.termux.terminal

/** Unicode width lookup, delegated to Rust. */
object WcWidth {
    fun width(ucs: Int): Int = RustJNI.termWcwidth(ucs)

    fun width(chars: CharArray, index: Int): Int {
        val c = chars[index]
        return if (Character.isHighSurrogate(c)) {
            width(Character.toCodePoint(c, chars[index + 1]))
        } else {
            width(c.code)
        }
    }

    fun zeroWidthCharsCount(chars: CharArray, start: Int, end: Int): Int {
        if (start < 0 || start >= chars.size) return 0
        var count = 0
        var i = start
        while (i < end && i < chars.size) {
            if (Character.isHighSurrogate(chars[i])) {
                if (width(Character.toCodePoint(chars[i], chars[i + 1])) <= 0) count++
                i += 2
            } else {
                if (width(chars[i].code) <= 0) count++
                i++
            }
        }
        return count
    }
}
```
**Delete:** `WcWidth.java`

### Task 2.8: Convert RustJNI.java → RustJNI.kt (258 lines)
**File:** `terminal-emulator/src/main/java/com/termux/terminal/RustJNI.kt`

```kotlin
package com.termux.terminal

/** JNI bridge to the Rust terminal emulator (termux_rs native library). */
object RustJNI {
    init {
        System.loadLibrary("termux_rs")
    }

    // ======================== LIFECYCLE ========================
    @JvmStatic external fun termEmulatorNew(cols: Int, rows: Int, cellW: Int, cellH: Int, transcriptRows: Int): Long
    @JvmStatic external fun termEmulatorFree(emulator: Long)
    @JvmStatic external fun termEmulatorResize(emulator: Long, cols: Int, rows: Int, cellW: Int, cellH: Int)
    @JvmStatic external fun termEmulatorReset(emulator: Long)

    // ======================== PROCESSING ========================
    @JvmStatic external fun termEmulatorProcessBytes(emulator: Long, data: ByteArray, offset: Int, length: Int)
    @JvmStatic external fun termEmulatorGetOutput(emulator: Long, buf: ByteArray, maxLen: Int): Int
    @JvmStatic external fun termEmulatorGetFlags(emulator: Long): Int
    @JvmStatic external fun termEmulatorPaste(emulator: Long, str: String)

    // ======================== STATE ========================
    @JvmStatic external fun termEmulatorGetCharAt(emulator: Long, row: Int, col: Int): Long
    @JvmStatic external fun termEmulatorGetCursorRow(emulator: Long): Int
    @JvmStatic external fun termEmulatorGetCursorCol(emulator: Long): Int
    @JvmStatic external fun termEmulatorGetCursorStyle(emulator: Long): Int
    @JvmStatic external fun termEmulatorGetRows(emulator: Long): Int
    @JvmStatic external fun termEmulatorGetColumns(emulator: Long): Int
    @JvmStatic external fun termEmulatorGetColor(emulator: Long, index: Int): Int
    @JvmStatic external fun termEmulatorIsReverseVideo(emulator: Long): Boolean
    @JvmStatic external fun termEmulatorIsAlternateBufferActive(emulator: Long): Boolean
    @JvmStatic external fun termEmulatorIsMouseTrackingActive(emulator: Long): Boolean

    // ======================== MOUSE ========================
    @JvmStatic external fun termEmulatorSendMouseEvent(emulator: Long, button: Int, col: Int, row: Int, pressed: Boolean)

    // ======================== SCROLL ========================
    @JvmStatic external fun termEmulatorGetScrollCounter(emulator: Long): Int
    @JvmStatic external fun termEmulatorClearScrollCounter(emulator: Long)

    // ======================== TITLE / TEXT ========================
    @JvmStatic external fun termEmulatorGetSelectedText(emulator: Long, x1: Int, y1: Int, x2: Int, y2: Int): String?
    @JvmStatic external fun termEmulatorComputeDrawRuns(emulator: Long, row: Int): IntArray?

    // ======================== WCWIDTH ========================
    @JvmStatic external fun termWcwidth(codePoint: Int): Int

    // ======================== BYTE QUEUE ========================
    @JvmStatic external fun termByteQueueNew(size: Int): Long
    @JvmStatic external fun termByteQueueFree(queue: Long)
    @JvmStatic external fun termByteQueueWrite(queue: Long, data: ByteArray, offset: Int, length: Int): Boolean
    @JvmStatic external fun termByteQueueRead(queue: Long, buf: ByteArray, maxLen: Int, blocking: Boolean): Int
    @JvmStatic external fun termByteQueueClose(queue: Long)

    // ======================== KEY HANDLER ========================
    @JvmStatic external fun termKeyHandlerGetCode(keyCode: Int, keyMode: Int, cursorApp: Boolean, keypadApp: Boolean): ByteArray?
}
```
**Delete:** `RustJNI.java`

### Task 2.9: Convert TerminalColorScheme.java → TerminalColorScheme.kt (126 lines)
Convert the constant array + Properties loading to Kotlin.

### Task 2.10: Convert TerminalColors.java → TerminalColors.kt (96 lines)
Convert mutable color state to Kotlin.

### Task 2.11: Convert TerminalBuffer.java → TerminalBuffer.kt (189 lines)
Convert thin Rust wrapper to Kotlin.

### Task 2.12: Convert TerminalEmulator.java → TerminalEmulator.kt (278 lines)
Convert thin Rust wrapper to Kotlin.

### Task 2.13: Convert TerminalSession.java → TerminalSession.kt (385 lines)
Convert process lifecycle + I/O threads to Kotlin. This is the most complex conversion — uses Handler, Message, Os.read/write, ParcelFileDescriptor.

### Task 2.14: Convert JNI.java → JNI.kt (41 lines)
Convert PTY subprocess creation. Uses `@JvmStatic external fun`.

**Verify:** `./gradlew :terminal-emulator:compileDebugKotlin :terminal-emulator:compileDebugJavaWithJavac` — no Java sources left

---

## Phase 3: NewTermux features → Kotlin

These are custom NewTermux additions (not upstream Termux). All are self-contained.

### Task 3.1: Convert NewTermuxTheme.java → Kotlin (54 lines)
Simple enum-like class for theme IDs.

### Task 3.2: Convert SshProfile.java → Kotlin (89 lines)
Data class with JSON serialization. Convert to Kotlin data class.

### Task 3.3: Convert SshProfileStore.java → Kotlin (45 lines)
File I/O + JSON parsing. Kotlin idiomatic with `buildList`.

### Task 3.4: Convert TextExpansionStore.java → Kotlin (61 lines)
SharedPreferences + JSON. Kotlin with coroutines-friendly patterns.

### Task 3.5: Convert PackageManagerMenu.java → Kotlin (24 lines)
Trivial menu builder.

### Task 3.6: Convert RootToggleManager.java → Kotlin (147 lines)
Shell command execution for root toggle. Uses `Runtime.exec`.

### Task 3.7: Convert SpeechInputManager.java → Kotlin (131 lines)
Android SpeechRecognizer integration.

### Task 3.8: Convert NewTermuxSettings.java → Kotlin (132 lines)
SharedPreferences wrapper. Kotlin property delegates.

### Task 3.9: Convert AccentSwatchView.java → Kotlin (69 lines)
Custom View — stays Java-like in Kotlin but cleaner with extension functions.

### Task 3.10: Convert ThemePreviewView.java → Kotlin (113 lines)
Custom View for theme preview.

### Task 3.11: Convert HsvColorWheelView.java → Kotlin (167 lines)
Custom View with touch handling. Canvas drawing.

### Task 3.12: Convert ColorPickerDialog.java → Kotlin (231 lines)
Dialog with custom views.

### Task 3.13: Convert AutoCorrectHandler.java → Kotlin (196 lines)
SpellCheckerSession listener + command corrections map.

### Task 3.14: Convert NewTermuxColorTheme.java → Kotlin (407 lines)
Color theme definitions. Large constant block + utility methods.

**Verify:** `./gradlew :app:compileDebugKotlin` — all NewTermux features compile as Kotlin

---

## Phase 4: More Java → Rust (pure logic, no Android APIs)

### Task 4.1: Move TerminalColorScheme constants to Rust
The 256-color default palette is pure data. Add `term_emulator_default_color(index: i32) -> i32` to Rust, thin wrapper in Kotlin.

### Task 4.2: Move DataUtils to Rust
`DataUtils.java` has pure string manipulation (`getTruncatedCommandOutput`, `hexEncode`). Add Rust FFI:
- `term_data_truncate(text, max_len, from_end, on_newline) -> String`
- `term_data_hex_encode(bytes) -> String`

### Task 4.3: Move TermUrlUtils to Rust
`TermuxUrlUtils.java` — URL detection/parsing. Pure regex + string ops.

### Task 4.4: Move StreamGobbler to Rust (partial)
The stream reading logic is pure I/O — can be Rust. The callback dispatch stays Java/Kotlin.

---

## Phase 5: termux-shared → Kotlin (selected files)

**Strategy:** Only convert files that are frequently modified or have no upstream equivalent. Leave upstream-heavy files as Java to ease merging.

### Task 5.1: Convert Error.java → Kotlin (298 lines)
Serializable error class. Clean Kotlin conversion.

### Task 5.2: Convert DataUtils.java → Kotlin (258 lines)
Pure utility methods.

### Task 5.3: Convert StreamGobbler.java → Kotlin (325 lines)
Process stream reader.

### Task 5.4: Convert Logger (termux-shared) → Kotlin (502 lines)
The shared Logger class. Larger than the terminal-emulator one.

### Task 5.5: Convert TermuxConstants.java → Kotlin (1338 lines)
Constants file — mechanical conversion, all `static final` → `const val` / `@JvmField`.

### Task 5.6: Convert ExtraKeysView.java → Kotlin (681 lines)
Custom View with complex touch handling. Moderate effort.

---

## Implementation Order

1. **Phase 1** (Task 1.1) — Kotlin plugin setup ← prerequisite
2. **Phase 2** (Tasks 2.1-2.14) — terminal-emulator Java → Kotlin ← highest value, thin wrappers
3. **Phase 3** (Tasks 3.1-3.14) — NewTermux features → Kotlin ← self-contained, no upstream conflicts
4. **Phase 4** (Tasks 4.1-4.4) — More pure logic → Rust ← performance gains
5. **Phase 5** (Tasks 5.1-5.6) — termux-shared → Kotlin ← selective, lower priority

## Expected Outcome

| After Phase | Java LOC | Kotlin LOC | Rust LOC | Rust % |
|-------------|----------|------------|----------|--------|
| Current | 45,363 | 0 | 5,223 | 10.3% |
| Phase 2 | 0 (terminal-emulator) | 1,739 | 5,223 | 75% of terminal-emulator |
| Phase 3 | ~12,000 (app) | 1,866 + 1,739 | 5,223 | — |
| Phase 4 | ~11,000 | 3,605 | 6,500+ | 13%+ of total |
| Phase 5 | ~8,000 | 8,800+ | 6,500+ | 15%+ of total |

## Pitfalls

1. **JNI name mangling** — Kotlin `object Foo` with `@JvmStatic external fun bar()` → JNI symbol is `Java_com_example_Foo_bar` (same as Java static). Safe to convert RustJNI.
2. **Kotlin `const val` vs Java `static final`** — Callers using `TextStyle.CHARACTER_ATTRIBUTE_BOLD` will work unchanged with Kotlin `const val`.
3. **Kotlin nullability** — `TerminalSessionClient` methods in Java accept `@NonNull` params. Kotlin makes this explicit. Ensure all callers pass non-null.
4. **Build time** — Adding Kotlin plugin adds ~30s to first build. Subsequent builds are fast.
5. **termux-shared upstream conflicts** — Converting termux-shared files to Kotlin makes merging upstream changes harder. Only convert files that diverge significantly from upstream.
6. **R8/ProGuard** — Kotlin generates different bytecode patterns. Ensure proguard-rules.pro handles Kotlin metadata annotations.
7. **JNI.java uses C native (libtermux.so)** — This is separate from Rust (libtermux_rs.so). Keep both native libs.
