package com.example.c_otomatch.utils

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

object Data {

    // ================== DATABASE KOTA (LENGKAP) ==================
    private val cityList = listOf(
        // Sumatera
        "Banda Aceh", "Medan", "Pematang Siantar", "Padang", "Bukittinggi",
        "Pekanbaru", "Dumai", "Jambi", "Palembang", "Lubuklinggau",
        "Bengkulu", "Bandar Lampung", "Metro", "Pangkal Pinang", "Batam", "Tanjung Pinang",

        // Jawa & Banten
        "Jakarta Pusat", "Jakarta Selatan", "Jakarta Barat", "Jakarta Timur", "Jakarta Utara",
        "Serang", "Cilegon", "Tangerang", "Tangerang Selatan",
        "Bandung", "Bekasi", "Bogor", "Depok", "Cimahi", "Sukabumi", "Tasikmalaya", "Cirebon", "Banjar",
        "Semarang", "Surakarta (Solo)", "Magelang", "Pekalongan", "Salatiga", "Tegal",
        "Yogyakarta",
        "Surabaya", "Malang", "Madiun", "Kediri", "Mojokerto", "Batu", "Pasuruan", "Probolinggo", "Blitar",

        // Bali & Nusa Tenggara
        "Denpasar", "Singaraja", "Mataram", "Bima", "Kupang",

        // Kalimantan
        "Pontianak", "Singkawang", "Palangka Raya", "Banjarmasin", "Banjarbaru",
        "Samarinda", "Balikpapan", "Bontang", "Tarakan",

        // Sulawesi
        "Makassar", "Parepare", "Palopo", "Manado", "Tomohon", "Bitung",
        "Palu", "Kendari", "Gorontalo",

        // Maluku & Papua
        "Ambon", "Ternate", "Jayapura", "Sorong", "Manokwari", "Merauke"
    ).sorted()

