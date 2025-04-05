# FieldPulse Home Screen Redesign Plan

## Objective
Create new home screen with:
1. Settings button linking to existing settings screen
2. Camera button implementing geotagged photo capture

## Implementation Steps

### 1. File Structure Changes
- New: `app/src/main/res/layout/activity_home.xml`
- New: `app/src/main/java/org/tarar/FieldPulse/HomeActivity.kt`
- Modified: `AndroidManifest.xml`

### 2. Key Features
- Material Design UI components
- Runtime permission handling
- Camera intent integration
- EXIF geotagging
- Location services integration

### 3. Testing Requirements
- Verify button navigation flows
- Test camera permission scenarios
- Validate GPS coordinates in photo metadata
- Check error handling cases

## Dependencies
- AndroidX Core
- Material Components
- Location Services