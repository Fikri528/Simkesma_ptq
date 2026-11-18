package id.ac.unimal.simkesma.ui.struktur

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import id.ac.unimal.simkesma.R
import id.ac.unimal.simkesma.data.*
import id.ac.unimal.simkesma.databinding.DialogInputBinding
import id.ac.unimal.simkesma.databinding.FragmentStrukturBinding
import id.ac.unimal.simkesma.databinding.ItemPersonBinding
import id.ac.unimal.simkesma.ui.BaseFragment
import id.ac.unimal.simkesma.ui.detail.DetailFragment
import id.ac.unimal.simkesma.ui.form.FormFragment

class StrukturFragment : BaseFragment(R.layout.fragment_struktur) {
    private var _b: FragmentStrukturBinding? = null
    private val b get() = _b!!
    private var periode = ""
    private var stop: (() -> Unit)? = null

    private var pengurusData: List<Item>? = null
    private var divisiData: List<Item>? = null
    private var selectedPeriodeFilter = "2025-2026"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentStrukturBinding.bind(view)
        b.header.tvTitle.setText(R.string.ent_struktur)
        b.header.btnBack.setOnClickListener { back() }
        b.header.btnAction.setText(R.string.btn_add_pengurus)
        b.header.btnAction.setIconResource(R.drawable.ic_add)
        b.header.btnAction.setOnClickListener { push(FormFragment.newInstance("pengurus", null)) }
        b.tvPeriode.setOnClickListener { if (Session.isAdmin) editPeriode() }

        Session.role.observe(viewLifecycleOwner) { b.header.btnAction.visibility = if (Session.isAdmin) View.VISIBLE else View.GONE }
        stop = Repo.watch("pengaturan/periode") { s ->
            if (_b == null) return@watch
            periode = s.getValue(String::class.java).orEmpty()
            b.tvPeriode.text = if (periode.isBlank()) getString(R.string.periode_unset) else getString(R.string.periode_fmt, periode)
        }

        val defaultPeriodes = listOf("Semua Periode", "2024-2025", "2025-2026", "2026-2027")
        b.ddPeriodeFilter.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, defaultPeriodes))
        b.ddPeriodeFilter.setText(selectedPeriodeFilter, false)
        b.ddPeriodeFilter.setOnItemClickListener { _, _, p, _ ->
            selectedPeriodeFilter = defaultPeriodes[p]
            tryBuild()
        }

        Repo.live("periode").observe(viewLifecycleOwner) { r ->
            if (_b == null) return@observe
            if (r is Resource.Success) {
                val list = listOf("Semua Periode") + r.data.map { it.str("nama") }.filter { it.isNotBlank() }.distinct()
                b.ddPeriodeFilter.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, list))
            }
        }

        Repo.live("pengurus").observe(viewLifecycleOwner) { r ->
            if (_b == null) return@observe
            if (r is Resource.Success) { pengurusData = r.data; tryBuild() }
            else if (r is Resource.Error) { pengurusData = emptyList(); tryBuild() }
        }
        Repo.live("divisi").observe(viewLifecycleOwner) { r ->
            if (_b == null) return@observe
            if (r is Resource.Success) { divisiData = r.data; tryBuild() }
            else if (r is Resource.Error) { divisiData = emptyList(); tryBuild() }
        }
    }

    private fun tryBuild() {
        val people = pengurusData ?: return
        val divs = divisiData ?: return
        build(people, divs)
    }

    private fun editPeriode() {
        val d = DialogInputBinding.inflate(layoutInflater)
        d.til1.hint = getString(R.string.f_periode)
        d.et1.setText(periode)
        d.til2.visibility = View.GONE; d.til3.visibility = View.GONE
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.btn_edit_periode).setView(d.root)
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.btn_save) { _, _ -> Repo.setValue("pengaturan/periode", d.et1.text.toString().trim()) }
            .show()
    }

    private fun build(people: List<Item>, divs: List<Item>) {
        if (_b == null) return
        b.loading.visibility = View.GONE
        val ll = b.llStruktur
        ll.removeAllViews()

        val pengurusList = people.filter {
            val per = it.str("periode")
            (selectedPeriodeFilter == "Semua Periode" || per.isBlank() || per == selectedPeriodeFilter)
        }

        val bphOrder = resources.getStringArray(R.array.opt_bph)
        val bph = bphOrder.flatMap { j -> pengurusList.filter { it.str("jabatan") == j } }
        if (bph.isNotEmpty()) addSection(getString(R.string.sec_bph), null, bph)

        divs.sortedBy { it.str("nama") }.forEach { d ->
            val nama = d.str("nama")
            val inDiv = pengurusList.filter { it.str("divisi") == nama }
            val leaders = listOf("Ketua Divisi", "Sekretaris Divisi").flatMap { j -> inDiv.filter { it.str("jabatan") == j } }
            val otherOfficers = inDiv.filter { !leaders.contains(it) && !bph.contains(it) }
            val divOfficers = leaders + otherOfficers
            if (divOfficers.isNotEmpty()) {
                addSection(getString(R.string.fmt_divisi, nama), getString(R.string.fmt_jumlah_anggota, divOfficers.size), divOfficers)
            }
        }
        b.tvEmpty.visibility = if (ll.childCount == 0) View.VISIBLE else View.GONE
    }

    private fun addSection(title: String, sub: String?, list: List<Item>) {
        val card = MaterialCardView(requireContext()).apply {
            radius = 20f * resources.displayMetrics.density
            cardElevation = resources.displayMetrics.density
            strokeWidth = 0
            setCardBackgroundColor(requireContext().getColor(R.color.white))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (12 * resources.displayMetrics.density).toInt() }
        }
        val col = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * resources.displayMetrics.density).toInt(); setPadding(p, p, p, p / 2)
        }
        val head = TextView(requireContext()).apply {
            text = if (sub == null) title else "$title • $sub"
            setTextColor(requireContext().getColor(R.color.primary_dark)); textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        col.addView(head)
        if (list.isEmpty()) col.addView(TextView(requireContext()).apply {
            setText(R.string.struktur_section_empty); setTextColor(requireContext().getColor(R.color.text_muted)); textSize = 13f
            setPadding(0, 12, 0, 12)
        })
        list.forEach { p ->
            val row = ItemPersonBinding.inflate(layoutInflater, col, false)
            val nm = p.str("namaLengkap")
            row.tvInitial.text = nm.take(1).uppercase()
            row.tvName.text = nm
            row.tvRole.text = p.str("jabatan")
            row.tvStatus.badge(p.str("divisi"))

            val foto = p.str("foto")
            if (foto.isNotBlank()) {
                try {
                    val bytes = android.util.Base64.decode(foto, android.util.Base64.DEFAULT)
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    row.ivAvatar.setImageBitmap(bmp)
                    row.ivAvatar.visibility = View.VISIBLE
                    row.tvInitial.visibility = View.GONE
                } catch (_: Exception) {
                    row.ivAvatar.visibility = View.GONE
                    row.tvInitial.visibility = View.VISIBLE
                }
            } else {
                row.ivAvatar.visibility = View.GONE
                row.tvInitial.visibility = View.VISIBLE
            }

            row.root.setOnClickListener { push(DetailFragment.newInstance("pengurus", p.id)) }
            col.addView(row.root)
        }
        card.addView(col)
        b.llStruktur.addView(card)
    }

    override fun onDestroyView() {
        stop?.invoke(); stop = null
        super.onDestroyView()
        _b = null
    }
}
