package com.xnigma.xnigma.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,       // e.g., "@alice"
    val publicKey: String,      // The Base64 encoded public key
    val dateAdded: Long = System.currentTimeMillis()
)