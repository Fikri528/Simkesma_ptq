package id.ac.unimal.simkesma.ui

import android.content.res.ColorStateList
import android.widget.TextView
import androidx.fragment.app.Fragment
import id.ac.unimal.simkesma.util.StatusStyle
import id.ac.unimal.simkesma.util.UiExt

abstract class BaseFragment(layout: Int) : Fragment(layout) {
    open val tealStatus: Boolean get() = true

    override fun onResume() {
        super.onResume()
        UiExt.statusBar(requireActivity(), tealStatus)
    }

    fun push(f: Fragment) = (requireActivity() as MainActivity).push(f)
    fun back() { parentFragmentManager.popBackStack() }
    fun main() = requireActivity() as MainActivity

    /** Mewarnai badge status sesuai nilainya. */
    fun TextView.badge(text: String?) {
        if (text.isNullOrBlank()) { visibility = android.view.View.GONE; return }
        visibility = android.view.View.VISIBLE
        val (bg, fg, _) = StatusStyle.colors(context, text)
        this.text = text
        setTextColor(fg)
        backgroundTintList = ColorStateList.valueOf(bg)
    }
}
