# Implement App Lock Blocking Logic and UI Updates

This plan outlines the steps to:
1. Update the app selection UI to gray out apps already assigned to other plans.
2. Refine the app blocking logic in `AppBlockEngineService` to respect plan restrictions.
3. Ensure an app can only belong to one plan at a time.

## User Review Required

> [!IMPORTANT]
> The current database schema uses `packageName` as a primary key for `AppBlockRule`. This naturally prevents an app from being in multiple plans. My proposed change will enforce this in the UI by showing which plan already contains the app and preventing re-selection.

## Proposed Changes

### Core Logic & Data Models

#### [MODIFY] [LocksViewModel.kt](file:///C:/Users/Debashis/StudioProjects/Digital_Monk/app/src/main/java/com/digitalmonk/app/ui/locks/LocksViewModel.kt)
- Update `AppItem` data class to include `assignedPlanName: String?`.
- Update `selectableApps` to combine `_installedApps`, `_searchQuery`, and `activeRules` to populate `assignedPlanName`.
- Update `toggleAppSelection` to prevent selecting an app that is already assigned to another plan.

#### [MODIFY] [LocksScreen.kt](file:///C:/Users/Debashis/StudioProjects/Digital_Monk/app/src/main/java/com/digitalmonk/app/ui/locks/LocksScreen.kt)
- Update `AppSelectorItem` to:
    - Display the assigned plan name if the app is already in another plan.
    - Gray out (reduce opacity/disable interaction) for apps already in another plan.

### Blocking Logic

#### [MODIFY] [AppBlockEngineService.kt](file:///C:/Users/Debashis/StudioProjects/Digital_Monk/app/src/main/java/com/digitalmonk/app/service/monitor/AppBlockEngineService.kt)
- Update `shouldBlock(rule)` to check specific restrictions (e.g., time limits, active days).
- Ensure `applyBlock(rule)` is called only when restrictions are met.

## Verification Plan

### Automated Tests
- Unit test for `LocksViewModel` to verify `selectableApps` correctly identifies apps in other plans.
- Unit test for `AppBlockEngineService` logic (if possible, or via integrated tests).

### Manual Verification
- Open the "Locks" screen.
- Create a plan and add some apps.
- Create a second plan and verify that apps from the first plan are grayed out and show the plan name.
- Verify that grayed-out apps cannot be selected.
- Open a blocked app and verify it is blocked according to the plan rules.
