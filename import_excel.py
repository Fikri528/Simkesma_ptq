import pandas as pd
import json
import numpy as np

excel_path = r'C:\Users\User\Downloads\Daftar_Pengurus_dan_Anggota_UKM_PTQ-1.xlsx'
dfs = pd.read_excel(excel_path, sheet_name=None)

OFFICIAL_DIVISIONS = [
    "Agama",
    "Humas",
    "Informasi dan Komunikasi",
    "Kewirausahaan",
    "Kaderisasi",
    "Pendidikan dan Pelatihan",
    "Kesekretariatan"
]

def map_divisi(raw):
    if not raw or str(raw).strip() == '' or str(raw).lower() == 'nan':
        return "Kesekretariatan"
    s = str(raw).strip()
    sl = s.lower()
    if "humas" in sl:
        return "Humas"
    elif "informasi" in sl or "komunikasi" in sl or "infokom" in sl:
        return "Informasi dan Komunikasi"
    elif "wirausaha" in sl or "kewirausahaan" in sl:
        return "Kewirausahaan"
    elif "kaderisasi" in sl:
        return "Kaderisasi"
    elif "pendidikan" in sl or "pelatihan" in sl:
        return "Pendidikan dan Pelatihan"
    elif "sekretaris" in sl or "sekretariat" in sl or "pengurus inti" in sl or "umum" in sl:
        return "Kesekretariatan"
    else:
        # Default for quranic fields like Tilawah, Tahfiz, Syarhil, Khattil, etc.
        return "Agama"

anggota_dict = {}

for sheet_name, df in dfs.items():
    df = df.replace({np.nan: None})
    for idx, row in df.iterrows():
        nama = row.get('Nama')
        if not nama or str(nama).strip() == '' or str(nama).lower() == 'nan':
            continue
        nama = str(nama).strip()

        nim = row.get('NIM')
        if nim is not None and str(nim).strip() != '':
            try:
                nim = str(int(float(nim)))
            except:
                nim = str(nim).strip()
        else:
            nim = '-'

        jabatan = row.get('Jabatan')
        if not jabatan or str(jabatan).strip() == '' or str(jabatan).lower() == 'nan':
            jabatan = 'Anggota'
        else:
            jabatan = str(jabatan).strip()

        div_raw = row.get('Divisi')
        if not div_raw or str(div_raw).strip() == '' or str(div_raw).lower() == 'nan':
            div_raw = row.get('Bidang Minat')

        divisi = map_divisi(div_raw)

        telepon = str(row.get('Telepon', ''))
        if telepon == 'nan' or not telepon:
            telepon = '-'

        email = str(row.get('Email', ''))
        if email == 'nan' or not email:
            email = f"{nama.lower().replace(' ', '_')}@ptq.unimal.ac.id"

        key = f"member_{sheet_name[:3]}_{idx}"
        anggota_dict[key] = {
            'namaLengkap': nama,
            'nim': nim,
            'email': email,
            'telepon': telepon,
            'divisi': divisi,
            'jabatan': jabatan,
            'status': 'Aktif',
            'createdAt': 1700000000000
        }

divisi_dict = {f"div_{i}": {'nama': div, 'deskripsi': f'Divisi {div}'} for i, div in enumerate(OFFICIAL_DIVISIONS)}

firebase_data = {
    'anggota': anggota_dict,
    'divisi': divisi_dict
}

with open('firebase_import.json', 'w', encoding='utf-8') as f:
    json.dump(firebase_data, f, ensure_ascii=False, indent=2)

print(f"Successfully processed {len(anggota_dict)} members into 7 official divisions: {set(v['divisi'] for v in anggota_dict.values())}")
