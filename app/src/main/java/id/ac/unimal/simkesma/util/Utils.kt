package id.ac.unimal.simkesma.util

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import id.ac.unimal.simkesma.R
import java.text.SimpleDateFormat
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.util.Date
import java.util.Locale
import java.util.TimeZone

const val ADMIN_EMAIL = "radikafikrigo2020@gmail.com"

object Validator {
    fun email(s: String) = android.util.Patterns.EMAIL_ADDRESS.matcher(s).matches()
    fun phone(s: String) = s.matches(Regex("^[0-9+\\-\\s]{8,16}$"))
    fun digits(s: String) = s.matches(Regex("^[0-9]+$"))
}

object DateUtil {
    private val id = Locale("id", "ID")
    private fun iso() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun today(): String = iso().format(Date())

    fun isoFromUtcMillis(ms: Long): String {
        val f = iso(); f.timeZone = TimeZone.getTimeZone("UTC"); return f.format(Date(ms))
    }

    fun utcMillisFromIso(s: String): Long? = try {
        val f = iso(); f.timeZone = TimeZone.getTimeZone("UTC"); f.parse(s)?.time
    } catch (e: Exception) { null }

    fun display(isoDate: String, pattern: String = "d MMM yyyy"): String = try {
        val d = iso().parse(isoDate)
        if (d == null) isoDate else SimpleDateFormat(pattern, id).format(d)
    } catch (e: Exception) { isoDate }

    /** "18 Mei 2025" -> "2025-05-18" */
    fun isoFromDisplay(s: String): String = try {
        val d = SimpleDateFormat("d MMMM yyyy", id).parse(s)
        if (d == null) s else iso().format(d)
    } catch (e: Exception) { s }

    fun millisText(ms: Long, pattern: String = "d MMM yyyy, HH:mm"): String =
        SimpleDateFormat(pattern, id).format(Date(ms))

    fun hijri(months: Array<String>): String = try {
        val h = HijrahDate.now()
        "${h.get(ChronoField.DAY_OF_MONTH)} ${months[h.get(ChronoField.MONTH_OF_YEAR) - 1]} ${h.get(ChronoField.YEAR)} H"
    } catch (e: Exception) { "" }
}

object UiExt {
    fun toast(c: Context, msg: String) = Toast.makeText(c, msg, Toast.LENGTH_LONG).show()
    fun toast(c: Context, res: Int) = Toast.makeText(c, res, Toast.LENGTH_LONG).show()

    fun statusBar(a: Activity, teal: Boolean) {
        a.window.statusBarColor = ContextCompat.getColor(a, if (teal) R.color.primary else R.color.white)
        WindowCompat.getInsetsController(a.window, a.window.decorView).isAppearanceLightStatusBars = !teal
    }

    fun fileName(c: Context, uri: Uri): String {
        c.contentResolver.query(uri, null, null, null, null)?.use {
            val i = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && it.moveToFirst()) return it.getString(i)
        }
        return uri.lastPathSegment ?: "berkas"
    }

    fun authError(e: Exception?): Int = when (e) {
        is FirebaseAuthInvalidUserException -> R.string.err_user_not_found
        is FirebaseAuthInvalidCredentialsException -> R.string.err_wrong_credentials
        is FirebaseAuthUserCollisionException -> R.string.err_email_used
        is FirebaseAuthWeakPasswordException -> R.string.err_weak_password
        is FirebaseNetworkException -> R.string.err_network
        else -> R.string.err_generic
    }
}

/** Warna status: Triple(background, teks, strip). */
object StatusStyle {
    fun colors(c: Context, s: String?): Triple<Int, Int, Int> {
        val k = s.orEmpty().lowercase()
        val (bg, fg, st) = when {
            k in setOf("terkirim", "aktif", "berjalan", "hadir", "sudah", "diproses") ->
                Triple(R.color.green_c, R.color.green_d, R.color.green)
            k.startsWith("menunggu") || k in setOf("ditunda", "wajib hadir", "belum didisposisi", "draft") ->
                Triple(R.color.amber_c, R.color.amber_d, R.color.amber)
            k in setOf("selesai", "akan datang", "rancangan", "alumni") ->
                Triple(R.color.blue_c, R.color.blue_d, R.color.blue)
            k in setOf("ditolak", "non-aktif", "dibatalkan") ->
                Triple(R.color.red_c, R.color.red_d, R.color.red)
            else -> Triple(R.color.container_high, R.color.text_secondary, R.color.text_muted)
        }
        return Triple(ContextCompat.getColor(c, bg), ContextCompat.getColor(c, fg), ContextCompat.getColor(c, st))
    }
}
