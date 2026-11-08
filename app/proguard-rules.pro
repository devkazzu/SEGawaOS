# MIO OS — R8 rules
#
# Manifest-declared components (MioApp, MainActivity, MusicService,
# MioNotificationListener, MioDeviceAdmin) are kept automatically by AGP.
# The launcher uses no reflection over its own classes, so nothing else needs
# an explicit keep rule.

-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Coil / OkHttp / Okio pull in optional platform hooks that R8 cannot see.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.jetbrains.annotations.**
-dontwarn kotlinx.coroutines.debug.**
