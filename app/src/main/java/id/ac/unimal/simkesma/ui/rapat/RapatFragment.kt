package id.ac.unimal.simkesma.ui.rapat

import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.text.HtmlCompat
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.ac.unimal.simkesma.R
import id.ac.unimal.simkesma.data.*
import id.ac.unimal.simkesma.databinding.FragmentRapatBinding
import id.ac.unimal.simkesma.databinding.ItemRapatBinding
import id.ac.unimal.simkesma.ui.BaseFragment
import id.ac.unimal.simkesma.ui.detail.DetailFragment
import id.ac.unimal.simkesma.ui.form.FormFragment
import id.ac.unimal.simkesma.util.DateUtil
import id.ac.unimal.simkesma.util.UiExt

class RapatFragment : BaseFragment(R.layout.fragment_rapat) {
    private var _b: FragmentRapatBinding? = null
    private val b get() = _b!!
    private val vm: RapatViewModel by viewModels()
    private lateinit var adapter: RapatAdapter
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timeout = Runnable { if (_b != null && adapter.itemCount == 0 && vm.raw() == null) showError() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentRapatBinding.bind(view)
        b.header.tvTitle.setText(R.string.title_jadwal)
        b.header.btnBack.setOnClickListener { back() }
        b.header.btnAction.setText(R.string.btn_create_schedule)
        b.header.btnAction.setOnClickListener { add() }
        b.fab.setOnClickListener { add() }
        b.search.etSearch.setHint(R.string.hint_search_rapat)
        b.search.btnFilter.visibility = View.GONE

        val jenis = listOf(getString(R.string.all_jenis)) + resources.getStringArray(R.array.opt_jenis_rapat)
        val status = listOf(getString(R.string.all_status)) + resources.getStringArray(R.array.opt_status_rapat)
        b.ddJenis.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, jenis))
        b.ddStatus.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, status))
        b.ddJenis.setText(vm.jenis ?: jenis[0], false)
        b.ddStatus.setText(vm.status ?: status[0], false)
        b.ddJenis.setOnItemClickListener { _, _, p, _ -> vm.jenis = if (p == 0) null else jenis[p]; vm.refilter() }
        b.ddStatus.setOnItemClickListener { _, _, p, _ -> vm.status = if (p == 0) null else status[p]; vm.refilter() }
        b.search.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun afterTextChanged(s: Editable?) { if (vm.q != s.toString()) { vm.q = s.toString(); vm.refilter() } }
        })
        b.tabUpcoming.setOnClickListener { vm.upcoming = true; vm.refilter(); renderTabs() }
        b.tabHistory.setOnClickListener { vm.upcoming = false; vm.refilter(); renderTabs() }
        renderTabs()

        adapter = RapatAdapter(
            onOpen = { push(DetailFragment.newInstance("rapat", it.id)) },
            onConfirm = { item ->
                val uid = Session.uid ?: return@RapatAdapter
                Repo.setValue("rapat/${item.id}/peserta/$uid", Session.nama) { e ->
                    if (e != null) UiExt.toast(requireContext(), R.string.err_generic)
                }
            }
        )
        b.rv.layoutManager = LinearLayoutManager(requireContext())
        b.rv.adapter = adapter
        b.error.btnRetry.setOnClickListener { showLoading() }
        b.empty.btnEmptyPrimary.setOnClickListener { add() }
        b.empty.btnEmptySecondary.setOnClickListener { resetFilters() }

        Session.role.observe(viewLifecycleOwner) {
            val a = Session.isAdmin
            b.fab.visibility = if (a) View.VISIBLE else View.GONE
            b.header.btnAction.visibility = if (a) View.VISIBLE else View.GONE
        }
        vm.result.observe(viewLifecycleOwner) { render(it) }
        vm.counts.observe(viewLifecycleOwner) { (u, h) -> b.badgeUpcoming.text = "$u"; b.badgeHistory.text = "$h" }
        if (vm.raw() == null) showLoading()
    }

    private fun add() {
        if (!Session.isAdmin) { UiExt.toast(requireContext(), R.string.err_admin_only); return }
        push(FormFragment.newInstance("rapat", null))
    }

    private fun resetFilters() {
        vm.q = ""; vm.jenis = null; vm.status = null
        b.search.etSearch.setText("")
        b.ddJenis.setText(getString(R.string.all_jenis), false)
        b.ddStatus.setText(getString(R.string.all_status), false)
        vm.refilter()
    }

    private fun renderTabs() {
        val ctx = requireContext()
        val on = ctx.getColor(R.color.primary); val off = ctx.getColor(R.color.text_secondary)
        listOf(
            Triple(b.tabUpcoming, b.tvTabUpcoming, b.badgeUpcoming) to vm.upcoming,
            Triple(b.tabHistory, b.tvTabHistory, b.badgeHistory) to !vm.upcoming
        ).forEach { (t, active) ->
            t.first.setBackgroundResource(if (active) R.drawable.bg_segment_active else 0)
            t.second.setTextColor(if (active) on else off)
            t.third.setTextColor(if (active) ctx.getColor(R.color.white) else off)
            t.third.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (active) on else ctx.getColor(R.color.container_high)
            )
        }
    }

    private fun showLoading() {
        b.rv.visibility = View.GONE; b.scrollEmpty.visibility = View.GONE; b.error.root.visibility = View.GONE
        b.loading.visibility = View.VISIBLE
        handler.removeCallbacks(timeout); handler.postDelayed(timeout, 12000)
    }

    private fun showError() {
        b.rv.visibility = View.GONE; b.scrollEmpty.visibility = View.GONE; b.loading.visibility = View.GONE
        b.error.root.visibility = View.VISIBLE
    }

    private fun render(r: Resource<List<Item>>) {
        when (r) {
            is Resource.Loading -> showLoading()
            is Resource.Error -> { handler.removeCallbacks(timeout); b.error.tvErrorMsg.text = r.message; showError() }
            is Resource.Success -> {
                handler.removeCallbacks(timeout)
                b.loading.visibility = View.GONE; b.error.root.visibility = View.GONE
                adapter.submitList(r.data)
                if (r.data.isEmpty()) {
                    b.rv.visibility = View.GONE; b.scrollEmpty.visibility = View.VISIBLE
                    val filtered = (vm.raw()?.isNotEmpty() == true)
                    val e = b.empty
                    e.tvEmptyPill.setText(if (filtered) R.string.empty_pill_search else R.string.empty_pill)
                    e.tvEmptyTitle.text = getString(
                        if (filtered) R.string.empty_title_search else R.string.empty_title, getString(R.string.sing_rapat)
                    )
                    e.tvEmptyDesc.text = getString(
                        if (filtered) R.string.empty_desc_search else R.string.empty_desc, getString(R.string.ent_rapat)
                    )
                    e.btnEmptyPrimary.visibility = if (Session.isAdmin && !filtered) View.VISIBLE else View.GONE
                    e.btnEmptyPrimary.setText(R.string.add_rapat)
                    e.btnEmptySecondary.visibility = if (filtered) View.VISIBLE else View.GONE
                } else { b.scrollEmpty.visibility = View.GONE; b.rv.visibility = View.VISIBLE }
            }
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacks(timeout)
        super.onDestroyView()
        _b = null
    }
}

