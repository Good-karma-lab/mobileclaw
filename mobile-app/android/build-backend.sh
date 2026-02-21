#!/bin/bash
# Build ZeroClaw Rust backend for Android
# This script cross-compiles the Rust backend to all Android architectures

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/../.."
BACKEND_ROOT="$PROJECT_ROOT"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}🦀 Building ZeroClaw backend for Android${NC}"
echo ""

# Check if Rust is installed
if ! command -v cargo &> /dev/null; then
    echo -e "${RED}❌ Cargo not found. Install Rust from https://rustup.rs/${NC}"
    exit 1
fi

# Check if rustup is available
if ! command -v rustup &> /dev/null; then
    echo -e "${RED}❌ rustup not found. Install from https://rustup.rs/${NC}"
    exit 1
fi

echo -e "${YELLOW}Checking Android targets...${NC}"

# Android targets
TARGETS=(
    "aarch64-linux-android"      # 64-bit ARM (modern phones/tablets)
    "armv7-linux-androideabi"    # 32-bit ARM (older devices)
    "x86_64-linux-android"       # x86_64 emulators
    "i686-linux-android"         # x86 emulators
)

# Install missing targets
for TARGET in "${TARGETS[@]}"; do
    if ! rustup target list --installed | grep -q "$TARGET"; then
        echo -e "${YELLOW}Installing target: $TARGET${NC}"
        rustup target add "$TARGET"
    else
        echo -e "${GREEN}✓ Target installed: $TARGET${NC}"
    fi
done

echo ""
echo -e "${YELLOW}Building Rust backend...${NC}"
echo ""

cd "$BACKEND_ROOT"

# Build for each target
for TARGET in "${TARGETS[@]}"; do
    echo -e "${YELLOW}Building for $TARGET...${NC}"

    if cargo build --release --target "$TARGET" --bin zeroclaw; then
        echo -e "${GREEN}✓ Build successful: $TARGET${NC}"
    else
        echo -e "${RED}✗ Build failed: $TARGET${NC}"
        exit 1
    fi
    echo ""
done

echo -e "${GREEN}✅ All backend binaries built successfully${NC}"
echo ""

# Create jniLibs directory structure
JNI_LIBS="$SCRIPT_DIR/app/src/main/jniLibs"
echo -e "${YELLOW}Creating jniLibs directory structure...${NC}"
mkdir -p "$JNI_LIBS"/{arm64-v8a,armeabi-v7a,x86_64,x86}

# Copy binaries to jniLibs with correct naming
echo -e "${YELLOW}Copying binaries to jniLibs...${NC}"

# ARM64
cp "$PROJECT_ROOT/target/aarch64-linux-android/release/zeroclaw" \
   "$JNI_LIBS/arm64-v8a/libzeroclaw.so"
echo -e "${GREEN}✓ Copied arm64-v8a/libzeroclaw.so${NC}"

# ARMv7
cp "$PROJECT_ROOT/target/armv7-linux-androideabi/release/zeroclaw" \
   "$JNI_LIBS/armeabi-v7a/libzeroclaw.so"
echo -e "${GREEN}✓ Copied armeabi-v7a/libzeroclaw.so${NC}"

# x86_64
cp "$PROJECT_ROOT/target/x86_64-linux-android/release/zeroclaw" \
   "$JNI_LIBS/x86_64/libzeroclaw.so"
echo -e "${GREEN}✓ Copied x86_64/libzeroclaw.so${NC}"

# x86
cp "$PROJECT_ROOT/target/i686-linux-android/release/zeroclaw" \
   "$JNI_LIBS/x86/libzeroclaw.so"
echo -e "${GREEN}✓ Copied x86/libzeroclaw.so${NC}"

echo ""
echo -e "${GREEN}✅ Binaries copied to jniLibs/${NC}"
echo ""
echo "Binary sizes:"
du -h "$JNI_LIBS"/**/libzeroclaw.so
echo ""
echo "Total size per architecture:"
du -sh "$JNI_LIBS"/*
echo ""
echo -e "${GREEN}✅ Build complete!${NC}"
echo ""
echo "Next steps:"
echo "  1. Build APK: npx react-native run-android"
echo "  2. Monitor daemon: adb logcat | grep ZeroClawDaemon"
echo "  3. Test gateway: adb shell curl http://127.0.0.1:8000/health"
