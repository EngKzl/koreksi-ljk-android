package net.pakeng.koreksiljk

import net.pakeng.koreksiljk.omr.Gray
import net.pakeng.koreksiljk.omr.Omr
import org.junit.Assert.assertEquals
import org.junit.Test

/** Uji mesin OMR dengan foto sintetis LJK sekolah (miring, terbalik, silang, lingkaran, coretan, ganda). */
class OmrTest {
    private fun load(name: String): Gray {
        // javax.imageio tidak ada di android.jar saat kompilasi, tapi tersedia di JVM saat uji
        val io = Class.forName("javax.imageio.ImageIO")
        val img = io.getMethod("read", java.io.InputStream::class.java).invoke(null, javaClass.classLoader!!.getResourceAsStream(name))!!
        val c = img.javaClass
        val w = c.getMethod("getWidth").invoke(img) as Int
        val h = c.getMethod("getHeight").invoke(img) as Int
        val px = c.getMethod("getRGB", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            IntArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(img, 0, 0, w, h, null, 0, w) as IntArray
        return Gray.fromArgb(w, h, px)
    }

    private fun check(seed: Int): Int {
        val ans = javaClass.classLoader!!.getResourceAsStream("ljk$seed.txt")!!.bufferedReader().readLines().filter { it.isNotBlank() }
            .map { val p = it.split(" "); Pair(if (p[0] == "-") null else p[0], p[1]) }
        val r = Omr.readSheet(load("ljk$seed.jpg"), ans.size)
        assert(r.ok) { "seed $seed: ${r.error}" }
        var err = 0
        ans.forEachIndexed { i, (a, s) ->
            val g = r.answers[i]
            val gs = if (g.status == "ragu") "ok" else g.status
            if (g.choice != a || gs != s) { err++; println("seed $seed q${i + 1}: $a/$s -> ${g.choice}/${g.status} ${g.marks}") }
        }
        return err
    }

    @Test
    fun readsSyntheticSheets() {
        var total = 0
        for (s in listOf(13, 15, 16)) total += check(s)
        assertEquals("salah baca", 0, total)
    }
}
