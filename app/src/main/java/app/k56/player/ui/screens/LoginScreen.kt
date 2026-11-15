package app.k56.player.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.k56.player.BuildConfig
import app.k56.player.core.model.BackendKind
import app.k56.player.core.model.DiscoveredServer
import app.k56.player.core.model.isImplemented
import app.k56.player.data.MediaRepository
import app.k56.player.ui.appViewModel
import app.k56.player.ui.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LoginViewModel(private val repository: MediaRepository) : ViewModel() {

    var backend by mutableStateOf(BackendKind.JELLYFIN)
    var internalUrl by mutableStateOf("")
    var externalUrl by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")

    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var quickConnectCode by mutableStateOf<String?>(null)
        private set
    var quickConnectError by mutableStateOf<String?>(null)
        private set
    var quickConnectBusy by mutableStateOf(false)
        private set

    private var quickConnectJob: Job? = null

    fun selectBackend(kind: BackendKind) {
        if (!kind.isImplemented || busy) return
        backend = kind
        error = null
    }

    fun login() {
        if (busy) return
        if (backend == BackendKind.LOCAL) {
            loginLocal()
            return
        }
        val primary = internalUrl.trim()
        if (primary.isEmpty()) {
            error = "Serveradresse fehlt."
            return
        }
        if (backend != BackendKind.KODI && backend != BackendKind.LOCAL &&
            backend != BackendKind.WEBDAV && username.isBlank()
        ) {
            error = "Benutzername fehlt."
            return
        }
        busy = true
        error = null
        viewModelScope.launch {
            try {
                repository.login(
                    backend = backend,
                    internalUrl = primary,
                    externalUrl = externalUrl.trim().takeIf { it.isNotEmpty() },
                    username = username,
                    password = password,
                )
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                busy = false
            }
        }
    }

    fun showError(message: String) {
        error = message
    }

    var discoveredServers by mutableStateOf<List<DiscoveredServer>>(emptyList())
        private set
    var discovering by mutableStateOf(false)
        private set

    fun discoverServers() {
        if (discovering) return
        discovering = true
        error = null
        viewModelScope.launch {
            try {
                discoveredServers = repository.discoverLocalServers()
                if (discoveredServers.isEmpty()) {
                    error = "Kein Jellyfin-Server im Netzwerk gefunden. Adresse manuell eingeben."
                }
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                discovering = false
            }
        }
    }

    fun loginLocal() {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                repository.login(BackendKind.LOCAL, "", null, "", "")
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                busy = false
            }
        }
    }

    fun startQuickConnect() {
        if (quickConnectBusy) return
        val primary = internalUrl.trim()
        if (primary.isEmpty()) {
            quickConnectError = "Serveradresse fehlt."
            return
        }
        quickConnectBusy = true
        quickConnectError = null
        quickConnectCode = null
        quickConnectJob = viewModelScope.launch {
            try {
                repository.quickConnect(
                    backend = backend,
                    internalUrl = primary,
                    externalUrl = externalUrl.trim().takeIf { it.isNotEmpty() },
                ) { code -> quickConnectCode = code }
            } catch (e: CancellationException) {
                // dialog closed
            } catch (e: Exception) {
                quickConnectError = e.userMessage()
            } finally {
                quickConnectBusy = false
            }
        }
    }

    fun cancelQuickConnect() {
        quickConnectJob?.cancel()
        quickConnectJob = null
        quickConnectBusy = false
        quickConnectCode = null
        quickConnectError = null
    }

    var plexCode by mutableStateOf<String?>(null)
        private set
    var plexError by mutableStateOf<String?>(null)
        private set
    var plexBusy by mutableStateOf(false)
        private set

    private var plexJob: Job? = null

    fun startPlex() {
        if (plexBusy) return
        plexBusy = true
        plexError = null
        plexCode = null
        plexJob = viewModelScope.launch {
            try {
                repository.plexSignIn { code -> plexCode = code }
            } catch (e: CancellationException) {
                // dialog closed
            } catch (e: Exception) {
                plexError = e.userMessage()
            } finally {
                plexBusy = false
            }
        }
    }

    fun cancelPlex() {
        plexJob?.cancel()
        plexJob = null
        plexBusy = false
        plexCode = null
        plexError = null
    }

    override fun onCleared() {
        quickConnectJob?.cancel()
        plexJob?.cancel()
    }
}

