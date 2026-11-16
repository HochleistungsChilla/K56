package app.k56.player.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.k56.player.R
import app.k56.player.dsp.DspState
import app.k56.player.dsp.IrLibrary
import app.k56.player.ui.components.ScreenHeader
import app.k56.player.ui.userMessage
import kotlinx.coroutines.launch
import java.io.File

/**
 * DSP controls: master switch, preamp, 10 bands and the convolution kernel
 * picked from the imported ViPER4Android `.irs` files.
 */
@Composable
fun DspScreen(
    dsp: DspState,
    irLibrary: IrLibrary,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(dsp.enabled) }
    var preamp by remember { mutableStateOf(dsp.preampDb) }
    val bands = remember { mutableStateListOf(*dsp.bandGains.toTypedArray()) }
    var limiter by remember { mutableStateOf(dsp.limiterEnabled) }
    var irName by remember { mutableStateOf(dsp.irName) }
    var kernels by remember { mutableStateOf(irLibrary.imported()) }
    var message by remember { mutableStateOf<String?>(null) }
    var scanToken by remember { mutableIntStateOf(0) }

    val pickKernel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val file = irLibrary.importFromUri(context, uri)
                kernels = irLibrary.imported()
                if (file != null) {
                    dsp.setIr(file)
                    irName = dsp.irName
                    message = null
                } else {
                    message = context.getString(R.string.dsp_import_failed)
                }
            }
        }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            irLibrary.rememberFolder(context, uri)
            scanToken++
        }
    }

    // First entry scans the known V4A folders; the SAF-picked tree is re-scanned
    // on every open so new kernels show up without another tap.
    LaunchedEffect(scanToken) {
        val added = runCatching { irLibrary.scanAndImport() }.getOrDefault(0)
        kernels = irLibrary.imported()
        if (added > 0) message = context.getString(R.string.dsp_imported, added)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        ScreenHeader(stringResource(R.string.dsp_title), onBack)

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.dsp_enable), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.dsp_enable_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            dsp.applyEnabled(it)
                        },
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.dsp_preamp, preamp.toInt()),
                    style = MaterialTheme.typography.titleMedium,
                )
                Slider(
                    value = preamp,
                    onValueChange = {
                        preamp = it
                        dsp.applyPreamp(it)
                    },
                    valueRange = -18f..12f,
                    steps = 29,
                )

                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.dsp_bands), style = MaterialTheme.typography.titleMedium)
                DspState.BANDS.forEachIndexed { index, freq ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (freq >= 1000) "${freq / 1000}k" else "$freq",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Slider(
                            value = bands[index],
                            onValueChange = {
                                bands[index] = it
                                dsp.setBand(index, it)
                            },
                            valueRange = -12f..12f,
                            steps = 47,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${bands[index].toInt()}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.dsp_limiter), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.dsp_limiter_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = limiter,
                        onCheckedChange = {
                            limiter = it
                            dsp.setLimiter(it)
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.dsp_convolution), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.dsp_convolution_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { scanToken++ },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.dsp_scan))
                    }
                    OutlinedButton(
                        onClick = { pickFolder.launch(null) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.dsp_pick_folder))
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { pickKernel.launch(arrayOf("audio/*", "application/octet-stream", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.dsp_pick_file))
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = irName == null,
                        onClick = {
                            dsp.clearIr()
                            irName = null
                        },
                        label = { Text(stringResource(R.string.dsp_ir_off)) },
                    )
                    Text(
                        text = irName ?: stringResource(R.string.dsp_ir_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                kernels.forEach { file: File ->
                    Spacer(Modifier.height(4.dp))
                    FilterChip(
                        selected = irName == file.name,
                        onClick = {
                            dsp.setIr(file)
                            irName = dsp.irName
                        },
                        label = { Text(file.nameWithoutExtension) },
                    )
                }

                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
