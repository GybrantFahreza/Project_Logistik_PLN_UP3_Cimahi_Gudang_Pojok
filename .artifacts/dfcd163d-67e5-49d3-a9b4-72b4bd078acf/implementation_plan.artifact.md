# Fix KAPT Incompatibility with Built-in Kotlin

The project is currently failing to sync because the `kotlin-kapt` plugin is incompatible with the "built-in Kotlin support" enabled by default in Android Gradle Plugin 9.3.2.

The recommended solution is to migrate from KAPT (Kotlin Annotation Processing Tool) to KSP (Kotlin Symbol Processing). KSP is more efficient, faster, and fully compatible with the new AGP built-in Kotlin support.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///C:/Users/Lenovo/AndroidStudioProjects/LogistikPintar/gradle/libs.versions.toml)
- Add KSP plugin version and definition.

#### [MODIFY] [build.gradle.kts (app)](file:///C:/Users/Lenovo/AndroidStudioProjects/LogistikPintar/app/build.gradle.kts)
- Replace `kotlin-kapt` plugin with the KSP plugin.
- Replace `kapt` dependency for Room with `ksp`.

## Verification Plan

### Automated Tests
- Run Gradle Sync to ensure the project configures correctly.
- Run a build to verify Room's code generation via KSP.
- Run `./gradlew :app:assembleDebug` to ensure the app compiles.

### Manual Verification
- Verify that the IDE no longer shows the "incompatible with built-in Kotlin" error.
