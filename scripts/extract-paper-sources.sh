#!/bin/bash
# Extract Paper decompiled sources from Gradle paperweight cache
# This script finds and extracts the Paper server sources with Mojang mappings
#
# Prerequisites:
#   1. Run `./gradlew build` at least once to populate the cache
#   2. Have a project using paperweight-userdev plugin
#
# Usage:
#   ./scripts/extract-paper-sources.sh [options]
#
# Options:
#   -v, --version VERSION   Specify Minecraft version (e.g., 1.21.8)
#   -o, --output DIR        Output directory (default: ./tmp/paper-sources)
#   -l, --list              List available versions in cache
#   -h, --help              Show this help message

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
OUTPUT_DIR=""
TARGET_VERSION=""
LIST_ONLY=false

# Gradle cache locations
GRADLE_CACHE="$HOME/.gradle/caches"
PAPERWEIGHT_CACHE="$GRADLE_CACHE/paperweight-userdev/v2/work"
MODULES_CACHE="$GRADLE_CACHE/modules-2/files-2.1/io.papermc.paper"

show_help() {
    echo "Paper Source Extractor"
    echo ""
    echo "Extract Paper decompiled sources from Gradle paperweight cache."
    echo ""
    echo "Usage: $0 [options]"
    echo ""
    echo "Options:"
    echo "  -v, --version VERSION   Specify Minecraft version (e.g., 1.21.8)"
    echo "  -o, --output DIR        Output directory (default: ./tmp/paper-sources)"
    echo "  -l, --list              List available versions in cache"
    echo "  -h, --help              Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 -l                       # List available versions"
    echo "  $0 -v 1.21.8                # Extract 1.21.8 sources"
    echo "  $0 -v 1.21.8 -o ./sources   # Extract to custom directory"
    echo ""
}

