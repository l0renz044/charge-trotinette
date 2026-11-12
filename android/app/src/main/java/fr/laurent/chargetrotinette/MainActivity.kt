package fr.laurent.chargetrotinette

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChargeTheme {
                MainScreen(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPlug()
    }
}

@Composable
fun ChargeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}

@Composable
fun Section(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
fun PlugPill(reachable: Boolean?, onClick: () -> Unit) {
    val color = when (reachable) {
        true -> Color(0xFF16A34A)
        false -> Color(0xFFDC2626)
        null -> Color(0xFF64748B)
    }
    val text = when (reachable) {
        true -> "Prise joignable"
        false -> "Prise injoignable"
        null -> "Prise ?"
    }
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onClick() },
        color = color,
    ) {
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun SettingField(label: String, value: String, keyboard: KeyboardType, onValue: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onValue(it)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun MainScreen(vm: MainViewModel) {
    val ui = vm.ui
    val context = LocalContext.current
    val plan = vm.currentPlan()
    val now = System.currentTimeMillis()

    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    // Bluetooth éteint : Android impose une boîte de dialogue « Autoriser à activer le Bluetooth ? ».
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.readScooter()
        } else {
            vm.onMessage("Bluetooth non activé : impossible de lire la trottinette. Saisis le niveau à la main.")
        }
    }
    fun readIfBluetoothOn() {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        when {
            adapter == null -> vm.onMessage("Ce téléphone n'a pas de Bluetooth. Saisis le niveau à la main.")
            adapter.isEnabled -> vm.readScooter()
            else -> enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            readIfBluetoothOn()
        } else {
            vm.onMessage("Permission Bluetooth refusée : impossible de lire la trottinette. Saisis le niveau à la main.")
        }
    }
    fun readWithPermission() {
        val granted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) readIfBluetoothOn() else permissionLauncher.launch(permissions)
    }

    // Test du Wi-Fi : Android exige la permission de localisation pour lire le nom du réseau.
    var afterLocation by remember { mutableStateOf("check") }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (afterLocation == "use") vm.useCurrentWifi() else vm.checkWifi(true)
        } else {
            vm.onMessage("Localisation refusée : Android l'exige pour lire le nom du Wi-Fi.")
        }
    }
    fun wifiAction(action: String) {
        afterLocation = action
        if (NetworkChecker.hasLocationPermission(context)) {
            if (action == "use") vm.useCurrentWifi() else vm.checkWifi(true)
        } else {
            locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    var settingsOpen by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("🛴 Charge trottinette", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                PlugPill(ui.plugReachable) { vm.refreshPlug() }
            }

            val wifi = ui.wifi
            if (wifi != null) {
                Text(
                    Fmt.wifi(wifi),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().clickable { wifiAction("check") },
                )
            }

            if (ui.updateLatest != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🆕 Mise à jour disponible : ${ui.updateLatest}")
                        Button(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.APK_URL)))
                        }) { Text("Télécharger") }
                    }
                }
            }

            Section("Dernière commande envoyée à la prise") {
                val last = ui.lastCommand
                if (last == null) {
                    Text("Aucune commande envoyée pour l'instant.")
                } else {
                    Text(Fmt.describe(last))
                }
                val note = Fmt.consistency(last, ui.plugStatus, now)
                if (note != null) {
                    Text(note, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                } else if (ui.plugStatus != null) {
                    val st = ui.plugStatus
                    Text(
                        "Prise : " + (if (st?.on == true) "allumée" else "éteinte"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Section("Niveau de la trottinette") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(onClick = { readWithPermission() }, enabled = !ui.busy) { Text("Lire (Bluetooth)") }
                    OutlinedTextField(
                        value = ui.currentPercentText,
                        onValueChange = { vm.onCurrentText(it) },
                        label = { Text("Niveau (%)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                val r = ui.reading
                val at = ui.readingAtMillis
                if (r != null && at != null) {
                    val parts = mutableListOf<String>()
                    if (r.percent != null) parts.add("${r.percent} %")
                    if (r.voltage != null) parts.add("${Fmt.volts(r.voltage)} V")
                    if (r.motorTempC != null) parts.add("moteur ${r.motorTempC} °C")
                    Text(
                        "Lu à ${Fmt.hm(at)} : " + parts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    "Trottinette allumée, appli iSinwheel fermée. Lis le niveau au repos, avant de brancher le chargeur.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Section("Charge") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = ui.mode == StartMode.NOW, onClick = { vm.onMode(StartMode.NOW) })
                    Text("Dès que possible")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = ui.mode == StartMode.READY_BY, onClick = { vm.onMode(StartMode.READY_BY) })
                    Text("Prête pour une heure donnée")
                }
                if (ui.mode == StartMode.READY_BY) {
                    OutlinedTextField(
                        value = ui.readyByText,
                        onValueChange = { vm.onReadyBy(it) },
                        label = { Text("Prête pour (HH:MM)") },
                        isError = !vm.readyByValid(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = ui.batteryHot, onCheckedChange = { vm.onHot(it) })
                    Text("Batterie chaude (attendre ${ui.settings.coolingMinutes} min avant de charger)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = ui.fullCharge, onCheckedChange = { vm.onFull(it) })
                    Text("Charger à 100 % cette fois")
                }

                if (plan == null) {
                    Text("Renseigne un niveau inférieur à l'objectif pour calculer la charge.")
                } else {
                    Text(
                        Fmt.duration(plan.durationSec),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val target = if (ui.fullCharge) 100 else ui.settings.targetPercent
                    val startText = if (plan.immediate) "Démarre maintenant" else "Démarre à ${Fmt.hm(plan.startAtMillis)}"
                    Text("$startText · fin vers ${Fmt.hm(plan.endAtMillis)} ($target %)")
                    if (plan.tooTight) {
                        Text("Trop juste pour finir à l'heure demandée : démarrage au plus tôt.", color = MaterialTheme.colorScheme.error)
                    }
                    if (plan.capped) {
                        Text("Durée plafonnée à ${Fmt.num(ui.settings.maxHours)} h.", color = MaterialTheme.colorScheme.error)
                    }
                }

                Button(
                    onClick = { vm.start() },
                    enabled = plan != null && !ui.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Lancer / planifier") }
                Button(
                    onClick = { vm.stop() },
                    enabled = !ui.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Annuler et couper") }
            }

            Section {
                Text(ui.message, style = MaterialTheme.typography.bodyMedium)
            }

            Section {
                TextButton(onClick = { settingsOpen = !settingsOpen }) {
                    Text(if (settingsOpen) "Réglages ▲" else "Réglages ▼")
                }
                if (settingsOpen) {
                    val s = ui.settings
                    SettingField("Adresse IP de la prise", s.plugHost, KeyboardType.Uri) {
                        vm.onSettings(s.copy(plugHost = it.trim()))
                    }
                    SettingField("Adresse Bluetooth de la trottinette", s.scooterMac, KeyboardType.Ascii) {
                        vm.onSettings(s.copy(scooterMac = it.trim().uppercase()))
                    }
                    OutlinedTextField(
                        value = s.plugSsid,
                        onValueChange = { vm.onSettings(s.copy(plugSsid = it)) },
                        label = { Text("Wi-Fi de la prise (nom du réseau)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { wifiAction("use") },
                            modifier = Modifier.weight(1f),
                        ) { Text("Réseau actuel") }
                        OutlinedButton(
                            onClick = { vm.readPlugWifi() },
                            enabled = !ui.busy,
                            modifier = Modifier.weight(1f),
                        ) { Text("Lire depuis la prise") }
                    }
                    OutlinedButton(
                        onClick = { wifiAction("check") },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Tester le réseau Wi-Fi") }
                    Text(
                        "Android ne donne le nom du Wi-Fi qu'avec la localisation autorisée et activée. L'appli n'utilise pas ta position.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    SettingField("Objectif de charge (%)", s.targetPercent.toString(), KeyboardType.Number) { text ->
                        val v = text.toIntOrNull()
                        if (v != null && v in 1..100) vm.onSettings(s.copy(targetPercent = v))
                    }
                    SettingField("Courant du chargeur (A)", Fmt.num(s.chargerAmps), KeyboardType.Decimal) { text ->
                        val v = text.replace(',', '.').toDoubleOrNull()
                        if (v != null && v > 0.0) vm.onSettings(s.copy(chargerAmps = v))
                    }
                    SettingField("Capacité de la batterie (Ah)", Fmt.num(s.batteryAh), KeyboardType.Decimal) { text ->
                        val v = text.replace(',', '.').toDoubleOrNull()
                        if (v != null && v > 0.0) vm.onSettings(s.copy(batteryAh = v))
                    }
                    SettingField("Coefficient de correction", Fmt.num(s.coefficient), KeyboardType.Decimal) { text ->
                        val v = text.replace(',', '.').toDoubleOrNull()
                        if (v != null && v > 0.0) vm.onSettings(s.copy(coefficient = v))
                    }
                    SettingField("Refroidissement (min)", s.coolingMinutes.toString(), KeyboardType.Number) { text ->
                        val v = text.toIntOrNull()
                        if (v != null && v >= 0) vm.onSettings(s.copy(coolingMinutes = v))
                    }
                    SettingField("Durée maximale (h)", Fmt.num(s.maxHours), KeyboardType.Decimal) { text ->
                        val v = text.replace(',', '.').toDoubleOrNull()
                        if (v != null && v > 0.0) vm.onSettings(s.copy(maxHours = v))
                    }
                    Text(
                        "Durée = (objectif - niveau) × capacité ÷ courant × coefficient. " +
                            "Ajuste le coefficient après un essai (ex. 1,10 si la batterie finit trop basse).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        onClick = { vm.testPlug() },
                        enabled = !ui.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Tester la prise (3 clics)") }
                    Text(
                        "À faire chargeur débranché de la trottinette et hors charge.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(modifier = Modifier.padding(4.dp))
            Text(
                "Charge trottinette v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
