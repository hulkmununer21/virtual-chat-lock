package com.xnigma.xnigma.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Base64
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
                        onOnboardingComplete()
                    }
                }) {
                    Text(if (pagerState.currentPage == 2) "Finish" else "Next")
                }
            }
        }
    ) { paddingValues ->
        // Applied paddingValues so the pager doesn't hide behind the bottom bar
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
    }
}

@Composable
fun KeyGenerationPage() {
    var isGenerating by remember { mutableStateOf(true) }
    var generatedKey by remember { mutableStateOf("") }
    val context = LocalContext.current

    // This block runs automatically when the user swipes to this page
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val cryptoManager = CryptoManager()

            // 1. Generate the RSA KeyPair in software
            val keyPair = cryptoManager.generateIdentityKeyPair()

            // 2. Encode the Public Key to a Base64 String for sharing
            val publicKeyBase64 = Base64.encodeToString(
                keyPair.public.encoded,
                Base64.NO_WRAP
            )

            // 3. Encrypt the Private Key using the Hardware Master Key
            val securedPrivateKeyBlob = cryptoManager.securePrivateKey(keyPair.private.encoded)
            
            // 4. Encode the encrypted Private Key blob to Base64 to store it safely
            val securedPrivateKeyBase64 = Base64.encodeToString(
                securedPrivateKeyBlob,
                Base64.NO_WRAP
            )

            // 5. Save everything to SharedPreferences
            val sharedPrefs = context.getSharedPreferences("xnigma_prefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().apply {
                putString("public_key", publicKeyBase64)
                putString("secured_private_key", securedPrivateKeyBase64)
                putBoolean("is_onboarded", true)
                apply()
            }

            // 6. Switch back to the Main thread to update the UI
            withContext(Dispatchers.Main) {
                generatedKey = publicKeyBase64
                isGenerating = false
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
        Text(text = "Generating Your Identity", style = MaterialTheme.typography.headlineMedium)
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
                    maxLines = 4, // Truncates the giant key visually
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("This is your public key. Keep it safe.", color = MaterialTheme.colorScheme.primary)
        }
    }
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
            // Opens the Android Accessibility Settings
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            context.startActivity(intent)
        }) {
            Text("Enable Accessibility")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(onClick = {
            // Opens the Android Display Over Other Apps Settings
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            context.startActivity(intent)
        }) {
            Text("Enable Overlay")
        }
    }
}