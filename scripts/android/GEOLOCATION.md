# Location failures after head unit sleep

The foreground lifecycle controller synchronises WebView pause and resume with
the screen and Activity. Its unit tests verify those transitions, not real GPS
delivery. A passing lifecycle suite does not establish that issue #388 is fixed.

The supplied report shows `document.visibilityState` as `visible` and both
`getCurrentPosition` and `watchPosition` reporting error 3, `Timeout expired`.
The watch method's immediate numeric return is a watch identifier, not a fix.
The website's offline weather can come from saved data.

Chromium's Android location provider uses the Criteria overload of
`LocationManager.requestLocationUpdates`. Android routes Criteria requests through
its fused provider from KitKat onwards. Chrome can use a Google Play Services
provider instead. Browser success alone does not identify which native path works.

## Collect evidence on the affected device

Use the debug APK's **Settings → About → Location diagnostics**. Debug and release
have different package names, app data and Android permissions. Enable
**Settings → Device → Allow Location** and grant precise location in the same APK
being tested. First reproduce the web failure in that APK after sleep.

1. Open diagnostics before rebooting or changing Android location settings
2. Run **Test Android Criteria provider** and wait for its result
3. Run **Test GPS directly** and wait for its result
4. Copy the report and retest the website after returning

Each test waits up to 60 seconds for a fix timestamped at or after registration.
A cached callback is reported but does not count as recovery. Switching tests,
pausing the diagnostic Activity or reaching the deadline unregisters its listener.
No coordinates are copied, logged or passed into the website.

| Criteria test | Direct GPS test | What it establishes |
| --- | --- | --- |
| Timeout | Fresh fix | Direct GPS works while the Criteria path failed in this run |
| Fresh fix | Fresh fix | Native requests work in this app at test time, investigate WebView and site behaviour next |
| Timeout | Timeout | Neither native request received a fresh fix within the test window, inspect Android providers and GNSS conditions |
| Blocked or unavailable | Any | Resolve the reported app permission or missing provider before interpreting a timeout |

These are sequential observations, not proof of a firmware defect. Starting a
native request can itself wake GPS and change the state being investigated.
Record whether the website starts working after either test. A cold satellite
fix can also take longer than the test window. These diagnostics deliberately do
not install a replacement `navigator.geolocation`, change the app's provider or
claim recovery without a device test.

## Direct GPS workaround for the confirmed provider split

The follow-up report on Android 10 with WebView 157 showed the Criteria request
reporting `Provider disabled: fused` and timing out after 60 seconds. A subsequent
GPS request obtained a fresh fix in about 625 ms, with a 5 ms fix age and 7.29 m
accuracy. Before the failure, both paths returned fresh fixes. This identifies a
usable alternative provider on that device, without establishing the firmware
cause of the fused-provider failure.

The opt-in **Settings → Device → Use Direct GPS** backend sends real GPS fixes
to the HTTPS main page. It does not depend on opening diagnostics, network
connectivity or obtaining a fused-provider fix. The diagnostic tests remain
independent and do not feed the page.

### Enable and verify on the affected head unit

1. Install the build containing the native GPS backend in the same app variant
   being tested
2. Enable **Allow Location** and **Use Direct GPS**, grant precise Android
   location permission and enable the device's GPS
3. Restart the app, open the HTTPS site and allow its location prompt
4. Confirm that a website `watchPosition` callback receives a fresh timestamp,
   coordinates and accuracy, rather than only returning a numeric watch ID
5. Put the head unit to sleep, wake it and repeat several times without opening
   diagnostics or toggling Android location
6. Confirm that tracking resumes after every wake, including with Background
   Media Playback enabled, and that no callbacks arrive while the app is hidden
7. Check `clearWatch`, navigation to another origin, site denial and revoking
   precise Android permission. Each must stop the relevant location delivery
8. Disable **Use Direct GPS** and restart to compare standard WebView behaviour

Allow time and satellite visibility for a genuine fix. Native unit tests and
JavaScript tests cannot establish recovery on the physical head unit. Do not
close issue #388 until this device acceptance test passes.

### Permission and lifetime boundaries

The document-start compatibility script keeps the browser's original
`watchPosition` solely long enough to obtain the real WebView permission result.
No fix from that request is required. The existing app setting, runtime Android
permissions, site dialog and Chromium Permissions Policy checks remain in the
permission path. Only the native permission callback can authorize the backend.
Every new request rechecks app and precise Android permissions before using a
cached fix. Android permissions are checked again on every GPS callback.

Native messages must come from the HTTPS main frame at the current page origin
and match its reply endpoint and session token. Responses use that document's
`JavaScriptReplyProxy`. Navigation, release, renderer death and disposal remove
its listener and invalidate its session. GPS callbacks from an old listener,
cached fixes predating registration and invalid or future timestamps are ignored.
No coordinates are logged or added to diagnostic reports.

## References

- [Chromium Android location provider](https://chromium.googlesource.com/chromium/src/+/6fe5c1ade0995e96eab84defe8871b7fa19ad15f/services/device/geolocation/android/java/src/org/chromium/device/geolocation/LocationProviderAndroid.java)
- [Chrome provider selection](https://chromium.googlesource.com/chromium/src/+/HEAD/chrome/browser/chrome_content_browser_client.cc)
- [Android LocationManager Criteria requests](https://developer.android.com/reference/android/location/LocationManager#requestLocationUpdates(long,float,android.location.Criteria,android.location.LocationListener,android.os.Looper))
- [Geolocation watch identifiers and timeout errors](https://www.w3.org/TR/geolocation/)