class RapatAdapter(
    private val onOpen: (Item) -> Unit,
    private val onConfirm: (Item) -> Unit
) : ListAdapter<Item, RapatAdapter.VH>(object : DiffUtil.ItemCallback<Item>() {
    override fun areItemsTheSame(a: Item, b: Item) = a.id == b.id
    override fun areContentsTheSame(a: Item, b: Item) = a == b
}) {
    class VH(val b: ItemRapatBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, v: Int) = VH(ItemRapatBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val i = getItem(pos); val b = h.b; val ctx = b.root.context
        val upcoming = i.str("status").equals("Akan Datang", true)
        val wajib = i.str("kewajiban").equals("Wajib Hadir", true)
        @Suppress("UNCHECKED_CAST")
        val peserta = (i.data["peserta"] as? Map<String, Any?>).orEmpty()
        val confirmed = Session.uid?.let { peserta.containsKey(it) } == true

        b.card.strokeColor = ctx.getColor(if (upcoming && wajib) R.color.teal_accent else R.color.border)
        b.tvBadge.text = if (upcoming) i.str("kewajiban") else i.str("status")
        val (bg, fg, _) = id.ac.unimal.simkesma.util.StatusStyle.colors(ctx, if (upcoming) (if (wajib) "wajib hadir" else "aktif") else i.str("status"))
        b.tvBadge.setTextColor(fg)
        b.tvBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(bg)
        b.tvBadge.setCompoundDrawablesWithIntrinsicBounds(
            if (upcoming && !wajib) R.drawable.ic_groups else R.drawable.bg_dot, 0, 0, 0
        )
        b.tvBadge.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(if (upcoming && wajib) ctx.getColor(R.color.amber) else fg)
        b.tvJenis.text = i.str("jenis")
        b.tvTitle.text = i.str("judul")

        val date = DateUtil.display(i.str("tanggal"), "EEEE, d MMM yyyy")
        b.tvTime.text = HtmlCompat.fromHtml(
            ctx.getString(R.string.fmt_time_html, TextUtils.htmlEncode(date), TextUtils.htmlEncode(i.str("jam"))),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        val lokasi = i.str("lokasi")
        b.tvPlace.text = lokasi
        b.ivPlace.setImageResource(if (lokasi.contains("meet", true) || lokasi.contains("zoom", true) || lokasi.contains("online", true)) R.drawable.ic_videocam else R.drawable.ic_location_on)
        b.tvLeader.text = HtmlCompat.fromHtml(
            ctx.getString(R.string.fmt_leader_html, TextUtils.htmlEncode(i.str("pemimpin"))), HtmlCompat.FROM_HTML_MODE_LEGACY
        )

        if (upcoming) {
            b.btnConfirm.visibility = if (confirmed) View.GONE else View.VISIBLE
            b.tvConfirmed.visibility = if (confirmed) View.VISIBLE else View.GONE
            b.spacer.visibility = if (confirmed) View.VISIBLE else View.GONE
            b.btnAgenda.setText(if (confirmed) R.string.btn_detail_rapat else R.string.btn_view_agenda)
        } else {
            b.btnConfirm.visibility = View.GONE; b.tvConfirmed.visibility = View.GONE; b.spacer.visibility = View.VISIBLE
            b.btnAgenda.setText(R.string.btn_view_notula)
        }
        b.btnConfirm.setOnClickListener { onConfirm(i) }
        b.btnAgenda.setOnClickListener { onOpen(i) }
        b.root.setOnClickListener { onOpen(i) }
    }
}
