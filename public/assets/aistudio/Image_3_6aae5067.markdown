---
name: Iris Notes System
colors:
  surface: '#fef7ff'
  surface-dim: '#e0d7e5'
  surface-bright: '#fef7ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#faf0ff'
  surface-container: '#f4ebf9'
  surface-container-high: '#eee5f4'
  surface-container-highest: '#e8dfee'
  on-surface: '#1e1a24'
  on-surface-variant: '#484455'
  inverse-surface: '#332e39'
  inverse-on-surface: '#f7eefc'
  outline: '#797487'
  outline-variant: '#cac3d8'
  surface-tint: '#6537e8'
  primary: '#4100b6'
  on-primary: '#ffffff'
  primary-container: '#5925dc'
  on-primary-container: '#ccbeff'
  inverse-primary: '#ccbeff'
  secondary: '#8135c5'
  on-secondary: '#ffffff'
  secondary-container: '#ba70ff'
  on-secondary-container: '#440076'
  tertiary: '#473167'
  on-tertiary: '#ffffff'
  tertiary-container: '#5f4880'
  on-tertiary-container: '#d6bbfb'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e7deff'
  primary-fixed-dim: '#ccbeff'
  on-primary-fixed: '#1e0060'
  on-primary-fixed-variant: '#4c06d0'
  secondary-fixed: '#f0dbff'
  secondary-fixed-dim: '#deb7ff'
  on-secondary-fixed: '#2c0050'
  on-secondary-fixed-variant: '#670fac'
  tertiary-fixed: '#eddcff'
  tertiary-fixed-dim: '#d6bbfb'
  on-tertiary-fixed: '#250e44'
  on-tertiary-fixed-variant: '#523c72'
  background: '#fef7ff'
  on-background: '#1e1a24'
  surface-variant: '#e8dfee'
typography:
  display-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 36px
    fontWeight: '700'
    lineHeight: 44px
  display-lg-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 36px
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 17px
    fontWeight: '400'
    lineHeight: 26px
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 22px
  body-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
  label-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
  label-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
  caption:
    fontFamily: Plus Jakarta Sans
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 14px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-mobile: 0.75rem
  margin: 1.25rem
  margin-mobile: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1.25rem
  space-xl: 2rem
---

## Brand & Style

This design system expresses a serene, focused, and luxurious environment tailored for thoughtful writing and personal knowledge capture. The aesthetic blends **modern tactile minimalism** with **delicate glassmorphic accents**. It aims to evoke tranquil clarity, creative confidence, and premium craftsmanship.

Key brand tenets include:
- **Atmospheric Calm:** Avoiding stark clinic-white canvases in favor of warm, mauve-tinted foundations that soothe the eyes during long writing sessions.
- **Jeweled Focus:** Using deep violet and radiant amethyst sparingly to direct attention without overwhelming mental bandwidth.
- **Physical Softness:** Tactile, rounded surfaces that mimic fine vellum or smooth digital stationery, grounding the user in an intentional digital ritual.

## Colors

The palette revolves around sophisticated purples balanced by warm, organic neutrals and ultra-readable slate-charcoal text:

- **Primary (`#5925DC` - Deep Royal Violet):** Drives primary actions, active indicators, and core interactive states.
- **Secondary (`#9B51E0` - Radiant Amethyst):** Highlights, productivity callouts, and expressive tags.
- **Tertiary (`#D6BBFB` - Luminous Lavender):** Subdued pills, tint layers, interactive selections, and soft badges.
- **Neutral Dark (`#1E1A24` - Deep Slate-Charcoal):** High-contrast body text and crisp display headlines, avoiding harsh `#000000`.
- **Canvas Base (`#FAF8FB` - Warm Mauve Tint):** Base workspace canvas.
- **Surface Elevation (`#FFFFFF` - Pure Vellum White):** Standard card backgrounds with a faint violet cast.
- **Subtle Border (`#EADBFC` - Whisper Violet):** Delimiting strokes for tactile edge definition.

## Typography

The type system is powered by **Plus Jakarta Sans**, chosen for its friendly geometric curves, pristine legibility at compact mobile scale, and refined editorial poise.

