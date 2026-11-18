package id.ac.unimal.simkesma.data

import androidx.lifecycle.LiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class NodeLiveData(private val ref: DatabaseReference) : androidx.lifecycle.MutableLiveData<Resource<List<Item>>>(Resource.Loading) {
    private val listener = object : ValueEventListener {
        @Suppress("UNCHECKED_CAST")
        override fun onDataChange(s: DataSnapshot) {
            val list = s.children.mapNotNull { c ->
                val m = c.value as? Map<String, Any?> ?: return@mapNotNull null
                Item(c.key ?: return@mapNotNull null, m)
            }
            Repo.memoryCache[ref.key.orEmpty()] = list
            value = Resource.Success(list)
        }

        override fun onCancelled(e: DatabaseError) {
            value = Resource.Error(e.message)
        }
    }

    override fun onActive() { ref.addValueEventListener(listener) }
    override fun onInactive() { ref.removeEventListener(listener) }
}

object Repo {
    private val root get() = FirebaseDatabase.getInstance().reference
    val memoryCache = HashMap<String, List<Item>>()

    fun ref(path: String): DatabaseReference = root.child(path)

    fun live(node: String): LiveData<Resource<List<Item>>> =
        NodeLiveData(root.child(node))

    fun values(node: String, key: String): List<String> {
        val cached = memoryCache[node]
        if (cached != null) {
            return cached.map { it.str(key) }.filter { it.isNotBlank() }.sorted()
        }
        val r = live(node).value as? Resource.Success
        if (r != null) {
            return r.data.map { it.str(key) }.filter { it.isNotBlank() }.sorted()
        }
        return emptyList()
    }

    fun newId(node: String): String = root.child(node).push().key ?: System.currentTimeMillis().toString()

    fun save(node: String, id: String, data: Map<String, Any?>, done: (String?) -> Unit) {
        root.child(node).child(id).updateChildren(data).addOnCompleteListener { done(it.exception?.message) }
    }

    fun setValue(path: String, value: Any?, done: (String?) -> Unit = {}) {
        root.child(path).setValue(value).addOnCompleteListener { done(it.exception?.message) }
    }

    fun update(path: String, data: Map<String, Any?>, done: (String?) -> Unit = {}) {
        root.child(path).updateChildren(data).addOnCompleteListener { done(it.exception?.message) }
    }

    fun delete(node: String, id: String, done: (String?) -> Unit) {
        root.child(node).child(id).removeValue().addOnCompleteListener { task ->
            if (task.isSuccessful) root.child("files").child(id).removeValue()
            done(task.exception?.message)
        }
    }

    fun saveFile(id: String, name: String, mime: String, b64: String, done: (String?) -> Unit) {
        root.child("files").child(id)
            .setValue(mapOf("nama" to name, "mime" to mime, "data" to b64))
            .addOnCompleteListener { done(it.exception?.message) }
    }

    fun loadFile(id: String, done: (Triple<String, String, String>?) -> Unit) {
        root.child("files").child(id).get().addOnCompleteListener { t ->
            val s = t.result
            if (!t.isSuccessful || s == null || !s.exists()) done(null)
            else done(
                Triple(
                    s.child("nama").getValue(String::class.java) ?: "berkas",
                    s.child("mime").getValue(String::class.java) ?: "application/octet-stream",
                    s.child("data").getValue(String::class.java) ?: ""
                )
            )
        }
    }

    fun watch(path: String, cb: (DataSnapshot) -> Unit): () -> Unit {
        val r = root.child(path)
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) = cb(s)
            override fun onCancelled(e: DatabaseError) {}
        }
        r.addValueEventListener(l)
        return { r.removeEventListener(l) }
    }
}

object Session {
    val role = androidx.lifecycle.MutableLiveData("anggota")
    val profile = androidx.lifecycle.MutableLiveData<Map<String, Any?>>(emptyMap())
    val isAdmin: Boolean get() = role.value == "admin"
    val uid: String? get() = FirebaseAuth.getInstance().currentUser?.uid
    val nama: String get() = profile.value?.get("nama")?.toString().orEmpty()
        .ifBlank { FirebaseAuth.getInstance().currentUser?.email.orEmpty().substringBefore("@") }

    private var stop: (() -> Unit)? = null

    fun start() {
        val u = uid ?: return
        stop?.invoke()
        stop = Repo.watch("users/$u") { s ->
            if (!s.exists()) {
                val email = FirebaseAuth.getInstance().currentUser?.email.orEmpty()
                val r = if (email.equals(id.ac.unimal.simkesma.util.ADMIN_EMAIL, true)) "admin" else "anggota"
                Repo.setValue("users/$u", mapOf("nama" to email.substringBefore("@"), "email" to email, "role" to r))
            } else {
                @Suppress("UNCHECKED_CAST")
                profile.value = (s.value as? Map<String, Any?>) ?: emptyMap()
                role.value = s.child("role").getValue(String::class.java) ?: "anggota"
            }
        }
    }

    fun clear() {
        stop?.invoke(); stop = null
        role.value = "anggota"; profile.value = emptyMap()
    }
}
