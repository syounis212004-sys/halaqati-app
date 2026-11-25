import halaqati.woff2.Woff2
import java.io.File
fun main(a: Array<String>) {
    val r = Woff2.decode(File(a[0]).readBytes()); File(a[1]).writeBytes(r)
}
