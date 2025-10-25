#!/bin/sh

if ! command -v magick > /dev/null 2>&1; then
    echo "Error: 'magick' command not found. Please install ImageMagick."
    exit 1
fi

# Metadata
magick docs/src/app/icon.svg -background none -size 512x512 metadata/en-US/images/icon.png
magick misc/featureGraphic.svg -background none -size 1024x500 metadata/en-US/images/featureGraphic.png

# GitHub Social
magick misc/featureGraphic.svg -background none -resize x640 -gravity center -crop 1280x640+0+0 +repage misc/githubSocialPreview.png
