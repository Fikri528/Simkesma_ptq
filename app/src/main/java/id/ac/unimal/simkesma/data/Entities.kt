package id.ac.unimal.simkesma.data

import android.content.Context
import id.ac.unimal.simkesma.R
import id.ac.unimal.simkesma.util.DateUtil
import id.ac.unimal.simkesma.util.StatusStyle
import java.text.NumberFormat
import java.util.Locale

enum class T { TEXT, MULTI, EMAIL, PHONE, NUMBER, DIGITS, DATE, TIME, DROPDOWN, FILE }

class F(
    val key: String, val label: Int, val type: T, val required: Boolean = true,
    val options: Int = 0, val source: String? = null, val sourceKey: String = "nama"
)

class Filter(
    val key: String, val allLabel: Int, val options: Int = 0,
    val source: String? = null, val sourceKey: String = "nama"
)

class Card(
    val strip: Int, val category: String?, val date: String?, val title: String,
    val status: String?, val line1: String?, val line1Icon: Int, val mono: Boolean = false,
    val line2: String?, val line2Icon: Int, val progress: Int? = null,
    val secondary: Int = 0, val secondaryKind: Int = 0, // 1 = edit (admin), 2 = berkas
    val avatarBase64: String? = null
)

class Entity(
    val node: String, val title: Int, val singular: Int,
    val fields: List<F>, val titleKey: String, val dateKey: String,
    val filters: List<Filter>, val boxStyle: Boolean, val unit: Int,
    val addLabel: Int, val fabLabel: Int,
    val toCard: (Context, Item, Map<String, Int>) -> Card
) {
    val fileField: F? get() = fields.firstOrNull { it.type == T.FILE }
}

object Entities {
    fun rupiah(v: Long): String =
        NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply { maximumFractionDigits = 0 }.format(v)

    private fun str(ctx: Context, res: Int, vararg a: Any) = ctx.getString(res, *a)

    val anggota = Entity(
        node = "anggota", title = R.string.ent_anggota, singular = R.string.sing_anggota,
        fields = listOf(
            F("namaLengkap", R.string.f_nama_lengkap, T.TEXT),
            F("nim", R.string.f_nim, T.DIGITS),
            F("email", R.string.f_email, T.EMAIL),
            F("telepon", R.string.f_telepon, T.PHONE),
            F("status", R.string.f_status_anggota, T.DROPDOWN, options = R.array.opt_status_anggota),
            F("foto", R.string.f_foto, T.FILE, required = false)
        ),
        titleKey = "namaLengkap", dateKey = "createdAt",
        filters = listOf(
            Filter("status", R.string.all_status, options = R.array.opt_status_anggota)
        ),
        boxStyle = false, unit = R.string.unit_anggota,
        addLabel = R.string.add_anggota, fabLabel = R.string.add_anggota,
        toCard = { c, i, _ ->
            Card(
                strip = StatusStyle.colors(c, i.str("status")).third,
                category = "ANGGOTA", date = null, title = i.str("namaLengkap"),
                status = i.str("status"),
                line1 = str(c, R.string.fmt_nim, i.str("nim")), line1Icon = R.drawable.ic_badge,
                line2 = i.str("telepon").ifBlank { i.str("email") }, line2Icon = R.drawable.ic_call,
                avatarBase64 = i.str("foto")
            )
        }
    )

    val divisi = Entity(
        node = "divisi", title = R.string.ent_divisi, singular = R.string.sing_divisi,
        fields = listOf(
            F("nama", R.string.f_nama_divisi, T.TEXT),
            F("deskripsi", R.string.f_deskripsi, T.MULTI),
            F("ketua", R.string.f_ketua, T.DROPDOWN, required = false, source = "anggota", sourceKey = "namaLengkap"),
            F("sekretaris", R.string.f_sekretaris, T.DROPDOWN, required = false, source = "anggota", sourceKey = "namaLengkap")
        ),
        titleKey = "nama", dateKey = "createdAt", filters = emptyList(), boxStyle = false,
        unit = R.string.unit_divisi, addLabel = R.string.add_divisi, fabLabel = R.string.add_divisi,
        toCard = { c, i, counts ->
            Card(
                strip = c.getColor(R.color.primary), category = null, date = null, title = i.str("nama"),
                status = null,
                line1 = str(c, R.string.fmt_ketua, i.str("ketua").ifBlank { "-" }), line1Icon = R.drawable.ic_person,
                line2 = str(c, R.string.fmt_jumlah_anggota, counts[i.str("nama")] ?: 0), line2Icon = R.drawable.ic_groups
            )
        }
    )

