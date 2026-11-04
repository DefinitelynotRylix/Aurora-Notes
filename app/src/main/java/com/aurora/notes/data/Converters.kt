package com.aurora.notes.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.aurora.notes.model.ChecklistItem
import com.aurora.notes.model.NoteImage

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromChecklist(list: List<ChecklistItem>?): String {
        return gson.toJson(list ?: emptyList<ChecklistItem>())
    }

    @TypeConverter
    fun toChecklist(json: String?): List<ChecklistItem> {
        if (json.isNullOrEmpty()) return emptyList()
        val type = object : TypeToken<List<ChecklistItem>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    @TypeConverter
    fun fromNoteImages(images: List<NoteImage>?): String {
        return gson.toJson(images ?: emptyList<NoteImage>())
    }

    @TypeConverter
    fun toNoteImages(json: String?): List<NoteImage> {
        if (json.isNullOrEmpty()) return emptyList()
        val type = object : TypeToken<List<NoteImage>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    @TypeConverter
    fun fromTags(tags: List<String>?): String {
        return gson.toJson(tags ?: emptyList<String>())
    }

    @TypeConverter
    fun toTags(json: String?): List<String> {
        if (json.isNullOrEmpty()) return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
}

