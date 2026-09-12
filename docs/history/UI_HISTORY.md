> Android/WebView polish: removed the temporary blue tap/focus highlight on Settings rows before selection dialogs open.

# Retra ROM UI

> v3.81 keeps the main Settings category cards at the exact v3.75 size, while slightly reducing the controls inside Settings pages (Screen orientation, Fast-forward speed/button, toggles, sliders, and similar rows). The synced smooth selection-frame animations remain preserved.

> v3.61 adds a mobile-safe Appearance theme carousel: swipe horizontally without triggering browser/app back navigation, with smooth snapping and tap-to-select preserved.

Updated HTML/CSS/JS emulator UI prototype.

## New in v1.9
- Clicking a ROM or ROM hack in the Library now opens a dedicated **detail page**
- The detail page is inspired by the screenshot you sent
- Removed manga-only / unnecessary parts and replaced them with emulator-friendly ROM info
- Added a **Resume** button
- Added **Recent saves** list
- Added **Change BG** feature
- Added **Change Cover** feature
- Users can upload their own image in the browser prototype to change:
  - the ROM background/hero image
  - the ROM cover/logo image

### Screen size editor update
- Replaced **Make fullscreen** with **Add buttons** in the Screen size preview menu
- Added a **Buttons editor** so controls can be shown/hidden
- Preview controls can now be **dragged anywhere** inside the screen layout editor
- Added a **size slider** so each control group can be resized
- Added **Reset layout** to restore the default control positions

## Current pages
- Library
- ROM Detail
- History
- More
- Settings
- Statistics
- Categories
- Support Us
- About
- Help

## Run
Open `index.html` in a browser.

## v2.0 Update
- Removed the extra subtitle / genre text on Library cards
- ROM cards now show a cleaner title-only layout


## v2.1 Update
- Reduced the height of each Statistics card by about 25–30%
- Added a new **Playtime by ROM** section
- Each ROM now shows its own total playtime


## v2.3 Update
- Library now shows only **All** by default
- Removed the default GBA / GBC / GB / Favorites chips
- **Add Category** now works
- New categories automatically appear beside **All** in the Library
- Categories are saved with `localStorage`
- Categories can be deleted from the Categories page


## v2.3 Update
- Removed the top download icon from the ROM detail page header
- Clicking the **filter / three-lines** button on a ROM now opens a bottom sheet
- The bottom sheet lets you put that ROM into any category you want
- ROMs can belong to multiple categories
- Library category chips now filter ROMs based on those assigned categories


## v2.4 Update
- Removed **Sort** and **Display** from the ROM category bottom sheet
- Scaled down the floating **Resume** button
- Moved **Change BG** and **Change Cover** into the top-right **three dots** menu
- ROM action row is now cleaner with only **In library** and **Resume**


## v2.5 Update
- Replaced the History header action with **Search** and **Remove everything**
- Added working **search in History**
- Added **confirmation** before clearing all history
- Added empty state after history is cleared


## v2.6 Update
- Fixed the ROM **three-dots menu** so **Change BG** and **Change Cover** can be clicked properly
- Moved the dropdown frame higher so it stays at the **top-right area** and does not cover the title as much
- Improved menu z-index and click handling


## v2.7 Update
- Removed the **Filter** button from the Library header
- Added **Refresh library** inside the Library **three-dots** menu
- Updated History entries to show clearer **last played date and time**

## v2.8 Update
- Removed **Download queue** from the More page

## v2.9 Update
- Changed History timestamps to relative-time style
- Examples now show:
  - 30 min ago
  - 2 hr ago
  - 3 days ago

## v3.0 Update
- Replaced browser confirmation popup with a custom in-app confirmation modal
- Refresh library now uses the UI confirmation
- Clear history now also uses the UI confirmation

## v3.1 Update
- Genre chips on the ROM detail page are now auto-detected from the ROM title / metadata
- Different ROMs now show more accurate genres instead of one fixed set
- Added fallback genre detection for imported ROMs


## v3.2 Update
- Added **Appearance** at the top of Settings
- Created a new **Appearance** page with **Theme** controls
- Added **System / Light / Dark** mode selector
- Added theme previews: Default, Dynamic, Catppuccin, Yotsuba, Tokyo Night, Monochrome, Teal & Turquoise, Tidal Wave, Yin & Yang, Nord, Strawberry Daiquiri, Tako, Green Apple, Lavender, and Midnight Dusk
- Added **Pure black dark mode** toggle
- Omitted the extra **Display** section as requested


