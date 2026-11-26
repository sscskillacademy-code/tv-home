package com.charan.glasshome

import android.service.notification.NotificationListenerService

/**
 * Android only lets an app see which music is playing (song, artist, cover) if it is an enabled
 * notification listener. This service exists for that permission alone; it reads no notifications.
 */
class MediaListener : NotificationListenerService()
