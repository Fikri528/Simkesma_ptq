package id.ac.unimal.simkesma.ui.list

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import androidx.core.os.bundleOf
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import id.ac.unimal.simkesma.R
import id.ac.unimal.simkesma.data.*
import id.ac.unimal.simkesma.databinding.FragmentEntityListBinding
import id.ac.unimal.simkesma.databinding.ItemChipFilterBinding
import id.ac.unimal.simkesma.databinding.ItemPillBinding
import id.ac.unimal.simkesma.ui.BaseFragment
import id.ac.unimal.simkesma.ui.MainActivity
import id.ac.unimal.simkesma.ui.detail.DetailFragment
import id.ac.unimal.simkesma.ui.form.FormFragment
import id.ac.unimal.simkesma.util.UiExt

class EntityListFragment : BaseFragment(R.layout.fragment_entity_list) {
    companion object {
        fun newInstance(node: String, top: Boolean = false) = EntityListFragment().apply {
            arguments = bundleOf("node" to node, "top" to top)
        }
    }

    override val tealStatus: Boolean get() = !(arguments?.getBoolean("top") ?: false)

    private var _b: FragmentEntityListBinding? = null
    private val b get() = _b!!
    private lateinit var ent: Entity
    private lateinit var vm: ListViewModel
    private lateinit var adapter: CardAdapter
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { showError() }
    private val pills = ArrayList<Pair<Filter?, MaterialButton>>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentEntityListBinding.bind(view)
        ent = Entities.get(requireArguments().getString("node")!!)
        vm = ViewModelProvider(this, ListViewModel.Factory(ent))[ListViewModel::class.java]
        val top = requireArguments().getBoolean("top")

