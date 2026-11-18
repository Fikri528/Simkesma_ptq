package id.ac.unimal.simkesma.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.firebase.auth.FirebaseAuth
import id.ac.unimal.simkesma.R
import id.ac.unimal.simkesma.data.Session
import id.ac.unimal.simkesma.databinding.ActivityMainBinding
import id.ac.unimal.simkesma.ui.auth.LoginActivity
import id.ac.unimal.simkesma.ui.dashboard.DashboardFragment
import id.ac.unimal.simkesma.ui.hub.HubFragment
import id.ac.unimal.simkesma.ui.list.EntityListFragment
import id.ac.unimal.simkesma.ui.profil.ProfilFragment

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (FirebaseAuth.getInstance().currentUser == null) { goLogin(); return }
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        Session.start()
        b.bottomNav.setOnItemSelectedListener { showTab(it.itemId); true }
        if (savedInstanceState == null) b.bottomNav.selectedItemId = R.id.nav_dashboard
    }

    private fun showTab(id: Int) {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val f: Fragment = when (id) {
            R.id.nav_dashboard -> DashboardFragment()
            R.id.nav_anggota -> EntityListFragment.newInstance("anggota", true)
            R.id.nav_sekretariat -> HubFragment()
            R.id.nav_arsip -> EntityListFragment.newInstance("arsip_dokumen", true)
            else -> ProfilFragment()
        }
        supportFragmentManager.beginTransaction().replace(R.id.container, f).commit()
    }

    fun push(f: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.container, f).addToBackStack(null).commit()
    }

    fun selectTab(id: Int) { b.bottomNav.selectedItemId = id }

    fun logout() {
        Session.clear()
        FirebaseAuth.getInstance().signOut()
        goLogin()
    }

    private fun goLogin() {
        startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