## v3.3 Update
- Made the **Appearance** page actually work
- Theme selection now changes the UI colors live
- Added **smooth horizontal swipe/scroll** for the theme preview row
- Added a new theme: **Aurora Mint**
- Added **Translucent mode** below **Pure black dark mode**
- Saved appearance choices with localStorage so they stay after refresh

## v3.4 Update
- Added working Sound frequency selector
- Added 44100 Hz
- Added 22050 Hz
- Added 11025 Hz
- Selected value is saved with localStorage

## v3.5 Update
- Fixed Appearance theme cards so they can be clicked again
- Mouse drag scrolling still works
- Mouse wheel / trackpad horizontal scrolling still works
- Clicks are only blocked after an actual drag, not on normal taps/clicks

## v3.6 Update
- Fixed **Light mode** so it actually changes the full UI
- **System** mode now follows the device/browser color-scheme preference
- System mode updates automatically if the OS appearance changes
- Pure black mode is disabled while Light mode is active
- Light mode now updates navigation, cards, settings, menus, text, and dialogs

## v3.7 Update
- Fixed the bottom navigation panel so it now changes with the selected Appearance theme
- Bottom nav now uses theme-based background, text color, and active state colors
- Also supports Light mode, Dark mode, and Translucent mode properly

## v3.8 Update
- Fixed the Appearance mode selector check icon
- The Dark mode check icon now only shows when Dark is the active mode
- Switching to Light or System no longer leaves the Dark check visible

## v3.9 Update
- Added check icons to System, Light, and Dark mode buttons
- Now whichever appearance mode is active shows the check icon

## v3.10 Update
- Added responsive horizontal padding / side gutters
- Settings cards no longer sit too close to the left and right edges
- Gutters scale automatically based on device width
- Updated headers and ROM detail layout to stay aligned with the new responsive insets

## v3.11 Update
- Made Settings card backgrounds truly edge-to-edge
- Settings home list now touches the left and right sides of the phone UI
- Video, Audio, Misc, Advanced, Appearance lower rows, and Layout cards are edge-to-edge
- Text, icons, toggles, and controls still keep responsive internal padding
- Works using the same responsive device gutter variable

## v3.12 Update
- Updated the main Settings panel to match the new rounded card style from your reference
- Replaced the edge-to-edge Settings home list with an inset rounded panel
- Improved Settings row spacing, icon blocks, hover feel, and footer styling
- Keeps the look responsive across device sizes

## v3.13 Update
- Changed the ROM patching control from a rounded square checkbox to a circular checkbox
- Kept the checkmark and active color styling

## v3.14 Update
- Cleaned up the ROM patching circle checkbox
- Removed the stray black line on the left side
- Centered the checkmark better and clipped it inside the circular control


## v3.15 Update
- Added a working Screen size preview page inside Video settings
- Screen size now opens a portrait / landscape emulator preview
- Long-press the game screen to open size options: Add buttons, Make centered, Best-scaling (4x), Break aspect ratio
- Selecting an option updates the preview and saves it


## v3.16 Update
- Fixed the ROM patching circular checkbox
- Removed the black line artifact on the left side of the checked circle
- Kept the ROM patching control fully circular and clean


## v3.17 Update
- Removed the internal checkmark from the ROM patching circle
- The control is now a clean plain circle when enabled
- This removes the remaining black line that appeared only in the ON state


## v3.18 Update
- Remade the Screen size preview to look more like a real landscape emulator screen
- Improved the portrait and landscape preview layouts
- Upgraded the preview controls and buttons so they look cleaner and more accurate
- Kept the long-press screen size menu working for fullscreen, centered, best-scaling, and break aspect ratio


## v3.19 Update
- Removed the portrait preview and made the Screen size page landscape-only
- Replaced the screen preview so it matches the reference layout more closely
- Upgraded the emulator preview buttons and overall control styling
- Kept the long-press menu for fullscreen, centered, best-scaling, and break aspect ratio

## v3.20 Update
- Fixed the JavaScript syntax error from the landscape Screen Size remake
- Restored clicking throughout the UI
- Screen Size button, settings buttons, themes, navigation, and long-press menu now work again
- Kept the landscape-only Screen Size design


