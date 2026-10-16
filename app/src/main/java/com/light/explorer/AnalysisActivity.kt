package com.light.explorer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.light.explorer.databinding.ActivityAnalysisBinding
import java.io.File
import kotlin.concurrent.thread

class AnalysisActivity : AppCompatActivity() {

    private lateinit var b: ActivityAnalysisBinding
    private var rows: List<Pair<File, Long>> = emptyList()
    private var total = 0L

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityAnalysisBinding.inflate(layoutInflater)
        setContentView(b.root)
        val path = intent.getStringExtra("path") ?: "/storage/emulated/0"
        val dir = File(path)
        b.tvTitle.text = "анализ: ${dir.name.lowercase().ifEmpty { "хранилище" }}"
        b.tvTotal.text = "считаю..."
        b.rv.layoutManager = LinearLayoutManager(this)
        b.rv.adapter = Ad()
        thread {
            val kids = try { dir.listFiles()?.toList() ?: emptyList() } catch (_: Exception) { emptyList() }
            val sized = kids.map { it to FileOps.dirSize(it) }.sortedByDescending { it.second }
            total = sized.sumOf { it.second }
            rows = sized.take(50)
            runOnUiThread {
                b.tvTotal.text = "всего: ${FileOps.formatSize(total)} · объектов: ${kids.size}"
                b.rv.adapter?.notifyDataSetChanged()
            }
        }
    }

    inner class Ad : RecyclerView.Adapter<VH>() {
        override fun getItemCount() = rows.size
        override fun onCreateViewHolder(p: ViewGroup, v: Int): VH {
            val vw = LayoutInflater.from(p.context).inflate(R.layout.item_analysis, p, false)
            return VH(vw)
        }
        override fun onBindViewHolder(h: VH, pos: Int) {
            val (f, s) = rows[pos]
            h.name.text = f.name.lowercase()
            h.size.text = FileOps.formatSize(s)
            h.pb.max = 100
            h.pb.progress = if (total == 0L) 0 else ((s * 100 / total).toInt().coerceIn(1, 100))
        }
    }
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.tvName)
        val size: TextView = v.findViewById(R.id.tvSize)
        val pb: ProgressBar = v.findViewById(R.id.pb)
    }
}