list_versions() {
    echo "Available Paper versions in cache:"
    echo ""
    if [ -d "$MODULES_CACHE/dev-bundle" ]; then
        for dir in "$MODULES_CACHE/dev-bundle"/*; do
            if [ -d "$dir" ]; then
                version=$(basename "$dir" | sed 's/-R0.1-SNAPSHOT//')
                echo "  - $version"
            fi
        done
    else
        echo "  (none found - run './gradlew build' first)"
    fi
    echo ""
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -v|--version)
            TARGET_VERSION="$2"
            shift 2
            ;;
        -o|--output)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        -l|--list)
            LIST_ONLY=true
            shift
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            show_help
            exit 1
            ;;
    esac
done

if [ "$LIST_ONLY" = true ]; then
    list_versions
    exit 0
fi

# Set default output directory
if [ -z "$OUTPUT_DIR" ]; then
    OUTPUT_DIR="$PROJECT_DIR/tmp/paper-sources"
fi

echo "=== Paper Source Extractor ==="
echo ""

# Check if paperweight cache exists
if [ ! -d "$PAPERWEIGHT_CACHE" ]; then
    echo "ERROR: Paperweight cache not found at $PAPERWEIGHT_CACHE"
    echo "Please run './gradlew build' first to populate the cache."
    exit 1
fi

# Function to find the hash associated with a specific version
find_version_hash() {
    local version="$1"
    local dev_bundle_zip="$MODULES_CACHE/dev-bundle/${version}-R0.1-SNAPSHOT"

    if [ ! -d "$dev_bundle_zip" ]; then
        echo ""
        return
    fi

    # Find the zip file
    local zip_file=$(find "$dev_bundle_zip" -name "*.zip" -type f 2>/dev/null | head -1)
    if [ -z "$zip_file" ]; then
        echo ""
        return
    fi

    # The hash is in the parent directory name
    echo "$(dirname "$zip_file" | xargs basename)"
}

# Function to get version from vanilla server jar
get_vanilla_version() {
    local vanilla_dir="$1"
    if [ -f "$vanilla_dir/vanillaServer.jar" ]; then
        unzip -p "$vanilla_dir/vanillaServer.jar" version.json 2>/dev/null | \
            grep -o '"id"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4 || echo ""
    else
        echo ""
    fi
}

# Function to find matching applyDevBundlePatches directory
find_patched_dir_for_version() {
    local version="$1"

    # Check each applyDevBundlePatches directory
    for dir in "$PAPERWEIGHT_CACHE"/applyDevBundlePatches_*; do
        if [ -d "$dir" ] && [ -f "$dir/output.jar" ]; then
            local metadata="$dir/metadata.json"
            if [ -f "$metadata" ]; then
                # Get the vanilla downloads hash
                local vanilla_hash=$(grep -o 'vanillaServerDownloads_[a-f0-9]*' "$metadata" 2>/dev/null | head -1)
                if [ -n "$vanilla_hash" ]; then
                    local vanilla_dir="$PAPERWEIGHT_CACHE/$vanilla_hash"
                    if [ -d "$vanilla_dir" ]; then
                        # Get version from the vanilla server jar
                        local jar_version=$(get_vanilla_version "$vanilla_dir")
                        if [ "$jar_version" = "$version" ]; then
                            echo "$dir"
                            return
                        fi
                    fi
                fi
            fi
        fi
    done
    echo ""
}

# Find mache sources for a version
find_mache_dir_for_version() {
    local version="$1"

    for dir in "$PAPERWEIGHT_CACHE"/setupMacheSources_*; do
        if [ -d "$dir" ] && [ -f "$dir/output.zip" ]; then
            # Similar logic - check related vanilla downloads
            local metadata="$dir/metadata.json"
            # For now just return latest if no specific match
            echo "$dir"
            return
        fi
    done
    echo ""
}

# Determine which version to extract
if [ -n "$TARGET_VERSION" ]; then
    echo "Target version: $TARGET_VERSION"

    # Check if the version exists
    if [ ! -d "$MODULES_CACHE/dev-bundle/${TARGET_VERSION}-R0.1-SNAPSHOT" ]; then
        echo "ERROR: Version $TARGET_VERSION not found in cache."
        echo ""
        list_versions
        exit 1
    fi

    MC_VERSION="$TARGET_VERSION"

    # Find the patched directory for this version
    PATCHED_DIR=$(find_patched_dir_for_version "$TARGET_VERSION")

    if [ -z "$PATCHED_DIR" ]; then
        echo "WARNING: Could not find exact match for version $TARGET_VERSION in work cache."
        echo "This may happen if you haven't built this version recently."
        echo ""
        echo "Attempting to find by examining vanilla server jars..."

        # Fallback: check vanilla server downloads for version by examining jar
        for vanilla_dir in "$PAPERWEIGHT_CACHE"/vanillaServerDownloads_*; do
            if [ -d "$vanilla_dir" ]; then
                local jar_version=$(get_vanilla_version "$vanilla_dir")
                if [ "$jar_version" = "$TARGET_VERSION" ]; then
                    VANILLA_HASH=$(basename "$vanilla_dir")
                    echo "Found vanilla downloads for $TARGET_VERSION: $VANILLA_HASH"

                    # Now find applyDevBundlePatches that references this
                    for patched in "$PAPERWEIGHT_CACHE"/applyDevBundlePatches_*; do
                        if [ -f "$patched/metadata.json" ]; then
                            if grep -q "$VANILLA_HASH" "$patched/metadata.json" 2>/dev/null; then
                                PATCHED_DIR="$patched"
                                echo "Found matching patched sources: $(basename "$PATCHED_DIR")"
                                break 2
                            fi
                        fi
                    done
                fi
            fi
        done
    fi
else
    echo "No version specified, using most recently used..."

    # Find the most recent by lastUsed timestamp
    PATCHED_DIR=""
    LATEST_TIMESTAMP=0

    for dir in "$PAPERWEIGHT_CACHE"/applyDevBundlePatches_*; do
        if [ -f "$dir/metadata.json" ] && [ -f "$dir/output.jar" ]; then
            TIMESTAMP=$(grep -o '"lastUsed":[0-9]*' "$dir/metadata.json" 2>/dev/null | cut -d':' -f2 || echo "0")
            if [ "$TIMESTAMP" -gt "$LATEST_TIMESTAMP" ] 2>/dev/null; then
                LATEST_TIMESTAMP=$TIMESTAMP
                PATCHED_DIR="$dir"
            fi
        fi
    done

    # Try to detect version from vanilla downloads
    if [ -n "$PATCHED_DIR" ] && [ -f "$PATCHED_DIR/metadata.json" ]; then
        VANILLA_HASH=$(grep -o 'vanillaServerDownloads_[a-f0-9]*' "$PATCHED_DIR/metadata.json" 2>/dev/null | head -1)
        if [ -n "$VANILLA_HASH" ]; then
            MC_VERSION=$(get_vanilla_version "$PAPERWEIGHT_CACHE/$VANILLA_HASH")
        fi
    fi

    if [ -z "$MC_VERSION" ]; then
        MC_VERSION="unknown"
    fi
fi

if [ -z "$PATCHED_DIR" ] || [ ! -f "$PATCHED_DIR/output.jar" ]; then
    echo "ERROR: Could not find decompiled sources for version $MC_VERSION"
    echo ""
    echo "Make sure you have run './gradlew build' with this version in your build.gradle:"
    echo "  paperweight.paperDevBundle(\"$MC_VERSION-R0.1-SNAPSHOT\")"
    exit 1
fi

echo "Found Paper-patched sources: $(basename "$PATCHED_DIR")"
echo "Minecraft version: $MC_VERSION"

# Find matching mache sources
MACHE_DIR=""
if [ -n "$PATCHED_DIR" ] && [ -f "$PATCHED_DIR/metadata.json" ]; then
    MACHE_HASH=$(grep -o 'setupMacheSources_[a-f0-9]*' "$PATCHED_DIR/metadata.json" 2>/dev/null | head -1)
    if [ -n "$MACHE_HASH" ] && [ -f "$PAPERWEIGHT_CACHE/$MACHE_HASH/output.zip" ]; then
        MACHE_DIR="$PAPERWEIGHT_CACHE/$MACHE_HASH"
    fi
fi

# Create output directory
echo ""
echo "Creating output directory: $OUTPUT_DIR"
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

# Extract Paper-patched sources (main extraction)
PAPER_OUTPUT="$OUTPUT_DIR/paper-server-$MC_VERSION-sources.jar"
echo ""
echo "Copying Paper-patched decompiled server to:"
echo "  $PAPER_OUTPUT"
cp "$PATCHED_DIR/output.jar" "$PAPER_OUTPUT"

# Extract to directory for easy browsing
PAPER_EXTRACTED="$OUTPUT_DIR/paper-server-$MC_VERSION"
echo ""
echo "Extracting sources to directory:"
echo "  $PAPER_EXTRACTED"
mkdir -p "$PAPER_EXTRACTED"
unzip -q "$PAPER_OUTPUT" -d "$PAPER_EXTRACTED"

# Also extract vanilla sources if available
if [ -n "$MACHE_DIR" ] && [ -f "$MACHE_DIR/output.zip" ]; then
    VANILLA_OUTPUT="$OUTPUT_DIR/vanilla-server-$MC_VERSION-sources.zip"
    echo ""
    echo "Copying vanilla Minecraft decompiled sources to:"
    echo "  $VANILLA_OUTPUT"
    cp "$MACHE_DIR/output.zip" "$VANILLA_OUTPUT"

    VANILLA_EXTRACTED="$OUTPUT_DIR/vanilla-server-$MC_VERSION"
    echo ""
    echo "Extracting vanilla sources to:"
    echo "  $VANILLA_EXTRACTED"
    mkdir -p "$VANILLA_EXTRACTED"
    unzip -q "$VANILLA_OUTPUT" -d "$VANILLA_EXTRACTED"
fi

# Create a summary
echo ""
echo "=== Extraction Complete ==="
echo ""
echo "Output location: $OUTPUT_DIR"
echo ""
echo "Files created:"
ls -lah "$OUTPUT_DIR"/*.jar "$OUTPUT_DIR"/*.zip 2>/dev/null || true
echo ""
echo "Directories created:"
ls -d "$OUTPUT_DIR"/*/ 2>/dev/null | while read d; do
    count=$(find "$d" -type f -name "*.java" | wc -l)
    echo "  $(basename "$d"): $count Java files"
done

echo ""
echo "Key directories to explore for optimization work:"
echo "  - net/minecraft/world/level/block/entity/  (block entities like hoppers)"
echo "  - net/minecraft/world/entity/              (entity classes)"
echo "  - net/minecraft/world/entity/ai/           (AI behaviors)"
echo "  - net/minecraft/server/level/              (server level classes)"
echo "  - net/minecraft/server/                    (server core)"
echo ""
echo "The Paper-patched sources include all Paper additions and optimizations."
echo "Use these with Mojang mappings for development with paperweight-userdev."