- **Editorial Rhythm:** Generous line heights on `body-lg` and `body-md` provide frictionless reading during deep content consumption.
- **Typographic Scale:** Mobile headlines stay tightly constrained to prevent awkward line breaks in multi-column note views.
- **Hierarchy:** Slate-charcoal (`#1E1A24`) is the dominant text value. Secondary metadata shifts down to `#635B6D` for effortless visual parsing.

## Layout & Spacing

A fluid grid architecture built for touch-first ergonomics, handling single-pane note flows and dual-column grid views:

- **Grid Model:** 4-column layout on compact mobile devices, shifting to an 8-column layout on foldable or tablet views.
- **Margins & Safe Zones:** A base outer margin of `1rem` on mobile keeps notes comfortable to thumb-scroll, with `2rem` bottom clearance reserved for floating action controls.
- **Staggered Density:** Note cards use variable vertical spacing (`0.75rem` to `1rem`) to support dynamic masonry layouts without visual clutter.

## Elevation & Depth

Visual hierarchy uses **tonal layering**, **delicate translucent glass**, and **amethyst-tinted ambient shadows**:

- **Ground Level (Canvas):** Mauve-tinted warm base (`#FAF8FB`) with no elevation.
- **Tactile Card Level:** Pure white (`#FFFFFF`) with a 1px boundary stroke (`#EADBFC`) and an ultra-diffused shadow: `0px 4px 20px -2px rgba(89, 37, 220, 0.05)`.
- **Floating Controls (Glassmorphism):** Floating action bars, toolbars, and search modules use a semi-transparent surface (`rgba(255, 255, 255, 0.82)`) with a `16px` backdrop-filter blur, paired with a subtle luminous top border (`rgba(255, 255, 255, 0.6)`) and ambient shadow: `0px 12px 32px -4px rgba(89, 37, 220, 0.12)`.
- **Modal Sheets:** Deep backdrop dimming (`rgba(30, 26, 36, 0.35)`) with an elevated surface carrying a soft shadow: `0px -8px 30px rgba(89, 37, 220, 0.08)`.

## Shapes

The interface embraces a **Level 2 (Rounded)** curvature with deliberate pill adaptations for micro-interactions:

- **Cards & Note Tiles:** `1rem` (16px) corner radius, delivering a smooth, friendly silhouette that mimics rounded notebook pages.
- **Interactive Buttons & Chips:** `1.5rem` (24px) for full pill shapes, providing unmistakable affordance for touch targets.
- **Floating Command Island:** Fully rounded continuous pill geometry (`2rem` / 32px), creating a sleek floating tool.
- **Inputs & Dropdowns:** `0.75rem` (12px) for structured balance inside nested panels.

## Components

### Buttons
- **Primary FAB / Action:** Rich gradient from Deep Violet (`#5925DC`) to Radiant Amethyst (`#9B51E0`), pure white text/iconography, soft purple glow on press.
- **Secondary / Ghost:** Transparent surface with `#EADBFC` border, deep violet text, filling with `rgba(214, 187, 251, 0.2)` on active state.

### Note Cards
- White background framed in subtle `1px solid #EADBFC`.
- Displays card title in `headline-md`, body preview truncated to 3 lines in `body-sm`, and bottom metadata row (date, category pill, pin icon).
- Pressed state gently drops scale to `0.98` with an inner amethyst hairline ring.

### Floating Action Bar
- Docked centrally at the bottom with a 16px bottom inset.
- Glassmorphic translucent fill, containing quick-action icons: Text Note, Voice Memo, Checklist, and Camera Scan.

### Chips & Productivity Badges
- **Status Badges (e.g., 'In Progress', 'Pinned', 'Ideas'):** Built with Tertiary Luminous Lavender tint backgrounds (`#F4EBFF`), `#5925DC` bold label text, and `0.5rem` internal padding.
- **Category Filter Chips:** Pill-shaped (`999px`), idle state in warm white with a thin border; active state in solid `#5925DC` with white text.

### Form Inputs & Editor Canvas
- **Editor Canvas:** Borderless, clean typographic workspace expanding naturally with scroll.
- **Inline Checklists:** Smooth custom checkboxes featuring soft rounded corners (`6px`), violet check fills upon completion, and strike-through text transitions with subtle opacity fade.