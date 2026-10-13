# Tele: release builds use R8 (isMinifyEnabled). Firebase and AndroidX ship their own consumer rules.
# Add -keep rules here only if a release build crashes at runtime.

# Keep the data models (field names are used when data is read back and logged).
-keep class com.tele.child.Models** { *; }
