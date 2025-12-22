import pandas as pd
import numpy as np
import tensorflow as tf
from sklearn.preprocessing import LabelEncoder
import io
import sys

# Konfigurasi Log
def log(msg):
    print(f"[INFO] {msg}")

# ==========================================
# 1. DATA LOADING & CLEANING
# ==========================================
log("Loading dataset from 'mobilbekas.csv'...")

try:
    # Menggunakan latin-1 untuk menghindari UnicodeDecodeError pada Windows
    with open('mobilbekas.csv', 'r', encoding='latin-1') as f:
        lines = f.readlines()
except Exception as e:
    log(f"Error reading file with latin-1: {e}")
    sys.exit(1)

cleaned_lines = []
for line in lines:
    line = line.strip().replace('""', '"')
    if line.startswith('"') and line.endswith('"'): line = line[1:-1]
    cleaned_lines.append(line)

try:
    df = pd.read_csv(io.StringIO("\n".join(cleaned_lines)), on_bad_lines='skip')
except:
    df = pd.read_csv(io.StringIO("\n".join(cleaned_lines)), error_bad_lines=False, engine='python')

log(f"Dataset loaded. Total rows: {len(df)}")

# Cleaning Functions
def clean_mileage(x):
    if pd.isna(x): return 0
    s = str(x).lower().replace('km', '').replace('.', '').replace(',', '').strip()
    if '-' in s: 
        try:
            a, b = s.split('-')
            return (float(a) + float(b)) / 2
        except: return 0
    try: return float(s)
    except: return 0

def clean_engine(x):
    if pd.isna(x): return 1500
    s = str(x).lower().replace('cc', '').replace('.', '').replace(',', '').replace('>', '').replace('<', '').strip()
    if '-' in s:
        try:
            a, b = s.split('-')
            return (float(a) + float(b)) / 2
        except: return 1500
    try: return float(s)
    except: return 1500

def clean_year(x):
    try: return int(x)
    except: return 2015

# Apply Cleaning
df.columns = [c.strip() for c in df.columns]

if 'Jarak tempuh' in df.columns:
    df['Jarak tempuh'] = df['Jarak tempuh'].apply(clean_mileage)
else:
    df['Jarak tempuh'] = 0

if 'Kapasitas mesin' in df.columns:
    df['Kapasitas mesin'] = df['Kapasitas mesin'].apply(clean_engine)
else:
    df['Kapasitas mesin'] = 1500

if 'Tahun' in df.columns:
    df['Tahun'] = df['Tahun'].apply(clean_year)
else:
    df['Tahun'] = 2015

if 'Harga' in df.columns:
    df['Harga'] = pd.to_numeric(df['Harga'], errors='coerce')
    df = df.dropna(subset=['Harga'])
    df = df[df['Harga'] > 10_000_000] # Filter outlier bawah
else:
    log("Column 'Harga' not found. Terminating.")
    sys.exit(1)

# ==========================================
# 2. ENCODING
# ==========================================
if 'Merek' in df.columns:
    top_brands = df['Merek'].value_counts().head(50).index.tolist()
    brand_map = {brand: i for i, brand in enumerate(top_brands)}
    brand_map['Other'] = len(top_brands)

    def encode_brand(x):
        return brand_map.get(x, brand_map['Other'])

    df['Brand_Code'] = df['Merek'].apply(encode_brand)
else:
    log("Column 'Merek' not found. Terminating.")
    sys.exit(1)

# ==========================================
# 3. NORMALIZATION
# ==========================================
# Features: [Brand_Code, Year, Mileage, Capacity]
X = df[['Brand_Code', 'Tahun', 'Jarak tempuh', 'Kapasitas mesin']].values.astype('float32')
y = df['Harga'].values.astype('float32')

mean = X.mean(axis=0)
std = X.std(axis=0)
std[std == 0] = 1.0 

X_scaled = (X - mean) / std

log("Data normalization complete.")

# ==========================================
# 4. MODEL TRAINING
# ==========================================
log("Starting model training (Epochs: 50)...")
model = tf.keras.Sequential([
    tf.keras.layers.Dense(128, activation='relu', input_shape=(4,)),
    tf.keras.layers.Dropout(0.1),
    tf.keras.layers.Dense(64, activation='relu'),
    tf.keras.layers.Dense(32, activation='relu'),
    tf.keras.layers.Dense(1)
])

model.compile(optimizer=tf.keras.optimizers.Adam(learning_rate=0.005), loss='mae')
model.fit(X_scaled, y, epochs=50, batch_size=32, verbose=0)
log("Model training finished.")

# ==========================================
# 5. EXPORT TFLITE
# ==========================================
converter = tf.lite.TFLiteConverter.from_keras_model(model)
tflite_model = converter.convert()

output_filename = 'car_price_model.tflite'
with open(output_filename, 'wb') as f:
    f.write(tflite_model)

log(f"Model saved to '{output_filename}'.")

# ==========================================
# 6. GENERATE CONFIG FOR KOTLIN
# ==========================================
print("\n--- Kotlin Configuration Data ---")
print(f"// Normalization Params (Mean & Std)")
print(f"private val inputMean = floatArrayOf({mean[0]:.4f}f, {mean[1]:.4f}f, {mean[2]:.4f}f, {mean[3]:.4f}f)")
print(f"private val inputStd = floatArrayOf({std[0]:.4f}f, {std[1]:.4f}f, {std[2]:.4f}f, {std[3]:.4f}f)")

print("\n// Brand Encoding Map")
print("private val brandMap = mapOf(")
for k, v in brand_map.items():
    print(f'    "{k}" to {v}f,')
print(")")
print("---------------------------------")