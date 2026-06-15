package edu.bluejack25_2.hwixel.data.source.local

import androidx.room.TypeConverter
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class ListStringConverter {

    @TypeConverter
    fun fromList(values: List<String>): String {
        return values.joinToString(separator = "\n") { value ->
            URLEncoder.encode(value, StandardCharsets.UTF_8.name())
        }
    }

    @TypeConverter
    fun toList(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return value.split("\n").map { encodedValue ->
            URLDecoder.decode(encodedValue, StandardCharsets.UTF_8.name())
        }
    }
}
