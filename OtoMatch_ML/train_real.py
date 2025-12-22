# TRAIN REAL DATA - OTOMATCH
# Menggunakan dataset asli 'mobilbekas.csv'
# Versi: Anti Macet (Skip Bad Lines)
# ==========================================

import pandas as pd
import numpy as np
import pickle
import io
from datetime import datetime
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import LabelEncoder, RobustScaler
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import mean_absolute_error, r2_score, mean_absolute_percentage_error
import warnings

warnings.filterwarnings('ignore')

print("⏳ STEP 1: LOADING & CLEANING DATASET ASLI...")

# 1. READ CSV DENGAN HANDLING FORMAT KHUSUS
try:
    with open('mobilbekas.csv', 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    cleaned_lines = []
    for line in lines:
        line = line.strip()
        # Hapus quote pembungkus di awal dan akhir baris (jika ada)
        if line.startswith('"') and line.endswith('"'):
            line = line[1:-1]
        # Perbaiki double quote "" menjadi "
        line = line.replace('""', '"')
        cleaned_lines.append(line)
    
    csv_content = "\n".join(cleaned_lines)
    
    # --- BAGIAN PENTING: SKIP BARIS ERROR ---
    try:
        # Untuk Pandas versi baru
        df = pd.read_csv(io.StringIO(csv_content), on_bad_lines='skip')
    except TypeError:
        # Fallback untuk Pandas versi lama
        df = pd.read_csv(io.StringIO(csv_content), error_bad_lines=False)
        
    print(f"✅ Dataset Loaded: {df.shape[0]} baris berhasil dibaca.")
    
except FileNotFoundError:
    print("❌ ERROR: File 'mobilbekas.csv' tidak ditemukan!")
    print("👉 Pastikan file ada di folder yang sama dengan script ini.")
    exit()
except Exception as e:
    print(f"❌ Error loading CSV: {e}")
    exit()

# 2. CLEANING DATA (Membersihkan data kotor)
print("\n🧹 Cleaning Data...")

# Fungsi pembersih Jarak tempuh
def clean_mileage(x):
    if pd.isna(x): return 0
    x = str(x).lower().replace(' km', '').replace('.', '').replace(',', '')
    if '-' in x:
        try:
            parts = x.split('-')
            # Ambil rata-rata dari range (misal "10.000-15.000")
            return int((int(parts[0]) + int(parts[1])) / 2)
        except: return 0
    try:
        return int(x)
    except: return 0

# Fungsi pembersih Kapasitas Mesin
def clean_capacity(x):
    if pd.isna(x): return np.nan
    x = str(x).lower()
    if '<1.000 cc' in x: return 900
    if '>1.000 - 1.500 cc' in x: return 1300
    if '>1.500 - 2.000 cc' in x: return 1800
    if '>2.000 - 3.000 cc' in x: return 2500
    if '>3.000 cc' in x: return 3500
    return np.nan

# Fungsi pembersih Tahun
def clean_year(x):
    x = str(x)
    if '<' in x: return 1985
    try:
        return int(x)
    except: return 2010 # Default fallback

# Terapkan Cleaning
# Pastikan nama kolom sesuai dengan CSV
# Kita cek dulu kolom yang tersedia, kalau beda dikit kita rename
if 'Jarak tempuh' in df.columns:
    df['Jarak tempuh'] = df['Jarak tempuh'].apply(clean_mileage)
if 'Kapasitas mesin' in df.columns:
    df['Kapasitas mesin'] = df['Kapasitas mesin'].apply(clean_capacity)
if 'Tahun' in df.columns:
    df['Tahun'] = df['Tahun'].apply(clean_year)
if 'Transmisi' in df.columns:
    df['Transmisi'] = df['Transmisi'].replace({'Automatic Triptonic': 'Automatic'})

# Isi data kosong (Imputation)
if 'Kapasitas mesin' in df.columns:
    median_cap = df['Kapasitas mesin'].median()
    df['Kapasitas mesin'].fillna(median_cap, inplace=True)

# Default values untuk data kosong
if 'Transmisi' in df.columns:
    df['Transmisi'].fillna('Automatic', inplace=True)
if 'Tipe bahan bakar' in df.columns:
    df['Tipe bahan bakar'].fillna('Bensin', inplace=True)

# Hapus baris dengan harga aneh (di bawah 10jt atau kosong)
if 'Harga' in df.columns:
    df = df[df['Harga'] > 10000000]

print(f"✅ Data Bersih: {len(df)} baris siap training")

# 3. FEATURE ENGINEERING
print("\n🛠️ Feature Engineering...")
df_engineered = df.copy()
current_year = datetime.now().year

if 'Tahun' in df_engineered.columns:
    df_engineered['Umur_Mobil'] = current_year - df_engineered['Tahun']
    df_engineered['Umur_Mobil_Squared'] = df_engineered['Umur_Mobil'] ** 2

if 'Jarak tempuh' in df_engineered.columns and 'Umur_Mobil' in df_engineered.columns:
    df_engineered['KM_per_Tahun'] = df_engineered['Jarak tempuh'] / (df_engineered['Umur_Mobil'] + 1)
    df_engineered['KM_per_Tahun_Normalized'] = np.log1p(df_engineered['KM_per_Tahun'])

if 'Merek' in df_engineered.columns:
    luxury_brands = ['Mercedes-Benz', 'BMW', 'Audi', 'Lexus', 'Volvo', 'Porsche', 'Ferrari', 'Lamborghini', 'Aston Martin']
    df_engineered['Is_Luxury'] = df_engineered['Merek'].isin(luxury_brands).astype(int)

if 'Kapasitas mesin' in df_engineered.columns:
    def categorize_engine(cc):
        if cc <= 1200: return 'Small'
        elif cc <= 1800: return 'Medium'
        else: return 'Large'
    df_engineered['Engine_Category'] = df_engineered['Kapasitas mesin'].apply(categorize_engine)

if 'Transmisi' in df_engineered.columns:
    df_engineered['Is_Automatic'] = (df_engineered['Transmisi'] == 'Automatic').astype(int)

if 'Tipe bahan bakar' in df_engineered.columns:
    df_engineered['Is_Diesel'] = (df_engineered['Tipe bahan bakar'] == 'Diesel').astype(int)
    df_engineered['Is_Hybrid'] = (df_engineered['Tipe bahan bakar'] == 'Hybrid').astype(int)

# 4. LABEL ENCODING & SCALING
label_encoders = {}
categorical_cols = ['Merek', 'Model', 'Transmisi', 'Tipe bahan bakar', 'Engine_Category']

# Pastikan kolom ada sebelum di-encode
existing_cat_cols = [col for col in categorical_cols if col in df_engineered.columns]

for col in existing_cat_cols:
    df_engineered[col] = df_engineered[col].astype(str)
    le = LabelEncoder()
    df_engineered[col] = le.fit_transform(df_engineered[col])
    label_encoders[col] = le

# Pilih fitur yang tersedia saja
potential_features = [
    'Merek', 'Model', 'Tahun', 'Jarak tempuh', 'Kapasitas mesin',
    'Umur_Mobil', 'Umur_Mobil_Squared', 'KM_per_Tahun', 'KM_per_Tahun_Normalized',
    'Is_Luxury', 'Engine_Category', 'Is_Automatic', 'Is_Diesel', 'Is_Hybrid'
]
selected_features = [f for f in potential_features if f in df_engineered.columns]

X = df_engineered[selected_features]
y = df_engineered['Harga']

scaler = RobustScaler()
X_scaled = scaler.fit_transform(X)

# 5. TRAINING MODEL
print("\n🏋️ Training Model (Random Forest)...")
X_train, X_test, y_train, y_test = train_test_split(X_scaled, y, test_size=0.2, random_state=42)

rf_model = RandomForestRegressor(n_estimators=100, max_depth=20, random_state=42, n_jobs=-1)
rf_model.fit(X_train, y_train)

# Evaluasi
y_pred = rf_model.predict(X_test)
r2 = r2_score(y_test, y_pred)
mape = mean_absolute_percentage_error(y_test, y_pred) * 100

print(f"\n✅ Training Selesai!")
print(f"📊 Akurasi (R2 Score): {r2:.4f}")
print(f"📉 Rata-rata Error (MAPE): {mape:.2f}%")

# 6. SAVE MODEL
print("\n💾 Menyimpan file .pkl...")
try:
    with open('best_car_price_model_random_forest_tuned.pkl', 'wb') as f:
        pickle.dump(rf_model, f)
    with open('car_price_scaler.pkl', 'wb') as f:
        pickle.dump(scaler, f)
    with open('car_price_label_encoders.pkl', 'wb') as f:
        pickle.dump(label_encoders, f)
    with open('car_price_selected_features.pkl', 'wb') as f:
        pickle.dump(selected_features, f)
    print("🎉 SUKSES! Model baru siap digunakan.")
    print("👉 Langkah terakhir: Restart 'app.py' Anda (Ctrl+C lalu python app.py)")
except Exception as e:
    print(f"Error saving: {e}")