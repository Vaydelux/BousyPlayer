#!/usr/bin/env python3
"""Offline WCAG 2.x contrast gate for every shipped BousyPlayer semantic palette.

Static semantic-color contrast is NOT a substitute for screenshots, assistive
technology, font scaling, alpha overlays, or real TV/phone UI review.
"""
import re
from pathlib import Path

theme = Path("app/src/main/java/com/vaydelux/bousyplayer/ui/PlayerTheme.kt").read_text()
colors = ("background", "panel", "panelLight", "accent", "secondary", "text", "muted")
checks = (
    ("background", "text"),
    ("background", "muted"),
    ("panel", "text"),
    ("panel", "muted"),
    ("panelLight", "text"),
    ("panelLight", "muted"),
    ("background", "accent"),
)
palettes = ("ocean", "obsidian", "amethyst", "ember")


def luminance(rgb):
    values = [int(rgb[i:i+2], 16) / 255 for i in (0, 2, 4)]
    linear = [x / 12.92 if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4
              for x in values]
    return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]


def contrast(a, b):
    light, dark = sorted((luminance(a), luminance(b)), reverse=True)
    return (light + 0.05) / (dark + 0.05)


def extract_palette(name):
    match = re.search(r"private val " + re.escape(name) +
                      r"\s*=\s*CinemaPalette\(([\s\S]*?)\)\s*(?:\n|$)", theme)
    assert match, f"Palette missing: {name}"
    values = re.findall(r"Color\(0xFF([0-9a-fA-F]{6})\)", match.group(1))
    assert len(values) == len(colors), f"{name}: expected exactly 7 opaque colors"
    return dict(zip(colors, values))


if __name__ == "__main__":
    all_scores = []
    for name in palettes:
        palette = extract_palette(name)
        for surface, foreground in checks:
            ratio = contrast(palette[surface], palette[foreground])
            assert ratio >= 4.5, (
                f"{name}: {foreground}/{surface} contrast {ratio:.2f}:1 "
                "is below WCAG AA small-text threshold 4.5:1"
            )
            all_scores.append(ratio)
    assert 'CompositionLocalProvider(LocalCinemaPalette provides palette)' in theme, (
        "Components must share one semantic palette"
    )
    print(f"PASS: {len(palettes)} themes; {len(all_scores)} semantic color pairings >= 4.5:1; "
          f"minimum {min(all_scores):.2f}:1 (static colors only)")
