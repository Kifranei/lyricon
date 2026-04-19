-dontwarn android.app.AndroidAppHelper
-dontwarn android.content.res.**
-dontwarn de.robv.android.xposed.**

-dontwarn javax.annotation.Nullable
-dontwarn java.lang.reflect.AnnotatedType

-keep class io.github.proify.lyricon.xposed.* { *; }
-keep class io.github.proify.lyricon.xposed.lyricon.** { *; }
-keep class io.github.proify.lyricon.xposed.systemui.hook.** { *; }

#
##opencc4j
#-keep class com.github.houbb.** { *; }
