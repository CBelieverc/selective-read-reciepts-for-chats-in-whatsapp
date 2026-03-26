package com.whatsapp.selectivereads.data

import androidx.room.TypeConverter

class Converters {

    @TypeConverter
    fun fromMessageStatus(status: MessageStatus): String = status.name

    @TypeConverter
    fun toMessageStatus(status: String): MessageStatus = MessageStatus.valueOf(status)
}
