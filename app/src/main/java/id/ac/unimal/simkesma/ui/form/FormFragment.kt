package id.ac.unimal.simkesma.ui.form

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Base64
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import id.ac.unimal.simkesma.R
import id.ac.unimal.simkesma.data.*
import id.ac.unimal.simkesma.databinding.*
import id.ac.unimal.simkesma.ui.BaseFragment
import id.ac.unimal.simkesma.ui.list.FileHelper
import id.ac.unimal.simkesma.util.DateUtil
import id.ac.unimal.simkesma.util.UiExt
import id.ac.unimal.simkesma.util.Validator

class FormFragment : BaseFragment(R.layout.fragment_form) {
    companion object {
        fun newInstance(node: String, id: String?) = FormFragment().apply {
            arguments = bundleOf("node" to node, "id" to id)
        }
    }

    private var _b: FragmentFormBinding? = null
    private val b get() = _b!!
    private lateinit var ent: Entity
    private lateinit var vm: FormViewModel
    private var editId: String? = null
    private var existing: Item? = null
    private val tils = LinkedHashMap<String, TextInputLayout>()
    private var fileNameView: android.widget.TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var saving = false

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            vm.fileUri = uri
            vm.fileName = UiExt.fileName(requireContext(), uri)
            fileNameView?.text = vm.fileName
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentFormBinding.bind(view)
        ent = Entities.get(requireArguments().getString("node")!!)
        editId = requireArguments().getString("id")
        vm = ViewModelProvider(this)[FormViewModel::class.java]

        b.header.tvTitle.text = getString(if (editId == null) R.string.form_add else R.string.form_edit, getString(ent.singular))
        b.header.btnBack.setOnClickListener { back() }
        buildFields()
        b.btnSave.setOnClickListener { save() }