    private val kategoriSurat = R.array.opt_kategori_surat

    val suratMasuk = Entity(
        node = "surat_masuk", title = R.string.ent_surat_masuk, singular = R.string.sing_surat_masuk,
        fields = listOf(
            F("nomorSurat", R.string.f_nomor_surat, T.TEXT),
            F("pengirim", R.string.f_pengirim, T.TEXT),
            F("perihal", R.string.f_perihal, T.TEXT),
            F("tanggal", R.string.f_tanggal, T.DATE),
            F("kategori", R.string.f_kategori, T.DROPDOWN, options = kategoriSurat),
            F("disposisi", R.string.f_disposisi, T.DROPDOWN, options = R.array.opt_disposisi),
            F("lampiran", R.string.f_lampiran, T.FILE, required = false)
        ),
        titleKey = "perihal", dateKey = "tanggal",
        filters = listOf(
            Filter("kategori", R.string.all_kategori, options = kategoriSurat),
            Filter("disposisi", R.string.all_disposisi, options = R.array.opt_disposisi)
        ),
        boxStyle = false, unit = R.string.unit_surat,
        addLabel = R.string.add_surat_masuk, fabLabel = R.string.add_surat_masuk,
        toCard = { c, i, _ ->
            val hasFile = i.str("lampiranNama").isNotBlank()
            Card(
                strip = StatusStyle.colors(c, i.str("disposisi")).third,
                category = i.str("kategori").uppercase(), date = DateUtil.display(i.str("tanggal")),
                title = i.str("perihal"), status = i.str("disposisi"),
                line1 = i.str("nomorSurat"), line1Icon = R.drawable.ic_tag, mono = true,
                line2 = str(c, R.string.fmt_pengirim, i.str("pengirim")), line2Icon = R.drawable.ic_apartment,
                secondary = if (hasFile) R.string.btn_attachment else 0, secondaryKind = 2
            )
        }
    )

    val suratKeluar = Entity(
        node = "surat_keluar", title = R.string.ent_surat_keluar, singular = R.string.sing_surat_keluar,
        fields = listOf(
            F("nomorSurat", R.string.f_nomor_surat, T.TEXT),
            F("tujuan", R.string.f_tujuan, T.TEXT),
            F("perihal", R.string.f_perihal, T.TEXT),
            F("tanggal", R.string.f_tanggal, T.DATE),
            F("kategori", R.string.f_kategori, T.DROPDOWN, options = kategoriSurat),
            F("status", R.string.f_status_surat, T.DROPDOWN, options = R.array.opt_status_surat),
            F("statusTtd", R.string.f_status_ttd, T.DROPDOWN, options = R.array.opt_status_ttd),
            F("lampiran", R.string.f_arsip_pdf, T.FILE, required = false)
        ),
        titleKey = "perihal", dateKey = "tanggal",
        filters = listOf(
            Filter("kategori", R.string.all_kategori, options = kategoriSurat),
            Filter("status", R.string.all_status, options = R.array.opt_status_surat)
        ),
        boxStyle = false, unit = R.string.unit_surat_aktif,
        addLabel = R.string.add_surat_keluar_header, fabLabel = R.string.add_surat_keluar_fab,
        toCard = { c, i, _ ->
            val st = i.str("status")
            val hasFile = i.str("lampiranNama").isNotBlank()
            val (sec, kind) = when {
                st.equals("Draft", true) || st.startsWith("Menunggu", true) -> R.string.btn_edit_draft to 1
                hasFile -> R.string.btn_download_pdf to 2
                else -> 0 to 0
            }
            Card(
                strip = StatusStyle.colors(c, st).third,
                category = i.str("kategori").uppercase(), date = DateUtil.display(i.str("tanggal")),
                title = i.str("perihal"), status = st,
                line1 = i.str("nomorSurat"), line1Icon = R.drawable.ic_tag, mono = true,
                line2 = str(c, R.string.fmt_tujuan, i.str("tujuan")), line2Icon = R.drawable.ic_apartment,
                secondary = sec, secondaryKind = kind
            )
        }
    )

