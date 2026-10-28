# R8 is on for release for speed, not size — see the note on isMinifyEnabled in
# build.gradle.kts. These rules keep it away from everything that finds classes
# by name at runtime, where a missing class shows up as a crash on a device
# rather than as a build error.

# Nothing is renamed. Stack traces stay readable, and every lookup by name —
# enum names in preferences, Media3 custom commands, library reflection — keeps
# working without a rule of its own.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,Exceptions,*Annotation*

# ---- Script engines -------------------------------------------------------------
# QuickJS runs module sources; jsoup parses lyrics and catalogue pages in shared.
-keep class org.jsoup.** { *; }
-keep class com.dokar.quickjs.** { *; }

# ---- Native and crypto --------------------------------------------------------
# ONNX Runtime and QuickJS are called from JNI, which R8 cannot see. SMBJ picks
# its BouncyCastle providers and event bus handlers reflectively.
-keep class ai.onnxruntime.** { *; }
-keep class com.hierynomus.** { *; }
-keep class net.engio.mbassy.** { *; }
-keep class org.bouncycastle.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }
# The vendored FFmpeg extension's JNI calls back into this by name to grow
# its output buffer (native/ffmpeg/ffmpeg_jni.cc, JNI_OnLoad).
-keep,includedescriptorclasses class androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder {
    private java.nio.ByteBuffer growOutputBuffer(androidx.media3.decoder.SimpleDecoderOutputBuffer, int);
}

# ---- Serialization --------------------------------------------------------------
# protobuf-lite reads its generated messages' fields reflectively.
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
# Ktor resolves serializers and engine pieces by type at runtime.
-keep class io.ktor.** { *; }
# Our own @Serializable models, whole: kotlinx.serialization's bundled rules
# cover the generated serializers, and keeping the classes too costs little.
-keep @kotlinx.serialization.Serializable class com.music.bitchord.** { *; }
-keep @kotlinx.serialization.Serializable class com.my.kizzy.** { *; }

# ---- WebView bridges ------------------------------------------------------------
# The Spotify token page calls back into these from JavaScript.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---- JVM-only APIs ------------------------------------------------------------
# Referenced by desktop-only paths in mbassy's EL filters, SMBJ's Kerberos
# and Ktor's IntelliJ debugger probe. None
# of them exist on Android and none of those paths run here — the debug build,
# which R8 never touches, has always lived without them.
-dontwarn java.beans.**
-dontwarn java.lang.management.**
-dontwarn javax.el.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn org.ietf.jgss.**

# ---- "BitChord for Wear OS": the ADB client that installs the watch app ----
# libadb-android drives the pairing and transport through Conscrypt and SPAKE2,
# both of which reach their native halves by name.
-keep class io.github.muntashirakon.adb.** { *; }
-keep class org.conscrypt.** { *; }
-keep class com.android.org.conscrypt.** { *; }
-dontwarn org.conscrypt.**
-dontwarn com.android.org.conscrypt.**
-dontwarn io.github.muntashirakon.adb.**