        if (editId != null) {
            val fresh = savedInstanceState == null
            Repo.live(ent.node).observe(viewLifecycleOwner) { r ->
                if (r is Resource.Success && existing == null) {
                    existing = r.data.firstOrNull { it.id == editId }
                    if (fresh) existing?.let { prefill(it) }
                }
            }
        }
    }

    private fun buildFields() {
        b.llForm.removeAllViews()
        ent.fields.forEachIndexed { idx, f ->
            when (f.type) {
                T.DROPDOWN -> {
                    val row = ItemDropdownBinding.inflate(layoutInflater, b.llForm, false)
                    row.til.id = 3000 + idx; row.dd.id = 2000 + idx
                    row.til.hint = label(f)
                    fillDropdown(row.dd, f)
                    if (f.source != null) Repo.live(f.source).observe(viewLifecycleOwner) { fillDropdown(row.dd, f) }
                    b.llForm.addView(row.root); tils[f.key] = row.til
                }
                T.FILE -> {
                    val row = ItemFilePickerBinding.inflate(layoutInflater, b.llForm, false)
                    row.tvFileLabel.text = label(f)
                    row.btnPick.setOnClickListener { picker.launch("*/*") }
                    fileNameView = row.tvFileName
                    if (vm.fileName.isNotEmpty()) row.tvFileName.text = vm.fileName
                    b.llForm.addView(row.root)
                }
                else -> {
                    val row = ItemInputBinding.inflate(layoutInflater, b.llForm, false)
                    row.til.id = 3000 + idx; row.et.id = 2000 + idx
                    row.til.hint = label(f)
                    row.et.inputType = when (f.type) {
                        T.EMAIL -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                        T.PHONE -> InputType.TYPE_CLASS_PHONE
                        T.NUMBER, T.DIGITS -> InputType.TYPE_CLASS_NUMBER
                        T.MULTI -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                        else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                    }
                    if (f.type == T.MULTI) row.et.minLines = 3
                    if (f.type == T.DATE || f.type == T.TIME) {
                        row.et.isFocusable = false; row.et.isCursorVisible = false
                        row.et.setOnClickListener { if (f.type == T.DATE) pickDate(row.et) else pickTime(row.et) }
                        row.til.endIconMode = TextInputLayout.END_ICON_CUSTOM
                        row.til.setEndIconDrawable(if (f.type == T.DATE) R.drawable.ic_event else R.drawable.ic_schedule)
                        row.til.setEndIconOnClickListener { row.et.performClick() }
                    }
                    b.llForm.addView(row.root); tils[f.key] = row.til
                }
            }
        }
    }

    private fun label(f: F) = getString(f.label) + if (f.required) " *" else ""

    private fun fillDropdown(dd: MaterialAutoCompleteTextView, f: F) {
        val updateOpts = {
            if (_b != null) {
                val opts = if (f.source != null) Repo.values(f.source, f.sourceKey) else resources.getStringArray(f.options).toList()
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, opts)
                dd.setAdapter(adapter)
            }
        }
        updateOpts()
        if (f.source != null) {
            Repo.live(f.source).observe(viewLifecycleOwner) { r ->
                if (r is Resource.Success) updateOpts()
            }
        }
        if (ent.node == "pengurus" && f.key == "namaLengkap") {
            dd.setOnItemClickListener { _, _, position, _ ->
                val opts = if (f.source != null) Repo.values(f.source, f.sourceKey) else emptyList()
                val selectedName = opts.getOrNull(position) ?: dd.text.toString()
                val members = Repo.memoryCache["anggota"].orEmpty()
                val m = members.firstOrNull { it.str("namaLengkap") == selectedName }
                if (m != null) {
                    tils["nim"]?.editText?.setText(m.str("nim"))
                    tils["email"]?.editText?.setText(m.str("email"))
                    tils["telepon"]?.editText?.setText(m.str("telepon"))
                }
            }
        }
    }

    private fun pickDate(et: com.google.android.material.textfield.TextInputEditText) {
        val cur = DateUtil.utcMillisFromIso(DateUtil.isoFromDisplay(et.text.toString())) ?: MaterialDatePicker.todayInUtcMilliseconds()
        val p = MaterialDatePicker.Builder.datePicker().setSelection(cur).build()
        p.addOnPositiveButtonClickListener { et.setText(DateUtil.display(DateUtil.isoFromUtcMillis(it), "d MMMM yyyy")) }
        p.show(childFragmentManager, "date")
    }

    private fun pickTime(et: com.google.android.material.textfield.TextInputEditText) {
        val p = MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H).setHour(8).setMinute(0).build()
        p.addOnPositiveButtonClickListener { et.setText(String.format("%02d:%02d", p.hour, p.minute)) }
        p.show(childFragmentManager, "time")
    }

    private fun prefill(item: Item) {
        ent.fields.forEach { f ->
            val til = tils[f.key] ?: return@forEach
            val v = item.str(f.key)
            when (f.type) {
                T.DROPDOWN -> (til.editText as MaterialAutoCompleteTextView).setText(v, false)
                T.DATE -> til.editText?.setText(if (v.isBlank()) "" else DateUtil.display(v, "d MMMM yyyy"))
                else -> til.editText?.setText(v)
            }
        }
        val ff = ent.fileField
        if (ff != null && vm.fileName.isEmpty()) fileNameView?.text = item.str(ff.key + "Nama").ifBlank { getString(R.string.no_file) }
    }

    private fun save() {
        if (saving) return
        val req = getString(R.string.err_required)
        val data = HashMap<String, Any?>()
        var ok = true

        val jabatanVal = tils["jabatan"]?.editText?.text?.toString()?.trim().orEmpty()
        val isBph = listOf("Ketua Umum", "Wakil Ketua", "Sekretaris", "Bendahara").any { it.equals(jabatanVal, true) }

        ent.fields.forEach { f ->
            val til = tils[f.key] ?: return@forEach
            til.error = null
            val v = til.editText?.text?.toString()?.trim().orEmpty()

            var isRequired = f.required
            if (ent.node == "pengurus" && f.key == "divisi" && isBph) {
                isRequired = false
            }

            if (v.isEmpty()) {
                if (isRequired) { til.error = req; ok = false } else data[f.key] = ""
                return@forEach
            }
            when (f.type) {
                T.EMAIL -> if (!Validator.email(v)) { til.error = getString(R.string.err_email_format); ok = false } else data[f.key] = v
                T.PHONE -> if (!Validator.phone(v)) { til.error = getString(R.string.err_phone); ok = false } else data[f.key] = v
                T.DIGITS -> if (!Validator.digits(v)) { til.error = getString(R.string.err_digits); ok = false } else data[f.key] = v
                T.NUMBER -> {
                    val n = v.toLongOrNull()
                    if (n == null || n < 0) { til.error = getString(R.string.err_number); ok = false }
                    else if (f.key == "progres" && n > 100) { til.error = getString(R.string.err_progress); ok = false }
                    else data[f.key] = if (f.key == "progres") n.toInt() else n
                }
                T.DATE -> data[f.key] = DateUtil.isoFromDisplay(v)
                else -> data[f.key] = v
            }
        }
        val ff = ent.fileField
        val hadFile = ff != null && existing?.str(ff.key + "Nama").orEmpty().isNotBlank()
        if (ff != null && ff.required && vm.fileUri == null && !hadFile) {
            UiExt.toast(requireContext(), R.string.err_file_required); ok = false
        }
        if (!ok) return

        var b64: String? = null
        var mime = "application/octet-stream"
        if (ff != null && vm.fileUri != null) {
            val uri = vm.fileUri!!
            try {
                val bytes = requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) { UiExt.toast(requireContext(), R.string.err_file_read); return }
                if (bytes.size > FileHelper.MAX_BYTES) { UiExt.toast(requireContext(), R.string.err_file_big); return }
                b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                mime = requireContext().contentResolver.getType(uri) ?: mime
                data[ff.key + "Nama"] = vm.fileName
                data[ff.key + "Mime"] = mime
                data[ff.key] = b64
            } catch (e: Exception) { UiExt.toast(requireContext(), R.string.err_file_read); return }
        }

        val id = editId ?: Repo.newId(ent.node)
        if (editId == null) {
            data["createdAt"] = System.currentTimeMillis()
            data["createdBy"] = Session.uid
            if (ent.node == "arsip_dokumen") data["uploaderNama"] = Session.nama
        }
        data["updatedAt"] = System.currentTimeMillis()

        setSaving(true)
        handler.postDelayed({
            if (saving && _b != null) { setSaving(false); UiExt.toast(requireContext(), R.string.err_timeout) }
        }, 10000)

        Repo.save(ent.node, id, data) cb@{ err ->
            if (_b == null) return@cb
            if (err != null) { setSaving(false); UiExt.toast(requireContext(), getString(R.string.err_save, err)); return@cb }
            if (b64 != null) {
                Repo.saveFile(id, vm.fileName, mime, b64) { e2 ->
                    if (_b == null) return@saveFile
                    setSaving(false)
                    if (e2 != null) UiExt.toast(requireContext(), getString(R.string.err_file_upload, e2))
                    else done()
                }
            } else { setSaving(false); done() }
        }
    }

    private fun done() {
        UiExt.toast(requireContext(), R.string.saved_ok)
        back()
    }

    private fun setSaving(on: Boolean) {
        saving = on
        b.progress.visibility = if (on) View.VISIBLE else View.GONE
        b.btnSave.isEnabled = !on
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
        _b = null
    }
}
