package com.mohammedsamir.halaqati.documents

import android.content.Context
import com.mohammedsamir.halaqati.R
import com.mohammedsamir.halaqati.ui.screens.NativeCertificateAssets
import com.mohammedsamir.halaqati.ui.screens.NativeCertificateDraft
import com.mohammedsamir.halaqati.ui.screens.NativeCertificateStyle
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/** HTML/CSS certificate renderer restored from the proven v2.44 document engine. */
object CertificateHtmlRenderer {
    fun build(context: Context, draft: NativeCertificateDraft, assets: NativeCertificateAssets): String {
        val css = context.assets.open("documents/certificate.css").bufferedReader().use { it.readText() }
        val fitScript = context.assets.open("documents/certificate-fit.js").bufferedReader().use { it.readText() }
        val style = draft.style
        val palette = palette(style)
        val centerLogo = if (style.showLogos) {
            DocumentImageEncoder.dataUrl(assets.centerLogo, 520, transparent = false)
                ?: if (LogoPolicy.builtIn) DocumentImageEncoder.resourceJpeg(context, R.drawable.center_logo) else null
        } else null
        val halaqaLogo = if (style.showLogos) {
            DocumentImageEncoder.dataUrl(assets.halaqaLogo, 520, transparent = false)
                ?: if (LogoPolicy.builtIn) DocumentImageEncoder.resourceJpeg(context, R.drawable.halaqa_logo) else null
        } else null
        val teacherSignature = DocumentImageEncoder.dataUrl(assets.teacherSignature, 380, transparent = true)
        val supervisorSignature = DocumentImageEncoder.dataUrl(assets.supervisorSignature, 380, transparent = true)
        val stamp = DocumentImageEncoder.dataUrl(assets.stamp, 420, transparent = true)
        val font = certificateFontStack(style.fontFamily, style.customFontName)
        val number = certificateNumberHtml(draft.number)
        val centerNumber = if (style.showNumber && style.numberPosition == "under_logo") {
            "<div class=\"cert-number cert-number-logo\">$number</div>"
        } else ""
        val topNumber = if (style.showNumber && style.numberPosition in setOf("top", "header")) {
            "<div class=\"cert-number cert-number-top\">$number</div>"
        } else ""
        val footerStart = if (style.showNumber && style.numberPosition == "footer_start") {
            "<div class=\"cert-number cert-number-footer\">$number</div>"
        } else "<div></div>"
        val footerEnd = if (style.showNumber && style.numberPosition == "footer_end") {
            "<div class=\"cert-number cert-number-right cert-number-footer\">$number</div>"
        } else "<div></div>"
        val dates = if (style.showDate) {
            """<div class="cert-dates"><span class="cert-date-hijri">${h(hijriArabic(draft.date))}</span><span class="cert-date-gregorian">${h(gregorianArabic(draft.date))}</span></div>"""
        } else ""
        val signatures = if (style.showSignatures) {
            """
            <div class="cert-signatures">
              ${signatureBlock(teacherSignature, "المحفظ", draft.teacherName)}
              ${if (style.showStamp) stampBlock(stamp) else "<div></div>"}
              ${signatureBlock(supervisorSignature, "المشرف", draft.supervisorName)}
            </div>
            """.trimIndent()
        } else ""
        val verse = if (style.showVerse) "<div class=\"cert-verse\">${h("﴿ وَقُلْ رَبِّ زِدْنِي عِلْمًا ﴾")}</div>" else "<div></div>"
        val variables = buildString {
            append("--cert-primary:${palette.primary};")
            append("--cert-secondary:${palette.secondary};")
            append("--cert-accent:${palette.accent};")
            append("--cert-paper:${palette.paper};")
            append("--cert-ink:${palette.ink};")
            append("--cert-title-size:${style.titleSize.coerceIn(30f,64f)}px;")
            append("--cert-student-size:${style.studentSize.coerceIn(28f,58f)}px;")
            append("--cert-body-size:${style.bodySize.coerceIn(15f,28f)}px;")
            append("--cert-logo-size:${style.logoSize.coerceIn(100f,230f)}px;")
            append("--cert-border-width:${style.borderWidth.coerceIn(1f,6f)}px;")
            append("--cert-ornament-opacity:${style.ornamentStrength.coerceIn(0f,1f)};")
            append("--cert-font:${font};")
        }
        val intro = if (draft.intro.isBlank()) "" else "<p class=\"cert-intro\">${h(draft.intro)}</p>"
        val dua = if (draft.dua.isBlank()) "" else "<p class=\"cert-dua\">${h(draft.dua)}</p>"
        val numberClass = if (topNumber.isNotEmpty()) " cert-num-top" else ""
        // RC24.1 fixed zones: .cert-main (head + body) / dates / signatures / footer. See certificate.css.
        val inner = """
          <article class="halaqati-certificate cert-template-${hAttr(style.template)}$numberClass" dir="rtl" style="${hAttr(variables)}">
            ${islamicPatternSvg()}
            <div class="cert-frame"></div>
            ${islamicCornerSvg("c1")}${islamicCornerSvg("c2")}${islamicCornerSvg("c3")}${islamicCornerSvg("c4")}
            ${islamicSideRosetteSvg("r1")}${islamicSideRosetteSvg("l1")}
            $topNumber
            <div class="cert-main">
              <header class="cert-head">
                <div class="cert-logo-slot cert-logo-slot-halaqa">${imageOrPlaceholder(halaqaLogo, "شعار الحلقة")}</div>
                <div class="cert-org"><h3>${h(draft.centerName)}</h3><h4>${h(draft.halaqaName)}</h4><div class="cert-orn-line"><span></span></div><div class="cert-kicker">شهادة تقدير وإنجاز</div></div>
                <div class="cert-logo-slot cert-logo-slot-center">${imageOrPlaceholder(centerLogo, "شعار المركز")}$centerNumber</div>
              </header>
              <section class="cert-body">
                <h1 class="cert-main-title">${h(draft.title)}</h1>
                $intro
                <div class="cert-name-box"><strong>${h(draft.studentName.ifBlank { "اسم الطالب" })}</strong></div>
                <p class="cert-achievement">${h(draft.achievement)}</p>
                $dua
              </section>
            </div>
            $dates
            $signatures
            <footer class="cert-footer">$footerStart$verse$footerEnd</footer>
          </article>
        """.trimIndent()
        return """
          <!doctype html><html lang="ar" dir="rtl"><head>
          <meta charset="utf-8"><meta name="viewport" content="width=1122,initial-scale=1,maximum-scale=1,user-scalable=no">
          <style>$css</style></head><body>$inner
          <script>$fitScript</script></body></html>
        """.trimIndent()
    }

