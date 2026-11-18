package id.ac.unimal.simkesma.ui.dashboard

import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import androidx.core.text.HtmlCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import id.ac.unimal.simkesma.R
import id.ac.unimal.simkesma.data.*
import id.ac.unimal.simkesma.databinding.DialogInputBinding
import id.ac.unimal.simkesma.databinding.FragmentDashboardBinding
import id.ac.unimal.simkesma.databinding.ItemQuickActionBinding
import id.ac.unimal.simkesma.databinding.ItemStatBinding
import id.ac.unimal.simkesma.ui.BaseFragment
import id.ac.unimal.simkesma.ui.detail.DetailFragment
import id.ac.unimal.simkesma.ui.form.FormFragment
import id.ac.unimal.simkesma.ui.list.EntityListFragment
import id.ac.unimal.simkesma.ui.rapat.RapatFragment
import id.ac.unimal.simkesma.ui.struktur.StrukturFragment
import id.ac.unimal.simkesma.util.DateUtil

class DashboardFragment : BaseFragment(R.layout.fragment_dashboard) {
    override val tealStatus: Boolean get() = false
    private var _b: FragmentDashboardBinding? = null
    private val b get() = _b!!
    private val stops = ArrayList<() -> Unit>()

    companion object { private var seeded = false }

    private class Quick(val icon: Int, val title: Int, val sub: String, val go: () -> Unit)
    private val statViews = HashMap<String, ItemStatBinding>()
    private var quickSubs: Array<TextView> = emptyArray()
    private var tadabburTeks = ""
    private var tadabburSumber = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentDashboardBinding.bind(view)
        b.header.tvTitle.setText(R.string.nav_dashboard)
        b.header.btnBell.setOnClickListener { push(EntityListFragment.newInstance("pengumuman")) }
        b.header.btnAvatar.setOnClickListener { main().selectTab(R.id.nav_profil) }
        b.tvHijri.text = DateUtil.hijri(resources.getStringArray(R.array.hijri_months))
        b.tvSeeCalendar.setOnClickListener { push(RapatFragment()) }
        b.tvSeeAnnouncements.setOnClickListener { push(EntityListFragment.newInstance("pengumuman")) }
        b.btnEditTadabbur.setOnClickListener { editTadabbur() }

        buildQuick()
        buildStats()
        renderTadabbur()

        Session.profile.observe(viewLifecycleOwner) { renderWelcome() }
        Session.role.observe(viewLifecycleOwner) {
            renderWelcome()
            b.btnEditTadabbur.visibility = if (Session.isAdmin) View.VISIBLE else View.GONE
            b.btnMeetingAction.visibility = if (Session.isAdmin) View.VISIBLE else View.GONE
            seedDivisi()
        }
        Repo.live("anggota").observe(viewLifecycleOwner) { r ->
            if (r is Resource.Success) {
                setStat("anggota", r.data.size)
                setStat("pengurus", r.data.count { it.str("jabatan").let { j -> j.isNotBlank() && j != "Anggota" } })
                quickSubs[0].text = getString(R.string.fmt_terdata, r.data.size)
            }
        }
        Repo.live("divisi").observe(viewLifecycleOwner) { r -> if (r is Resource.Success) { setStat("divisi", r.data.size); seedDivisi() } }
        Repo.live("program_kerja").observe(viewLifecycleOwner) { r -> if (r is Resource.Success) setStat("proker", r.data.size) }
        Repo.live("surat_masuk").observe(viewLifecycleOwner) { r -> if (r is Resource.Success) setStat("masuk", r.data.size) }
        Repo.live("surat_keluar").observe(viewLifecycleOwner) { r -> if (r is Resource.Success) setStat("keluar", r.data.size) }
        Repo.live("kehadiran").observe(viewLifecycleOwner) { r ->
            if (r is Resource.Success) setStat("hadir", r.data.count { it.str("uid") == Session.uid })
        }
        Repo.live("rapat").observe(viewLifecycleOwner) { r -> if (r is Resource.Success) renderMeeting(r.data) }
        Repo.live("pengumuman").observe(viewLifecycleOwner) { r -> if (r is Resource.Success) renderAnnouncements(r.data) }

