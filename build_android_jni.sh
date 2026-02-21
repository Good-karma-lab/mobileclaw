#!/bin/bash
# Build ZeroClaw JNI library for Android
#
# This script builds the Rust library as a JNI shared object (.so)
# that can be loaded directly into the Android app process.

set -e

echo "=========================================="
echo " BUILDING ZEROCLAW JNI FOR ANDROID"
echo "=========================================="

# Setup environment
export PATH="$HOME/.cargo/bin:$PATH"
export NDK_HOME="$HOME/Library/Android/sdk/ndk/27.1.12297006"

if [ ! -d "$NDK_HOME" ]; then
    echo "❌ NDK not found at: $NDK_HOME"
    exit 1
fi

# Configure NDK toolchain
NDK_BIN="$NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/bin"
export CC_aarch64_linux_android="$NDK_BIN/aarch64-linux-android30-clang"
export CXX_aarch64_linux_android="$NDK_BIN/aarch64-linux-android30-clang++"
export AR_aarch64_linux_android="$NDK_BIN/llvm-ar"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$NDK_BIN/aarch64-linux-android30-clang"

echo "1. Checking Rust toolchain..."
if ! command -v cargo &> /dev/null; then
    echo "❌ Cargo not found. Installing Rust..."
    curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --default-toolchain stable
    export PATH="$HOME/.cargo/bin:$PATH"
fi

rustc --version
cargo --version

echo ""
echo "2. Adding Android target..."
rustup target add aarch64-linux-android

echo ""
echo "3. Building JNI library for ARM64..."
echo "  Target: aarch64-linux-android"
echo "  Type: cdylib (shared library)"
echo "  Profile: release (optimized)"
echo ""

cargo build --lib --target aarch64-linux-android --release

RUST_LIB="target/aarch64-linux-android/release/libzeroclaw.so"

if [ ! -f "$RUST_LIB" ]; then
    echo "❌ Build failed - library not found: $RUST_LIB"
    exit 1
fi

echo ""
echo "4. Build successful!"
ls -lh "$RUST_LIB"
file "$RUST_LIB"

echo ""
echo "5. Copying to mobile app jniLibs..."
JNILIB_DIR="mobile-app/android/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$JNILIB_DIR"
cp "$RUST_LIB" "$JNILIB_DIR/"

ls -lh "$JNILIB_DIR/libzeroclaw.so"

echo ""
echo "=========================================="
echo " BUILD COMPLETE"
echo "=========================================="
echo "Library: $JNILIB_DIR/libzeroclaw.so"
echo "Ready for APK packaging"
echo ""
