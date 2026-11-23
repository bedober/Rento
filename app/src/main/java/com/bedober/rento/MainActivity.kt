package com.bedober.rento

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    private lateinit var updateManager: AppUpdateManager
    private val updateDownloaded = mutableStateOf(false)
    private val updateLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != RESULT_OK) {
            // The app remains usable; Play may offer the update again later.
        }
    }
    private val installListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) updateDownloaded.value = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateManager = AppUpdateManagerFactory.create(this)
        updateManager.registerListener(installListener)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF087A43))) {
                ProductionRentoApp(
                    updateDownloaded = updateDownloaded.value,
                    onInstallUpdate = { updateManager.completeUpdate() }
                )
            }
        }
        checkForPlayUpdate()
    }

    override fun onResume() {
        super.onResume()
        if (::updateManager.isInitialized) {
            updateManager.appUpdateInfo.addOnSuccessListener { info ->
                if (info.installStatus() == InstallStatus.DOWNLOADED) updateDownloaded.value = true
            }
        }
    }

    private fun checkForPlayUpdate() {
        updateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                updateManager.startUpdateFlowForResult(
                    info,
                    updateLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                )
            }
        }
    }

    override fun onDestroy() {
        if (::updateManager.isInitialized) updateManager.unregisterListener(installListener)
        super.onDestroy()
    }
}

@Composable
private fun ProductionRentoApp(updateDownloaded: Boolean, onInstallUpdate: () -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val functions = remember { FirebaseFunctions.getInstance() }
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }
    var role by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf<Long?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { currentUser = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    DisposableEffect(currentUser?.uid) {
        val uid = currentUser?.uid
        if (uid == null) {
            role = ""
            balance = null
            onDispose { }
        } else {
            val profileListener = db.collection("users").document(uid).addSnapshotListener { snap, error ->
                if (error != null) status = error.localizedMessage ?: "Unable to load profile"
                role = snap?.getString("role") ?: "UNSELECTED"
            }
            val walletListener = db.collection("wallets").document(uid).addSnapshotListener { snap, _ ->
                balance = snap?.getLong("balanceUgx") ?: 0L
            }
            onDispose { profileListener.remove(); walletListener.remove() }
        }
    }

    fun runTask(task: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            status = ""
            try { task() } catch (e: Exception) { status = e.localizedMessage ?: "Request failed" }
            finally { busy = false }
        }
    }

    if (updateDownloaded) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Rento update downloaded", fontWeight = FontWeight.Bold)
                    Text("Install now to restart with the latest version. Your account and app data stay on this device.", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = onInstallUpdate) { Text("Install") }
            }
        }
    }

    if (currentUser == null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(28.dp))
            Text("Rento", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("Secure property and rent management")
            if (register) {
                OutlinedTextField(name, { name = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone (+256...)") }, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Button(enabled = !busy && email.isNotBlank() && password.length >= 6 && (!register || (name.isNotBlank() && phone.isNotBlank())), onClick = {
                runTask {
                    if (register) {
                        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
                        val uid = requireNotNull(result.user).uid
                        db.collection("users").document(uid).set(mapOf("uid" to uid, "name" to name.trim(), "email" to email.trim(), "phone" to phone.trim(), "role" to "UNSELECTED", "createdAt" to System.currentTimeMillis())).await()
                    } else auth.signInWithEmailAndPassword(email.trim(), password).await()
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Please wait…" else if (register) "Create account" else "Sign in") }
            TextButton(onClick = { register = !register; status = "" }) { Text(if (register) "Already registered? Sign in" else "Create a Rento account") }
            Text("Use your real account. Demo balances and sample properties are disabled.", style = MaterialTheme.typography.bodySmall)
            if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.error)
        }
    } else if (role == "UNSELECTED" || role.isBlank()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Choose your account type", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("This choice is saved securely and cannot be changed from the app.")
            Spacer(Modifier.height(16.dp))
            Button(enabled = !busy, onClick = { runTask { functions.getHttpsCallable("selectUserRole").call(mapOf("role" to "LANDLORD")).await() } }, modifier = Modifier.fillMaxWidth()) { Text("I am a landlord") }
            OutlinedButton(enabled = !busy, onClick = { runTask { functions.getHttpsCallable("selectUserRole").call(mapOf("role" to "TENANT")).await() } }, modifier = Modifier.fillMaxWidth()) { Text("I am a tenant") }
            Text("Caretaker accounts require administrator approval.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { auth.signOut() }) { Text("Sign out") }
            if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.error)
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Rento", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(currentUser?.email ?: "", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { auth.signOut() }) { Text("Sign out") }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Available wallet balance", style = MaterialTheme.typography.titleMedium)
                    Text(balance?.let { "UGX " + "%,d".format(it) } ?: "Loading…", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Live server balance · UGX", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(if (role == "LANDLORD") "Landlord workspace" else "Tenant workspace", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Your account is connected to Rento services. Property, lease, invoice and payment actions are enabled only when their server workflows are deployed and authorized.")
            if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.error)
        }
    }
}
