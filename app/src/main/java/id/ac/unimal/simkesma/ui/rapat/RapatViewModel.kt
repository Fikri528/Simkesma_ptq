package id.ac.unimal.simkesma.ui.rapat

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import id.ac.unimal.simkesma.data.Item
import id.ac.unimal.simkesma.data.Repo
import id.ac.unimal.simkesma.data.Resource

class RapatViewModel : ViewModel() {
    private val src = Repo.live("rapat")
    private val trigger = MutableLiveData(0)
    var upcoming = true
    var q = ""
    var jenis: String? = null
    var status: String? = null
    val counts = MutableLiveData(Pair(0, 0))

    val result = MediatorLiveData<Resource<List<Item>>>().apply {
        fun recompute() {
            val r = src.value ?: return
            if (r !is Resource.Success) { value = r; return }
            val query = q.trim().lowercase()
            val base = r.data.filter { it.str("jenis") == (jenis ?: it.str("jenis")) && it.str("status") == (status ?: it.str("status")) }
                .filter { query.isEmpty() || it.data.values.any { v -> v is String && v.lowercase().contains(query) } }
            val up = base.filter { it.str("status").equals("Akan Datang", true) }
            val hist = base.filter { !it.str("status").equals("Akan Datang", true) }
            counts.value = Pair(up.size, hist.size)
            value = Resource.Success(
                if (upcoming) up.sortedBy { it.str("tanggal") + it.str("jam") }
                else hist.sortedByDescending { it.str("tanggal") + it.str("jam") }
            )
        }
        addSource(src) { recompute() }
        addSource(trigger) { recompute() }
    }

    fun raw(): List<Item>? = (src.value as? Resource.Success)?.data
    fun refilter() { trigger.value = (trigger.value ?: 0) + 1 }
}