    private data class Palette(val paper: String, val primary: String, val accent: String, val secondary: String, val ink: String)

    private fun palette(style: NativeCertificateStyle): Palette {
        val base = when (style.template) {
            "royal" -> Palette("#FFFAF0", "#174936", "#C5A350", "#6F4523", "#17231E")
            "gold" -> Palette("#FFFAF2", "#53421D", "#C89D3D", "#7B4A22", "#241F17")
            "blue" -> Palette("#F7F6EF", "#164F63", "#B99654", "#6B4928", "#172027")
            "minimal" -> Palette("#FFFDF8", "#263B36", "#A88A56", "#66482F", "#1F2724")
            "burgundy" -> Palette("#FFF9F2", "#6F2632", "#C49E4C", "#7A4C30", "#311F20")
            else -> Palette("#FBF7EB", "#0B5F5B", "#B9954A", "#8A4D22", "#18211E")
        }
        fun c(v: String, fallback: String) = v.trim().takeIf { it.matches(Regex("^#[0-9A-Fa-f]{6}$")) } ?: fallback
        return Palette(c(style.paperHex, base.paper), c(style.primaryHex, base.primary), c(style.accentHex, base.accent), c(style.secondaryHex, base.secondary), c(style.inkHex, base.ink))
    }

    private fun imageOrPlaceholder(src: String?, label: String): String = if (src != null) {
        "<img class=\"cert-logo\" src=\"${hAttr(src)}\" alt=\"\">"
    } else {
        "<div class=\"cert-logo cert-logo-placeholder\">${h(label)}</div>"
    }

    private fun signatureBlock(src: String?, role: String, name: String): String = """
      <div class="cert-sign"><div class="cert-sign-line">${src?.let { "<img src=\"${hAttr(it)}\" alt=\"\">" } ?: ""}</div><span>${h(role)}</span><b>${h(name)}</b></div>
    """.trimIndent()

    private fun stampBlock(src: String?): String = if (src != null) {
        "<div class=\"cert-stamp\"><img src=\"${hAttr(src)}\" alt=\"\"></div>"
    } else "<div class=\"cert-stamp\"><span>الختم الرسمي</span></div>"

    private fun certificateNumberHtml(number: String): String = "<span class=\"cert-number-label\">رقم الشهادة:</span><span class=\"cert-number-code\" dir=\"ltr\">${h(number)}</span>"

