#!/bin/bash

source ./deploy_bundles_to_felix.config

# Stop on error
set -e

# Remove existing bundles in felix
cd "$FELIX_BUNDLE_DIR"
echo "Removing all felix bundles in $FELIX_BUNDLE_DIR"
if [ "$(ls -A $FELIX_BUNDLE_DIR)" ]; then
  rm *.jar
fi
cd "$CWD"

# Remove felix cache
echo "Removing cache in $FELIX_CACHE"
if [ -d "$FELIX_CACHE" ]; then
  rm -R "$FELIX_CACHE"
fi

# Build and move new bundles
cd "$BUNDLE_DIR"
source "$BUNDLE_SCRIPT"
mv *.jar "$FELIX_BUNDLE_DIR"
cd "$CWD"

# cp dependent bundles
echo "Copy dependent bundles in $DEPENDENT_BUNDLE_DIR to $FELIX_BUNDLE_DIR"
cd "$DEPENDENT_BUNDLE_DIR"
cp *.jar "$FELIX_BUNDLE_DIR/"
cd "$CWD"

# cp felix config to felix local
echo "Copy config $FELIX_CONFIG to $FELIX_FELIX_CONFIG"
cp "$FELIX_CONFIG" "$FELIX_FELIX_CONFIG"