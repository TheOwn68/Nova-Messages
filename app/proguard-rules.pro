# Optimized rules to restore original 6.2MB size
# Removed broad keep rules that prevented shrinking

# EventBus
-keepattributes *Annotation*
-keepclassmembers class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.Entity
-keep class * extends androidx.room.Dao

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class org.nova.messages.models.** {
    *** Companion;
}
-keepclasseswithmembers class org.nova.messages.models.** {
    *** serializer(...);
}