    private fun islamicPatternSvg(): String = """<svg class="cert-geo-bg" viewBox="0 0 1122 794" preserveAspectRatio="none" aria-hidden="true"><defs><pattern id="certGirihPattern" width="96" height="96" patternUnits="userSpaceOnUse"><g fill="none" stroke="currentColor" stroke-width="1"><path d="M48 4 64 20 87 20 87 43 92 48 87 53 87 76 64 76 48 92 32 76 9 76 9 53 4 48 9 43 9 20 32 20Z"/><path d="M48 14 58 28 76 30 68 48 76 66 58 68 48 82 38 68 20 66 28 48 20 30 38 28Z"/><path d="M0 48h96M48 0v96M14 14l68 68M82 14 14 82" opacity=".65"/><circle cx="48" cy="48" r="10"/></g></pattern></defs><rect width="1122" height="794" fill="url(#certGirihPattern)"/></svg>"""

    private fun islamicCornerSvg(cls: String): String = """<svg class="cert-corner $cls" viewBox="0 0 180 180" aria-hidden="true"><g fill="none" stroke-linejoin="round" stroke-linecap="round"><path class="cert-corner-line primary" d="M8 8H172M8 8V172M22 22H148M22 22V148"/><path class="cert-corner-line accent" d="M12 36H126M36 12V126"/><g transform="translate(22 22)"><polygon class="cert-corner-line accent cert-star-fill" points="58,4 68,35 96,20 81,48 112,58 81,68 96,96 68,81 58,112 48,81 20,96 35,68 4,58 35,48 20,20 48,35"/><polygon class="cert-corner-line primary" points="58,14 86,30 102,58 86,86 58,102 30,86 14,58 30,30"/><rect class="cert-corner-line accent" x="30" y="30" width="56" height="56" transform="rotate(45 58 58)"/><circle class="cert-corner-line primary" cx="58" cy="58" r="18"/><path class="cert-corner-line accent" d="M58 14V102M14 58H102M27 27l62 62M89 27 27 89"/></g><path class="cert-corner-line primary" d="M116 22h32v32M22 116v32h32"/><path class="cert-corner-line accent" d="M124 30h16v16M30 124v16h16"/></g></svg>"""

    private fun islamicSideRosetteSvg(cls: String): String = """<svg class="cert-side-rosette $cls" viewBox="0 0 72 72" aria-hidden="true"><g fill="none" stroke-linejoin="round"><polygon class="primary" points="36,3 44,24 65,15 48,32 69,36 48,40 65,57 44,48 36,69 28,48 7,57 24,40 3,36 24,32 7,15 28,24"/><polygon class="accent" points="36,12 53,19 60,36 53,53 36,60 19,53 12,36 19,19"/><rect class="accent" x="21" y="21" width="30" height="30" transform="rotate(45 36 36)"/><circle class="primary" cx="36" cy="36" r="8"/></g></svg>"""

    private fun gregorianArabic(value: String): String {
        val date = runCatching { LocalDate.parse(value) }.getOrNull() ?: return value
        val months = listOf("يناير","فبراير","مارس","أبريل","مايو","يونيو","يوليو","أغسطس","سبتمبر","أكتوبر","نوفمبر","ديسمبر")
        return "${date.dayOfMonth} ${months[date.monthValue - 1]} ${date.year}م"
    }

    private fun hijriArabic(value: String): String {
        val date = runCatching { LocalDate.parse(value) }.getOrNull() ?: return value
        val h = HijrahDate.from(date)
        val months = listOf("محرم","صفر","ربيع الأول","ربيع الآخر","جمادى الأولى","جمادى الآخرة","رجب","شعبان","رمضان","شوال","ذو القعدة","ذو الحجة")
        val month = h.get(ChronoField.MONTH_OF_YEAR).coerceIn(1, 12)
        return "${h.get(ChronoField.DAY_OF_MONTH)} ${months[month - 1]} ${h.get(ChronoField.YEAR)} هـ"
    }

    private fun h(value: String): String = value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;")
    private fun hAttr(value: String): String = h(value)
    private fun certificateFontStack(value: String, custom: String): String = when (value.lowercase()) {
        "cairo" -> "'Cairo',sans-serif"
        "sans", "sans-serif", "arial", "tahoma" -> "sans-serif"
        "serif" -> "serif"
        "mono", "monospace" -> "monospace"
        "device" -> "'${cssString(custom.ifBlank { "sans-serif" })}',sans-serif"
        else -> "'Cairo',sans-serif"
    }

    private fun cssString(value: String): String = value.replace("\\","\\\\").replace("'","\\'").replace("\n"," ").replace("\r"," ")
}
