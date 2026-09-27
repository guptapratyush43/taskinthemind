# PDFBox reflects over COS object types and font resources.
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# BouncyCastle registers algorithms by reflection.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# PDFBox references desktop-only APIs that are absent on Android but never hit.
-dontwarn java.awt.**
-dontwarn javax.imageio.**
