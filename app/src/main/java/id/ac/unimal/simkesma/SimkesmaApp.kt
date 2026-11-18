package id.ac.unimal.simkesma

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class SimkesmaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (_: Exception) {
        }

        try {
            val ref = FirebaseDatabase.getInstance().reference.child("periode")
            ref.get().addOnSuccessListener { s ->
                if (!s.exists() || s.childrenCount == 0L) {
                    val now = System.currentTimeMillis()
                    val p1 = ref.push().key ?: "p1"
                    ref.child(p1).setValue(mapOf("nama" to "2024-2025", "status" to "Non-Aktif", "createdAt" to now))
                    val p2 = ref.push().key ?: "p2"
                    ref.child(p2).setValue(mapOf("nama" to "2025-2026", "status" to "Aktif", "createdAt" to now + 1))
                    val p3 = ref.push().key ?: "p3"
                    ref.child(p3).setValue(mapOf("nama" to "2026-2027", "status" to "Akan Datang", "createdAt" to now + 2))
                }
            }
        } catch (_: Exception) {}
    }
}
