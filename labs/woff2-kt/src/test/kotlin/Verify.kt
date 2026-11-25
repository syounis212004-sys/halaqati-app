import halaqati.woff2.Woff2
import java.io.File
import java.lang.management.ManagementFactory

fun main(args: Array<String>) {
    val dir = File(args[0]); val refDir = File(args[1]); val rounds = args.getOrNull(2)?.toInt() ?: 3
    val files = dir.listFiles { f -> f.name.endsWith(".woff2") }!!.sortedBy { it.name }
    val tmx = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    val tid = Thread.currentThread().id
    var ok = 0; val fails = ArrayList<String>()
    val times = ArrayList<Double>(); var maxAlloc = 0L; var maxName = ""
    val perFile = StringBuilder("file,woff2_bytes,ttf_bytes,best_ms,alloc_bytes,identical\n")
    for (f in files) {
        val src = f.readBytes(); val ref = File(refDir, f.name.replace(".woff2", ".ttf")).readBytes()
        var best = Double.MAX_VALUE; var res: ByteArray? = null; var err: String? = null; var alloc = 0L
        for (r in 0 until rounds) {
            try {
                val a0 = tmx.getThreadAllocatedBytes(tid); val t0 = System.nanoTime()
                res = Woff2.decode(src)
                val dt = (System.nanoTime() - t0) / 1e6; alloc = tmx.getThreadAllocatedBytes(tid) - a0
                if (dt < best) best = dt
            } catch (e: Exception) { err = e.toString(); break }
        }
        val same = res != null && res.contentEquals(ref)
        if (same) ok++ else fails.add(f.name + " " + (err ?: "differs (len ${res?.size} vs ${ref.size})"))
        times.add(best); if (alloc > maxAlloc) { maxAlloc = alloc; maxName = f.name }
        perFile.append("${f.name},${src.size},${ref.size},${"%.2f".format(best)},$alloc,$same\n")
    }
    File(args.getOrElse(3) { "results.csv" }).writeText(perFile.toString())
    val s = times.sorted()
    println("files=${files.size} identical=$ok failed=${fails.size}")
    fails.take(20).forEach { println("FAIL $it") }
    println("best-of-$rounds ms: min=%.2f median=%.2f mean=%.2f p95=%.2f max=%.2f total=%.0f".format(s.first(), s[s.size / 2], s.average(), s[(s.size * 0.95).toInt()], s.last(), s.sum()))
    println("max thread-allocated bytes per decode: $maxAlloc ($maxName)")
    val rt = Runtime.getRuntime(); println("maxMemory=${rt.maxMemory() shr 20}MB")
}
