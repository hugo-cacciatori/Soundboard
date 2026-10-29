# Kotlin's multifile facades (e.g. CollectionsKt) are chains of package-private classes. When R8
# moved one part to another package, the next part could no longer extend it and the release
# crashed at launch (IllegalAccessError). Keep Kotlin's classes in their own packages.
-keeppackagenames kotlin.**
