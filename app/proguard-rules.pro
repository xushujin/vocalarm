# No reflection-based serialization or class lookup is used: alarms are parsed field by field,
# ViewModels are created by lambdas, and every manifest component is kept by AGP's manifest rules.
# These attributes keep crash line numbers usable together with mapping.txt.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx-coroutines resolves the Android Main dispatcher through ServiceLoader. Explicit keeps
# stop R8 from merging the service interface into its provider (which would break Dispatchers.Main).
-keep class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
-keep class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
