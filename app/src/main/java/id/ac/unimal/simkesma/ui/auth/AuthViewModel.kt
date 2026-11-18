package id.ac.unimal.simkesma.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import id.ac.unimal.simkesma.R
import id.ac.unimal.simkesma.data.Repo
import id.ac.unimal.simkesma.util.ADMIN_EMAIL
import id.ac.unimal.simkesma.util.UiExt

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    class Error(val res: Int) : AuthState()
    class Info(val res: Int) : AuthState()
}

class AuthViewModel : ViewModel() {
    private val auth get() = FirebaseAuth.getInstance()
    private val _state = MutableLiveData<AuthState>(AuthState.Idle)
    val state: LiveData<AuthState> = _state

    fun consume() { _state.value = AuthState.Idle }

    fun login(email: String, pass: String, adminMode: Boolean) {
        _state.value = AuthState.Loading
        auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { t ->
            if (!t.isSuccessful) {
                _state.value = AuthState.Error(UiExt.authError(t.exception)); return@addOnCompleteListener
            }
            if (!adminMode) { _state.value = AuthState.Success; return@addOnCompleteListener }
            val uid = auth.currentUser?.uid ?: return@addOnCompleteListener
            Repo.ref("users/$uid/role").get().addOnCompleteListener { r ->
                val role = r.result?.getValue(String::class.java)
                val isAdmin = role == "admin" || (role == null && email.equals(ADMIN_EMAIL, true))
                if (isAdmin) _state.value = AuthState.Success
                else {
                    auth.signOut()
                    _state.value = AuthState.Error(R.string.err_not_admin)
                }
            }
        }
    }

    fun register(nama: String, nim: String, email: String, pass: String) {
        _state.value = AuthState.Loading
        auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener { t ->
            if (!t.isSuccessful) {
                _state.value = AuthState.Error(UiExt.authError(t.exception)); return@addOnCompleteListener
            }
            val uid = t.result?.user?.uid ?: return@addOnCompleteListener
            val role = if (email.equals(ADMIN_EMAIL, true)) "admin" else "user"
            Repo.setValue(
                "users/$uid",
                mapOf("nama" to nama, "nim" to nim, "email" to email, "role" to role)
            ) { err ->
                if (err != null) {
                    _state.value = AuthState.Error(R.string.err_generic)
                } else {
                    _state.value = AuthState.Success
                }
            }
        }
    }

    fun resetPassword(email: String) {
        _state.value = AuthState.Loading
        auth.sendPasswordResetEmail(email).addOnCompleteListener { t ->
            _state.value = if (t.isSuccessful) AuthState.Info(R.string.reset_sent)
            else AuthState.Error(UiExt.authError(t.exception))
        }
    }
}
