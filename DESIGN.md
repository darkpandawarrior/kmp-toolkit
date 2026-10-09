# kmp-toolkit DESIGN.md

> Inherits: house design standard (AgentHarness skill `design-md`). This file wins on conflict.
> Agents: read this before creating or changing UI. Values live in code; this file names them.
> Source of truth: `designsystem/src/commonMain/kotlin/com/siddharth/kmp/designsystem/`
> (`DesignTokens.kt`, `Adaptive.kt`, `Motion.kt`, `Primitives.kt`, `ThemeController.kt`).
> Dial: ENERGY 3 / RHYTHM 4 / MOTION 2

This is the shared token layer. Doori, Gaddi, Candidai, PaymentsLab-KMP, cv-siddharth-kmp,
Ghar and kmp-app-template consume it (vendored at `external/kmp-toolkit`) and reference this
file instead of repeating it. An app DESIGN.md only records what it adds or overrides.

## Overview

A library, not a brand. The `designsystem` module ships geometry, rhythm, motion and a few
brand-agnostic primitives; it ships no palette. The feel it enforces is a flat, bordered,
dark-first dashboard surface: cards carry a 1dp outline instead of a drop shadow, motion is quick
and low drama, and one 4dp spacing scale gives every app the same rhythm. Colour always comes from
the consuming app's `MaterialTheme.colorScheme`.

## Colors

The module defines no colour scheme. Every primitive reads Material 3 roles from
`MaterialTheme.colorScheme`, so a consumer's theme decides the look. Roles in use:

- `surface` card fill, `outlineVariant` 1dp card border, `background` / `onBackground` page title.
- `onSurface` card title, `onSurfaceVariant` subtitles, captions, section labels.
- `primary` icons, selected state, progress; tinted fills use `primary` at alpha 0.12 (icon
  container) and 0.16 (selected `TagChip`).
- `surfaceVariant` unselected `TagChip` and the pending step node.
- `error` for `ErrorState` text. Never a literal.

Only literals in the module, both private to `StepTimeline.kt`: success `0xFF1E9E6A`, danger
`0xFFCE3B3B`. Promote them to a shared semantic token before a third consumer needs them.
Dark is the default (`ThemeController(defaultDark = true)`); light is supported, persisted through
`ThemeStore`.

## Typography

Type comes from the consumer's `MaterialTheme.typography`. Roles used by primitives:
`headlineSmall` page title, `titleMedium` card title, `bodyMedium` body, `bodySmall` subtitle and
loading label, `labelSmall` chip text.

The one in-module style is the eyebrow: monospace, Medium weight, 11sp, letter spacing 2sp,
rendered upper case with a `// ` prefix for the "// SECTION" idiom (`Primitives.kt`, private
`EyebrowStyle`).

Form-factor sizes are in `AdaptiveTokens` (`Adaptive.kt`), title / sectionTitle / body / caption in sp:

| Set | title | sectionTitle | body | caption |
|---|---|---|---|---|
| Compact | 20 | 18 | 14 | 12 |
| Medium | 24 | 20 | 15 | 13 |
| Expanded | 28 | 22 | 16 | 14 |
| Watch | 16 | 14 | 13 | 11 |
| Tv | 34 | 24 | 18 | 14 |
| TvMedium | 40 | 28 | 20 | 16 |
| TvExpanded | 48 | 32 | 22 | 18 |

## Layout

`DesignTokens.Spacing` (4dp scale): xxs 2, xs 4, s 8, m 12, l 16, xl 20, xxl 24, huge 32;
aliases `screen` 16, `section` 24; `contentMaxWidth` 1080dp for centred dashboard columns.

`DesignTokens.Size`: monogram 36 / 44, iconInline 16, icon 20, iconLg 24, iconXl 28,
minTouch 48, railWidth 88, barTrackHeight 8, statCardMin 96, actionTile 72, avatar 40 / 56.

Adaptive ladder (`AdaptiveTokens`, resolved by `FormFactor` x `WindowType`):

