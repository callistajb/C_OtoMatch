package com.example.c_otomatch.utils

import com.example.c_otomatch.models.Car

object Data {
    val carList = listOf(
        Car(
            id = 1, name = "Civic Turbo", brand = "Honda", year = 2021, price = "Rp 420.000.000",
            mileage = "20.000 km", location = "Tangerang", imageUrl = "https://www.carmudi.co.id/journal/wp-content/uploads/2017/08/Civic-Type-R-Carmudi-2.jpg",
            isWishlist = false, isSold = false, sellerName = "Callista Jasmine", sellerContact = "081234567890",
            bodyType = "Sedan", color = "Hitam", transmission = "Automatic", fuel = "Bensin",
            kmRange = "<50.000 km", sellerUid = "ADMIN_SEEDER_ID"
        ),
        Car(
            id = 2, name = "Fortuner VRZ", brand = "Toyota", year = 2020, price = "Rp 520.000.000",
            mileage = "35.000 km", location = "Jakarta", imageUrl = "https://paultan.org/image/2021/03/2021_Toyota_Fortuner_VRZ_Malaysia_Ext-4-1200x703.jpg",
            isWishlist = false, isSold = false, sellerName = "Callista Jasmine", sellerContact = "081234567890",
            bodyType = "SUV", color = "Putih", transmission = "Automatic", fuel = "Diesel",
            kmRange = "50.000-100.000 km", sellerUid = "ADMIN_SEEDER_ID"
        ),
        Car(
            id = 3, name = "Xpander Ultimate", brand = "Mitsubishi", year = 2019, price = "Rp 250.000.000",
            mileage = "40.000 km", location = "Bekasi", imageUrl = "https://img.philcarprice.com/2022/05/09/jaxy2frq/sterling-silver-metallic-2022-c3fd.jpg",
            isWishlist = false, isSold = false, sellerName = "Budi Santoso", sellerContact = "082233445566",
            bodyType = "MPV", color = "Silver", transmission = "Manual", fuel = "Bensin",
            kmRange = "50.000-100.000 km", sellerUid = "ADMIN_SEEDER_ID"
        ),
        Car(
            id = 4, name = "Mazda 3", brand = "Mazda", year = 2022, price = "Rp 480.000.000",
            mileage = "10.000 km", location = "BSD City", imageUrl = "https://preview.redd.it/qjydm5hrhxw81.jpg?width=1080&crop=smart&auto=webp&s=657a4d3e80f86868477a5c0520d9169a5e7ad7f0",
            isWishlist = false, isSold = false, sellerName = "Andi Wijaya", sellerContact = "083344556677",
            bodyType = "Sedan", color = "Biru", transmission = "Automatic", fuel = "Bensin",
            kmRange = "<50.000 km", sellerUid = "ADMIN_SEEDER_ID"
        ),
        Car(
            id = 5, name = "Tesla Model 3", brand = "Tesla", year = 2023, price = "Rp 1.200.000.000",
            mileage = "5.000 km", location = "Jakarta Selatan", imageUrl = "https://i.pinimg.com/originals/7e/14/3d/7e143dbdc463da1a01b90262f8c20872.jpg",
            isWishlist = false, isSold = false, sellerName = "Kevin Lim", sellerContact = "081122334455",
            bodyType = "Sedan", color = "Putih", transmission = "Automatic", fuel = "Listrik",
            kmRange = "<10.000 km", sellerUid = "ADMIN_SEEDER_ID"
        ),
        Car(
            id = 6, name = "BMW X5", brand = "BMW", year = 2021, price = "Rp 1.600.000.000",
            mileage = "25.000 km", location = "Jakarta Barat", imageUrl = "https://octane.rent/wp-content/uploads/2024/12/bmw_x5_black_06.webp",
            isWishlist = false, isSold = false, sellerName = "Jonathan", sellerContact = "081278945612",
            bodyType = "SUV", color = "Hitam", transmission = "Automatic", fuel = "Bensin",
            kmRange = "<50.000 km", sellerUid = "ADMIN_SEEDER_ID"
        ),
        Car(
            id = 7, name = "Mercedes-Benz E200", brand = "Mercedes-Benz", year = 2020, price = "Rp 1.250.000.000",
            mileage = "30.000 km", location = "Jakarta Utara", imageUrl = "https://th.bing.com/th/id/OIP.NaQuKAcGRvuDDvwkitsMlwHaFj?w=266&h=200&c=7&r=0&o=7&cb=ucfimgc2&dpr=1.5&pid=1.7&rm=3",
            isWishlist = false, isSold = false, sellerName = "Agus Setiawan", sellerContact = "082188889999",
            bodyType = "Sedan", color = "Silver", transmission = "Automatic", fuel = "Bensin",
            kmRange = "20.000-50.000 km", sellerUid = "ADMIN_SEEDER_ID"
        ),
        Car(
            id = 8, name = "Jeep Wrangler Rubicon", brand = "Jeep", year = 2018, price = "Rp 1.000.000.000",
            mileage = "60.000 km", location = "Bandung", imageUrl = "https://th.bing.com/th/id/OIP.P6tSDX_BThFVwuQxulX7qgHaFP?w=272&h=192&c=7&r=0&o=7&cb=ucfimgc2&dpr=1.5&pid=1.7&rm=3",
            isWishlist = false, isSold = false, sellerName = "Rina Oktaviani", sellerContact = "081345678912",
            bodyType = "SUV", color = "Merah", transmission = "Automatic", fuel = "Bensin",
            kmRange = "50.000-100.000 km", sellerUid = "ADMIN_SEEDER_ID"
        ),
        Car(
            id = 9, name = "Volkswagen Beetle Classic", brand = "Volkswagen", year = 1974, price = "Rp 180.000.000",
            mileage = "120.000 km", location = "Yogyakarta", imageUrl = "https://thumbs.dreamstime.com/b/yellow-volkswagen-kafer-classic-vw-beetle-almere-flevoland-netherlands-july-parked-public-parking-lot-56190030.jpg",
            isWishlist = false, isSold = false, sellerName = "Hendra Gunawan", sellerContact = "081311122233",
            bodyType = "Coupe", color = "Kuning", transmission = "Manual", fuel = "Bensin",
            kmRange = ">100.000 km", sellerUid = "ADMIN_SEEDER_ID"
        ),
        Car(
            id = 10, name = "Suzuki Jimny", brand = "Suzuki", year = 2023, price = "Rp 420.000.000",
            mileage = "8.000 km", location = "Tangerang Selatan", imageUrl = "https://steerwellauto.com/wp-content/uploads/2024/04/1-12-scaled.jpg",
            isWishlist = false, isSold = false, sellerName = "Dewi Anggraini", sellerContact = "085678901234",
            bodyType = "SUV", color = "Hijau Army", transmission = "Automatic", fuel = "Bensin",
            kmRange = "<10.000 km", sellerUid = "ADMIN_SEEDER_ID"
        ),
        Car(
            id = 11, name = "Hyundai Ioniq 5", brand = "Hyundai", year = 2024, price = "Rp 850.000.000",
            mileage = "3.000 km", location = "BSD City", imageUrl = "https://img.indianautosblog.com/2021/06/09/hyundai-ioniq-5-rear-quarter-6b5c.jpg",
            isWishlist = false, isSold = false, sellerName = "Tania Widjaja", sellerContact = "081800112233",
            bodyType = "Crossover", color = "Abu-abu", transmission = "Automatic", fuel = "Listrik",
            kmRange = "<10.000 km", sellerUid = "ADMIN_SEEDER_ID"
        ),
        Car(
            id = 12, name = "Toyota Kijang Super", brand = "Toyota", year = 1996, price = "Rp 65.000.000",
            mileage = "180.000 km", location = "Cirebon", imageUrl = "https://img.cintamobil.com/2023/12/29/20231229224246-2781.png",
            isWishlist = false, isSold = false, sellerName = "Slamet Riyadi", sellerContact = "081366778899",
            bodyType = "MPV", color = "Hijau Tua", transmission = "Manual", fuel = "Bensin",
            kmRange = ">100.000 km", sellerUid = "ADMIN_SEEDER_ID"
        ),
        Car(
            id = 13, name = "Range Rover Evoque", brand = "Land Rover", year = 2022, price = "Rp 1.750.000.000",
            mileage = "12.000 km", location = "Jakarta Pusat", imageUrl = "https://th.bing.com/th/id/OIP.CtAeybq5bcW84P3KjU_9SAHaEK?w=286&h=180&c=7&r=0&o=7&cb=ucfimgc2&dpr=1.5&pid=1.7&rm=3",
            isWishlist = false, isSold = false, sellerName = "William Tanuwijaya", sellerContact = "081700334455",
            bodyType = "SUV", color = "Putih Mutiara", transmission = "Automatic", fuel = "Bensin",
            kmRange = "<20.000 km", sellerUid = "ADMIN_SEEDER_ID"
        ),
        Car(
            id = 14, name = "Daihatsu Ayla", brand = "Daihatsu", year = 2021, price = "Rp 135.000.000",
            mileage = "25.000 km", location = "Serpong", imageUrl = "https://imgcdnused.carbay.com/car_image/102023/1698835223340.jpeg",
            isWishlist = false, isSold = false, sellerName = "Riko Santoso", sellerContact = "082190112233",
            bodyType = "Hatchback", color = "Merah", transmission = "Automatic", fuel = "Bensin",
            kmRange = "<50.000 km", sellerUid = "ADMIN_SEEDER_ID"
        ),
        Car(
            id = 15, name = "Nissan Leaf", brand = "Nissan", year = 2023, price = "Rp 700.000.000",
            mileage = "6.000 km", location = "Jakarta Selatan", imageUrl = "https://tse2.mm.bing.net/th/id/OIP.SYlfFIE-CD7BlV74NDNZ8wHaE8?cb=ucfimgc2&rs=1&pid=ImgDetMain&o=7&rm=3",
            isWishlist = false, isSold = false, sellerName = "Angga Saputra", sellerContact = "081299887766",
            bodyType = "Hatchback", color = "Putih", transmission = "Automatic", fuel = "Listrik",
            kmRange = "<10.000 km", sellerUid = "ADMIN_SEEDER_ID"
        )
    )
}