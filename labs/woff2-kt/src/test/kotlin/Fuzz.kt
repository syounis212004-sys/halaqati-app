import halaqati.woff2.*
import java.io.File
fun main(a: Array<String>) {
    val src = File(a[0]).readBytes(); val rnd = java.util.Random(1)
    var ok = 0; var wx = 0; var bad = 0
    for (i in 0 until 3000) {
        val b = if (i % 2 == 0) src.copyOf(rnd.nextInt(src.size)) else src.clone().also { for (k in 0 until 3) it[rnd.nextInt(it.size)] = rnd.nextInt().toByte() }
        try { Woff2.decode(b); ok++ } catch (e: Woff2Exception) { wx++ } catch (e: Throwable) { bad++; if (bad < 5) println(e) }
    }
    println("decoded=$ok Woff2Exception=$wx otherThrowable=$bad")
}
