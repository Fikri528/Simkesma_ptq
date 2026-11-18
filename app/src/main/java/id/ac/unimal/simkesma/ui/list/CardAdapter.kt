package id.ac.unimal.simkesma.ui.list

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.ac.unimal.simkesma.data.Card
import id.ac.unimal.simkesma.data.Entity
import id.ac.unimal.simkesma.data.Item
import id.ac.unimal.simkesma.databinding.ItemCardBinding
import id.ac.unimal.simkesma.util.StatusStyle

class CardAdapter(
    private val ent: Entity,
    private val onItem: (Item) -> Unit,
    private val onSecondary: (Item, Card) -> Unit
) : ListAdapter<Item, CardAdapter.VH>(object : DiffUtil.ItemCallback<Item>() {
    override fun areItemsTheSame(a: Item, b: Item) = a.id == b.id
    override fun areContentsTheSame(a: Item, b: Item) = a == b
}) {
    var counts: Map<String, Int> = emptyMap()
    var admin = false

    class VH(val b: ItemCardBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, v: Int) = VH(ItemCardBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = getItem(pos)
        val b = h.b
        val ctx = b.root.context
        val c = ent.toCard(ctx, item, counts)

        b.strip.setBackgroundColor(c.strip)
        b.tvCategory.visibility = if (c.category.isNullOrBlank()) View.GONE else View.VISIBLE
        b.tvCategory.text = c.category
        b.tvDate.text = c.date.orEmpty()
        if (c.status.isNullOrBlank()) b.tvStatus.visibility = View.GONE else {
            val (bg, fg, _) = StatusStyle.colors(ctx, c.status)
            b.tvStatus.visibility = View.VISIBLE
            b.tvStatus.text = c.status
            b.tvStatus.setTextColor(fg)
            b.tvStatus.backgroundTintList = ColorStateList.valueOf(bg)
        }
        b.tvTitle.text = c.title
        if (!c.avatarBase64.isNullOrBlank()) {
            try {
                val bytes = android.util.Base64.decode(c.avatarBase64, android.util.Base64.DEFAULT)
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                b.ivCardAvatar.setImageBitmap(bmp)
                b.ivCardAvatar.visibility = View.VISIBLE
            } catch (_: Exception) {
                b.ivCardAvatar.visibility = View.GONE
            }
        } else {
            b.ivCardAvatar.visibility = View.GONE
        }
        if (c.progress != null) {
            b.llProgress.visibility = View.VISIBLE
            b.progressBar.setProgressCompat(c.progress, false)
            b.tvProgress.text = "${c.progress}%"
        } else b.llProgress.visibility = View.GONE

        bindLine(b.rowLine1, b.ivLine1, b.tvLine1, c.line1, c.line1Icon, c.mono)
        bindLine(b.rowLine2, b.ivLine2, b.tvLine2, c.line2, c.line2Icon, false)

        val showSecondary = c.secondary != 0 && (c.secondaryKind != 1 || admin)
        b.btnSecondary.visibility = if (showSecondary) View.VISIBLE else View.GONE
        if (showSecondary) {
            b.btnSecondary.setText(c.secondary)
            b.btnSecondary.setOnClickListener { onSecondary(item, c) }
        }
        b.btnDetail.setOnClickListener { onItem(item) }
        b.root.setOnClickListener { onItem(item) }
    }

    private fun bindLine(row: View, iv: android.widget.ImageView, tv: android.widget.TextView, text: String?, icon: Int, mono: Boolean) {
        if (text.isNullOrBlank() || icon == 0) { row.visibility = View.GONE; return }
        row.visibility = View.VISIBLE
        iv.setImageResource(icon)
        tv.text = text
        tv.typeface = if (mono) Typeface.MONOSPACE else Typeface.DEFAULT
    }
}
