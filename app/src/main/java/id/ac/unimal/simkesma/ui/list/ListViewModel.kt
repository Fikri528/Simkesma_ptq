package id.ac.unimal.simkesma.ui.list

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import id.ac.unimal.simkesma.data.Entity
import id.ac.unimal.simkesma.data.Item
import id.ac.unimal.simkesma.data.Repo
import id.ac.unimal.simkesma.data.Resource

class ListViewModel(val ent: Entity) : ViewModel() {
    private val src = Repo.live(ent.node)
    private val trigger = MutableLiveData(0)
    var q = ""
    var sort = 0 // 0 terbaru, 1 terlama, 2 A-Z
    val sel = HashMap<String, String>()

    val result = MediatorLiveData<Resource<List<Item>>>().apply {
        fun recompute() {
            val r = src.value ?: return
            value = if (r is Resource.Success) Resource.Success(filterAndSort(r.data)) else r
        }
        addSource(src) { recompute() }
        addSource(trigger) { recompute() }
    }

    fun rawCount(): Int = (src.value as? Resource.Success)?.data?.size ?: 0
    fun refilter() { trigger.value = (trigger.value ?: 0) + 1 }

    private fun filterAndSort(list: List<Item>): List<Item> {
        val query = q.trim().lowercase()
        var out = list.filter { item ->
            sel.all { (k, v) -> item.str(k) == v } &&
                (query.isEmpty() || item.data.values.any { it is String && it.lowercase().contains(query) })
        }
        val key = { i: Item -> i.str(ent.dateKey).ifEmpty { i.str("createdAt") } + i.str("createdAt") }
        out = when (sort) {
            1 -> out.sortedBy(key)
            2 -> out.sortedBy { it.str(ent.titleKey).lowercase() }
            else -> out.sortedByDescending(key)
        }
        return out
    }

    class Factory(private val ent: Entity) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ListViewModel(ent) as T
    }
}