    val rapat = Entity(
        node = "rapat", title = R.string.ent_rapat, singular = R.string.sing_rapat,
        fields = listOf(
            F("judul", R.string.f_judul_rapat, T.TEXT),
            F("jenis", R.string.f_jenis, T.DROPDOWN, options = R.array.opt_jenis_rapat),
            F("tanggal", R.string.f_tanggal, T.DATE),
            F("jam", R.string.f_jam, T.TIME),
            F("lokasi", R.string.f_lokasi, T.TEXT),
            F("pemimpin", R.string.f_pemimpin, T.TEXT),
            F("kewajiban", R.string.f_kewajiban, T.DROPDOWN, options = R.array.opt_kewajiban),
            F("agenda", R.string.f_agenda, T.MULTI, required = false),
            F("status", R.string.f_status, T.DROPDOWN, options = R.array.opt_status_rapat)
        ),
        titleKey = "judul", dateKey = "tanggal", filters = emptyList(), boxStyle = false,
        unit = R.string.unit_rapat, addLabel = R.string.add_rapat, fabLabel = R.string.add_rapat,
        toCard = { c, i, _ ->
            Card(
                strip = StatusStyle.colors(c, i.str("status")).third,
                category = i.str("jenis").uppercase(), date = DateUtil.display(i.str("tanggal")),
                title = i.str("judul"), status = i.str("status"),
                line1 = "${DateUtil.display(i.str("tanggal"), "EEEE, d MMM yyyy")} • ${i.str("jam")} WIB",
                line1Icon = R.drawable.ic_schedule,
                line2 = i.str("lokasi"), line2Icon = R.drawable.ic_location_on
            )
        }
    )

    val proker = Entity(
        node = "program_kerja", title = R.string.ent_proker, singular = R.string.sing_proker,
        fields = listOf(
            F("nama", R.string.f_nama_proker, T.TEXT),
            F("deskripsi", R.string.f_deskripsi, T.MULTI, required = false),
            F("divisi", R.string.f_divisi_pj, T.DROPDOWN, source = "divisi"),
            F("tanggal", R.string.f_tanggal_pelaksanaan, T.DATE),
            F("anggaran", R.string.f_anggaran, T.NUMBER, required = false),
            F("progres", R.string.f_progres, T.NUMBER),
            F("status", R.string.f_status, T.DROPDOWN, options = R.array.opt_status_proker)
        ),
        titleKey = "nama", dateKey = "tanggal",
        filters = listOf(
            Filter("divisi", R.string.all_divisi, source = "divisi"),
            Filter("status", R.string.all_status, options = R.array.opt_status_proker)
        ),
        boxStyle = false, unit = R.string.unit_proker,
        addLabel = R.string.add_proker, fabLabel = R.string.add_proker,
        toCard = { c, i, _ ->
            val ang = i.long("anggaran")
            Card(
                strip = StatusStyle.colors(c, i.str("status")).third,
                category = i.str("divisi").uppercase(), date = DateUtil.display(i.str("tanggal")),
                title = i.str("nama"), status = i.str("status"),
                line1 = if (ang > 0) str(c, R.string.fmt_anggaran, rupiah(ang)) else str(c, R.string.fmt_anggaran, "-"),
                line1Icon = R.drawable.ic_payments,
                line2 = str(c, R.string.fmt_target, DateUtil.display(i.str("tanggal"))), line2Icon = R.drawable.ic_event,
                progress = i.int("progres").coerceIn(0, 100)
            )
        }
    )

    val arsip = Entity(
        node = "arsip_dokumen", title = R.string.ent_arsip, singular = R.string.sing_arsip,
        fields = listOf(
            F("judul", R.string.f_judul_dokumen, T.TEXT),
            F("kategori", R.string.f_kategori, T.DROPDOWN, options = R.array.opt_kategori_arsip),
            F("tanggal", R.string.f_tanggal, T.DATE),
            F("deskripsi", R.string.f_deskripsi, T.MULTI, required = false),
            F("berkas", R.string.f_berkas, T.FILE)
        ),
        titleKey = "judul", dateKey = "tanggal",
        filters = listOf(Filter("kategori", R.string.all_kategori, options = R.array.opt_kategori_arsip)),
        boxStyle = true, unit = R.string.unit_dokumen,
        addLabel = R.string.add_arsip, fabLabel = R.string.add_arsip,
        toCard = { c, i, _ ->
            Card(
                strip = c.getColor(R.color.primary), category = i.str("kategori").uppercase(),
                date = DateUtil.display(i.str("tanggal")), title = i.str("judul"), status = null,
                line1 = i.str("berkasNama"), line1Icon = R.drawable.ic_attach_file,
                line2 = str(c, R.string.fmt_uploader, i.str("uploaderNama").ifBlank { "-" }), line2Icon = R.drawable.ic_person,
                secondary = R.string.btn_download, secondaryKind = 2
            )
        }
    )

