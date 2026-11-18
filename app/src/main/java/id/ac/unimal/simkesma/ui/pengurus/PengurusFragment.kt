package id.ac.unimal.simkesma.ui.pengurus

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import id.ac.unimal.simkesma.R
import id.ac.unimal.simkesma.data.*
import id.ac.unimal.simkesma.databinding.DialogAssignPengurusBinding
import id.ac.unimal.simkesma.databinding.FragmentPengurusBinding
import id.ac.unimal.simkesma.ui.BaseFragment
import id.ac.unimal.simkesma.ui.detail.DetailFragment
import id.ac.unimal.simkesma.ui.form.FormFragment
import id.ac.unimal.simkesma.ui.list.CardAdapter
import id.ac.unimal.simkesma.ui.struktur.StrukturFragment
import id.ac.unimal.simkesma.util.UiExt

class PengurusFragment : BaseFragment(R.layout.fragment_pengurus) {
    private var _b: FragmentPengurusBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: CardAdapter
    private var selectedPeriodeFilter = "2025-2026"
    private var rawOfficers = emptyList<Item>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentPengurusBinding.bind(view)
        b.header.tvTitle.setText(R.string.ent_pengurus)
        b.header.btnBack.setOnClickListener { back() }
        b.header.btnAction.setText(R.string.btn_add_pengurus)
        b.header.btnAction.setIconResource(R.drawable.ic_add)
        b.header.btnAction.setOnClickListener { showAssignDialog() }

        b.btnViewStruktur.setOnClickListener { push(StrukturFragment()) }

        adapter = CardAdapter(
            ent = Entities.anggota,
            onItem = { push(DetailFragment.newInstance("anggota", it.id)) },
            onSecondary = { item, _ -> push(FormFragment.newInstance("anggota", item.id)) }
        )
        b.rv.layoutManager = LinearLayoutManager(requireContext())
        b.rv.adapter = adapter

        Session.role.observe(viewLifecycleOwner) {
            val admin = Session.isAdmin
            b.header.btnAction.visibility = if (admin) View.VISIBLE else View.GONE
            adapter.admin = admin
            adapter.notifyDataSetChanged()
        }

        val defaultPeriodes = listOf("Semua Periode", "2024-2025", "2025-2026", "2026-2027")
        b.ddPeriodeFilter.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, defaultPeriodes))
        b.ddPeriodeFilter.setText(selectedPeriodeFilter, false)
        b.ddPeriodeFilter.setOnItemClickListener { _, _, p, _ ->
            selectedPeriodeFilter = defaultPeriodes[p]
            applyFilter()
        }

        Repo.live("periode").observe(viewLifecycleOwner) { r ->
            if (_b == null) return@observe
            if (r is Resource.Success) {
                val list = listOf("Semua Periode") + r.data.map { it.str("nama") }.filter { it.isNotBlank() }.distinct()
                b.ddPeriodeFilter.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, list))
            }
        }

        Repo.live("anggota").observe(viewLifecycleOwner) { r ->
            if (_b == null) return@observe
            if (r is Resource.Success) {
                b.loading.visibility = View.GONE
                rawOfficers = r.data.filter {
                    val j = it.str("jabatan")
                    j.isNotBlank() && !j.equals("Anggota", true)
                }
                applyFilter()
            } else if (r is Resource.Error) {
                b.loading.visibility = View.GONE
                b.tvEmpty.visibility = View.VISIBLE
            }
        }
    }

    private fun applyFilter() {
        val filtered = if (selectedPeriodeFilter == "Semua Periode") rawOfficers
        else rawOfficers.filter { it.str("periode").isBlank() || it.str("periode") == selectedPeriodeFilter }
        adapter.submitList(filtered)
        b.tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showAssignDialog() {
        val ar = Repo.live("anggota").value as? Resource.Success ?: return
        val d = DialogAssignPengurusBinding.inflate(layoutInflater)

        val members = ar.data.sortedBy { it.str("namaLengkap") }
        val memberNames = members.map { it.str("namaLengkap") }
        d.tilMember.hint = getString(R.string.select_member)
        d.ddMember.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, memberNames))

        val jabatanOptions = resources.getStringArray(R.array.opt_jabatan).filter { !it.equals("Anggota", true) }
        d.tilJabatan.hint = getString(R.string.select_jabatan)
        d.ddJabatan.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, jabatanOptions))

        val divNames = listOf("-") + resources.getStringArray(R.array.opt_divisi).toList()
        d.tilDivisi.hint = getString(R.string.select_divisi)
        d.ddDivisi.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, divNames))

        val periodes = listOf("2024-2025", "2025-2026", "2026-2027")
        d.tilPeriode.hint = getString(R.string.select_periode)
        d.ddPeriode.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, periodes))
        d.ddPeriode.setText("2025-2026", false)

        val dlg = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.title_assign_pengurus)
            .setView(d.root)
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.btn_save, null)
            .create()

        dlg.setOnShowListener {
            dlg.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val memberName = d.ddMember.text.toString().trim()
                val selectedJabatan = d.ddJabatan.text.toString().trim()
                val selectedDiv = d.ddDivisi.text.toString().trim()
                val selectedPer = d.ddPeriode.text.toString().trim()

                if (memberName.isEmpty()) {
                    d.tilMember.error = getString(R.string.err_select_member)
                    return@setOnClickListener
                }
                if (selectedJabatan.isEmpty()) {
                    d.tilJabatan.error = getString(R.string.err_select_jabatan)
                    return@setOnClickListener
                }

                val member = members.firstOrNull { it.str("namaLengkap") == memberName }
                if (member == null) return@setOnClickListener

                val targetDiv = if (selectedDiv == "-") "" else selectedDiv
                val updateData = mapOf("jabatan" to selectedJabatan, "divisi" to targetDiv, "periode" to selectedPer)

                Repo.update("anggota/${member.id}", updateData) { err ->
                    if (_b == null) return@update
                    if (err != null) {
                        UiExt.toast(requireContext(), getString(R.string.err_save, err))
                    } else {
                        Repo.update("users/${member.id}", updateData)
                        UiExt.toast(requireContext(), R.string.saved_ok)
                    }
                }
                dlg.dismiss()
            }
        }
        dlg.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