## v3.21 Update
- The Screen Size page now automatically switches the whole phone mockup into landscape
- Leaving the Screen Size page switches the phone mockup back to normal portrait
- Improved the Screen Size page layout so the landscape emulator preview fits better inside the rotated phone
- Kept the existing long-press screen size menu and controls


## v3.22 Update
- Added working Fast-forward speed choices: 2×, 4×, 8×, and 16×
- Replaced the Retra text logo with the provided Spring After Winter logo asset
- Updated the More and About pages to use the new Retra branding


## v3.23 Update
- Removed the left-side helper text from the landscape Screen size preview
- Scaled the landscape preview up so it fills the available screen area better
- Kept the screen size page in a cleaner full-preview layout


## v3.24 Update
- Added working Screen orientation dialog with: Auto rotate, Landscape, Reverse landscape, Portrait, and System default
- Added working GLSL Shader dialog with None and Install Shaders action
- Added a reusable custom confirmation modal so refresh/clear actions stay inside the UI


## v3.26 Update
- Restyled the Screen orientation and GLSL Shader dialogs so they follow the active Appearance theme
- Dialog colors now adapt to Dark, Light, System, and theme accent colors
- Scaled the dialogs down for a cleaner fit on the screen


## v3.26 Update
- Fast-forward speed now uses the same themed selection dialog style instead of a dropdown
- GLSL Shader continues using the same appearance-based dialog system
- Fast-forward options remain 2×, 4×, 8×, and 16×


Update v3.28:
- Redesigned Screen Size landscape page to prioritize the full preview area.
- Kept a back button as a compact floating control.
- Reduced header clutter and improved fit so the full control layout is easier to visualize.

## v3.29 Screen Size Fit Fix
- Fixed the landscape Screen Size preview shell overflowing below the phone frame.
- All D-pad, A/B, Start/Select, L/R and menu controls now remain visible on short landscape screens.
- GBA preview now uses the native 240x160 (3:2) aspect ratio for Fullscreen, Centered and Best-scaling modes.
- Break aspect ratio remains intentionally stretched.
- Preview dynamically recalculates on resize/orientation changes and respects mobile safe-area insets.


### v3.31 screen size control editor update
- Replaced the text button editor with a small **+ add control** button inside the landscape preview
- Added an **Add control** modal for adding extra buttons and combo controls
- Added a floating **scale handle** beside the selected control for resizing
- Removed the old bottom **Buttons editor** panel
- Reset layout now removes extra controls and restores the default layout


### v3.35 UI polish
- Fixed fast-forward chevrons so they point to the right.
- Replaced legacy CSS-drawn action icons with cleaner inline vector SVG icons.
- Refined the menu control with a rounded-corner frame and cleaner hamburger glyph.
- Reduced the Add control modal size and added stronger Android landscape autoscaling.


### v3.36 scale handle behavior
- The resize/scale handle now hides when you tap the screen/background area.
- Tapping a control selects it again and shows the resize handle.
- Opening the Add control dialog temporarily hides the scale handle for a cleaner preview.


### v3.37 menu icon polish
- Updated the menu button to look closer to the My Boy! menu icon.
- Added a compact rounded-square inner badge with three white bars and subtle corner highlight.


### v3.38 preview top bar color
- Changed the thin header line on the preview screen from orange to yellow.


### v3.40 long-press menu and top button alignment
- Fixed the screen-size context menu so it stays visible after long-press release instead of disappearing immediately.
- Aligned the Back button and + Add button so they sit on the same top row height.


### v3.41 screen-size menu
- Added **Make fullscreen** back to the long-press screen-size menu.
- Menu now contains: Make fullscreen, Make centered, Best scaling (4x), and Break aspect ratio.
- Fullscreen keeps the GBA 3:2 aspect ratio while using nearly all available preview space.


### v3.42 long-press release click fix
- Fixed the screen-size menu so the first release/click after a long-press is ignored.
- This prevents Make fullscreen / Make centered / Best-scaling (4x) / Break aspect ratio from disappearing immediately when you lift your finger.


### v3.43 single outer frame
- Removed the extra inner rounded frame/border in the screen-size phone preview.
- Kept only a single outer frame.
- Expanded the emulator content area so it uses the freed space more cleanly inside the phone screen.


