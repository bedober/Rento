package com.bedober.rento

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val RentoGreen = Color(0xFF0A7A42)

data class UnitCard(val number: String, val property: String, val rent: Long, val tenant: String, val status: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RentoTheme { RentoApp() } }
    }
}

@Composable
private fun RentoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = RentoGreen, secondary = Color(0xFF65A982), background = Color(0xFFF5F5F5)), content = content)
}

@Composable
private fun RentoApp() {
    var role by remember { mutableStateOf("Landlord") }
    var tab by remember { mutableStateOf("Home") }
    var showBalance by remember { mutableStateOf(true) }
    val units = remember { listOf(
        UnitCard("A-01", "Green View Apartments", 450000, "Sarah N.", "Occupied"),
        UnitCard("A-02", "Green View Apartments", 450000, "David K.", "Occupied"),
        UnitCard("B-01", "Green View Apartments", 520000, "Vacant", "Vacant"),
        UnitCard("H-01", "Rento Hostel", 180000, "Mary A.", "Occupied"),
        UnitCard("H-02", "Rento Hostel", 180000, "Vacant", "Vacant")
    ) }
    Scaffold(bottomBar = {
        NavigationBar {
            listOf("Home", "Properties", "Wallet", "Payments", "More").forEach { destination ->
                NavigationBarItem(selected = tab == destination, onClick = { tab = destination },
                    icon = { Text(when(destination) { "Home" -> "⌂"; "Properties" -> "▦"; "Wallet" -> "◉"; "Payments" -> "↔"; else -> "⋯" }) },
                    label = { Text(destination) })
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text("Rento", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = RentoGreen)
                Text("Rent Made Simple · DEMO MODE", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("View as", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(12.dp))
                    listOf("Landlord", "Tenant", "Caretaker").forEach { selectedRole ->
                        FilterChip(selected = role == selectedRole, onClick = { role = selectedRole }, label = { Text(selectedRole) }, modifier = Modifier.padding(end = 4.dp))
                    }
                }
            }
            when (tab) {
                "Home", "Wallet" -> {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = RentoGreen), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp)) {
                                Text(if (role == "Tenant") "My wallet" else "Wallet balance", color = Color.White)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (showBalance) "UGX 2,450,000" else "UGX ••••••", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick = { showBalance = !showBalance }) { Text(if (showBalance) "Hide" else "Show", color = Color.White) }
                                }
                                Text("Demo balance · Not real money", color = Color.White.copy(alpha = .8f))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { tab = "Wallet" }) { Text("Top up", color = Color.White) }
                                    OutlinedButton(onClick = { tab = "Payments" }) { Text("Transactions", color = Color.White) }
                                }
                            }
                        }
                    }
                    item { Text(if (role == "Tenant") "My rent" else "Portfolio overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Metric("Properties", "2")
                            Metric("Units", "5")
                            Metric("Occupied", "60%")
                        }
                    }
                    item { Metric("Expected monthly rent", "UGX 1,780,000", Modifier.fillMaxWidth()) }
                    item { Text("Recent units", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    items(units) { unit ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Row { Text(unit.number, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text(unit.status, color = if (unit.status == "Occupied") RentoGreen else Color.Gray) }
                                Text(unit.property, color = Color.Gray)
                                Text(unit.tenant)
                                Text("UGX " + "%,d".format(unit.rent) + " / month")
                            }
                        }
                    }
                }
                "Properties" -> {
                    item { Text("Properties & units", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                    item { Button(onClick = { }) { Text("＋ Add property") } }
                    items(units) { unit ->
                        Card { Column(Modifier.padding(16.dp)) { Text("${unit.property} · ${unit.number}", fontWeight = FontWeight.Bold); Text("${unit.status} · UGX ${"%,d".format(unit.rent)}") } }
                    }
                }
                "Payments" -> {
                    item { Text("Rent payments", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                    item { Text("No live payments in demo. Connect Firebase and Pesapal sandbox to test transactions.") }
                    item { Button(onClick = { }) { Text("Pay rent (demo flow)") } }
                }
                else -> {
                    item { Text("More", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                    items(listOf("Tenants", "Maintenance", "Announcements & chat", "Reports", "Profile & KYC", "Settings")) { entry ->
                        ListItem(headlineContent = { Text(entry) }, trailingContent = { Text("›") })
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) { Column(Modifier.padding(14.dp)) { Text(label, color = Color.Gray, style = MaterialTheme.typography.labelMedium); Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) } }
}