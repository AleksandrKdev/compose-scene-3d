# iOS sample

Open `iosApp.xcodeproj` in Xcode, select the `iosApp` scheme and an arm64 iPhone simulator, then
press Run. The Xcode build phase invokes Gradle and embeds the static Kotlin framework and Compose
resources automatically.

To verify the complete app from the command line:

```shell
xcodebuild -project samples/ios-app/iosApp.xcodeproj \
  -scheme iosApp -configuration Debug -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  CODE_SIGNING_ALLOWED=NO build
```

## Physical iPhone

The sample requires iOS 18.5 or newer because the current Filament KMP native distribution contains
objects built with that deployment target.

1. Connect the iPhone by USB, unlock it, tap **Trust This Computer**, and enable Developer Mode in
   **Settings → Privacy & Security → Developer Mode**.
2. Open `iosApp.xcodeproj`, select the `iosApp` target, then **Signing & Capabilities**.
3. Enable **Automatically manage signing** and select your Apple developer team. Xcode may write the
   team directly into project settings; alternatively set `TEAM_ID` in
   `Configuration/Config.xcconfig`.
4. If the bundle identifier is already taken, replace `dev.composescene3d.sample` in the xcconfig
   with a unique reverse-domain identifier.
5. Select the connected iPhone in the destination menu and press Run. On the phone, approve the
   developer certificate if iOS asks for it.

The standalone framework tasks remain available:

```shell
./gradlew :samples:ios-shared:linkDebugFrameworkIosSimulatorArm64
./gradlew :samples:ios-shared:linkDebugFrameworkIosArm64
```
