package com.light.explorer

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.light.explorer.databinding.ActivityTextBinding
import java.io.File
import kotlin.concurrent.thread

class TextViewerActivity : AppCompatActivity() {

    private lateinit var b: ActivityTextBinding
    private lateinit var file: File
    private var offset = 0L
    private var total = 0L
    private var editable = false
    private val page = 128 * 1024

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityTextBinding.inflate(layoutInflater)
        setContentView(b.root)
        val p = intent.getStringExtra("path") ?: run { finish(); return }
        file = File(p)
        b.tvTitle.text = file.name.lowercase()
        // редактирование только для файлов до 2 мб (иначе постраничный просмотр до 3 гб)
        editable = file.length() <= 2 * 1024 * 1024
        b.etContent.isFocusable = editable
        b.etContent.isFocusableInTouchMode = editable
        b.btnSave.visibility = if (editable) android.view.View.VISIBLE else android.view.View.GONE
        loadPage(0)
        b.btnNext.setOnClickListener { loadPage(offset + page) }
        b.btnPrev.setOnClickListener { loadPage(offset - page) }
        b.btnSave.setOnClickListener { save() }
    }

    private fun loadPage(off: Long) {
        if (!::file.isInitialized) return
        thread {
            try {
                val (text, len) = FileOps.readPage(file, off, page)
                total = len
                offset = off.coerceIn(0, maxOf(0, len - 1))
                runOnUiThread {
                    b.etContent.setText(text)
                    val pg = if (total == 0L) "пусто" else "позиция ${FileOps.formatSize(offset)} / ${FileOps.formatSize(total)}"
                    b.tvPage.text = if (editable) "${FileOps.formatSize(total)} · редактирование включено" else pg
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "ошибка чтения", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun save() {
        thread {
            try {
                file.writeText(b.etContent.text.toString())
                runOnUiThread { Toast.makeText(this, "сохранено", Toast.LENGTH_SHORT).show() }
            } catch (_: Exception) {
                runOnUiThread { Toast.makeText(this, "ошибка сохранения", Toast.LENGTH_SHORT).show() }
            }
        }
    }
}
