package com.xnigma.xnigma.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xnigma.xnigma.crypto.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DecryptionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Anti-Screenshot Shield
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        // Reverted to standard dimming behavior for stable Compose rendering
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        val payload = intent.getStringExtra("EXTRA_PAYLOAD") ?: ""

        setContent {
            MaterialTheme {
                DecryptionScreen(
                    payload = payload,
                    context = applicationContext,
                    onClose = { finish() }
                )
            }
        }
    }
}

@Composable
fun DecryptionScreen(payload: String, context: Context, onClose: () -> Unit) {
    var decryptedMessage by remember { mutableStateOf("Decrypting...") }
    var isError by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(payload) {
        coroutineScope.launch(Dispatchers.Default) {
            try {
                val sharedPrefs = context.getSharedPreferences("xnigma_prefs", Context.MODE_PRIVATE)
                val securedKeyBase64 = sharedPrefs.getString("secured_private_key", null)
                
                if (securedKeyBase64 != null) {
                    val cryptoManager = CryptoManager()
                    val securedPrivateKeyBlob = android.util.Base64.decode(securedKeyBase64, android.util.Base64.NO_WRAP)
                    
                    val rawPrivateKeyBytes = cryptoManager.unlockPrivateKey(securedPrivateKeyBlob)
                    val privateKey = cryptoManager.getPrivateKeyFromBytes(rawPrivateKeyBytes)
                    
                    val plainText = cryptoManager.decryptMessage(payload, privateKey)
                    
                    withContext(Dispatchers.Main) {
                        decryptedMessage = plainText
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        decryptedMessage = "Identity key missing. Vault locked."
                        isError = true
                    }
                }
            } catch (e: Exception) {
                Log.e("Decryption", "Decryption failed", e)
                withContext(Dispatchers.Main) {
                    decryptedMessage = "Access Denied. Invalid or tampered cipher."
                    isError = true
                }
            }
        }
    }

    // Centered, full-screen box (The proven, stable layout)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = interactionSource, indication = null) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF121212).copy(alpha = 0.95f))
                .border(1.dp, if (isError) Color.Red.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                .clickable(interactionSource = interactionSource, indication = null) {} // Prevent closing when tapping inside
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isError) "Xnigma Error" else "Decrypted Message",
                style = MaterialTheme.typography.labelMedium,
                color = if (isError) Color(0xFFFF5252) else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = decryptedMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }
    }
}