| Set | screenPadding | itemSpacing | sectionSpacing | toolbar | columns |
|---|---|---|---|---|---|
| Compact | 16 | 8 | 24 | 64 | 2 |
| Medium | 24 | 12 | 32 | 68 | 3 |
| Expanded | 32 | 16 | 40 | 72 | 4 |
| Watch | 8 | 4 | 12 | 32 | 1 |
| Tv | 48 | 16 | 32 | 80 | 5 |
| TvMedium | 56 | 20 | 40 | 88 | 6 |
| TvExpanded | 64 | 24 | 48 | 96 | 7 |

Handheld and Desktop use the Material 3 width classes (600dp, 840dp). Tv adds overscan padding
(28 / 32 / 36dp). Helpers: `screenPadding()`, `readableWidth()` (640dp default, 1100dp on Tv,
unbounded on Watch), `contentWidth()`, `byWindow()`, `byFormFactor()`. Use the helpers, do not
branch on width in screens.

## Elevation & Depth

Flat by default. `DesignTokens.Elevation`: flat 0, raised 1, floating 3, overlay 8. Cards sit at
flat with a 1dp `outlineVariant` border. Use floating / overlay only for genuinely floating layers
(menus, bottom sheets, snackbars).

## Shapes

`DesignTokens.Shape` (all rounded corners): badge 8, chip 10, monogram 10, card 16, cardLg 20,
hero 24 dp. Pick from this list; do not mix in a one-off radius.

## Components

Brand-agnostic primitives in `Primitives.kt`: `PageHeader`, `SectionCard`, `SectionLabel`,
`TagChip`, `LoadingState`, `ErrorState`, `EmptyState`. Others in the same package: `StepTimeline`,
`OtpField`, `PageIndicator`, `PayloadCard`, `AnimatedCounter`, `ChipRow`, `MarkdownText`,
`RedactionReveal`, `ZoomableImage`, `AdaptiveNavigationShell` (with `NavigationLayout.kt`),
`ComingSoonDialog`, plus the AI settings section under `ai/`. Screenshot goldens are in
`designsystem/screenshots`.

`SectionCard` is the fundamental container: optional tinted icon tile (icon 20dp, `chip` shape),
title and subtitle, trailing slot, then content. Default content padding is `Spacing.l`.
Reuse these before writing a new card, header or empty state in an app.

## Motion

`DesignTokens.Motion`: INSTANT 90 ms, FAST 160 ms, MEDIUM 240 ms, SLOW 360 ms. Screen transitions
`screenEnter` / `screenExit` are a fade plus a 0.95 scale over MEDIUM. `focusScale()` grows a
focused element by `AdaptiveTokens.focusScale` (1.08 on Tv, 1.0 elsewhere) over FAST. Apps must
honour reduced motion where the platform exposes it.

## Do's and Don'ts

- Do take every dp, radius, elevation and duration from `DesignTokens`.
- Do read colour from `MaterialTheme.colorScheme` roles, so dark and light both work.
- Do use `byFormFactor` / `byWindow` for per-device values, and keep touch targets at `minTouch`.
- Do add a screenshot preview in `DesignSystemPreviews.kt` for a new primitive.
- Don't add a palette, brand font or app-specific type to this module.
- Don't use drop shadows to separate cards; use the 1dp outline.
- Don't hard-code `Color(0x...)` outside a semantic token.
- Don't couple a primitive to an app's data contract package.

## Agent notes

- Read the token file before writing any number; this file can lag the code and the code wins.
- Changing a token here moves all seven consuming apps. Check `graphify affected` first.
- This repo has no screens of its own. Hierarchy comes from spacing, the outline and the type
  roles, not from colour blocks or gradients.
- Run the `antislop` skill as the filter on any UI diff and report its Delivery Gate result.

## Changelog

| Date | Change | Why | Source |
|---|---|---|---|
| 2026-10-09 | Initial version, distilled from the code token files and existing design docs | Establish design direction for agents | DESIGN.md rollout |

## Open questions

- None recorded yet.

## Evolving this file

Agents: when you change UI and find this file wrong or silent, fix it in the same change and add a Changelog row. Code token files win over this file; when they disagree, correct the doc. A user correction of a visual choice with a stated reason becomes a rule here immediately. Lessons that apply beyond this repo go to the LEARNINGS log of the `design-md` skill in AgentHarness.
