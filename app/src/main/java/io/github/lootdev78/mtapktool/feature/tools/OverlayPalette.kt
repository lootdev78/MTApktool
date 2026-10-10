package io.github.lootdev78.mtapktool.feature.tools

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.widget.TextView

data class OverlayPalette(val surface: Int, val text: Int, val accent: Int) {
    fun background() = GradientDrawable().apply { setColor(surface); cornerRadius = 4f; setStroke(1, accent) }
    fun label(context: Context, title: String, action: (() -> Unit)? = null) = TextView(context).apply {
        text = title; setTextColor(textColor()); textSize = 13f; setPadding(14, 12, 14, 12)
        if (action != null) { isClickable = true; setOnClickListener { action() } }
    }
    private fun textColor() = text
    companion object {
        fun read(context: Context): OverlayPalette {
            val preferences = context.getSharedPreferences("mt_tools_palette", 0)
            return OverlayPalette(preferences.getInt("surface", 0xff303030.toInt()), preferences.getInt("text", 0xffe0e0e0.toInt()), preferences.getInt("accent", 0xff2196f3.toInt()))
        }
    }
}