    val pengumuman = Entity(
        node = "pengumuman", title = R.string.ent_pengumuman, singular = R.string.sing_pengumuman,
        fields = listOf(
            F("judul", R.string.f_judul, T.TEXT),
            F("isi", R.string.f_isi, T.MULTI),
            F("tanggal", R.string.f_tanggal, T.DATE)
        ),
        titleKey = "judul", dateKey = "tanggal", filters = emptyList(), boxStyle = false,
        unit = R.string.unit_pengumuman, addLabel = R.string.add_pengumuman, fabLabel = R.string.add_pengumuman,
        toCard = { c, i, _ ->
            Card(
                strip = c.getColor(R.color.amber), category = null, date = DateUtil.display(i.str("tanggal")),
                title = i.str("judul"), status = null,
                line1 = i.str("isi"), line1Icon = R.drawable.ic_description,
                line2 = null, line2Icon = 0
            )
        }
    )

    val periode = Entity(
        node = "periode", title = R.string.ent_periode, singular = R.string.sing_periode,
        fields = listOf(
            F("nama", R.string.f_periode, T.TEXT),
            F("status", R.string.f_status, T.DROPDOWN, options = R.array.opt_status_periode)
        ),
        titleKey = "nama", dateKey = "createdAt", filters = emptyList(), boxStyle = false,
        unit = R.string.unit_periode, addLabel = R.string.add_periode, fabLabel = R.string.add_periode,
        toCard = { c, i, _ ->
            Card(
                strip = StatusStyle.colors(c, i.str("status")).third,
                category = "PERIODE", date = null, title = i.str("nama"),
                status = i.str("status"),
                line1 = str(c, R.string.fmt_status_periode, i.str("status")), line1Icon = R.drawable.ic_calendar_today,
                line2 = null, line2Icon = 0
            )
        }
    )

    val pengurus = Entity(
        node = "pengurus", title = R.string.ent_pengurus, singular = R.string.sing_pengurus,
        fields = listOf(
            F("namaLengkap", R.string.f_nama_lengkap, T.DROPDOWN, source = "anggota", sourceKey = "namaLengkap"),
            F("nim", R.string.f_nim, T.DIGITS),
            F("email", R.string.f_email, T.EMAIL),
            F("telepon", R.string.f_telepon, T.PHONE),
            F("divisi", R.string.f_divisi, T.DROPDOWN, required = false, source = "divisi", sourceKey = "nama"),
            F("jabatan", R.string.f_jabatan, T.DROPDOWN, options = R.array.opt_jabatan),
            F("periode", R.string.f_periode, T.DROPDOWN, source = "periode", sourceKey = "nama"),
            F("foto", R.string.f_foto, T.FILE, required = false)
        ),
        titleKey = "namaLengkap", dateKey = "createdAt",
        filters = listOf(
            Filter("divisi", R.string.all_divisi, source = "divisi"),
            Filter("periode", R.string.select_periode, source = "periode", sourceKey = "nama")
        ),
        boxStyle = false, unit = R.string.unit_pengurus,
        addLabel = R.string.add_pengurus, fabLabel = R.string.add_pengurus,
        toCard = { c, i, _ ->
            Card(
                strip = StatusStyle.colors(c, i.str("divisi")).third,
                category = i.str("jabatan").uppercase(), date = i.str("periode").ifBlank { null },
                title = i.str("namaLengkap"),
                status = i.str("divisi").ifBlank { "BPH" },
                line1 = str(c, R.string.fmt_nim, i.str("nim")), line1Icon = R.drawable.ic_badge,
                line2 = str(c, R.string.fmt_divisi, i.str("divisi").ifBlank { "-" }), line2Icon = R.drawable.ic_apartment,
                avatarBase64 = i.str("foto")
            )
        }
    )

    private val all = listOf(anggota, divisi, suratMasuk, suratKeluar, rapat, proker, arsip, pengumuman, periode, pengurus)
    fun get(node: String): Entity = all.first { it.node == node }
}
