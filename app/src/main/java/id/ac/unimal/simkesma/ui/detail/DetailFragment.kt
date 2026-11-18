package id.ac.unimal.simkesma.ui.detail

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.os.bundleOf
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import id.ac.unimal.simkesma.R
import id.ac.unimal.simkesma.data.*
import id.ac.unimal.simkesma.databinding.DialogInputBinding
import id.ac.unimal.simkesma.databinding.DialogQrBinding
import id.ac.unimal.simkesma.databinding.FragmentDetailBinding
import id.ac.unimal.simkesma.databinding.ItemFieldRowBinding
import id.ac.unimal.simkesma.ui.BaseFragment
import id.ac.unimal.simkesma.ui.form.FormFragment
import id.ac.unimal.simkesma.ui.list.FileHelper
import id.ac.unimal.simkesma.util.DateUtil
import id.ac.unimal.simkesma.util.UiExt

class DetailFragment : BaseFragment(R.layout.fragment_detail) {
    companion object {
        fun newInstance(node: String, id: String) = DetailFragment().apply {
            arguments = bundleOf("node" to node, "id" to id)
        }
    }

    private var _b: FragmentDetailBinding? = null
    private val b get() = _b!!
    private lateinit var ent: Entity
    private lateinit var itemId: String
    private var current: Item? = null
    private var deleted = false
    private val stops = ArrayList<() -> Unit>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentDetailBinding.bind(view)
        ent = Entities.get(requireArguments().getString("node")!!)
        itemId = requireArguments().getString("id")!!

        b.header.tvTitle.setText(ent.singular)
        b.header.btnBack.setOnClickListener { back() }
        b.btnEdit.setOnClickListener { push(FormFragment.newInstance(ent.node, itemId)) }
        b.btnDelete.setOnClickListener { confirmDelete() }
        b.btnOpen.setOnClickListener { FileHelper.open(requireContext(), itemId) }
        b.btnDownload.setOnClickListener { FileHelper.download(requireContext(), itemId) }

