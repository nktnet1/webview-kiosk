# Only smokeRelease includes this file. The instrumentation APK is compiled
# against unminified Kotlin APIs but runs inside the R8-minified app process.
# Keep the binary entry points called by the test APK without retaining these
# test-only APIs in production release builds.
-keep class kotlinx.coroutines.BuildersKt { *; }
-keep class kotlinx.coroutines.CoroutineScope { *; }
-keep class kotlinx.coroutines.CoroutineStart { *; }
-keep class kotlinx.coroutines.Job { *; }
-keep class kotlinx.coroutines.flow.FlowCollector { *; }

# Kotlin's public facade classes inherit implementations from generated
# superclasses. Preserving just the facades would still allow R8 to remove
# their inherited methods, producing NoSuchMethodError in the test APK.
-keep class kotlin.ResultKt {
    public static void throwOnFailure(java.lang.Object);
}
-keep class kotlin.Unit { *; }
-keep class kotlin.coroutines.intrinsics.IntrinsicsKt { *; }
-keep class kotlin.coroutines.intrinsics.IntrinsicsKt__IntrinsicsKt {
    public static java.lang.Object getCOROUTINE_SUSPENDED();
}
-keep class kotlin.coroutines.Continuation { *; }
-keep class kotlin.coroutines.CoroutineContext { *; }
-keep class kotlin.coroutines.EmptyCoroutineContext { *; }
-keep class kotlin.text.StringsKt { *; }
-keep class kotlin.text.StringsKt__StringsKt {
    public static boolean isBlank(java.lang.CharSequence);
    public static java.lang.String removePrefix(java.lang.String, java.lang.CharSequence);
}
-keep class kotlin.collections.CollectionsKt { *; }
-keep class kotlin.collections.CollectionsKt___CollectionsKt {
    public static java.lang.Object firstOrNull(java.util.List);
    public static java.util.List drop(java.lang.Iterable, int);
}
