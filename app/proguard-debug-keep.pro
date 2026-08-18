# Debug build only: run R8 to avoid AGP's per-class dexing transform dropping a class
# from the core:model java-library (see PlaybackSource NoClassDefFoundError).
# Keep everything, do not rename, do not optimize — behaviorally equivalent to d8.
-dontshrink
-dontobfuscate
-dontoptimize
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile,LineNumberTable
# Debug only: d8 does not enforce the missing-class check, so ignore it here.
-ignorewarnings
