import pandas as pd
import numpy as np
import tensorflow as tf
import json

# 1. LOAD DATA
print("⏳ Loading dataset...")
# Pastikan nama file CSV sesuai
try:
    df = pd.read_csv('mobilbekas.csv', on_bad_lines='skip')
except:
    # Data Dummy kalau CSV tidak ada (Hanya untuk bikin struktur model)
    data = {
        'Merek': ['Toyota', 'Honda', 'Suzuki', 'BMW'] * 25,
        'Tahun': [2020, 2019, 2018, 2021] * 25,
        'Kapasitas mesin': ['1500 cc', '2000 cc', '1200 cc', '3000 cc'] * 25,
        'Jarak tempuh': ['10.000 km', '20.000 km', '50.000 km', '5.000 km'] * 25,
        'Harga': [200000000, 300000000, 150000000, 800000000] * 25
    }
    df = pd.DataFrame(data)

# 2. CLEANING SIMPLE
def clean_currency(x):
    try: return float(str(x).replace('Rp', '').replace('.', '').replace(' ', ''))
    except: return 0.0

def clean_km(x):
    try: return float(str(x).lower().replace('km', '').replace('.', '').replace(',', '').replace(' ', ''))
    except: return 0.0

def clean_cc(x):
    try: return float(str(x).lower().replace('cc', '').replace('.', '').replace(',', '').replace(' ', ''))
    except: return 1500.0

# Sesuaikan nama kolom dengan CSV mu
if 'Harga' in df.columns: df['Harga'] = df['Harga'].apply(clean_currency)
if 'Jarak tempuh' in df.columns: df['Jarak tempuh'] = df['Jarak tempuh'].apply(clean_km)
if 'Kapasitas mesin' in df.columns: df['Kapasitas mesin'] = df['Kapasitas mesin'].apply(clean_cc)
if 'Tahun' in df.columns: df['Tahun'] = pd.to_numeric(df['Tahun'], errors='coerce').fillna(2010)

# 3. ENCODING (Merek -> Angka)
# Kita ambil Top 30 Merek, sisanya "Other"
top_brands = df['Merek'].value_counts().head(50).index.tolist()
brand_map = {brand: i for i, brand in enumerate(top_brands)}
brand_map['Other'] = len(top_brands)

def encode_brand(x):
    return brand_map.get(x, brand_map['Other'])

df['Brand_Code'] = df['Merek'].apply(encode_brand)

# 4. PREPARE TRAINING DATA
# Input: [Brand_Code, Tahun, Jarak, CC]
X = df[['Brand_Code', 'Tahun', 'Jarak tempuh', 'Kapasitas mesin']].values.astype('float32')
y = df['Harga'].values.astype('float32')

# 5. BUILD MODEL (Simple Neural Network)
model = tf.keras.Sequential([
    tf.keras.layers.Dense(64, activation='relu', input_shape=(4,)), # 4 Input Features
    tf.keras.layers.Dense(32, activation='relu'),
    tf.keras.layers.Dense(1) # 1 Output (Harga)
])

model.compile(optimizer='adam', loss='mae')
print("🏋️ Training model sebentar...")
model.fit(X, y, epochs=50, verbose=0)

# 6. CONVERT TO TFLITE
print("💾 Converting to TFLite...")
converter = tf.lite.TFLiteConverter.from_keras_model(model)
tflite_model = converter.convert()

with open('car_price_model.tflite', 'wb') as f:
    f.write(tflite_model)

print("\n✅ SUKSES! File 'car_price_model.tflite' berhasil dibuat.")
print("👉 Copy file ini ke folder Android: app/src/main/assets/")
print("\n👇 COPY KODE MAP INI KE KOTLIN (CarPriceHelper.kt):")
print("val brandMap = mapOf(")
for k, v in brand_map.items():
    print(f'    "{k}" to {v}f,')
print(")")