## v3.44 Update
- Fixed the Screen size **Menu** control so it matches the visual size of **Fast Forward** and **Quick Save**.
- Removed the small inner square from the Menu icon.
- Replaced it with a clean, modern **three-line hamburger menu** icon.
- Added responsive menu sizing so it stays matched on shorter landscape phones.


## v3.45 Official Logo
- Replaced the previous Retra branding image with the new official pixel-art flower/snow logo provided by the user.
- The new logo is now used anywhere the shared `assets/branding/retra-logo.png` asset appears, including the More and About pages.


## v3.46 Live Button Size
- Added a live percentage badge beside the selected controller button.
- The value updates continuously while dragging the resize handle (for example 85%, 100%, 125%).
- The percentage stays visible while a control is selected and hides when the selection is cleared or the Add control dialog opens.


## v3.47 Hold to Remove Control
- Press and hold any controller control for about half a second to show a **Remove control** action.
- Moving more than a few pixels cancels the hold so normal dragging remains responsive.
- Added controls are deleted; built-in controls are hidden and can be restored with **Reset layout**.
- Right-click also opens the remove action on desktop for easier testing.


## v3.50 Responsive Side Gutters

- Fixed Settings detail cards touching the left and right edges.
- Restored the normal responsive side gutter on Settings pages.
- Settings lists and Layout cards now stay cleanly inset on both sides.
- Preserved the rounded card edges and internal row spacing.


- Fixed responsive side gutters / horizontal padding for settings-style cards and recent save cards so they no longer touch the left or right edges on different device sizes.


## v3.54 Edge-to-Edge Settings Detail Pages

- Restored full-width setting list/card backgrounds on Video, Audio, Advanced, Fonts, and other Settings detail pages.
- Appearance keeps its theme picker inset while the lower settings rows return to edge-to-edge.
- Layout option cards also return to the edge-to-edge detail-page style.
- Row text, toggles, sliders, and controls keep normal inner padding for readability.
- ROM detail gutters remain unchanged.


## v3.55 Edge-to-Edge Recent Saves
- Recent save-state rows now reach both left and right edges of the ROM detail content area.
- Removed rounded inset-card styling from save rows so they match the full-width Settings detail style.
- Save names, timestamps, and dropdown controls keep comfortable internal padding.


## v3.56 Reference Controller Icons
- Restyled Quick Load, Quick Save, and Fast Forward controller utility buttons to match the supplied My Boy!-style reference.
- Added compact square frames with flatter dark translucent faces and brighter outlines.
- Reworked Quick Load to a curved up/right arrow, Quick Save to a cleaner floppy-disk glyph, and Fast Forward to bold double chevrons.
- Applied the same icon design to both the on-screen controller and the Add control list.
- Kept responsive sizing and light-mode support.


## v3.57 Rounded Utility Button Corners

- Quick Load, Quick Save, and Fast Forward keep the v3.56 reference icons.
- Restored soft rounded corners so the three utility buttons match the rest of the controller UI.
- Uses 12px radius normally and 10px on compact landscape layouts.


## v3.58 Matched Utility Button Glass

- Quick Load, Quick Save, and Fast Forward now reuse the same `myboy-menu-button` component/class as the Menu button.
- Their background opacity, gradient, border, shadows, corner radius, and responsive sizes now match Menu exactly.
- Existing reference glyphs are preserved.
- Light mode and selected-control feedback remain supported.


## v3.59 Display Version Label
- User-facing version text now shows **Retra v1.0.0**.
- Removed the active UI font name from the Settings/About version labels.

## v3.60 Dynamic ROM Library
- Removed all bundled / hard-coded sample ROM cards from the Library.
- Library now starts empty and shows a clear empty state.
- Added a **+** button in the Library header.
- The + button opens the device file picker for `.gba`, `.gbc`, `.gb`, `.zip`, `.ips`, `.ups`, and `.bps` files.
- Selected ROM/ROM-hack files are added to the Library immediately with a generated cover, title from the filename, and detected system badge.
- ROM metadata is saved locally, and ROM file blobs are stored locally with IndexedDB when the browser supports it.
- Search and custom categories now work with dynamically imported ROMs.
- Removed the old hard-coded History entries and sample playtime-by-ROM rows.
- Statistics now start at zero; Library/GBA/GBC/GB counts update from imported ROMs.
- No copyrighted ROM files are included in the project.


