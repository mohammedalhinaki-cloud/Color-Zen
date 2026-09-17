# ---------------------------------------------------------------------------
# Color Zen - R8 / ProGuard rules
#
# The app is pure Kotlin + Jetpack Compose with no reflection-based JSON
# mapping (org.json is used explicitly), so almost nothing needs keeping.
# Compose, AndroidX and the Play Billing Library all ship their own consumer
# rules.
# ---------------------------------------------------------------------------

# Keep line numbers + source file for readable crash reports in Play Console.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Google Play Billing: the library ships consumer rules, but keep the client
# entry points and AIDL interfaces explicitly - billing goes through binder and
# obfuscating these has historically broken purchases.
-keep class com.android.billingclient.api.** { *; }
-keep class com.android.vending.billing.** { *; }
-dontwarn com.android.billingclient.**
-dontwarn com.android.vending.billing.**

# Kotlin metadata / coroutines
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Enum values()/valueOf() used by the difficulty + product catalogs.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Parcelable / Serializable data classes are not used across process
# boundaries, but keep the standard creators cheaply.
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
