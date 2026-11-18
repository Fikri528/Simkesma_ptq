package id.ac.unimal.simkesma.ui.hub

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import id.ac.unimal.simkesma.R
import id.ac.unimal.simkesma.databinding.FragmentHubBinding
import id.ac.unimal.simkesma.databinding.ItemQuickActionBinding
import id.ac.unimal.simkesma.ui.BaseFragment
import id.ac.unimal.simkesma.ui.list.EntityListFragment
import id.ac.unimal.simkesma.ui.pengurus.PengurusFragment
import id.ac.unimal.simkesma.ui.presensi.PresensiFragment
import id.ac.unimal.simkesma.ui.rapat.RapatFragment
import id.ac.unimal.simkesma.ui.struktur.StrukturFragment

class HubFragment : BaseFragment(R.layout.fragment_hub) {
    override val tealStatus: Boolean get() = false

    private class M(val icon: Int, val title: Int, val sub: Int, val make: () -> Fragment)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentHubBinding.bind(view)
        b.header.tvTitle.setText(R.string.nav_sekretariat)
        b.header.btnBell.setOnClickListener { push(EntityListFragment.newInstance("pengumuman")) }
        b.header.btnAvatar.setOnClickListener { main().selectTab(R.id.nav_profil) }
        val menus = listOf(
            M(R.drawable.ic_admin_panel_settings, R.string.ent_pengurus, R.string.hub_pengurus) { PengurusFragment() },
            M(R.drawable.ic_calendar_today, R.string.ent_periode, R.string.hub_periode) { EntityListFragment.newInstance("periode") },
            M(R.drawable.ic_mark_email_unread, R.string.ent_surat_masuk, R.string.hub_surat_masuk) { EntityListFragment.newInstance("surat_masuk") },
            M(R.drawable.ic_send, R.string.ent_surat_keluar, R.string.hub_surat_keluar) { EntityListFragment.newInstance("surat_keluar") },
            M(R.drawable.ic_event, R.string.title_jadwal, R.string.hub_jadwal) { RapatFragment() },
            M(R.drawable.ic_assignment, R.string.ent_proker, R.string.hub_proker) { EntityListFragment.newInstance("program_kerja") },
            M(R.drawable.ic_groups, R.string.ent_divisi, R.string.hub_divisi) { EntityListFragment.newInstance("divisi") },
            M(R.drawable.ic_account_tree, R.string.ent_struktur, R.string.hub_struktur) { StrukturFragment() },
            M(R.drawable.ic_qr_code_scanner, R.string.ent_presensi, R.string.hub_presensi) { PresensiFragment() }
        )
        menus.forEach { m ->
            val item = ItemQuickActionBinding.inflate(layoutInflater, b.grid, false)
            item.ivIcon.setImageResource(m.icon)
            item.tvTitle.setText(m.title)
            item.tvSub.setText(m.sub)
            item.root.setOnClickListener { push(m.make()) }
            b.grid.addView(item.root)
        }
    }
}