@Composable
fun LoginScreen(repository: MediaRepository) {
    val vm = appViewModel { LoginViewModel(repository) }
    val localPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) {
            vm.loginLocal()
        } else {
            vm.showError("Ohne Medienzugriff kann K56 keine lokale Musik lesen.")
        }
    }
    var showQuickConnect by remember { mutableStateOf(false) }
    var showPlex by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var backendMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .size(84.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(24.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text("K56", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Deine Musik. Dein Server. Dein Player.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(30.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Server", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))

                Box {
                    OutlinedButton(
                        onClick = { backendMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(vm.backend.label)
                            Text(
                                vm.backend.hint,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = backendMenu,
                        onDismissRequest = { backendMenu = false },
                    ) {
                        BackendKind.entries.forEach { kind ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            if (kind.isImplemented) kind.label else "${kind.label} · bald",
                                        )
                                        Text(
                                            kind.hint,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                enabled = kind.isImplemented,
                                onClick = {
                                    vm.selectBackend(kind)
                                    backendMenu = false
                                },
                            )
                        }
                    }
                }

                if (vm.backend == BackendKind.LOCAL) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "K56 liest die Audiodateien auf diesem Gerät über MediaStore. " +
                            "Es werden keine Daten hochgeladen und keine Netzwerkverbindung benötigt.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val permissions = if (Build.VERSION.SDK_INT >= 33) {
                                arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
                            } else {
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                            localPermissionLauncher.launch(permissions)
                        },
                        enabled = !vm.busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        if (vm.busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("Lokale Musik freigeben")
                        }
                    }
                    val localError = vm.error
                    if (localError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = localError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else if (vm.backend == BackendKind.PLEX) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "K56 meldet sich über plex.tv an und findet deinen Server automatisch. " +
                            "Du bekommst einen Code, den du auf plex.tv/link eingibst.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            vm.startPlex()
                            showPlex = true
                        },
                        enabled = !vm.plexBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        if (vm.plexBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("Mit Plex anmelden")
                        }
                    }
                    val plexError = vm.plexError
                    if (plexError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = plexError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = vm.internalUrl,
                    onValueChange = { vm.internalUrl = it },
                    label = { Text("LAN-Adresse") },
                    placeholder = { Text("http://192.168.1.10:8096") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (vm.backend == BackendKind.JELLYFIN) {
                    TextButton(
                        onClick = vm::discoverServers,
                        enabled = !vm.discovering,
                    ) {
                        if (vm.discovering) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Server im Netzwerk suchen")
                    }
                    vm.discoveredServers.forEach { server ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable { vm.internalUrl = server.url },
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(server.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    server.url,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = vm.externalUrl,
                    onValueChange = { vm.externalUrl = it },
                    label = { Text("Externe Adresse (optional)") },
                    placeholder = { Text("https://music.example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Im WLAN wird die LAN-Adresse bevorzugt, im Mobilfunk die externe. " +
                        "Dafür ist keine Standortberechtigung nötig.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = vm.username,
                    onValueChange = { vm.username = it },
                    label = { Text("Benutzer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = vm.password,
                    onValueChange = { vm.password = it },
                    label = { Text("Passwort") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) "Verbergen" else "Zeigen")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )

                val error = vm.error
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = vm::login,
                    enabled = !vm.busy && vm.backend.isImplemented,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    if (vm.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Anmelden")
                    }
                }
                }
            }
        }

        if (vm.backend == BackendKind.JELLYFIN) {
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(Modifier.weight(1f))
                Text(
                    "oder",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                HorizontalDivider(Modifier.weight(1f))
            }
            Spacer(Modifier.height(18.dp))
            OutlinedButton(
                onClick = {
                    vm.startQuickConnect()
                    showQuickConnect = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Quick Connect")
            }
        }

        Spacer(Modifier.height(22.dp))
        Text(
            "Android 9+ · Jellyfin ab ${BuildConfig.JELLYFIN_MIN_VERSION} · GPL-3.0",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }

    if (showQuickConnect) {
        AlertDialog(
            onDismissRequest = {
                showQuickConnect = false
                vm.cancelQuickConnect()
            },
            title = { Text("Quick Connect") },
            text = {
                Column {
                    val code = vm.quickConnectCode
                    val quickError = vm.quickConnectError
                    when {
                        quickError != null -> Text(quickError)
                        code != null -> {
                            Text("Diesen Code in Jellyfin freigeben:")
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = code,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Jellyfin → Einstellungen → Quick Connect",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        else -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Warte auf Server …")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showQuickConnect = false
                    vm.cancelQuickConnect()
                }) { Text("Abbrechen") }
            },
        )
    }

    if (showPlex) {
        AlertDialog(
            onDismissRequest = {
                showPlex = false
                vm.cancelPlex()
            },
            title = { Text("Plex-Login") },
            text = {
                Column {
                    val code = vm.plexCode
                    val plexError = vm.plexError
                    when {
                        plexError != null -> Text(plexError)

                        code != null -> {
                            Text("Code auf plex.tv/link eingeben:")
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = code,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "https://plex.tv/link",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        else -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Warte auf Plex …")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPlex = false
                    vm.cancelPlex()
                }) { Text("Abbrechen") }
            },
        )
    }
}
