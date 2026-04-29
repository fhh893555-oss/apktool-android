# Apktool runtime relies on reflection for YAML parsing (SnakeYAML-style) and
# for some smali grammar runtime classes. Keep their members so consumers'
# R8/D8 doesn't strip them.
-keep class brut.androlib.meta.** { *; }
-keep class org.yaml.snakeyaml.** { *; }

# Smali/baksmali ANTLR runtime grammar classes are loaded reflectively.
-keep class org.antlr.runtime.** { *; }
-keep class com.android.tools.smali.** { *; }

# Apktool exception types are used to signal callable failures - keep names
# stable for catch sites in host apps.
-keep class brut.common.BrutException { *; }
-keep class brut.androlib.exceptions.** { *; }

# The Android entry point.
-keep class brut.androlib.android.ApktoolAndroid { *; }
