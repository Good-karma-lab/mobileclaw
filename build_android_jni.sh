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

# Configure NDK toolchain — armv7 (32-bit)
export CC_armv7_linux_androideabi="$NDK_BIN/armv7a-linux-androideabi30-clang"
export CXX_armv7_linux_androideabi="$NDK_BIN/armv7a-linux-androideabi30-clang++"
export AR_armv7_linux_androideabi="$NDK_BIN/llvm-ar"
export CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER="$NDK_BIN/armv7a-linux-androideabi30-clang"

echo ""
echo "2. Adding Android targets..."
rustup target add aarch64-linux-android
rustup target add armv7-linux-androideabi

# ── aarch64 (ARM64) ──────────────────────────────────────────────────────────
echo ""
echo "3a. Building JNI library for ARM64..."
echo "  Target: aarch64-linux-android"
echo "  Type: cdylib (shared library)"
echo "  Profile: release (optimized)"
echo ""

cargo build --lib --target aarch64-linux-android --release

RUST_LIB_ARM64="target/aarch64-linux-android/release/libzeroclaw.so"

if [ ! -f "$RUST_LIB_ARM64" ]; then
    echo "❌ Build failed - library not found: $RUST_LIB_ARM64"
    exit 1
fi

echo ""
echo "3a. Build successful (ARM64)!"
ls -lh "$RUST_LIB_ARM64"
file "$RUST_LIB_ARM64"

echo ""
echo "4a. Copying ARM64 library to mobile app jniLibs..."
JNILIB_ARM64="mobile-app/android/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$JNILIB_ARM64"
cp "$RUST_LIB_ARM64" "$JNILIB_ARM64/"
ls -lh "$JNILIB_ARM64/libzeroclaw.so"

# ── armv7 (ARM 32-bit — older/budget devices) ─────────────────────────────────
echo ""
echo "3b. Building JNI library for ARM 32-bit..."
echo "  Target: armv7-linux-androideabi"
echo "  Type: cdylib (shared library)"
echo "  Profile: release (optimized)"
echo ""

cargo build --lib --target armv7-linux-androideabi --release

RUST_LIB_ARMV7="target/armv7-linux-androideabi/release/libzeroclaw.so"

if [ ! -f "$RUST_LIB_ARMV7" ]; then
    echo "❌ Build failed - library not found: $RUST_LIB_ARMV7"
    exit 1
fi

echo ""
echo "3b. Build successful (armv7)!"
ls -lh "$RUST_LIB_ARMV7"
file "$RUST_LIB_ARMV7"

echo ""
echo "4b. Copying armv7 library to mobile app jniLibs..."
JNILIB_ARMV7="mobile-app/android/app/src/main/jniLibs/armeabi-v7a"
mkdir -p "$JNILIB_ARMV7"
cp "$RUST_LIB_ARMV7" "$JNILIB_ARMV7/"
ls -lh "$JNILIB_ARMV7/libzeroclaw.so"

echo ""
echo "=========================================="
echo " BUILD COMPLETE"
echo "=========================================="
echo "Libraries:"
echo "  ARM64:  $JNILIB_ARM64/libzeroclaw.so"
echo "  ARMv7:  $JNILIB_ARMV7/libzeroclaw.so"
echo "Ready for APK packaging"
echo ""
