package com.xnigma.xnigma.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact)

    // Returns a Flow stream so the Compose UI auto-updates when data changes
    @Query("SELECT * FROM contacts ORDER BY dateAdded DESC")
    fun getAllContacts(): Flow<List<Contact>>

    @Query("DELETE FROM contacts WHERE id = :contactId")
    suspend fun deleteContact(contactId: Int)
}