    // ================== DATABASE MOBIL (LENGKAP) ==================
    private val carDataMap = mapOf(
        "Toyota" to listOf(
            "Avanza", "Veloz", "Innova Zenix", "Innova Reborn", "Kijang Innova", "Kijang Kapsul",
            "Fortuner", "Rush", "Agya", "Calya", "Yaris", "Yaris Cross", "Yaris Bakpao", "Yaris Lele",
            "Vios", "Camry", "Alphard", "Vellfire", "Raize", "Sienta", "Hilux",
            "Corolla Altis", "Corolla Cross", "Land Cruiser", "Voxy", "Soluna", "Starlet",
            "Harrier", "Supra", "GR 86", "Crown", "HiAce", "C-HR", "Prius", "bZ4X",
            "Nav1", "Etios Valco", "Limo", "Wish", "Hardtop", "FJ Cruiser"
        ),
        "Honda" to listOf(
            "Brio", "Brio Satya", "Brio RS", "Jazz (GD3)", "Jazz (GE8)", "Jazz (GK5)",
            "HR-V", "CR-V", "BR-V", "WR-V", "Mobilio", "City", "City Hatchback",
            "Civic", "Civic Turbo", "Civic Type R", "Civic Estilo", "Civic Ferio", "Civic Genio",
            "Accord", "Freed", "Odyssey", "Stream", "CR-Z", "Elysion", "Prelude"
        ),
        "Daihatsu" to listOf(
            "Xenia", "Terios", "Ayla", "Sigra", "Gran Max", "Luxio", "Sirion", "Rocky",
            "Taruna", "Zebra", "Taft", "Feroza", "Ceria", "Espass", "Classy", "Winner",
            "Hijet", "Charade", "YRV", "Copen", "Himax"
        ),
        "Suzuki" to listOf(
            "Ertiga", "XL7", "Ignis", "Baleno", "S-Cross", "Jimny", "Karimun", "Karimun Wagon R", "Karimun Estilo",
            "APV", "Swift", "SX4", "Grand Vitara", "S-Presso", "Carry", "Katana", "Escudo",
            "Sidekick", "Aerio", "Splash", "Forza", "Amenity", "Vitara", "Fronx"
        ),
        "Mitsubishi" to listOf(
            "Xpander", "Xpander Cross", "Pajero Sport", "Xforce", "Outlander Sport", "Outlander PHEV",
            "Triton", "L300", "Mirage", "Eclipse Cross", "Lancer", "Lancer Evo", "Galant",
            "Kuda", "Grandis", "Eterna", "Maven", "Colt T120SS", "Strada"
        ),
        "Wuling" to listOf(
            "Confero", "Confero S", "Cortez", "Almaz", "Almaz RS", "Alvez",
            "Air EV", "BinguoEV", "Cloud EV", "Formo"
        ),
        "Hyundai" to listOf(
            "Stargazer", "Stargazer X", "Creta", "Ioniq 5", "Ioniq 6", "Palisade",
            "Santa Fe", "Tucson", "Kona Electric", "Staria", "H-1", "Avega", "Grand Avega",
            "Getz", "Atoz", "Trajet", "Accent", "Matrix", "Elantra"
        ),
        "Nissan" to listOf(
            "Grand Livina", "Livina", "X-Trail", "Serena", "Juke", "March", "Magnite",
            "Kicks e-Power", "Leaf", "Terra", "Navara", "Evalia", "Teana", "Elgrand",
            "Latio", "Frontier", "Terrano"
        ),
        "Mazda" to listOf(
            "Mazda2", "Mazda3", "Mazda6", "CX-3", "CX-5", "CX-30", "CX-8", "CX-9", "CX-60",
            "Biante", "BT-50", "MX-5", "RX-8", "VX-1", "Familia", "Lantis", "Astina", "Vantrend"
        ),
        "Kia" to listOf(
            "Sonet", "Seltos", "Carens", "Carnival", "Grand Carnival", "EV6", "EV9",
            "Rio", "Picanto", "Sportage", "Sedona", "Visto", "Pregio", "Travello", "Pride", "Optima", "Sorento"
        ),
        "Isuzu" to listOf("Panther", "Mu-X", "D-Max", "Traga", "Elf", "Bighorn"),
        "Chevrolet" to listOf(
            "Spin", "Captiva", "Trax", "Trailblazer", "Spark", "Orlando", "Colorado",
            "Aveo", "Optra", "Zafira", "Camaro", "Blazer", "Trooper", "Lova", "Estate"
        ),
        "Ford" to listOf(
            "Fiesta", "Focus", "EcoSport", "Everest", "Ranger", "Escape", "Mustang", "Laser", "Lynx", "Gala"
        ),
        "BMW" to listOf(
            "Serie 3 (320i, 330i)", "Serie 5 (520i, 530i)", "Serie 7", "X1", "X3", "X5", "X7",
            "iX", "i4", "i7", "iX1", "M3", "M4", "M2", "Serie 4", "Serie 2", "Z4", "X4", "X6", "i8"
        ),
        "Mercedes-Benz" to listOf(
            "C-Class", "E-Class", "S-Class", "GLA", "GLB", "GLC", "GLE", "GLS", "CLA",
            "A-Class", "G-Class (G-Wagon)", "V-Class", "EQE", "EQS", "EQA", "EQB", "SLK", "CLS", "ML-Class", "Sprinter"
        ),
        "Lexus" to listOf("RX", "NX", "LX", "LM", "ES", "LS", "UX", "IS", "GS", "RC", "LC", "CT", "RZ"),
        "Mini" to listOf("Cooper", "Cooper S", "Countryman", "Clubman", "Cabriolet", "John Cooper Works", "Paceman", "Electric"),
        "Volkswagen" to listOf("Golf", "Polo", "Tiguan", "T-Cross", "Scirocco", "Caravelle", "Transporter", "Beetle (Kodok)", "Touran", "Touareg"),
        "Audi" to listOf("A3", "A4", "A5", "A6", "A8", "Q3", "Q5", "Q7", "Q8", "TT", "R8", "RS4", "RS6"),
        "Volvo" to listOf("XC90", "XC60", "XC40", "S90", "V60", "S80", "S60", "960", "850", "740", "C40 Recharge"),
        "Peugeot" to listOf("3008", "5008", "2008", "206", "207", "406", "508", "308", "RCZ", "405", "407"),
        "Land Rover" to listOf("Range Rover", "Range Rover Sport", "Evoque", "Velar", "Defender", "Discovery"),
        "Jeep" to listOf("Wrangler", "Rubicon", "Gladiator", "Grand Cherokee", "Compass", "Renegade", "CJ7"),
        "Subaru" to listOf("XV", "Crosstrek", "Forester", "Impreza", "WRX", "BRZ", "Levorg", "Outback"),
        "Datsun" to listOf("Go", "Go+", "Cross"),
        "Proton" to listOf("Exora", "Saga", "Persona", "Gen-2", "Wira", "Neo", "Savvy", "Waja", "Preve", "Suprima"),
        "Renault" to listOf("Triber", "Kwid", "Koleos", "Duster", "Megane", "Clio", "Captur"),
        "Chery" to listOf("Omoda 5", "Omoda E5", "Tiggo 5X", "Tiggo 7 Pro", "Tiggo 8 Pro", "Jaecoo J7", "Tiggo Cross"),
        "BYD" to listOf("Dolphin", "Atto 3", "Seal", "M6", "Sealion 7", "Denza D9"),
        "MG" to listOf("MG 4 EV", "MG ZS", "MG ZS EV", "MG HS", "MG 5 GT", "MG VS HEV", "Cyberster"),
        "DFSK" to listOf("Glory 560", "Glory 580", "Glory i-Auto", "Gelora", "Gelora E", "Super Cab", "Seres E1"),
        "Neta" to listOf("Neta V", "Neta V-II", "Neta X"),
        "GWM" to listOf("Tank 500", "Tank 300", "Haval H6", "Haval Jolion"),
        "VinFast" to listOf("VF 5", "VF e34"),
        "Fiat" to listOf("500", "Punto", "Uno"),
        "Smart" to listOf("Fortwo", "Forfour"),
        "Tata" to listOf("Super Ace", "Xenon", "Safari", "Aria", "Vista", "Nano"),
        "Porsche" to listOf("Macan", "Cayenne", "Panamera", "911", "718 Boxster", "718 Cayman", "Taycan"),
        "Ferrari" to listOf("458", "488", "F8 Tributo", "California", "Portofino", "Roma", "SF90", "296 GTB", "360 Modena", "F430", "F12 Berlinetta"),
        "Lamborghini" to listOf("Aventador", "Huracan", "Urus", "Gallardo", "Murcielago", "Revuelto", "Diablo"),
        "Rolls-Royce" to listOf("Phantom", "Ghost", "Cullinan", "Wraith", "Dawn"),
        "Bentley" to listOf("Continental GT", "Bentayga", "Flying Spur", "Mulsanne"),
        "Aston Martin" to listOf("DB11", "Vantage", "DBX", "DBS", "Rapide"),
        "McLaren" to listOf("720S", "570S", "Artura", "GT", "MP4-12C", "650S"),
        "Maserati" to listOf("Ghibli", "Levante", "Quattroporte", "GranTurismo", "Grecale"),
        "Hummer" to listOf("H1", "H2", "H3"),
        "Jaguar" to listOf("XF", "XJ", "XE", "F-Pace", "E-Pace", "F-Type", "I-Pace"),
        "Cadillac" to listOf("Escalade"),
        "Tesla" to listOf("Model 3", "Model Y", "Model S", "Model X"),
        "Timor" to listOf("S515", "S515i", "DOHC", "SOHC"),
        "Opel" to listOf("Blazer", "Optima", "Vectra", "Kadett"),
        "Daewoo" to listOf("Matiz", "Espero", "Nexia"),
        "Geely" to listOf("Panda", "MK", "Emgrand"),
        "Esemka" to listOf("Bima"),
        "Hino" to listOf("Dutro", "Ranger", "300 Series", "500 Series"),
        "Brabus" to listOf("G-Class", "S-Class", "C-Class"),
        "Foton" to listOf("View", "Tunland"),
        "Lainnya" to emptyList()
    )

    fun uploadDataToFirebase() {
        val db = FirebaseFirestore.getInstance()

        // 1. Upload Kota
        val locationData = hashMapOf("cities" to cityList)
        db.collection("otomatch_Data").document("locations")
            .set(locationData)
            .addOnSuccessListener { Log.d("Data", "✅ Sukses upload Data Kota ke Firestore") }
            .addOnFailureListener { Log.e("Data", "❌ Gagal upload Data Kota", it) }

        // 2. Upload Mobil
        val carData = hashMapOf("brands" to carDataMap)
        db.collection("otomatch_Data").document("car_models")
            .set(carData)
            .addOnSuccessListener { Log.d("Data", "✅ Sukses upload Data Mobil ke Firestore") }
            .addOnFailureListener { Log.e("Data", "❌ Gagal upload Data Mobil", it) }
    }
}