-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn java.awt.Dimension
-dontwarn java.awt.Rectangle
-dontwarn javax.xml.bind.DatatypeConverter

# sherpa-onnx native JNI reads Kotlin config objects and fields by their
# original names. R8 obfuscation breaks OfflineTtsConfig.model and aborts ART.
-keep class com.k2fsa.sherpa.onnx.** { *; }
