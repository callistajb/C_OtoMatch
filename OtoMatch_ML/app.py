from flask import Flask, request, jsonify
import pandas as pd
import numpy as np
import pickle
import difflib # <--- LIBRARY BARU BUAT BENERIN TYPO
from datetime import datetime

app = Flask(__name__)

# --- 1. LOAD MODEL DAN KOMPONEN ---
print("Sedang memuat model ML...")
try:
    with open('best_car_price_model_random_forest_tuned.pkl', 'rb') as f:
        model = pickle.load(f)
    with open('car_price_scaler.pkl', 'rb') as f:
        scaler = pickle.load(f)
    with open('car_price_label_encoders.pkl', 'rb') as f:
        label_encoders = pickle.load(f)
    with open('car_price_selected_features.pkl', 'rb') as f:
        selected_features = pickle.load(f)
    print("✅ SUKSES: Model berhasil dimuat!")
except Exception as e:
    print(f"❌ ERROR: Gagal memuat file .pkl.")
    print(f"Detail: {e}")

# --- HELPER: FUNGSI BENERIN TYPO ---
def fix_typo(input_text, valid_options):
    """
    Mencari kata yang paling mirip.
    Contoh: 'Toyta' -> 'Toyota', 'Jaz' -> 'Jazz'
    """
    if not input_text: return input_text
    
    # Cari 1 kata yang paling mirip dengan kemiripan minimal 60% (0.6)
    matches = difflib.get_close_matches(input_text, valid_options, n=1, cutoff=0.6)
    
    if matches:
        return matches[0] # Kembalikan kata yang benar
    return input_text # Kalau ga ada yang mirip, balikin aslinya

# --- 2. LOGIKA PREDIKSI ---
def predict_price_logic(data):
    input_data = pd.DataFrame([data])
    current_year = datetime.now().year
    
    # Feature Engineering (Sama seperti sebelumnya)
    if 'Tahun' in input_data.columns:
        input_data['Tahun'] = pd.to_numeric(input_data['Tahun'], errors='coerce')
        input_data['Umur_Mobil'] = current_year - input_data['Tahun']
        input_data['Umur_Mobil_Squared'] = input_data['Umur_Mobil'] ** 2

    if 'Jarak tempuh' in input_data.columns:
        input_data['Jarak tempuh'] = pd.to_numeric(input_data['Jarak tempuh'], errors='coerce')
        
    if 'Jarak tempuh' in input_data.columns and 'Umur_Mobil' in input_data.columns:
        input_data['KM_per_Tahun'] = input_data['Jarak tempuh'] / (input_data['Umur_Mobil'] + 1)
        input_data['KM_per_Tahun_Normalized'] = np.log1p(input_data['KM_per_Tahun'])

    if 'Merek' in input_data.columns:
        # List mobil mewah harus sama persis ejaannya dengan data training
        luxury_brands = ['Mercedes-Benz', 'BMW', 'Audi', 'Lexus', 'Volvo', 'Porsche', 'Ferrari', 'Lamborghini', 'Aston Martin']
        input_data['Is_Luxury'] = input_data['Merek'].isin(luxury_brands).astype(int)

    if 'Kapasitas mesin' in input_data.columns:
        input_data['Kapasitas mesin'] = pd.to_numeric(input_data['Kapasitas mesin'], errors='coerce')
        def categorize_engine(cc):
            if cc <= 1200: return 'Small'
            elif cc <= 1800: return 'Medium'
            else: return 'Large'
        input_data['Engine_Category'] = input_data['Kapasitas mesin'].apply(categorize_engine)
        
        if 'Engine_Category' in label_encoders:
             valid_cats = label_encoders['Engine_Category'].classes_
             # Fix typo juga untuk kategori mesin jaga-jaga
             current_val = input_data['Engine_Category'].iloc[0]
             fixed_val = fix_typo(current_val, valid_cats)
             input_data['Engine_Category'] = fixed_val

    # Boolean Features
    if 'Transmisi' in input_data.columns:
        input_data['Is_Automatic'] = (input_data['Transmisi'] == 'Automatic').astype(int)

    if 'Tipe bahan bakar' in input_data.columns:
        input_data['Is_Diesel'] = (input_data['Tipe bahan bakar'] == 'Diesel').astype(int)
        input_data['Is_Hybrid'] = (input_data['Tipe bahan bakar'] == 'Hybrid').astype(int)

    # Encoding
    for col in input_data.columns:
        if col in label_encoders:
            try:
                val = str(input_data[col].iloc[0])
                if val not in label_encoders[col].classes_:
                    # Fallback ke kelas pertama
                    input_data[col] = label_encoders[col].transform([label_encoders[col].classes_[0]])
                else:
                    input_data[col] = label_encoders[col].transform([val])
            except:
                pass

    # Reindex & Scaling
    input_data = input_data.reindex(columns=selected_features, fill_value=0)
    input_scaled = scaler.transform(input_data)
    
    prediction = model.predict(input_scaled)[0]
    return float(prediction)

# --- 3. ROUTE API ---
@app.route('/predict', methods=['POST'])
def predict():
    try:
        data = request.get_json()
        print(f"📥 Data Mentah: {data}")
        
        # 1. Ambil Input User
        input_brand = data.get('brand', 'Toyota').strip().title()
        input_model = data.get('model', 'Avanza').strip().title()
        
        # 2. AUTO-CORRECT (Anti Pak Alex)
        # Ambil daftar Merek & Model yang valid dari 'otak' ML (LabelEncoder)
        valid_brands = label_encoders['Merek'].classes_
        valid_models = label_encoders['Model'].classes_
        
        # Cari yang paling mirip
        fixed_brand = fix_typo(input_brand, valid_brands)
        fixed_model = fix_typo(input_model, valid_models)
        
        # Log untuk pembuktian ke Pak Alex
        if input_brand != fixed_brand:
            print(f"🔧 TYPO DETECTED: '{input_brand}' dikoreksi menjadi '{fixed_brand}'")
        if input_model != fixed_model:
            print(f"🔧 TYPO DETECTED: '{input_model}' dikoreksi menjadi '{fixed_model}'")

        # 3. Masukkan data yang sudah dikoreksi
        formatted_data = {
            'Merek': fixed_brand,
            'Model': fixed_model,
            'Tahun': int(data.get('year', 2020)),
            'Transmisi': data.get('transmission', 'Manual'),
            'Tipe bahan bakar': data.get('fuel', 'Bensin'),
            'Jarak tempuh': int(data.get('mileage', 0)),
            'Kapasitas mesin': int(data.get('capacity', 1500))
        }
        
        result_price = predict_price_logic(formatted_data)
        formatted_price = f"Rp {result_price:,.0f}".replace(",", ".")
        
        print(f"📤 Prediksi ({fixed_brand} {fixed_model}): {formatted_price}")
        
        return jsonify({
            'status': 'success',
            'predicted_price': result_price,
            'formatted_price': formatted_price,
            # Kita kirim balik nama yang sudah dikoreksi supaya Android tau
            'corrected_brand': fixed_brand, 
            'corrected_model': fixed_model
        })
        
    except Exception as e:
        print(f"❌ Error: {str(e)}")
        return jsonify({'status': 'error', 'message': str(e)}), 500

if __name__ == '__main__':
    print("🚀 Server OtoMatch (Anti-Typo) berjalan di port 5000...")
    app.run(host='0.0.0.0', port=5000, debug=True)