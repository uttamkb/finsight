---
name: audit-ui
description: Audits the UI codebase to enforce DaisyUI semantic theming, remove static Tailwind color classes, and ensure full theme responsiveness.
---

# UI Audit Skill (DaisyUI Enforcement)

When auditing the UI for styling consistency and theme support, follow these guidelines to ensure the application fully leverages the DaisyUI theme engine:

## 1. Remove Static Tailwind Colors
Search for and replace hardcoded literal colors with DaisyUI semantic base colors.
- `bg-white` ➡️ `bg-base-100` (or `bg-base-200` for cards)
- `text-white` ➡️ `text-base-content` or `text-primary-content`
- `text-gray-*` / `text-neutral-*` ➡️ `text-base-content/60` (adjust opacity for shade)
- `bg-gray-*` / `bg-neutral-*` ➡️ `bg-base-200` or `bg-base-300`
- `border-gray-*` ➡️ `border-base-content/20`

## 2. Standardize Status Colors
Replace explicit color names used for statuses with semantic intent colors.
- `bg-indigo-*` / `text-indigo-*` ➡️ `bg-primary` / `text-primary`
- `bg-emerald-*` / `text-emerald-*` ➡️ `bg-success` / `text-success`
- `bg-amber-*` / `text-amber-*` ➡️ `bg-warning` / `text-warning`
- `bg-red-*` / `text-red-*` ➡️ `bg-error` / `text-error`
- `bg-blue-*` / `text-blue-*` ➡️ `bg-info` / `text-info`

## 3. Remove Explicit Dark Mode Classes
DaisyUI handles dark mode automatically via the `data-theme` attribute on the `html` tag.
- Strip `dark:bg-*`, `dark:text-*`, and `dark:border-*` modifiers.
- Ensure the base classes are semantic (e.g., `bg-base-100`) so they invert automatically.

## 4. Fix Hardcoded Hex Codes in Charts
Charting libraries (like Recharts) often use hardcoded hex arrays which break in dark mode.
- Replace hex codes (`#0ea5e9`, `#facc15`) with CSS custom properties linked to the theme: `var(--color-primary)`, `var(--color-warning)`, `var(--color-success)`, etc.

## 5. Update Print Variants
Ensure print styles also respect the semantic hierarchy instead of reverting to static colors.
- `print:bg-white` ➡️ `print:bg-base-100`
- `print:text-black` ➡️ `print:text-base-content`

## 6. Enforce Accessible Contrast (Light Mode)
Light mode themes often default "content" color variables to Black. When background colors like `primary` or `secondary` are vibrant (Pink, Purple, Blue), black text becomes unreadable.
- **Rule**: Use `text-*-content` (DaisyUI) or `text-*-foreground` (Local Mapping) exclusively for text overlaying theme colors.
- **Adjustment**: If contrast is low (e.g., in the provided screenshot where "Upload Statement" was black text on Pink), fix it centrally in `globals.css` by mapping the content variables to high-contrast colors (e.g., `#ffffff`) explicitly for the Light theme.
- **Component Check**: Prefer using native DaisyUI button classes (e.g., `btn btn-primary`) rather than manual `bg-primary text-primary-foreground` strings, as they handle accessible "content" colors by default.

## Implementation Approach
- Use generic regex scripts or bulk find/replace to apply these mappings across the `src/` directory.
- Test the theme switcher component to verify that background/text colors smoothly transition between Light and Dark modes.
- Perform an "Accessibility Audit" specifically on buttons in Light mode to ensure text is visible over vibrant backgrounds.
