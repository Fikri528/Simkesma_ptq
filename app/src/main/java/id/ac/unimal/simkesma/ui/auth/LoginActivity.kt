package id.ac.unimal.simkesma.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import id.ac.unimal.simkesma.R
import id.ac.unimal.simkesma.databinding.ActivityLoginBinding
import id.ac.unimal.simkesma.databinding.DialogInputBinding
import id.ac.unimal.simkesma.ui.MainActivity
import id.ac.unimal.simkesma.util.UiExt
import id.ac.unimal.simkesma.util.Validator

class LoginActivity : AppCompatActivity() {
    private lateinit var b: ActivityLoginBinding
    private val vm: AuthViewModel by viewModels()
    private var adminMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sesi tersimpan: langsung masuk tanpa login ulang.
        if (FirebaseAuth.getInstance().currentUser != null) { openMain(); return }

        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)
        UiExt.statusBar(this, true)
        adminMode = savedInstanceState?.getBoolean("admin") ?: false
        renderTabs()

        b.tabAnggota.setOnClickListener { adminMode = false; renderTabs() }
        b.tabAdmin.setOnClickListener { adminMode = true; renderTabs() }
        b.btnLogin.setOnClickListener { doLogin() }
        b.tvRegister.setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
        b.tvForgot.setOnClickListener { showForgot() }

        vm.state.observe(this) { s ->
            val loading = s is AuthState.Loading
            b.progress.visibility = if (loading) View.VISIBLE else View.GONE
            b.btnLogin.isEnabled = !loading
            when (s) {
                is AuthState.Success -> { vm.consume(); openMain() }
                is AuthState.Error -> { vm.consume(); UiExt.toast(this, s.res) }
                is AuthState.Info -> { vm.consume(); UiExt.toast(this, s.res) }
                else -> {}
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("admin", adminMode)
    }

    private fun renderTabs() {
        val on = R.drawable.bg_segment_login_active
        b.tabAnggota.setBackgroundResource(if (adminMode) 0 else on)
        b.tabAdmin.setBackgroundResource(if (adminMode) on else 0)
        val white = ContextCompat.getColor(this, R.color.white)
        val grey = ContextCompat.getColor(this, R.color.text_secondary)
        b.tabAnggota.setTextColor(if (adminMode) grey else white)
        b.tabAnggota.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(if (adminMode) grey else white)
        b.tabAdmin.setTextColor(if (adminMode) white else grey)
        b.tabAdmin.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(if (adminMode) white else grey)
    }

    private fun doLogin() {
        val email = b.etEmail.text.toString().trim()
        val pass = b.etPassword.text.toString()
        b.tilEmail.error = null; b.tilPassword.error = null
        var ok = true
        if (email.isEmpty()) { b.tilEmail.error = getString(R.string.err_required); ok = false }
        else if (!Validator.email(email)) { b.tilEmail.error = getString(R.string.err_email_format); ok = false }
        if (pass.isEmpty()) { b.tilPassword.error = getString(R.string.err_required); ok = false }
        else if (pass.length < 6) { b.tilPassword.error = getString(R.string.err_password_short); ok = false }
        if (!ok) return
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(b.root.windowToken, 0)
        vm.login(email, pass, adminMode)
    }

    private fun showForgot() {
        val d = DialogInputBinding.inflate(layoutInflater)
        d.til1.hint = getString(R.string.f_email)
        d.et1.inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or android.text.InputType.TYPE_CLASS_TEXT
        d.et1.setText(b.etEmail.text)
        d.til2.visibility = View.GONE; d.til3.visibility = View.GONE
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.forgot_title).setMessage(R.string.forgot_msg).setView(d.root)
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.btn_send) { _, _ ->
                val e = d.et1.text.toString().trim()
                if (!Validator.email(e)) UiExt.toast(this, R.string.err_email_format) else vm.resetPassword(e)
            }.show()
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
