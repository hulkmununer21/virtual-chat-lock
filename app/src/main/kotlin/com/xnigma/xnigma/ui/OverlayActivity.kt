package com.xnigma.xnigma.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xnigma.xnigma.crypto.CryptoManager
import com.xnigma.xnigma.database.AppDatabase
import com.xnigma.xnigma.database.Contact
import com.xnigma.xnigma.services.XnigmaAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OverlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Remove background dimming entirely so it looks like it's floating directly on the host app
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        
        val database = AppDatabase.getDatabase(applicationContext)
        val contactDao = database.contactDao()
        val cryptoManager = CryptoManager()

        val sharedPrefs = applicationContext.getSharedPreferences("xnigma_prefs", android.content.Context.MODE_PRIVATE)
        val myPublicKeyBase64 = sharedPrefs.getString("public_key", null)

        setContent {
            MaterialTheme {
                GlassmorphicScreen(
                    contactsFlow = contactDao.getAllContacts(),
                    onClose = { finish() },
                    onEncryptAndInject = { plaintext, selectedContact ->
                        if (myPublicKeyBase64 != null) {
                            try {
                                // 1. Heavy ECC processing runs safely on the background thread
                                val recipientKey = cryptoManager.getPublicKeyFromString(selectedContact.publicKey)
                                val myPublicKey = cryptoManager.getPublicKeyFromString(myPublicKeyBase64)
                                val cipherText = cryptoManager.encryptMessage(plaintext, recipientKey, myPublicKey)
                                
                                // 2. Switch back to Main UI Thread to trigger the stable clipboard injection
                                runOnUiThread {
                                    XnigmaAccessibilityService.injectCipherText(applicationContext, cipherText)
                                    finish()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                runOnUiThread {
                                    android.widget.Toast.makeText(
                                        this@OverlayActivity, 
                                        "Error: Encryption failed. Check if contact key matches ECC format.", 
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassmorphicScreen(
    contactsFlow: kotlinx.coroutines.flow.Flow<List<Contact>>,
    onClose: () -> Unit,
    onEncryptAndInject: (String, Contact) -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    
    val contacts by contactsFlow.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }

    // Invisible full-screen box to catch clicks outside the UI and close it
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = interactionSource, indication = null) { onClose() }, 
        contentAlignment = Alignment.Center // Center the slim UI on the screen
    ) {
        // THE GLASSMORPHIC CARD
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f) // 90% of screen width
                .clip(RoundedCornerShape(24.dp))
                // Dark frosted glass background
                .background(Color(0xFF121212).copy(alpha = 0.85f))
                // Thin glowing glass border
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                .clickable(interactionSource = interactionSource, indication = null) {} // Prevent closing when tapping inside
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            
            // ROW 1: Minimalist Contact Dropdown
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedContact?.username ?: "Select Recipient",
                    onValueChange = {},
                    readOnly = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        focusedContainerColor = Color.White.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .height(55.dp)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    if (contacts.isEmpty()) {
                        DropdownMenuItem(text = { Text("Vault is empty") }, onClick = { expanded = false })
                    } else {
                        contacts.forEach { contact ->
                            DropdownMenuItem(
                                text = { Text(contact.username) },
                                onClick = {
                                    selectedContact = contact
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // ROW 2: Input Field & Lock Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom, // Align bottom so button stays grounded if text expands
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Draft secret...", color = Color.White.copy(alpha = 0.5f)) },
                    textStyle = LocalTextStyle.current.copy(color = Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 55.dp, max = 150.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        containerColor = Color.White.copy(alpha = 0.05f),
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(16.dp)
                )

                FloatingActionButton(
                    onClick = {
                        if (selectedContact != null && messageText.isNotBlank()) {
                            // Leave this running on Dispatchers.Default for heavy encryption math
                            coroutineScope.launch(Dispatchers.Default) {
                                onEncryptAndInject(messageText, selectedContact!!)
                            }
                        }
                    },
                    containerColor = if (selectedContact != null && messageText.isNotBlank()) 
                                     MaterialTheme.colorScheme.primary 
                                     else Color.White.copy(alpha = 0.1f),
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp),
                    modifier = Modifier.size(55.dp) // Match height of the text field
                ) {
                    Icon(Icons.Default.Lock, contentDescription = "Lock & Inject")
                }
            }
        }
    }
}