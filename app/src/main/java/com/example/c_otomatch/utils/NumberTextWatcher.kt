package com.example.c_otomatch.utils

import android.text.Editable
import android.text.TextWatcher
import android.util.Log // <-- Pastiin import ini ada
import android.widget.EditText
import java.lang.ref.WeakReference
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

// Ini kelas 'sakti' buat format angka otomatis
// Bisa dipake di EditText mana aja
class NumberTextWatcher(
    editText: EditText,
    private val useCurrency: Boolean = true // Kalo 'true' pake "Rp", kalo 'false' cuma angka
) : TextWatcher {

    private val editTextRef: WeakReference<EditText> = WeakReference(editText)
    // Format angka indonesia (pake titik)
    private val formatter = DecimalFormat("#,###")

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        // Gausah diapa-apain
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        // Gausah diapa-apain
    }

    override fun afterTextChanged(s: Editable?) {
        val editText = editTextRef.get() ?: return

        // Lepas listener-nya dulu, biar ga looping pas kita ubah teksnya
        editText.removeTextChangedListener(this)

        try {
            var originalString = s.toString()

            // 1. Bersihin string dari format lama (Rp, titik, spasi, dll)
            originalString = originalString.replace("[^\\d]".toRegex(), "")

            if (originalString.isNotEmpty()) {
                // 2. Ubah string angka jadi Long
                val longVal = originalString.toLong()

                // 3. Format angka itu pake titik
                val formattedString = formatter.format(longVal)

                // 4. Tambahin "Rp " kalo emang disuruh
                if (useCurrency) {
                    editText.setText("Rp $formattedString")
                } else {
                    editText.setText(formattedString)
                }

                // 5. Pindahin kursor ke paling belakang
                editText.setSelection(editText.text.length)
            } else {
                // Kalo user hapus semua, ya kosongin aja
                editText.setText("")
            }
        } catch (nfe: NumberFormatException) {
            // Kalo ada error (misal angkanya kegedean), biarin aja
            Log.e("NumberTextWatcher", "Error formatting", nfe)
        }

        // Pasang lagi listener-nya
        editText.addTextChangedListener(this)
    }

    // Helper statis buat bersihin string (dipake di SellFragment)
    companion object {
        fun cleanDigits(text: String): String {
            return text.replace(Regex("[^0-9]"), "")
        }

        fun formatToRupiah(number: Long): String {
            return "Rp ${NumberFormat.getNumberInstance(Locale("id", "ID")).format(number)}"
        }

        fun formatToKm(number: Long): String {
            return "${NumberFormat.getNumberInstance(Locale("id", "ID")).format(number)} km"
        }

        fun formatToCc(number: Long): String {
            return "${NumberFormat.getNumberInstance(Locale("id", "ID")).format(number)} cc"
        }
    }
}