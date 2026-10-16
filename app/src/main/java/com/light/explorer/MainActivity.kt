package com.light.explorer

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.light.explorer.databinding.ActivityMainBinding
import java.io.File
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private var cur: File = File("/storage/emulated/0")
    private var items: List<File> = emptyList()
    private val selected = mutableSetOf<String>()
    private var clip: List<File> = emptyList()
    private var clipMove = false
    private var searchJob: Thread? = null
    private lateinit var adapter: FileAdapter

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        adapter = FileAdapter()
        b.rv.layoutManager = LinearLayoutManager(this)
        b.rv.adapter = adapter
        b.rv.setHasFixedSize(true)

        if (!Environment.getExternalStorageDirectory().exists()) {
            cur = filesDir
        }
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (_: Exception) {}
        }

        b.btnUp.setOnClickListener { up() }
        b.btnSelectAll.setOnClickListener { selectAll() }
        b.btnAnalysis.setOnClickListener {
            startActivity(Intent(this, AnalysisActivity::class.java).putExtra("path", cur.absolutePath))
        }
        b.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(a: CharSequence?, s: Int, c: Int, d: Int) {}
            override fun onTextChanged(a: CharSequence?, s: Int, e: Int, c: Int) {}
            override fun afterTextChanged(e: android.text.Editable?) {
                val q = e.toString().trim()
                searchJob?.interrupt()
                if (q.isEmpty()) { load() ; return }
                val root = cur
                searchJob = thread {
                    try {
                        Thread.sleep(300)
                        val res = FileOps.fastSearch(root, q)
                        runOnUiThread { items = res; selected.clear(); adapter.notify(); updateCount() }
                    } catch (_: InterruptedException) {}
                }
            }
        })
        buildBottom()
        load()
    }

    override fun onResume() { super.onResume(); load() }
    override fun onBackPressed() {
        if (b.etSearch.text.isNotEmpty()) { b.etSearch.setText(""); return }
        if (selected.isNotEmpty()) { selected.clear(); adapter.notify(); updateCount(); return }
        if (!up()) super.onBackPressed()
    }

    private fun up(): Boolean {
        val p = cur.parentFile ?: return false
        if (p.absolutePath.length < 2) return false
        cur = p
        b.etSearch.setText("")
        load()
        return true
    }

    private fun load() {
        thread {
            val list = try { FileOps.listDir(cur) } catch (_: Exception) { emptyList() }
            runOnUiThread {
                if (b.etSearch.text.isEmpty()) { items = list }
                selected.clear()
                b.tvPath.text = cur.absolutePath.lowercase()
                adapter.notify()
                updateCount()
            }
        }
    }

    private fun updateCount() {
        b.tvCount.text = if (selected.isEmpty()) "${items.size} объектов" else "выбрано: ${selected.size}"
        buildBottom()
    }

    private fun selectAll() {
        if (selected.size == items.size) selected.clear()
        else { selected.clear(); items.forEach { selected.add(it.absolutePath) } }
        adapter.notify(); updateCount()
    }

    private fun buildBottom() {
        b.bottomBar.removeAllViews()
        val hasSel = selected.isNotEmpty()
        val defs = mutableListOf<Pair<String, () -> Unit>>()
        defs.add("создать" to { dlgCreate() })
        defs.add("папка" to { dlgMkdir() })
        if (hasSel) {
            defs.add("копировать" to { copySel(false) })
            defs.add("переместить" to { copySel(true) })
            defs.add("удалить" to { dlgDelete() })
            defs.add("переименовать" to { dlgRename() })
            defs.add("сжать" to { dlgZip() })
        }
        if (clip.isNotEmpty()) defs.add("вставить" to { paste() })
        if (!hasSel) {
            defs.add("анализ" to {
                startActivity(Intent(this, AnalysisActivity::class.java).putExtra("path", cur.absolutePath))
            })
        }
        defs.forEach { (t, fn) ->
            val btn = Button(this, null, android.R.attr.borderlessButtonStyle)
            btn.text = t
            btn.isAllCaps = false
            btn.textSize = 13f
            btn.setTextColor(0xFFD32F2F.toInt())
            btn.setBackgroundColor(0x00000000)
            btn.stateListAnimator = null
            btn.elevation = 0f
            btn.setOnClickListener { fn() }
            b.bottomBar.addView(btn)
        }
    }

    private fun selFiles(): List<File> = items.filter { selected.contains(it.absolutePath) }

    private fun dlgCreate() {
        val et = EditText(this); et.hint = "имя файла"; et.textSize = 14f
        AlertDialog.Builder(this).setTitle("новый файл").setView(et)
            .setPositiveButton("создать") { _, _ ->
                val n = et.text.toString().trim(); if (n.isEmpty()) return@setPositiveButton
                thread { File(cur, n).createNewFile(); runOnUiThread { load() } }
            }.setNegativeButton("отмена", null).show()
    }

    private fun dlgMkdir() {
        val et = EditText(this); et.hint = "имя папки"; et.textSize = 14f
        AlertDialog.Builder(this).setTitle("новая папка").setView(et)
            .setPositiveButton("создать") { _, _ ->
                val n = et.text.toString().trim(); if (n.isEmpty()) return@setPositiveButton
                thread { File(cur, n).mkdirs(); runOnUiThread { load() } }
            }.setNegativeButton("отмена", null).show()
    }

    private fun dlgRename() {
        val f = selFiles().firstOrNull() ?: return
        val et = EditText(this); et.setText(f.name); et.textSize = 14f
        AlertDialog.Builder(this).setTitle("переименовать").setView(et)
            .setPositiveButton("ок") { _, _ ->
                val n = et.text.toString().trim(); if (n.isEmpty()) return@setPositiveButton
                thread { f.renameTo(File(f.parent, n)); runOnUiThread { load() } }
            }.setNegativeButton("отмена", null).show()
    }

    private fun dlgDelete() {
        val fs = selFiles(); if (fs.isEmpty()) return
        AlertDialog.Builder(this).setTitle("удалить ${fs.size} объектов?")
            .setPositiveButton("удалить") { _, _ ->
                thread { fs.forEach { FileOps.deleteRecursive(it) }; runOnUiThread { load() } }
            }.setNegativeButton("отмена", null).show()
    }

    private fun copySel(move: Boolean) {
        clip = selFiles(); clipMove = move
        toast(if (move) "вырезано: ${clip.size}" else "скопировано: ${clip.size}")
        buildBottom()
    }

    private fun paste() {
        val dst = cur; val src = clip; val mv = clipMove
        thread {
            src.forEach {
                try {
                    if (mv) FileOps.moveFile(it, File(dst, it.name))
                    else FileOps.copyFile(it, File(dst, it.name))
                } catch (e: Exception) { e.printStackTrace() }
            }
            runOnUiThread { clip = emptyList(); load() }
        }
    }

    private fun dlgZip() {
        val fs = selFiles(); if (fs.isEmpty()) return
        val et = EditText(this); et.setText("архив.zip"); et.textSize = 14f
        AlertDialog.Builder(this).setTitle("сжать в zip").setView(et)
            .setPositiveButton("сжать") { _, _ ->
                var n = et.text.toString().trim(); if (n.isEmpty()) n = "архив.zip"
                if (!n.endsWith(".zip")) n += ".zip"
                val out = File(cur, n)
                thread {
                    try { FileOps.zipFiles(fs, out) } catch (e: Exception) { e.printStackTrace() }
                    runOnUiThread { load() }
                }
            }.setNegativeButton("отмена", null).show()
    }

    private fun toast(t: String) = Toast.makeText(this, t.lowercase(), Toast.LENGTH_SHORT).show()

    private fun openFile(f: File) {
        val ext = FileOps.extOf(f.name)
        when {
            f.isDirectory -> { cur = f; b.etSearch.setText(""); load() }
            FileOps.isText(ext) || ext == "csv" -> {
                startActivity(Intent(this, TextViewerActivity::class.java).putExtra("path", f.absolutePath))
            }
            ext == "zip" -> dlgZipList(f)
            ext == "rar" -> dlgRarList(f)
            ext == "apk" -> dlgApk(f)
            ext == "pdf" -> openWith(f, "application/pdf")
            ext in setOf("png","jpg","jpeg","webp","gif","bmp") -> dlgImage(f)
            ext == "svg" -> dlgSvg(f)
            else -> openWith(f, null)
        }
    }

    private fun uriFor(f: File): Uri =
        try { FileProvider.getUriForFile(this, "com.light.explorer.provider", f) }
        catch (_: Exception) { Uri.fromFile(f) }

    private fun openWith(f: File, mime: String?) {
        try {
            val uri = uriFor(f)
            val it = Intent(Intent.ACTION_VIEW)
            val type = mime ?: contentResolver.getType(uri) ?: "*/*"
            it.setDataAndType(uri, type)
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(it, "открыть"))
        } catch (_: Exception) { toast("нет приложения для открытия") }
    }

    private fun dlgImage(f: File) {
        try {
            val opts = BitmapFactory.Options(); opts.inJustDecodeBounds = true
            BitmapFactory.decodeFile(f.absolutePath, opts)
            var sample = 1
            while (opts.outWidth / sample > 2048 || opts.outHeight / sample > 2048) sample *= 2
            val o2 = BitmapFactory.Options(); o2.inSampleSize = sample
            val bmp = BitmapFactory.decodeFile(f.absolutePath, o2) ?: run { openWith(f, "image/*"); return }
            val iv = ImageView(this); iv.setImageBitmap(bmp)
            iv.adjustViewBounds = true
            AlertDialog.Builder(this).setTitle(f.name.lowercase()).setView(iv)
                .setPositiveButton("открыть в системе") { _, _ -> openWith(f, "image/*") }
                .setNegativeButton("закрыть", null).show()
        } catch (_: Exception) { openWith(f, "image/*") }
    }

    private fun dlgSvg(f: File) {
        val wv = WebView(this); wv.settings.builtInZoomControls = true
        wv.loadUrl("file://" + f.absolutePath)
        AlertDialog.Builder(this).setTitle(f.name.lowercase()).setView(wv)
            .setPositiveButton("как текст") { _, _ ->
                startActivity(Intent(this, TextViewerActivity::class.java).putExtra("path", f.absolutePath))
            }
            .setNegativeButton("закрыть", null).show()
    }

    private fun dlgZipList(f: File) {
        thread {
            val names = try {
                java.util.zip.ZipFile(f).use { z -> z.entries().asSequence().map { it.name }.take(500).toList() }
            } catch (e: Exception) { listOf("ошибка: ${e.message}") }
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle(f.name.lowercase() + " — " + FileOps.formatSize(f.length()))
                    .setItems(names.toTypedArray(), null)
                    .setPositiveButton("распаковать") { _, _ ->
                        thread {
                            try { FileOps.unzip(f, File(cur, f.nameWithoutExtension)); runOnUiThread { load() } }
                            catch (e: Exception) { runOnUiThread { toast("ошибка распаковки") } }
                        }
                    }
                    .setNeutralButton("открыть как текст") { _, _ ->
                        startActivity(Intent(this, TextViewerActivity::class.java).putExtra("path", f.absolutePath))
                    }
                    .setNegativeButton("закрыть", null).show()
            }
        }
    }

    private fun dlgRarList(f: File) {
        thread {
            val names = try {
                com.github.junrar.Junrar.getContents(f)?.map { it.fileNameString }?.take(500) ?: emptyList()
            } catch (e: Exception) { listOf("ошибка: ${e.message}") }
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle(f.name.lowercase() + " — " + FileOps.formatSize(f.length()))
                    .setItems(names.toTypedArray(), null)
                    .setPositiveButton("распаковать") { _, _ ->
                        thread {
                            try {
                                com.github.junrar.Junrar.extract(f, File(cur, f.nameWithoutExtension))
                                runOnUiThread { load() }
                            } catch (e: Exception) { runOnUiThread { toast("ошибка распаковки rar") } }
                        }
                    }
                    .setNegativeButton("закрыть", null).show()
            }
        }
    }

    private fun dlgApk(f: File) {
        val pm = packageManager
        val info = try { pm.getPackageArchiveInfo(f.absolutePath, 0) } catch (_: Exception) { null }
        val label = info?.applicationInfo?.let {
            it.sourceDir = f.absolutePath; it.publicSourceDir = f.absolutePath
            try { pm.getApplicationLabel(it).toString() } catch (_: Exception) { f.name }
        } ?: f.name
        AlertDialog.Builder(this)
            .setTitle(label.lowercase())
            .setMessage("${f.name.lowercase()}\n${FileOps.formatSize(f.length())}\n${info?.packageName ?: ""}")
            .setPositiveButton("установить") { _, _ -> openWith(f, "application/vnd.android.package-archive") }
            .setNegativeButton("закрыть", null).show()
    }

    inner class FileAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<VH>() {
        fun notify() { notifyDataSetChanged() }
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(p: ViewGroup, v: Int): VH {
            val vw = LayoutInflater.from(p.context).inflate(R.layout.item_file, p, false)
            return VH(vw)
        }
        override fun onBindViewHolder(h: VH, pos: Int) {
            val f = items[pos]
            h.name.text = f.name.lowercase()
            val meta = if (f.isDirectory) "папка"
                else FileOps.formatSize(f.length()) + " · " + FileOps.extOf(f.name)
            h.meta.text = meta
            h.icon.setImageResource(if (f.isDirectory) R.drawable.ic_folder else R.drawable.ic_file)
            h.cb.setOnCheckedChangeListener(null)
            h.cb.isChecked = selected.contains(f.absolutePath)
            h.itemView.setBackgroundColor(if (h.cb.isChecked) 0xFFFFEBEE.toInt() else 0xFFFFFFFF.toInt())
            h.cb.setOnCheckedChangeListener { _, c ->
                if (c) selected.add(f.absolutePath) else selected.remove(f.absolutePath)
                h.itemView.setBackgroundColor(if (c) 0xFFFFEBEE.toInt() else 0xFFFFFFFF.toInt())
                updateCount()
            }
            h.itemView.setOnClickListener { openFile(f) }
            h.itemView.setOnLongClickListener {
                if (selected.contains(f.absolutePath)) selected.remove(f.absolutePath)
                else selected.add(f.absolutePath)
                notifyDataSetChanged(); updateCount(); true
            }
        }
    }

    class VH(v: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.tvName)
        val meta: TextView = v.findViewById(R.id.tvMeta)
        val icon: ImageView = v.findViewById(R.id.ivIcon)
        val cb: CheckBox = v.findViewById(R.id.cb)
    }
}
