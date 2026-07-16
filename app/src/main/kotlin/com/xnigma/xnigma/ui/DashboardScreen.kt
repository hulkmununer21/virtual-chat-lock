package com.xnigma.xnigma.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xnigma.xnigma.crypto.CryptoManager
import com.xnigma.xnigma.database.Contact
import com.xnigma.xnigma.database.ContactDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// =====================================================================
// VIEWMODEL & FACTORY 
// =====================================================================

class DashboardViewModel(private val contactDao: ContactDao) : ViewModel() {
    
    val contacts: StateFlow<List<Contact>> = contactDao.getAllContacts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addContact(username: String, publicKey: String) {
        viewModelScope.launch {
            contactDao.insertContact(Contact(username = username, publicKey = publicKey))
        }
    }

    fun updateContact(contact: Contact) {
        viewModelScope.launch {
            contactDao.updateContact(contact)
        }
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            contactDao.deleteContact(contact)
        }
    }
}

class DashboardViewModelFactory(private val contactDao: ContactDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(contactDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// =====================================================================
// MAIN UI COMPOSABLE
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("xnigma_prefs", Context.MODE_PRIVATE)
    val myPublicKeyBase64 = sharedPrefs.getString("public_key", "No Key Found. Please generate one.") ?: ""

    var selectedTab by remember { mutableStateOf(0) }
    var showContactDialog by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<Contact?>(null) }
    
    val contactsList by viewModel.contacts.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Xnigma", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Contacts, contentDescription = "Contacts") },
                    label = { Text("Vault") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Fingerprint, contentDescription = "Identity") },
                    label = { Text("Identity") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(onClick = { 
                    editingContact = null
                    showContactDialog = true 
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Contact")
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> ContactsTab(
                    contacts = contactsList,
                    onEdit = { contact ->
                        editingContact = contact
                        showContactDialog = true
                    },
                    onDelete = { contact ->
                        viewModel.deleteContact(contact)
                        coroutineScope.launch { snackbarHostState.showSnackbar("${contact.username} purged from vault.") }
                    }
                )
                1 -> IdentityTab(
                    myPublicKeyBase64 = myPublicKeyBase64,
                    context = context,
                    onCopy = { coroutineScope.launch { snackbarHostState.showSnackbar("Public Key copied to clipboard!") } }
                )
                2 -> SettingsTab()
            }
        }

        if (showContactDialog) {
            ContactDialog(
                initialContact = editingContact,
                snackbarHostState = snackbarHostState,
                onDismiss = { showContactDialog = false },
                onSave = { username, key ->
                    if (editingContact == null) {
                        viewModel.addContact(username, key)
                        coroutineScope.launch { snackbarHostState.showSnackbar("Target secured.") }
                    } else {
                        viewModel.updateContact(editingContact!!.copy(username = username, publicKey = key))
                        coroutineScope.launch { snackbarHostState.showSnackbar("Target updated.") }
                    }
                    showContactDialog = false
                }
            )
        }
    }
}

// =====================================================================
// TAB SCREENS & COMPONENTS
// =====================================================================

@Composable
fun ContactsTab(contacts: List<Contact>, onEdit: (Contact) -> Unit, onDelete: (Contact) -> Unit) {
    if (contacts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Your vault is empty.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(contacts) { contact ->
                ContactCard(contact, onEdit, onDelete)
            }
        }
    }
}

@Composable
fun ContactCard(contact: Contact, onEdit: (Contact) -> Unit, onDelete: (Contact) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.username,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = contact.publicKey,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row {
                IconButton(onClick = { onEdit(contact) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { onDelete(contact) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun ContactDialog(
    initialContact: Contact?,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit, 
    onSave: (String, String) -> Unit
) {
    var username by remember { mutableStateOf(initialContact?.username ?: "") }
    var publicKey by remember { mutableStateOf(initialContact?.publicKey ?: "") }
    val coroutineScope = rememberCoroutineScope()
    val cryptoManager = remember { CryptoManager() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialContact == null) "Add to Vault" else "Edit Target") },
        text = {
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username (e.g., @alice)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = publicKey,
                    onValueChange = { publicKey = it },
                    label = { Text("Base64 Public Key") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (username.isNotBlank() && publicKey.isNotBlank()) {
                        try {
                            // Validates the key mathematically before allowing save
                            cryptoManager.getPublicKeyFromString(publicKey.trim())
                            onSave(username.trim(), publicKey.trim())
                        } catch (e: Exception) {
                            coroutineScope.launch { 
                                snackbarHostState.showSnackbar("CRITICAL: Invalid RSA Public Key format.") 
                            }
                        }
                    }
                },
                enabled = username.isNotBlank() && publicKey.isNotBlank()
            ) {
                Text("Save Key")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun IdentityTab(myPublicKeyBase64: String, context: Context, onCopy: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Your Public Identity", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Public Key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                
                Text(
                    text = myPublicKeyBase64,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Xnigma Public Key", myPublicKeyBase64)
                        clipboard.setPrimaryClip(clip)
                        onCopy()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy")
                    }

                    TextButton(onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Here is my Xnigma Public Key:\n\n$myPublicKeyBase64")
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Share Public Key via...")
                        context.startActivity(shareIntent)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share")
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsTab() {
    val context = LocalContext.current
    var showExportDialog by remember { mutableStateOf(false) }
    var exportedPayload by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Security Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Export Identity",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Export your encrypted Private Key to a new device. You must set a temporary password to secure it during transit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showExportDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Export Private Key")
                }
            }
        }
    }

    if (showExportDialog) {
        ExportIdentityDialog(
            context = context,
            onDismiss = { showExportDialog = false },
            onExported = { payload ->
                exportedPayload = payload
                showExportDialog = false
            }
        )
    }

    if (exportedPayload != null) {
        AlertDialog(
            onDismissRequest = { exportedPayload = null },
            title = { Text("Your Encrypted Identity") },
            text = {
                Column {
                    Text("Copy this string to your new device. It can only be unlocked with the password you just set.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = exportedPayload!!,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = { exportedPayload = null }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
fun ExportIdentityDialog(context: Context, onDismiss: () -> Unit, onExported: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Secure Your Key") },
        text = {
            Column {
                Text("Enter a strong password to lock your identity payload.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (password.length < 6) {
                        errorMessage = "Password must be at least 6 characters."
                    } else if (password != confirmPassword) {
                        errorMessage = "Passwords do not match."
                    } else {
                        val sharedPrefs = context.getSharedPreferences("xnigma_prefs", Context.MODE_PRIVATE)
                        val securedKeyBase64 = sharedPrefs.getString("secured_private_key", null)
                        val publicKeyBase64 = sharedPrefs.getString("public_key", null)
                        
                        if (securedKeyBase64 != null && publicKeyBase64 != null) {
                            val securedPrivateKeyBlob = android.util.Base64.decode(securedKeyBase64, android.util.Base64.NO_WRAP)
                            val cryptoManager = com.xnigma.xnigma.crypto.CryptoManager()
                            
                            val payload = cryptoManager.exportIdentityWithPassword(publicKeyBase64, securedPrivateKeyBlob, password)
                            onExported(payload)
                        } else {
                            errorMessage = "Error: Key not found on device."
                        }
                    }
                }
            ) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}