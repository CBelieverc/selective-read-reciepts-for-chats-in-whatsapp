# Add project specific ProGuard rules here.

# Keep Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep data classes
-keep class com.whatsapp.selectivereads.data.** { *; }

# Keep NotificationListenerService
-keep class * extends android.service.notification.NotificationListenerService { *; }