        stops.add(Repo.watch("pengaturan/periode") { s ->
            if (_b == null) return@watch
            val p = s.getValue(String::class.java).orEmpty()
            b.tvPeriode.text = if (p.isBlank()) "" else getString(R.string.periode_fmt, p)
        })
        stops.add(Repo.watch("pengaturan/tadabbur") { s ->
            tadabburTeks = s.child("teks").getValue(String::class.java).orEmpty()
            tadabburSumber = s.child("sumber").getValue(String::class.java).orEmpty()
            if (_b != null) renderTadabbur()
        })
    }

    private fun buildQuick() {
        val items = listOf(
            Quick(R.drawable.ic_group, R.string.q_anggota, getString(R.string.fmt_terdata, 0)) { main().selectTab(R.id.nav_anggota) },
            Quick(R.drawable.ic_folder_open, R.string.q_surat, getString(R.string.q_surat_sub)) { push(EntityListFragment.newInstance("surat_keluar")) },
            Quick(R.drawable.ic_event, R.string.q_agenda, getString(R.string.q_agenda_sub)) { push(RapatFragment()) },
            Quick(R.drawable.ic_account_tree, R.string.q_struktur, getString(R.string.q_struktur_sub)) { push(StrukturFragment()) }
        )
        val subs = ArrayList<TextView>()
        items.forEach { q ->
            val v = ItemQuickActionBinding.inflate(layoutInflater, b.gridQuick, false)
            v.ivIcon.setImageResource(q.icon); v.tvTitle.setText(q.title); v.tvSub.text = q.sub
            v.root.setOnClickListener { q.go() }
            b.gridQuick.addView(v.root); subs.add(v.tvSub)
        }
        quickSubs = subs.toTypedArray()
    }

    private fun buildStats() {
        data class S(val key: String, val icon: Int, val tag: Int, val label: Int)
        listOf(
            S("anggota", R.drawable.ic_groups, R.string.tag_total, R.string.st_anggota),
            S("pengurus", R.drawable.ic_badge, R.string.tag_bph, R.string.st_pengurus),
            S("divisi", R.drawable.ic_apartment, R.string.tag_bidang, R.string.st_divisi),
            S("proker", R.drawable.ic_assignment, R.string.tag_program, R.string.st_proker),
            S("masuk", R.drawable.ic_mark_email_unread, R.string.tag_inbound, R.string.st_masuk),
            S("keluar", R.drawable.ic_send, R.string.tag_outbound, R.string.st_keluar),
            S("hadir", R.drawable.ic_how_to_reg, R.string.tag_presensi, R.string.st_hadir)
        ).forEach { s ->
            val v = ItemStatBinding.inflate(layoutInflater, b.gridStats, false)
            v.ivIcon.setImageResource(s.icon); v.tvTag.setText(s.tag); v.tvLabel.setText(s.label)
            v.tvValue.text = "…"
            b.gridStats.addView(v.root); statViews[s.key] = v
        }
    }

    private fun setStat(key: String, n: Int) { if (_b != null) statViews[key]?.tvValue?.text = n.toString() }

    private fun renderWelcome() {
        if (_b == null) return
        val p = Session.profile.value.orEmpty()
        val nama = Session.nama.substringBefore(" ")
        b.tvGreeting.text = getString(R.string.greeting, nama)
        val admin = Session.isAdmin
        b.tvRoleLine.setText(if (admin) R.string.role_line_admin else R.string.role_line_member)
        b.tvRoleChip.setText(R.string.chip_active)
        val nim = p["nim"]?.toString().orEmpty(); val div = p["divisi"]?.toString().orEmpty()
        b.tvNimLine.text = listOfNotNull(
            nim.takeIf { it.isNotBlank() }?.let { getString(R.string.fmt_nim, it) },
            div.takeIf { it.isNotBlank() }?.let { getString(R.string.fmt_divisi, it) }
        ).joinToString(" • ").ifBlank { getString(R.string.profile_incomplete) }
    }

    private fun renderMeeting(list: List<Item>) {
        if (_b == null) return
        val today = DateUtil.today()
        val next = list.filter { it.str("status").equals("Akan Datang", true) && it.str("tanggal") >= today }
            .minByOrNull { it.str("tanggal") + it.str("jam") }
        if (next == null) {
            b.ivMeeting.setImageResource(R.drawable.ic_event_busy)
            b.tvMeetingTitle.setText(R.string.no_meeting_title)
            b.tvMeetingDesc.setText(R.string.no_meeting_desc)
            b.tvMeetingStatus.setText(R.string.meeting_status_clear)
            b.btnMeetingAction.setText(R.string.btn_new_meeting)
            b.btnMeetingAction.setOnClickListener { push(FormFragment.newInstance("rapat", null)) }
        } else {
            b.ivMeeting.setImageResource(R.drawable.ic_event)
            b.tvMeetingTitle.text = next.str("judul")
            b.tvMeetingDesc.text = getString(
                R.string.fmt_meeting_desc, DateUtil.display(next.str("tanggal"), "EEEE, d MMM yyyy"), next.str("jam"), next.str("lokasi")
            )
            b.tvMeetingStatus.text = getString(R.string.fmt_leader_plain, next.str("pemimpin"))
            b.btnMeetingAction.setText(R.string.btn_detail_rapat)
            b.btnMeetingAction.visibility = View.VISIBLE
            b.btnMeetingAction.setOnClickListener { push(DetailFragment.newInstance("rapat", next.id)) }
        }
    }

    private fun renderAnnouncements(list: List<Item>) {
        if (_b == null) return
        b.llAnnouncements.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        b.listAnnouncements.removeAllViews()
        list.sortedByDescending { it.str("tanggal") + it.str("createdAt") }.take(3).forEach { i ->
            val t = TextView(requireContext()).apply {
                text = HtmlCompat.fromHtml(
                    "<b>${TextUtils.htmlEncode(i.str("judul"))}</b><br>${TextUtils.htmlEncode(i.str("isi"))}", HtmlCompat.FROM_HTML_MODE_LEGACY
                )
                maxLines = 3; ellipsize = TextUtils.TruncateAt.END
                setTextColor(requireContext().getColor(R.color.text_secondary)); textSize = 14f
                setBackgroundResource(R.drawable.bg_badge)
                backgroundTintList = android.content.res.ColorStateList.valueOf(requireContext().getColor(R.color.white))
                val p = (14 * resources.displayMetrics.density).toInt(); setPadding(p, p, p, p)
                layoutParams = android.widget.LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = p / 2 }
                setOnClickListener { push(DetailFragment.newInstance("pengumuman", i.id)) }
            }
            b.listAnnouncements.addView(t)
        }
    }

    private fun renderTadabbur() {
        val teks = tadabburTeks.ifBlank { getString(R.string.tadabbur_default) }
        val sumber = if (tadabburTeks.isBlank()) "" else tadabburSumber
        b.tvTadabbur.text = if (sumber.isBlank()) teks else "“$teks” ($sumber)"
        if (tadabburTeks.isBlank()) b.tvTadabbur.text = getString(R.string.tadabbur_default)
    }

    private fun editTadabbur() {
        val d = DialogInputBinding.inflate(layoutInflater)
        d.til1.hint = getString(R.string.f_tadabbur_teks); d.et1.setText(tadabburTeks)
        d.et1.minLines = 3
        d.et1.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        d.til2.hint = getString(R.string.f_tadabbur_sumber); d.et2.setText(tadabburSumber)
        d.til3.visibility = View.GONE
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.tadabbur_title).setView(d.root)
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                Repo.setValue("pengaturan/tadabbur", mapOf("teks" to d.et1.text.toString().trim(), "sumber" to d.et2.text.toString().trim()))
            }.show()
    }

    /** Mengisi 7 divisi awal sekali saja jika node divisi masih kosong (hanya admin). */
    private fun seedDivisi() {
        if (!Session.isAdmin) return
        val r = Repo.live("divisi").value as? Resource.Success ?: return
        if (r.data.isNotEmpty() || seeded) return
        seeded = true
        val now = System.currentTimeMillis()
        resources.getStringArray(R.array.opt_divisi_awal).forEachIndexed { i, nama ->
            Repo.save("divisi", Repo.newId("divisi"), mapOf("nama" to nama, "deskripsi" to "", "createdAt" to now + i)) { }
        }
    }

    override fun onDestroyView() {
        stops.forEach { it() }; stops.clear(); statViews.clear()
        super.onDestroyView(); _b = null
    }
}
