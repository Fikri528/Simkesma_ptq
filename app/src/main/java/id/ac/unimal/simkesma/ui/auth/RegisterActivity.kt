package id.ac.unimal.simkesma.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import id.ac.unimal.simkesma.R
import id.ac.unimal.simkesma.databinding.ActivityRegisterBinding
import id.ac.unimal.simkesma.ui.MainActivity
import id.ac.unimal.simkesma.util.UiExt
import id.ac.unimal.simkesma.util.Validator

class RegisterActivity : AppCompatActivity() {
    private lateinit var b: ActivityRegisterBinding
    private val vm: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(b.root)
        UiExt.statusBar(this, true)

        b.btnRegister.setOnClickListener { submit() }
        b.tvToLogin.setOnClickListener { finish() }

        vm.state.observe(this) { s ->
            val loading = s is AuthState.Loading
            b.progress.visibility = if (loading) View.VISIBLE else View.GONE
            b.btnRegister.isEnabled = !loading
            when (s) {
                is AuthState.Success -> {
                    vm.consume()
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
                    finish()
                }
                is AuthState.Error -> { vm.consume(); UiExt.toast(this, s.res) }
                else -> {}
            }
        }
    }

    private fun submit() {
        val nama = b.etNama.text.toString().trim()
        val nim = b.etNim.text.toString().trim()
        val email = b.etEmail.text.toString().trim()
        val pass = b.etPassword.text.toString()
        val conf = b.etConfirm.text.toString()
        listOf(b.tilNama, b.tilNim, b.tilEmail, b.tilPassword, b.tilConfirm).forEach { it.error = null }
        var ok = true
        val req = getString(R.string.err_required)
        if (nama.isEmpty()) { b.tilNama.error = req; ok = false }
        if (nim.isEmpty()) { b.tilNim.error = req; ok = false }
        else if (!Validator.digits(nim)) { b.tilNim.error = getString(R.string.err_digits); ok = false }
        if (email.isEmpty()) { b.tilEmail.error = req; ok = false }
        else if (!Validator.email(email)) { b.tilEmail.error = getString(R.string.err_email_format); ok = false }
        if (pass.isEmpty()) { b.tilPassword.error = req; ok = false }
        else if (pass.length < 6) { b.tilPassword.error = getString(R.string.err_password_short); ok = false }
        if (conf != pass) { b.tilConfirm.error = getString(R.string.err_password_mismatch); ok = false }
        if (ok) vm.register(nama, nim, email, pass)
    }
}
