# MedRad Study App — iOS foundation

Native SwiftUI foundation for the iPhone/iPad app.

## Current state
- SwiftUI app shell and bottom tab navigation
- Home dashboard with cards and quick navigation
- Profile/settings entry point
- Courses, Medical Feeds, Discover Scholars and Tutor Directory destination screens (UI placeholders)
- GitHub Actions simulator build and zipped artifact

## Build
The CI workflow `.github/workflows/ios-build.yml` runs on pushes to `ios-parity-foundation` and can be manually triggered. It builds an unsigned iOS Simulator app and uploads it as an artifact.

## Important
This is a UI foundation, not feature parity yet. Authentication, live Supabase data, course content, feeds, scholars, tutors, exams and profile settings still need implementation. Do not change the existing backend, account records, or Android app as part of iOS work.
