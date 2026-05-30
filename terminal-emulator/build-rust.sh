#!/bin/bash
# Cross-compile Rust terminal emulator for Android arm64-v8a
# Usage: ./build-rust.sh [NDK_PATH]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RUST_DIR="$SCRIPT_DIR/src/main/rust"
TARGET="aarch64-linux-android"
OUTPUT_DIR="$SCRIPT_DIR/src/main/jniLibs/arm64-v8a"

# Find NDK - prefer explicit arg > env var > auto-detect latest
find_ndk() {
    if [ -n "${1:-}" ] && [ -d "$1" ]; then echo "$1"; return; fi
    if [ -n "${ANDROID_NDK_HOME:-}" ] && [ -d "$ANDROID_NDK_HOME" ]; then echo "$ANDROID_NDK_HOME"; return; fi
    if [ -n "${ANDROID_NDK:-}" ] && [ -d "$ANDROID_NDK" ]; then echo "$ANDROID_NDK"; return; fi

    # Auto-detect: find all NDK versions, pick latest
    local sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
    local ndk_base="$sdk_root/ndk"
    if [ -d "$ndk_base" ]; then
        local latest
        latest=$(ls -1d "$ndk_base"/*/ 2>/dev/null | sort -V | tail -1)
        if [ -n "$latest" ]; then echo "${latest%/}"; return; fi
    fi

    # Gradle-managed NDK
    local gradle_ndk="$HOME/.gradle/caches/transforms-4/*/transformed/ndk-*"
    for d in $gradle_ndk; do
        if [ -d "$d/toolchains/llvm/prebuilt" ]; then echo "$d"; return; fi
    done

    echo ""
}

NDK_PATH=$(find_ndk "${1:-}")

if [ -z "$NDK_PATH" ] || [ ! -d "$NDK_PATH" ]; then
    echo "ERROR: NDK not found"
    echo "Set ANDROID_NDK_HOME or pass NDK path as argument"
    exit 1
fi

# Find the toolchain
TOOLCHAIN="$NDK_PATH/toolchains/llvm/prebuilt/linux-x86_64"
if [ ! -d "$TOOLCHAIN" ]; then
    # macOS
    TOOLCHAIN="$NDK_PATH/toolchains/llvm/prebuilt/darwin-x86_64"
fi
if [ ! -d "$TOOLCHAIN" ]; then
    echo "ERROR: LLVM toolchain not found in $NDK_PATH"
    exit 1
fi

# Find clang - prefer highest API level
LINKER=""
for api in $(seq 35 -1 21); do
    candidate="$TOOLCHAIN/bin/aarch64-linux-android${api}-clang"
    if [ -f "$candidate" ]; then LINKER="$candidate"; break; fi
done
if [ -z "$LINKER" ]; then
    LINKER=$(ls "$TOOLCHAIN/bin/aarch64-linux-android"*-clang 2>/dev/null | sort -V | tail -1)
fi
if [ -z "$LINKER" ] || [ ! -f "$LINKER" ]; then
    echo "ERROR: aarch64 clang not found in $TOOLCHAIN/bin/"
    exit 1
fi

NDK_VERSION=$(basename "$NDK_PATH")
echo "=== Rust Cross-Compilation for Android arm64-v8a ==="
echo "NDK:        $NDK_PATH ($NDK_VERSION)"
echo "Toolchain:  $TOOLCHAIN"
echo "Linker:     $LINKER"
echo "Target:     $TARGET"
echo "Rust:       $(rustc --version 2>/dev/null || echo 'not found')"
echo "Source:     $RUST_DIR"
echo "Output:     $OUTPUT_DIR"
echo ""

# Ensure target is installed
rustup target add "$TARGET" 2>/dev/null || true

# Configure cargo for this target
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$LINKER"
export CC_aarch64_linux_android="$LINKER"
export AR_aarch64_linux_android="$TOOLCHAIN/bin/llvm-ar"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_AR="$AR_aarch64_linux_android"

# Build
echo "Building release..."
cd "$RUST_DIR"
cargo build --release --target "$TARGET" 2>&1

# Copy output
mkdir -p "$OUTPUT_DIR"
SO_FILE="$RUST_DIR/target/$TARGET/release/libtermux_rs.so"

if [ -f "$SO_FILE" ]; then
    cp "$SO_FILE" "$OUTPUT_DIR/libtermux_rs.so"
    SIZE=$(ls -lh "$OUTPUT_DIR/libtermux_rs.so" | awk '{print $5}')
    echo ""
    echo "SUCCESS: libtermux_rs.so ($SIZE) -> $OUTPUT_DIR/"
    file "$OUTPUT_DIR/libtermux_rs.so"
else
    echo "ERROR: $SO_FILE not found"
    ls -la "$RUST_DIR/target/$TARGET/release/" 2>/dev/null || echo "target dir missing"
    exit 1
fi
