package com.xiaoyi.watch

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

object NoticePic {
    const val W = 216
    const val H = 104

    class Pack(val w: Int, val h: Int, val bytes: ByteArray)

    fun pack(app: String, body: String): Pack {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.BLACK)
        val name = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.SANS_SERIF
        }
        val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14f
        }
        val title = clip(app, 12).ifBlank { "訊息" }
        canvas.drawText(ellipsize(title, name, W - 8f), 4f, 22f, name)
        val lines = wrap(clip(body, 80), text, W - 8f).take(3)
        var y = 46f
        for (line in lines) {
            canvas.drawText(line, 4f, y, text)
            y += 20f
        }
        canvas.drawText("點一下關閉", 4f, 98f, hint)
        return Pack(W, H, packBits(bmp))
    }

    private fun packBits(bmp: Bitmap): ByteArray {
        val out = ByteArray(W * H / 8)
        val pixels = IntArray(W)
        var bit = 0
        for (y in 0 until H) {
            bmp.getPixels(pixels, 0, W, 0, y, W, 1)
            for (x in 0 until W) {
                val c = pixels[x]
                val lum = (Color.red(c) + Color.green(c) + Color.blue(c)) / 3
                if (lum > 80) out[bit shr 3] = (out[bit shr 3].toInt() or (0x80 shr (bit and 7))).toByte()
                bit++
            }
        }
        bmp.recycle()
        return out
    }

    private fun clip(s: String, n: Int): String {
        val t = s.replace('\n', ' ').trim()
        return if (t.length <= n) t else t.substring(0, n)
    }

    private fun ellipsize(s: String, paint: Paint, maxW: Float): String {
        if (paint.measureText(s) <= maxW) return s
        var t = s
        while (t.isNotEmpty() && paint.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    private fun wrap(s: String, paint: Paint, maxW: Float): List<String> {
        if (s.isBlank()) return listOf(" ")
        val lines = mutableListOf<String>()
        var cur = ""
        for (ch in s) {
            val next = cur + ch
            if (paint.measureText(next) > maxW && cur.isNotEmpty()) {
                lines.add(cur)
                cur = ch.toString()
            } else {
                cur = next
            }
        }
        if (cur.isNotEmpty()) lines.add(cur)
        return lines
    }
}
