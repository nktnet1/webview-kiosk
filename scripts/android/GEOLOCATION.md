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

## References

- [Chromium Android location provider](https://chromium.googlesource.com/chromium/src/+/6fe5c1ade0995e96eab84defe8871b7fa19ad15f/services/device/geolocation/android/java/src/org/chromium/device/geolocation/LocationProviderAndroid.java)
- [Chrome provider selection](https://chromium.googlesource.com/chromium/src/+/HEAD/chrome/browser/chrome_content_browser_client.cc)
- [Android LocationManager Criteria requests](https://developer.android.com/reference/android/location/LocationManager#requestLocationUpdates(long,float,android.location.Criteria,android.location.LocationListener,android.os.Looper))
- [Geolocation watch identifiers and timeout errors](https://www.w3.org/TR/geolocation/)
