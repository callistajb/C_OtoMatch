package com.example.c_otomatch.models

import com.google.firebase.firestore.DocumentId
import java.util.Date

data class Comment(
    @DocumentId
    var id: String = "",

    val userName: String = "",
    val text: String = "",
    val rating: Float = 0f,
    val userId: String = "",
    val userPhotoUrl: String = "",
    val timestamp: Date? = null
)