# Add Active Calories Burned Visualization

Add a toggle switch to show the user's active calories burned overlaying the heart rate graph. The calorie data will be rendered as soft background vertical bars that double the resolution of the existing vertical graph lines (i.e. two bars per time interval between vertical grid lines). The bars will dynamically transition from a soft blue for lower calorie burn to yellow for higher exertion.

## User Review Required

- **Data Syncing**: The current `readHeartRateData` function polls Health Connect data every 5 minutes. The active calories will sync at the same time and interval.
- **Data Source**: I'll use Health Connect's `ActiveCaloriesBurnedRecord` for active calories.
- **Visual Design**: The colors will transition from blue to yellow based on a max calorie value found within the *current zoom window* to properly visualize relative exertion dynamically.

## Proposed Changes

### App Manifest
- Declare the new health permission in `AndroidManifest.xml` (`HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)`).
- Provide the localized permission rationale if needed (we'll ensure the existing rationale screen applies or covers it).

### MainActivity.kt

#### [MODIFY] MainActivity.kt
- **Permissions**: Add `HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)` to the required permissions list.
- **Data Fetching**: Create a `readCaloriesData(HealthConnectClient)` function alongside the `readHeartRateData`.
- **State Management**: Add new state variables for the toggle (`showCalories`) and the retrieved data (`caloriesData`).
- **UI additions**:
    - Add a toggle switch in the UI near the "Refresh" button (e.g. `Switch` component) to toggle the calorie visualization.
    - Pass `showCalories` and `caloriesData` to `HeartRateGraph`.

#### [MODIFY] HeartRateGraph composable (in MainActivity.kt)
- **Data Processing**: Extract `ActiveCaloriesBurnedRecord` data. Active calories are recorded over intervals (start to end time). We will need to map these continuous ranges into discrete buckets aligned with the graph's time grid.
- **Drawing Logic**:
    - Within the Canvas `onDraw` phase, if `showCalories` is true, process the calorie data.
    - Calculate the dynamic maximum calories burned within the *visible bounds* of the zoomed/panned view.
    - Draw the background vertical bars representing calorie buckets. These bars will sit behind the heart rate graph lines and points.
    - Apply a color gradient or interpolation from soft blue to yellow based on the bucket value relative to the dynamic maximum.
    - Add a small text label in one of the corners displaying the max calorie value for the current zoomed window.

## Verification Plan

### Automated Tests
- The changes are UI-heavy. The current project only has default setup instrumented/unit tests. We will ensure the app compiles correctly and the tests don't fail.

### Manual Verification
- Compile and run the app.
- Ensure the new permission is requested.
- Verify the toggle switch turns the visualization on and off.
- Verify zooming and panning updates the max burned label and recolors/reshapes the bars accordingly.