package com.xnigma.xnigma.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xnigma.xnigma.crypto.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onOnboardingComplete: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current // Grab context to save the final state

    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (pagerState.currentPage > 0) {
                    TextButton(onClick = {
                        coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }) {
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(onClick = {
                    if (pagerState.currentPage < 2) {
                        coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        // STATE RETENTION: Seal the onboarding process only when they hit Finish
                        val sharedPrefs = context.getSharedPreferences("xnigma_prefs", Context.MODE_PRIVATE)
                        sharedPrefs.edit().putBoolean("is_onboarding_completed", true).apply()
                        
                        onOnboardingComplete()
                    }
                }) {
                    Text(if (pagerState.currentPage == 2) "Finish" else "Next")
                }
            }
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) 
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> KeyGenerationPage()
                2 -> PermissionsPage()
            }
        }
    }
}

@Composable
fun WelcomePage() {
    var showImportDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to Xnigma",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Encrypt text in any app. No central servers. You hold the keys.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(48.dp))
        
        TextButton(onClick = { showImportDialog = true }) {
            Text("I already have an account (Import Identity)")
        }
    }

    if (showImportDialog) {
        ImportIdentityDialog(onDismiss = { showImportDialog = false })
    }
}

@Composable
fun KeyGenerationPage() {
    var isGenerating by remember { mutableStateOf(true) }
    var generatedKey by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val sharedPrefs = context.getSharedPreferences("xnigma_prefs", Context.MODE_PRIVATE)
            val existingKey = sharedPrefs.getString("public_key", null)

            // CRITICAL SAFEGUARD: Skip generation if they just imported an identity
            if (existingKey != null) {
                withContext(Dispatchers.Main) {
                    generatedKey = existingKey
                    isGenerating = false
                }
            } else {
                val cryptoManager = CryptoManager()
                val keyPair = cryptoManager.generateIdentityKeyPair()
                
                val publicKeyBase64 = android.util.Base64.encodeToString(keyPair.public.encoded, android.util.Base64.NO_WRAP)
                val securedPrivateKeyBlob = cryptoManager.securePrivateKey(keyPair.private.encoded)
                val securedPrivateKeyBase64 = android.util.Base64.encodeToString(securedPrivateKeyBlob, android.util.Base64.NO_WRAP)

                sharedPrefs.edit().apply {
                    putString("public_key", publicKeyBase64)
                    putString("secured_private_key", securedPrivateKeyBase64)
                    // Removed the premature flag here so they don't skip the permissions page
                    apply()
                }

                withContext(Dispatchers.Main) {
                    generatedKey = publicKeyBase64
                    isGenerating = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Your Identity", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        if (isGenerating) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Securing keys in hardware enclave...")
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = generatedKey,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("This is your public key. Keep it safe.", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ImportIdentityDialog(onDismiss: () -> Unit) {
    var payload by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Identity") },
        text = {
            Column {
                Text("Paste your exported identity string and the password you used to lock it.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = payload,
                    onValueChange = { payload = it },
                    label = { Text("Encrypted Payload") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Decryption Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isImporting = true
                    errorMessage = null
                    
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val cryptoManager = CryptoManager()
                            val (publicKeyBase64, newSecuredPrivateKeyBlob) = cryptoManager.importIdentityWithPassword(payload.trim(), password)
                            
                            val securedPrivateKeyBase64 = android.util.Base64.encodeToString(
                                newSecuredPrivateKeyBlob,
                                android.util.Base64.NO_WRAP
                            )

                            val sharedPrefs = context.getSharedPreferences("xnigma_prefs", Context.MODE_PRIVATE)
                            sharedPrefs.edit().apply {
                                putString("public_key", publicKeyBase64)
                                putString("secured_private_key", securedPrivateKeyBase64)
                                apply()
                            }

                            withContext(Dispatchers.Main) {
                                isImporting = false
                                onDismiss()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                isImporting = false
                                errorMessage = "Import failed. Invalid payload or wrong password."
                            }
                        }
                    }
                },
                enabled = payload.isNotBlank() && password.isNotBlank() && !isImporting
            ) {
                Text(if (isImporting) "Importing..." else "Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isImporting) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PermissionsPage() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "We Need Access", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "To overlay the decryption window and inject cipher text, you must grant Accessibility and Overlay permissions.",
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(onClick = {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            context.startActivity(intent)
        }) {
            Text("Enable Accessibility")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(onClick = {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            context.startActivity(intent)
        }) {
            Text("Enable Overlay")
        }
    }
}