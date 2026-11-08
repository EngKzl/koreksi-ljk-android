package net.pakeng.koreksiljk

import net.pakeng.koreksiljk.omr.Gray
import net.pakeng.koreksiljk.omr.Omr
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.imageio.ImageIO

/** Uji mesin OMR dengan foto LJK sintetis (miring, diputar, coretan, ganda). */
class OmrTest {
    private fun load(name: String): Gray {
        val img = ImageIO.read(javaClass.classLoader!!.getResourceAsStream(name))
        val px = img.getRGB(0, 0, img.width, img.height, null, 0, img.width)
        return Gray.fromArgb(img.width, img.height, px)
    }

    private fun check(seed: Int): Int {
        val truth = javaClass.classLoader!!.getResourceAsStream("truth$seed.txt")!!.bufferedReader().readLines().filter { it.isNotBlank() }
        val absen = truth[0].toInt(); val paket = truth[1]
        val ans = truth.drop(2).map { val p = it.split(" "); Pair(if (p[0] == "-") null else p[0], p[1]) }
        val r = Omr.readSheet(load("img$seed.jpg"), ans.size, 3)
        assert(r.ok) { "seed $seed: ${r.error}" }
        assertEquals("absen seed $seed", absen, r.absen)
        assertEquals("paket seed $seed", paket, r.paket)
        var err = 0
        ans.forEachIndexed { i, (a, s) ->
            val g = r.answers[i]
            if (g.choice != a || (s == "ganda") != (g.status == "ganda")) { err++; println("seed $seed q${i + 1}: $a/$s -> ${g.choice}/${g.status} ${g.marks}") }
        }
        return err
    }

    @Test
    fun readsSyntheticSheets() {
        var total = 0
        for (s in listOf(2, 3, 4, 13, 19)) total += check(s)
        assertEquals("salah baca", 0, total)
    }
}
