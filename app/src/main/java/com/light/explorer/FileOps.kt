package com.light.explorer

import java.io.*
import java.nio.channels.FileChannel
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object FileOps {

    val textExt = setOf(
        "txt","java","csv","svg","py","go","rs","cpp","c","h","hpp",
        "kt","kts","js","ts","html","css","xml","json","md","log",
        "properties","gradle","sh","yml","yaml","toml","ini","sql","r","dart","lua"
    )

    fun extOf(name: String): String {
        val i = name.lastIndexOf('.')
        if (i < 0) return ""
        return name.substring(i + 1).lowercase()
    }

    fun isText(ext: String) = textExt.contains(ext)

    fun formatSize(s: Long): String {
        if (s < 1024) return "$s б"
        val kb = s / 1024.0
        if (kb < 1024) return String.format("%.1f кб", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f мб", mb)
        val gb = mb / 1024.0
        return String.format("%.2f гб", gb)
    }

    fun listDir(dir: File): List<File> {
        val arr = dir.listFiles() ?: return emptyList()
        return arr.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun copyFile(src: File, dst: File) {
        if (src.isDirectory) {
            dst.mkdirs()
            src.listFiles()?.forEach { copyFile(it, File(dst, it.name)) }
            return
        }
        dst.parentFile?.mkdirs()
        FileInputStream(src).channel.use { sc ->
            FileOutputStream(dst).channel.use { dc ->
                var pos = 0L
                val size = sc.size()
                while (pos < size) {
                    val n = sc.transferTo(pos, minOf(16 * 1024 * 1024L, size - pos), dc)
                    if (n <= 0) break
                    pos += n
                }
            }
        }
        dst.setLastModified(src.lastModified())
    }

    fun moveFile(src: File, dst: File): Boolean {
        if (src.renameTo(dst)) return true
        copyFile(src, dst)
        deleteRecursive(src)
        return true
    }

    fun deleteRecursive(f: File) {
        if (f.isDirectory) f.listFiles()?.forEach { deleteRecursive(it) }
        f.delete()
    }

    fun dirSize(f: File, depth: Int = 8): Long {
        if (!f.exists()) return 0
        if (f.isFile) return f.length()
        if (depth < 0) return 0
        var s = 0L
        try {
            f.listFiles()?.forEach { s += dirSize(it, depth - 1) }
        } catch (_: Exception) {}
        return s
    }

    fun zipFiles(sources: List<File>, outZip: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outZip))).use { zos ->
            sources.forEach { addToZip(it, "", zos) }
        }
    }

    private fun addToZip(f: File, base: String, zos: ZipOutputStream) {
        val entryName = if (base.isEmpty()) f.name else "$base/${f.name}"
        if (f.isDirectory) {
            f.listFiles()?.forEach { addToZip(it, entryName, zos) }
        } else {
            zos.putNextEntry(ZipEntry(entryName))
            FileInputStream(f).use { it.copyTo(zos, 64 * 1024) }
            zos.closeEntry()
        }
    }

    fun unzip(zip: File, dest: File) {
        ZipFile(zip).use { zf ->
            zf.entries().asSequence().forEach { e ->
                val out = File(dest, e.name)
                if (e.isDirectory) out.mkdirs()
                else {
                    out.parentFile?.mkdirs()
                    zf.getInputStream(e).use { ins ->
                        FileOutputStream(out).use { outs -> ins.copyTo(outs) }
                    }
                }
            }
        }
    }

    fun readPage(f: File, offset: Long, size: Int): Pair<String, Long> {
        RandomAccessFile(f, "r").use { raf ->
            val len = raf.length()
            val off = offset.coerceIn(0, maxOf(0, len - 1))
            raf.seek(off)
            val buf = ByteArray(minOf(size, (len - off).toInt().coerceAtLeast(0)))
            if (buf.isEmpty()) return "" to len
            val n = raf.read(buf)
            val text = try { String(buf, 0, n, Charsets.UTF_8) }
            catch (_: Exception) { String(buf, 0, n, Charsets.ISO_8859_1) }
            return text to len
        }
    }

    fun fastSearch(root: File, query: String, maxRes: Int = 500): List<File> {
        val q = query.lowercase()
        val out = ArrayList<File>(maxRes)
        val stack = ArrayDeque<File>()
        stack.add(root)
        var scanned = 0
        while (stack.isNotEmpty() && out.size < maxRes && scanned < 20000) {
            val d = stack.removeLast()
            val kids = try { d.listFiles() } catch (_: Exception) { null } ?: continue
            for (k in kids) {
                scanned++
                if (k.name.lowercase().contains(q)) {
                    out.add(k)
                    if (out.size >= maxRes) break
                }
                if (k.isDirectory && scanned < 20000) stack.add(k)
            }
        }
        out.sortWith(compareBy({ !it.name.lowercase().startsWith(q) }, { !it.isDirectory }, { it.name.lowercase() }))
        return out
    }
}
