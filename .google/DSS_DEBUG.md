# Dual Screenshot (DSS) Implementation & Debug Summary

**Goal:** Capture both screens (Top/Display 0 and Bottom/Display 1) on an AYN Thor handheld (Android 13/API 33) and stitch them vertically.

## 1. Current Implementation

*   **Architecture:**
    *   `KeepAliveService` (:keepalive process) triggers `DualScreenshotManager`.
    *   `DualScreenshotManager` sends broadcast to `HomeKeyInterceptorService` (Main process/Accessibility) to collapse shade.
    *   `DualScreenshotManager` starts `DualScreenshotService` (Main process).
    *   `DualScreenshotService` executes capture logic sequentially.

*   **Capture Methods Available:**
    1.  **AccessibilityService (`takeScreenshot`)**: Standard API 30+.
    2.  **MediaProjection (`createVirtualDisplay`)**: Requires user permission token.

*   **Current Logic (Hybrid):**
    *   **Top Screen:** Uses `MediaProjection` (if token available) or Accessibility. **Status: WORKING.**
    *   **Bottom Screen:** Forces `AccessibilityService.takeScreenshot(displayId)`. **Status: FAILING.**

## 2. The Problem

*   **Symptom:** The bottom half of the stitched image is a solid blue block (our debug fallback).
*   **Logs:**
    *   `ACCESSIBILITY_CAPTURE_FAILED details="errorCode=3 displayId=4"` (Error 3 = `ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY`).
    *   This happens whether we target `displayId=1` (User claim) or `displayId=4` (Discovered via `DisplayManager`).
*   **MediaProjection Failure:**
    *   When we tried using `MediaProjection` for the bottom screen (targeting Display 4's metrics), it captured a **shrunken version of the Top Screen (Display 0)**.
    *   This indicates `MediaProjection` is mirroring the primary display instead of capturing the secondary one, likely because the token is bound to the default display session.

## 3. What We Have Tried

1.  **Accessibility Capture:**
    *   Tried `displayId=1`. Result: Blue box (likely Error 3 or null).
    *   Tried `displayId=4` (from `DisplayManager`). Result: Explicit Error 3 (`INVALID_DISPLAY`).
    *   Confirmed `canTakeScreenshot="true"` is in `accessibility_service_config.xml`.

2.  **MediaProjection Capture:**
    *   Implemented full permission flow (`createScreenCaptureIntent`).
    *   Captured Top screen successfully.
    *   Captured Bottom screen (ID 4 metrics) -> Resulted in mirrored Top screen content (scaled).
    *   Tried launching permission activity on Secondary Display (via `ActivityOptions.setLaunchDisplayId`). Result: Same behavior (Mirroring).

3.  **Discovery:**
    *   `DisplayManager` reports two displays: ID 0 (Top) and ID 4 (Bottom?).
    *   User believes Bottom is ID 1. We tried both IDs with Accessibility; both failed.

## 4. Conclusion / Blocker

*   **Accessibility API** is explicitly rejecting capture of the secondary display on this OS version/ROM.
*   **MediaProjection API** seems unable to target the secondary display independently (defaults to mirroring primary).

We need a new strategy to capture the secondary display on this specific hardware.