        Session.role.observe(viewLifecycleOwner) { updateAdmin() }
        Repo.live(ent.node).observe(viewLifecycleOwner) { r ->
            if (r is Resource.Success && !deleted) {
                val it = r.data.firstOrNull { x -> x.id == itemId }
                if (it != null) { current = it; render(it) }
            }
        }
        if (ent.node == "divisi") Repo.live("anggota").observe(viewLifecycleOwner) { current?.let { render(it) } }
        if (ent.node == "rapat") setupRapat()
    }

    private fun updateAdmin() {
        val admin = Session.isAdmin
        b.llAdmin.visibility = if (admin) View.VISIBLE else View.GONE
        b.btnEditNotula.visibility = if (admin && ent.node == "rapat") View.VISIBLE else View.GONE
        b.btnQr.visibility = if (admin && ent.node == "rapat") View.VISIBLE else View.GONE
    }

    private fun render(item: Item) {
        val ctx = requireContext()
        val card = ent.toCard(ctx, item, emptyMap())
        b.tvCategory.visibility = if (card.category.isNullOrBlank()) View.GONE else View.VISIBLE
        b.tvCategory.text = card.category
        b.tvStatus.badge(card.status)
        b.tvName.text = item.str(ent.titleKey)
        if (card.progress != null) {
            b.llProgress.visibility = View.VISIBLE
            b.progressBar.setProgressCompat(card.progress, false)
            b.tvProgress.text = "${card.progress}%"
        } else b.llProgress.visibility = View.GONE

        b.llFields.removeAllViews()
        ent.fields.filter { it.type != T.FILE && it.key != ent.titleKey }.forEach { f ->
            val raw = item.str(f.key)
            val value = when {
                raw.isBlank() -> "-"
                f.type == T.DATE -> DateUtil.display(raw, "EEEE, d MMMM yyyy")
                f.type == T.TIME -> "$raw WIB"
                f.key == "anggaran" -> Entities.rupiah(raw.toLongOrNull() ?: 0)
                f.key == "progres" -> "$raw%"
                else -> raw
            }
            val row = ItemFieldRowBinding.inflate(layoutInflater, b.llFields, false)
            row.tvLabel.setText(f.label)
            row.tvValue.text = value
            b.llFields.addView(row.root)
        }

        val ff = ent.fileField
        val fileName = if (ff != null) item.str(ff.key + "Nama") else ""
        b.cardFile.visibility = if (fileName.isNotBlank()) View.VISIBLE else View.GONE
        b.tvFileName.text = fileName

        if (ent.node == "divisi") {
            val nama = item.str("nama")
            val members = (Repo.live("anggota").value as? Resource.Success)?.data.orEmpty()
                .filter { it.str("divisi") == nama }.sortedBy { it.str("namaLengkap") }
            b.cardMembers.visibility = View.VISIBLE
            b.tvMembersTitle.text = getString(R.string.members_title, members.size)
            b.tvMembers.text = if (members.isEmpty()) getString(R.string.members_empty)
            else members.joinToString("\n") { "• ${it.str("namaLengkap")} (${it.str("jabatan")})" }
        }
        if (ent.node == "rapat") renderRapat(item)
        updateAdmin()
    }

    // ---------- rapat: notula, konfirmasi, QR ----------
    private fun setupRapat() {
        b.llRapat.visibility = View.VISIBLE
        stops.add(Repo.watch("notula/$itemId/isi") { s ->
            if (_b == null) return@watch
            b.tvNotula.text = s.getValue(String::class.java)?.takeIf { it.isNotBlank() } ?: getString(R.string.notula_empty)
        })
        b.btnEditNotula.setOnClickListener { editNotula() }
        b.btnQr.setOnClickListener { showQr() }
        b.btnConfirm.setOnClickListener {
            val uid = Session.uid ?: return@setOnClickListener
            Repo.setValue("rapat/$itemId/peserta/$uid", Session.nama) { err ->
                if (err != null) UiExt.toast(requireContext(), R.string.err_generic)
            }
        }
    }

    private fun renderRapat(item: Item) {
        @Suppress("UNCHECKED_CAST")
        val peserta = (item.data["peserta"] as? Map<String, Any?>).orEmpty()
        val confirmed = Session.uid?.let { peserta.containsKey(it) } == true
        b.btnConfirm.isEnabled = !confirmed && item.str("status").equals("Akan Datang", true)
        b.btnConfirm.setText(if (confirmed) R.string.attendance_confirmed else R.string.btn_confirm_attendance)
        b.tvPeserta.text = if (peserta.isEmpty()) getString(R.string.peserta_empty)
        else getString(R.string.peserta_fmt, peserta.size, peserta.values.joinToString(", "))
    }

    private fun editNotula() {
        val d = DialogInputBinding.inflate(layoutInflater)
        d.til1.hint = getString(R.string.notula)
        d.et1.minLines = 5
        d.et1.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        d.et1.setText(b.tvNotula.text.takeIf { it.toString() != getString(R.string.notula_empty) })
        d.til2.visibility = View.GONE; d.til3.visibility = View.GONE
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.btn_edit_notula).setView(d.root)
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                Repo.setValue(
                    "notula/$itemId",
                    mapOf("isi" to d.et1.text.toString().trim(), "penulis" to Session.nama, "updatedAt" to System.currentTimeMillis())
                ) { err -> if (err != null) UiExt.toast(requireContext(), R.string.err_generic) }
            }.show()
    }

    private fun showQr() {
        val code = "SIMKESMA|$itemId"
        val bmp: Bitmap = BarcodeEncoder().encodeBitmap(code, BarcodeFormat.QR_CODE, 700, 700)
        val d = DialogQrBinding.inflate(layoutInflater)
        d.ivQr.setImageBitmap(bmp)
        d.tvCode.text = getString(R.string.manual_code_fmt, itemId)
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.btn_show_qr).setView(d.root)
            .setPositiveButton(R.string.btn_close, null).show()
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_title)
            .setMessage(getString(R.string.delete_msg, current?.str(ent.titleKey).orEmpty()))
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                deleted = true
                Repo.delete(ent.node, itemId) { err ->
                    if (_b == null) return@delete
                    if (err == null) {
                        UiExt.toast(requireContext(), R.string.deleted_ok)
                        back()
                    } else {
                        deleted = false
                        UiExt.toast(requireContext(), R.string.err_generic)
                    }
                }
            }.show()
    }

    override fun onDestroyView() {
        stops.forEach { it() }; stops.clear()
        super.onDestroyView()
        _b = null
    }
}
