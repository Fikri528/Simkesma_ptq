package id.ac.unimal.simkesma.ui.profil

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import id.ac.unimal.simkesma.R
import id.ac.unimal.simkesma.data.Repo
import id.ac.unimal.simkesma.data.Session
import id.ac.unimal.simkesma.databinding.DialogInputBinding
import id.ac.unimal.simkesma.databinding.FragmentProfilBinding
import id.ac.unimal.simkesma.databinding.ItemFieldRowBinding
import id.ac.unimal.simkesma.ui.BaseFragment
import id.ac.unimal.simkesma.ui.list.EntityListFragment
import id.ac.unimal.simkesma.ui.list.FileHelper
import id.ac.unimal.simkesma.util.UiExt
import id.ac.unimal.simkesma.util.Validator
import java.io.ByteArrayOutputStream

class ProfilFragment : BaseFragment(R.layout.fragment_profil) {
    override val tealStatus: Boolean get() = false
    private var _b: FragmentProfilBinding? = null
    private val b get() = _b!!
    private var periode = ""
    private var stop: (() -> Unit)? = null

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val bytes = requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    if (bytes.size > FileHelper.MAX_BYTES) {
                        UiExt.toast(requireContext(), R.string.err_file_big)
                        return@registerForActivityResult
                    }
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    savePhoto(b64)
                }
            } catch (_: Exception) {
                UiExt.toast(requireContext(), R.string.err_file_read)
            }
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            try {
                val stream = ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, stream)
                val bytes = stream.toByteArray()
                if (bytes.size > FileHelper.MAX_BYTES) {
                    UiExt.toast(requireContext(), R.string.err_file_big)
                    return@registerForActivityResult
                }
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                savePhoto(b64)
            } catch (_: Exception) {
                UiExt.toast(requireContext(), R.string.err_file_read)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentProfilBinding.bind(view)
        b.header.tvTitle.setText(R.string.nav_profil)
        b.header.btnBell.setOnClickListener { push(EntityListFragment.newInstance("pengumuman")) }
        b.header.btnAvatar.visibility = View.GONE

        b.flAvatar.setOnClickListener { showPhotoDialog() }
        b.btnEditProfile.setOnClickListener { editProfile() }
        b.btnResetPass.setOnClickListener { resetPassword() }
        b.btnPeriode.setOnClickListener { editPeriode() }
        b.btnAnnouncements.setOnClickListener { push(EntityListFragment.newInstance("pengumuman")) }
        b.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.btn_logout).setMessage(R.string.logout_msg)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_logout) { _, _ -> main().logout() }.show()
        }
        Session.profile.observe(viewLifecycleOwner) { render() }
        Session.role.observe(viewLifecycleOwner) {
            val a = Session.isAdmin
            b.btnPeriode.visibility = if (a) View.VISIBLE else View.GONE
            b.btnAnnouncements.visibility = if (a) View.VISIBLE else View.GONE
            render()
        }
        stop = Repo.watch("pengaturan/periode") { s -> periode = s.getValue(String::class.java).orEmpty(); if (_b != null) render() }
    }

    private fun showPhotoDialog() {
        val hasPhoto = Session.profile.value?.get("foto")?.toString().orEmpty().isNotBlank()
        val options = if (hasPhoto) {
            arrayOf(getString(R.string.pick_gallery), getString(R.string.pick_camera), getString(R.string.delete_photo))
        } else {
            arrayOf(getString(R.string.pick_gallery), getString(R.string.pick_camera))
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pick_image_title)
            .setItems(options) { _, which ->
                if (which == 0) galleryLauncher.launch("image/*")
                else if (which == 1) cameraLauncher.launch(null)
                else if (which == 2) deletePhoto()
            }
            .show()
    }

    private fun deletePhoto() {
        val uid = Session.uid ?: return
        UiExt.toast(requireContext(), R.string.saved_ok)
        Repo.update("users/$uid", mapOf("foto" to "")) { e ->
            if (e == null) {
                Repo.update("anggota/$uid", mapOf("foto" to ""))
            }
        }
    }

    private fun savePhoto(b64: String) {
        val uid = Session.uid ?: return
        UiExt.toast(requireContext(), R.string.saved_ok)
        Repo.update("users/$uid", mapOf("foto" to b64)) { e ->
            if (e == null) {
                Repo.update("anggota/$uid", mapOf("foto" to b64))
            }
        }
    }

    private fun row(parent: android.widget.LinearLayout, label: Int, value: String) {
        val r = ItemFieldRowBinding.inflate(layoutInflater, parent, false)
        r.tvLabel.setText(label); r.tvValue.text = value.ifBlank { "-" }
        parent.addView(r.root)
    }

    private fun render() {
        if (_b == null) return
        val p = Session.profile.value.orEmpty()
        val email = FirebaseAuth.getInstance().currentUser?.email.orEmpty()
        b.tvName.text = Session.nama
        b.tvEmail.text = email
        b.tvRole.setText(if (Session.isAdmin) R.string.role_admin_label else R.string.role_member_label)

        val foto = p["foto"]?.toString().orEmpty()
        if (foto.isNotBlank()) {
            try {
                val bytes = Base64.decode(foto, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                b.ivAvatar.setImageBitmap(bmp)
            } catch (_: Exception) {}
        } else {
            b.ivAvatar.setImageResource(R.drawable.logo_ukmptq)
        }

        b.llInfo.removeAllViews()
        row(b.llInfo, R.string.f_email, email)
        row(b.llInfo, R.string.f_nim, p["nim"]?.toString().orEmpty())
        row(b.llInfo, R.string.f_divisi, p["divisi"]?.toString().orEmpty())
        row(b.llInfo, R.string.f_telepon, p["telepon"]?.toString().orEmpty())
        row(b.llInfo, R.string.f_jabatan, getString(if (Session.isAdmin) R.string.role_admin_label else R.string.role_member_label))
        b.llOrg.removeAllViews()
        row(b.llOrg, R.string.org_name_label, getString(R.string.org_name))
        row(b.llOrg, R.string.org_univ_label, getString(R.string.org_univ))
        row(b.llOrg, R.string.f_periode, periode.ifBlank { getString(R.string.periode_unset) })
    }

    private fun editProfile() {
        val p = Session.profile.value.orEmpty()
        val d = DialogInputBinding.inflate(layoutInflater)
        d.til1.hint = getString(R.string.f_nama_lengkap); d.et1.setText(p["nama"]?.toString().orEmpty())
        d.til2.hint = getString(R.string.f_nim); d.et2.setText(p["nim"]?.toString().orEmpty())
        d.et2.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        d.til3.hint = getString(R.string.f_telepon); d.et3.setText(p["telepon"]?.toString().orEmpty())
        d.et3.inputType = android.text.InputType.TYPE_CLASS_PHONE
        val dlg = MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.btn_edit_profile).setView(d.root)
            .setNegativeButton(R.string.btn_cancel, null).setPositiveButton(R.string.btn_save, null).create()
        dlg.setOnShowListener {
            dlg.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val nama = d.et1.text.toString().trim(); val nim = d.et2.text.toString().trim(); val tel = d.et3.text.toString().trim()
                d.til1.error = null; d.til2.error = null; d.til3.error = null
                var ok = true
                if (nama.isEmpty()) { d.til1.error = getString(R.string.err_required); ok = false }
                if (nim.isNotEmpty() && !Validator.digits(nim)) { d.til2.error = getString(R.string.err_digits); ok = false }
                if (tel.isNotEmpty() && !Validator.phone(tel)) { d.til3.error = getString(R.string.err_phone); ok = false }
                if (!ok) return@setOnClickListener
                val uid = Session.uid ?: return@setOnClickListener
                Repo.update("users/$uid", mapOf("nama" to nama, "nim" to nim, "telepon" to tel)) { e ->
                    if (e != null) UiExt.toast(requireContext(), getString(R.string.err_save, e))
                    else {
                        Repo.update("anggota/$uid", mapOf("namaLengkap" to nama, "nim" to nim, "telepon" to tel))
                        UiExt.toast(requireContext(), R.string.saved_ok)
                    }
                }
                dlg.dismiss()
            }
        }
        dlg.show()
    }

    private fun resetPassword() {
        val email = FirebaseAuth.getInstance().currentUser?.email ?: return
        FirebaseAuth.getInstance().sendPasswordResetEmail(email).addOnCompleteListener { t ->
            if (_b == null) return@addOnCompleteListener
            UiExt.toast(requireContext(), if (t.isSuccessful) getString(R.string.reset_sent) else getString(UiExt.authError(t.exception)))
        }
    }

    private fun editPeriode() {
        val d = DialogInputBinding.inflate(layoutInflater)
        d.til1.hint = getString(R.string.f_periode); d.et1.setText(periode)
        d.til2.visibility = View.GONE; d.til3.visibility = View.GONE
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.btn_edit_periode).setView(d.root)
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.btn_save) { _, _ -> Repo.setValue("pengaturan/periode", d.et1.text.toString().trim()) }.show()
    }

    override fun onDestroyView() { stop?.invoke(); stop = null; super.onDestroyView(); _b = null }
}