## v3.61 Update
- Fixed the Appearance theme carousel on Android/mobile.
- Horizontal swipes stay inside the theme picker instead of triggering back navigation.
- Added direction locking so vertical gestures can still scroll the settings page.
- Theme cards smoothly snap toward the nearest card after a drag.
- Tapping a theme still selects it normally.
## v3.63
- Removed the gamepad emoji/logo from the empty Library state when no ROMs are present.



## v3.64
- Raised the bottom navigation so it stays above Android phone navigation bars and software keys.
- Added responsive bottom safe-area handling for phones with different system UI heights.
- Updated the layout to recalculate the bottom inset on resize, orientation change, and viewport changes.


## v3.65
- Reworked Appearance theme carousel for smoother phone swiping with native momentum.
- Added click-and-drag mouse/pen scrolling with gentle inertia and card snapping.
- Improved trackpad/mouse-wheel horizontal browsing and prevented accidental theme selection after dragging.
- Theme selection now centers only the carousel instead of moving the whole page.


## v3.66
- Press and hold a ROM in Library to open ROM actions.
- Add/remove ROMs from Favourites; favourites are pinned to the front and show a heart badge.
- Remove ROMs from the Retra Library without deleting the original device file.
- Long press works with touch and mouse and cancels cleanly when scrolling.


## v3.68
- Moved the Add ROM (+) button out of the header and back to a floating bottom-right position above the bottom navigation.
- Fixed the ROM detail page top spacing/gap when opening a library item.
- Scaled the floating Resume button down and moved it higher so it no longer feels oversized or too low.


## v3.69
- Normal Retra pages are portrait-first on mobile.
- Landscape rotation is allowed only for the in-game/controller preview.
- Added best-effort Screen Orientation API locking for Android/PWA/WebView environments.
- Added a CSS fallback that keeps normal UI centered at portrait width if the browser refuses orientation locking.


## v3.70
- Removed the visible inner frame/bezel from the Screen size landscape preview, leaving a single outer preview frame.
- Expanded the emulator/controller canvas to use the full available preview area.
- Reduced and re-constrained controller sizes so the D-pad, A/B, shoulders, menu, Start/Select, and utility controls remain fully inside mobile landscape screens.


## v3.71
- Made the Screen Size emulator display full-bleed so it touches the top, bottom, left, and right edges of the single outer preview.
- Kept controller buttons overlaid inside the game area and moved them closer to the preview edges.
- Removed remaining inset spacing from all screen-size modes in landscape preview.


## v3.72
- Restored distinct Screen Size preview modes instead of making every option fullscreen.
- Fullscreen now uses the complete landscape emulator area with no outer spacing.
- Best scaling and Centered remain smaller and preserve the GBA 3:2 aspect ratio.
- Break aspect ratio remains true full-bleed.
- Removed the old 10px landscape editor padding that caused a visible gap even in Fullscreen.


## Resizable emulator screen update
- Uses the v3.72 responsive screen-mode build as the base.
- Tap the emulator screen in Screen size to select it.
- Drag the top, bottom, left, or right handle independently to resize the game screen.
- Manual dimensions are saved as Custom resize, while Fullscreen, Centered, Best scaling (4x), and Break aspect ratio remain available by long-pressing the screen.

## v3.74 Screen resize update
- Replaced the four emulator-screen edge resize handles with one **expand/minimize handle**.
- Tap the emulator screen to select it; the same resize handle used by controller buttons appears at the screen corner.
- Drag outward to expand the emulator screen and inward to minimize it.
- Screen resizing stays inside the preview bounds and preserves the selected screen proportions.
- The live percentage badge now also shows the emulator screen size.


## v3.81
Reduced the controls inside Settings one more small step. Screen orientation, fast-forward options, toggles, sliders and selection rows are slightly more compact while the main Settings cards remain at the v3.75 size.


## v3.81
Slider rows inside Settings now share one consistent size and spacing, while remaining only slightly taller than normal setting rows. The main Settings category cards remain unchanged.

### v3.81.1 Android/WebView settings tap fix
- Removed the transient blue/cyan full-width row highlight that could appear before Settings selection dialogs.
- Touch hover/active/focus states on Settings launcher rows are forced transparent on coarse-pointer devices.
- Settings launcher buttons are blurred immediately on click so Android WebView does not keep a one-frame focus paint.
- Selected option styling inside dialogs remains unchanged.
