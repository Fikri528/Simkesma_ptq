package id.ac.unimal.simkesma.ui.presensi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import id.ac.unimal.simkesma.R
import id.ac.unimal.simkesma.data.*
import id.ac.unimal.simkesma.databinding.FragmentPresensiBinding
import id.ac.unimal.simkesma.databinding.ItemAttendanceBinding
import id.ac.unimal.simkesma.ui.BaseFragment
import id.ac.unimal.simkesma.util.DateUtil
import id.ac.unimal.simkesma.util.UiExt

class CaptureActivityPortrait : com.journeyapps.barcodescanner.CaptureActivity()

class PresensiFragment : BaseFragment(R.layout.fragment_presensi) {
    private var _b: FragmentPresensiBinding? = null
    private val b get() = _b!!

    private val scanLauncher = registerForActivityResult(ScanContract()) { r ->
        val c = r.contents
        if (c != null) record(c, "QR Code")
    }

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (_b == null) return@registerForActivityResult
        if (granted) { showDenied(false); startScan() } else showDenied(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentPresensiBinding.bind(view)
        b.header.tvTitle.setText(R.string.ent_presensi)
        b.header.btnBack.setOnClickListener { back() }
        b.btnScan.setOnClickListener { requestScan() }
        b.btnManual.setOnClickListener {
            val code = b.etCode.text.toString().trim()
            if (code.isEmpty()) b.tilCode.error = getString(R.string.err_required)
            else { b.tilCode.error = null; record(code, "Kode Manual") }
        }
        b.btnSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + requireContext().packageName)))
        }
        Repo.live("rapat").observe(viewLifecycleOwner) { }
        Repo.live("kehadiran").observe(viewLifecycleOwner) { r -> if (r is Resource.Success) renderHistory(r.data) }
    }

    private fun requestScan() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startScan()
        else permLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun showDenied(denied: Boolean) {
        b.tvPermInfo.visibility = if (denied) View.VISIBLE else View.GONE
        b.btnSettings.visibility = if (denied) View.VISIBLE else View.GONE
    }

    private fun startScan() {
        val o = ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt(getString(R.string.scan_prompt))
            .setBeepEnabled(false)
            .setOrientationLocked(true)
            .setCaptureActivity(CaptureActivityPortrait::class.java)
        try { scanLauncher.launch(o) } catch (e: Exception) { UiExt.toast(requireContext(), R.string.err_generic) }
    }

    private fun record(raw: String, method: String) {
        val uid = Session.uid ?: return
        val rapatId = raw.removePrefix("SIMKESMA|").trim()
        val rapat = (Repo.live("rapat").value as? Resource.Success)?.data?.firstOrNull { it.id == rapatId }
        if (rapat == null) { UiExt.toast(requireContext(), R.string.err_qr_invalid); return }
        val existing = (Repo.live("kehadiran").value as? Resource.Success)?.data.orEmpty()
            .any { it.str("uid") == uid && it.str("rapatId") == rapatId }
        if (existing) { UiExt.toast(requireContext(), R.string.err_already_present); return }
        val id = Repo.newId("kehadiran")
        val data = mapOf(
            "uid" to uid, "nama" to Session.nama, "rapatId" to rapatId, "rapatJudul" to rapat.str("judul"),
            "lokasi" to rapat.str("lokasi"), "waktu" to System.currentTimeMillis(), "metode" to method, "status" to "Hadir"
        )
        Repo.save("kehadiran", id, data) { err ->
            if (_b == null) return@save
            if (err == null) { UiExt.toast(requireContext(), getString(R.string.presence_ok, rapat.str("judul"))); b.etCode.setText("") }
            else UiExt.toast(requireContext(), getString(R.string.err_save, err))
        }
    }

    private fun renderHistory(all: List<Item>) {
        val uid = Session.uid
        val mine = (if (Session.isAdmin) all else all.filter { it.str("uid") == uid }).sortedByDescending { it.long("waktu") }
        b.llHistory.removeAllViews()
        b.tvHistoryEmpty.visibility = if (mine.isEmpty()) View.VISIBLE else View.GONE
        mine.forEach { i ->
            val row = ItemAttendanceBinding.inflate(layoutInflater, b.llHistory, false)
            row.tvTitle.text = i.str("rapatJudul")
            row.tvStatus.badge(i.str("status"))
            val who = if (Session.isAdmin) "${i.str("nama")} • " else ""
            row.tvInfo.text = who + DateUtil.millisText(i.long("waktu")) + " • " + i.str("metode")
            row.tvPlace.text = i.str("lokasi")
            b.llHistory.addView(row.root)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