        b.headerMain.root.visibility = if (top) View.VISIBLE else View.GONE
        b.headerSub.root.visibility = if (top) View.GONE else View.VISIBLE
        b.headerMain.tvTitle.setText(ent.title)
        b.headerMain.btnBell.setOnClickListener { push(newInstance("pengumuman")) }
        b.headerMain.btnAvatar.setOnClickListener { main().selectTab(R.id.nav_profil) }
        b.headerSub.tvTitle.setText(ent.title)
        b.headerSub.btnBack.setOnClickListener { back() }
        b.headerSub.btnAction.setText(ent.addLabel)
        b.headerSub.btnAction.setOnClickListener { add() }
        b.fab.setText(ent.fabLabel)
        b.fab.setOnClickListener { add() }
        b.search.btnFilter.setOnClickListener { reset() }
        b.search.etSearch.setHint(R.string.hint_search)
        b.tvReset.setOnClickListener { reset() }
        b.search.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (vm.q != s.toString()) { vm.q = s.toString(); vm.refilter() }
            }
        })

        adapter = CardAdapter(ent, { push(DetailFragment.newInstance(ent.node, it.id)) }, ::onSecondary)
        b.rv.layoutManager = LinearLayoutManager(requireContext())
        b.rv.adapter = adapter

        if (ent.boxStyle) buildBoxes() else buildPills()
        b.error.btnRetry.setOnClickListener { showLoading() }
        b.empty.btnEmptySecondary.setOnClickListener { reset() }
        b.empty.btnEmptyPrimary.setOnClickListener { add() }

        // Menjaga daftar dinamis (divisi) tetap aktif agar pilihan filter tersedia.
        Repo.live("divisi").observe(viewLifecycleOwner) { }
        if (ent.node == "divisi" || ent.node == "anggota") {
            Repo.live("anggota").observe(viewLifecycleOwner) { r ->
                if (r is Resource.Success && ent.node == "divisi") {
                    adapter.counts = r.data.groupingBy { it.str("divisi") }.eachCount()
                    adapter.notifyDataSetChanged()
                }
            }
        }
        Session.role.observe(viewLifecycleOwner) { updateAdminUi() }
        vm.result.observe(viewLifecycleOwner) { render(it) }
        showLoadingIfNeeded()
    }

    private fun updateAdminUi() {
        val admin = Session.isAdmin
        b.fab.visibility = if (admin) View.VISIBLE else View.GONE
        b.headerSub.btnAction.visibility = if (admin) View.VISIBLE else View.GONE
        adapter.admin = admin
        adapter.notifyDataSetChanged()
    }

    private fun add() {
        if (!Session.isAdmin) { UiExt.toast(requireContext(), R.string.err_admin_only); return }
        push(FormFragment.newInstance(ent.node, null))
    }

    private fun onSecondary(item: Item, c: Card) {
        if (c.secondaryKind == 1) push(FormFragment.newInstance(ent.node, item.id))
        else FileHelper.download(requireContext(), item.id)
    }

    // ---------- filter UI ----------
    private fun optionsOf(f: Filter): List<String> =
        if (f.source != null) Repo.values(f.source, f.sourceKey)
        else resources.getStringArray(f.options).toList()

    private fun buildPills() {
        b.scrollPills.visibility = View.VISIBLE
        b.pillRow.removeAllViews(); pills.clear()
        val sortNames = resources.getStringArray(R.array.sort_options)
        val sortPill = ItemPillBinding.inflate(layoutInflater, b.pillRow, false).root
        sortPill.backgroundTintList = android.content.res.ColorStateList.valueOf(requireContext().getColor(R.color.primary))
        sortPill.setTextColor(requireContext().getColor(R.color.white))
        sortPill.iconTint = android.content.res.ColorStateList.valueOf(requireContext().getColor(R.color.white))
        sortPill.text = sortNames[vm.sort]
        sortPill.setOnClickListener { v ->
            popup(v, sortNames.toList()) { i, _ -> vm.sort = i; sortPill.text = sortNames[i]; vm.refilter() }
        }
        b.pillRow.addView(sortPill)
        pills.add(null to sortPill)
        ent.filters.forEach { f ->
            val p = ItemPillBinding.inflate(layoutInflater, b.pillRow, false).root
            p.text = vm.sel[f.key] ?: getString(f.allLabel)
            p.setOnClickListener { v ->
                val opts = listOf(getString(f.allLabel)) + optionsOf(f)
                popup(v, opts) { i, name ->
                    if (i == 0) vm.sel.remove(f.key) else vm.sel[f.key] = name
                    p.text = name; vm.refilter()
                }
            }
            b.pillRow.addView(p)
            pills.add(f to p)
        }
    }

    private fun popup(anchor: View, items: List<String>, onPick: (Int, String) -> Unit) {
        val pm = PopupMenu(requireContext(), anchor)
        items.forEachIndexed { i, s -> pm.menu.add(0, i, i, s) }
        pm.setOnMenuItemClickListener { onPick(it.itemId, items[it.itemId]); true }
        pm.show()
    }

    private fun buildBoxes() {
        b.boxRow.visibility = View.VISIBLE
        b.scrollChips.visibility = View.VISIBLE
        val sortNames = resources.getStringArray(R.array.sort_options)
        b.ddSort.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, sortNames))
        b.ddSort.setText(sortNames[vm.sort], false)
        b.ddSort.setOnItemClickListener { _, _, pos, _ -> vm.sort = pos; vm.refilter() }

        val f = ent.filters.first()
        val all = getString(f.allLabel)
        val opts = listOf(all) + optionsOf(f)
        b.ddFilter.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, opts))
        b.ddFilter.setText(vm.sel[f.key] ?: all, false)
        b.ddFilter.setOnItemClickListener { _, _, pos, _ -> setCategory(f, opts[pos], pos == 0) }

        b.chipGroup.removeAllViews()
        opts.forEachIndexed { i, name ->
            val chip = ItemChipFilterBinding.inflate(layoutInflater, b.chipGroup, false).root
            chip.text = name
            chip.id = 7000 + i
            chip.isChecked = (vm.sel[f.key] ?: all) == name
            chip.setOnClickListener { setCategory(f, name, i == 0) }
            b.chipGroup.addView(chip)
        }
    }

    private fun setCategory(f: Filter, name: String, isAll: Boolean) {
        if (isAll) vm.sel.remove(f.key) else vm.sel[f.key] = name
        b.ddFilter.setText(name, false)
        for (i in 0 until b.chipGroup.childCount) {
            val c = b.chipGroup.getChildAt(i) as com.google.android.material.chip.Chip
            c.isChecked = c.text.toString() == name
        }
        vm.refilter()
    }

    private fun reset() {
        vm.q = ""; vm.sort = 0; vm.sel.clear()
        b.search.etSearch.setText("")
        if (ent.boxStyle) buildBoxes() else buildPills()
        vm.refilter()
    }

    // ---------- state rendering ----------
    private fun showLoadingIfNeeded() {
        if (vm.result.value is Resource.Success) return
        showLoading()
    }

    private fun showLoading() {
        b.rv.visibility = View.GONE; b.scrollEmpty.visibility = View.GONE; b.error.root.visibility = View.GONE
        b.loading.visibility = View.VISIBLE
        handler.removeCallbacks(timeout); handler.postDelayed(timeout, 12000)
    }

    private fun showError() {
        if (_b == null) return
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
                val n = r.data.size
                b.tvCount.text = HtmlCompat.fromHtml(getString(R.string.count_fmt, n, getString(ent.unit)), HtmlCompat.FROM_HTML_MODE_LEGACY)
                if (n == 0) showEmpty(vm.rawCount() > 0) else {
                    b.scrollEmpty.visibility = View.GONE; b.rv.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showEmpty(filtered: Boolean) {
        b.rv.visibility = View.GONE; b.scrollEmpty.visibility = View.VISIBLE
        val e = b.empty
        val name = getString(ent.singular)
        e.tvEmptyPill.setText(if (filtered) R.string.empty_pill_search else R.string.empty_pill)
        e.tvEmptyTitle.text = getString(if (filtered) R.string.empty_title_search else R.string.empty_title, name)
        e.tvEmptyDesc.text = getString(if (filtered) R.string.empty_desc_search else R.string.empty_desc, getString(ent.title))
        e.btnEmptyPrimary.visibility = if (Session.isAdmin && !filtered) View.VISIBLE else View.GONE
        e.btnEmptyPrimary.setText(ent.addLabel)
        e.btnEmptySecondary.visibility = if (filtered) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        handler.removeCallbacks(timeout)
        super.onDestroyView()
        _b = null
    }
}
