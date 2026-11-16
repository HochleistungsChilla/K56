package app.k56.player.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import app.k56.player.dsp.DdcStore
import app.k56.player.dsp.DspState
import app.k56.player.dsp.IrLibrary
import app.k56.player.ui.components.ScreenHeader
import kotlinx.coroutines.launch
import java.io.File

/**
 * DP settings in the shape ViPER4Android uses: one master switch, then a list
 * of effects. Each row stays collapsed until it is needed, so the screen does
 * not dump twenty sliders at once.
 */
@Composable
fun DspScreen(
    dsp: DspState,
    irLibrary: IrLibrary,
    ddcStore: DdcStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(dsp.enabled) }
    var preamp by remember { mutableStateOf(dsp.preampDb) }
    var limiter by remember { mutableStateOf(dsp.limiterEnabled) }

    var clarity by remember { mutableStateOf(dsp.clarity) }
    var bassGain by remember { mutableStateOf(dsp.bassGainDb) }
    var bassFreq by remember { mutableStateOf(dsp.bassFreq) }
    var spectrum by remember { mutableStateOf(dsp.spectrumExtension) }
    var width by remember { mutableStateOf(dsp.stereoWidth) }
    var haas by remember { mutableStateOf(dsp.haasMs) }
    var reverbWet by remember { mutableStateOf(dsp.reverbWet) }
    var reverbRoom by remember { mutableStateOf(dsp.reverbRoom) }
    var compOn by remember { mutableStateOf(dsp.compressorEnabled) }
    var compThreshold by remember { mutableStateOf(dsp.compressorThresholdDb) }
    var compRatio by remember { mutableStateOf(dsp.compressorRatio) }
    var agcOn by remember { mutableStateOf(dsp.agcEnabled) }
    var tubeDrive by remember { mutableStateOf(dsp.tubeDrive) }
    var tubeMix by remember { mutableStateOf(dsp.tubeMix) }

    val bands = remember { mutableStateListOf(*dsp.bandGains.toTypedArray()) }
    var irName by remember { mutableStateOf(dsp.irName) }
    var kernels by remember { mutableStateOf(irLibrary.imported()) }
    var ddcName by remember { mutableStateOf(dsp.ddcName) }
    var profiles by remember { mutableStateOf(ddcStore.imported()) }
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
                } else {
                    message = context.getString(R.string.dsp_import_failed)
                }
            }
        }
    }

    val pickDdc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val file = ddcStore.importFromUri(context, uri)
                profiles = ddcStore.imported()
                if (file != null) {
                    dsp.setDdc(file)
                    ddcName = dsp.ddcName
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

    LaunchedEffect(scanToken) {
        runCatching { ddcStore.ensureBundled() }
        runCatching { ddcStore.scanAndImport() }
        val added = runCatching { irLibrary.scanAndImport() }.getOrDefault(0)
        kernels = irLibrary.imported()
        profiles = ddcStore.imported()
        if (added > 0) message = context.getString(R.string.dsp_imported, added)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        ScreenHeader(stringResource(R.string.dsp_title), onBack)

        // Master section
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
                if (enabled) {
                    FxSlider(
                        label = stringResource(R.string.dsp_preamp, preamp.toInt()),
                        value = preamp,
                        range = -18f..12f,
                        onChange = { preamp = it; dsp.applyPreamp(it) },
                    )
                }
            }
        }

        if (!enabled) {
            Text(
                text = stringResource(R.string.dsp_disabled_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            return@Column
        }

        // --- Effects, ViPER4Android style ---------------------------------

        EffectCard(
            title = stringResource(R.string.ddc_title),
            subtitle = ddcName ?: stringResource(R.string.dsp_ir_off),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { scanToken++ }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.dsp_scan))
                }
                OutlinedButton(
                    onClick = { pickDdc.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.ddc_pick))
                }
            }
            Spacer(Modifier.height(8.dp))
            FilterChip(
                selected = ddcName == null,
                onClick = { dsp.clearDdc(); ddcName = null },
                label = { Text(stringResource(R.string.dsp_ir_off)) },
            )
            profiles.forEach { file: File ->
                Spacer(Modifier.height(4.dp))
                FilterChip(
                    selected = ddcName == file.name,
                    onClick = { dsp.setDdc(file); ddcName = dsp.ddcName },
                    label = { Text(file.nameWithoutExtension) },
                )
            }
        }

        EffectCard(
            title = stringResource(R.string.dsp_convolution),
            subtitle = irName ?: stringResource(R.string.dsp_ir_off),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { scanToken++ }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.dsp_scan))
                }
                OutlinedButton(onClick = { pickFolder.launch(null) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.dsp_pick_folder))
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { pickKernel.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.dsp_pick_file))
            }
            Spacer(Modifier.height(8.dp))
            FilterChip(
                selected = irName == null,
                onClick = { dsp.clearIr(); irName = null },
                label = { Text(stringResource(R.string.dsp_ir_off)) },
            )
            kernels.forEach { file: File ->
                Spacer(Modifier.height(4.dp))
                FilterChip(
                    selected = irName == file.name,
                    onClick = { dsp.setIr(file); irName = dsp.irName },
                    label = { Text(file.nameWithoutExtension) },
                )
            }
        }

        EffectCard(
            title = stringResource(R.string.dsp_bands),
            subtitle = if (bands.all { it == 0f }) {
                stringResource(R.string.dsp_flat)
            } else {
                stringResource(R.string.dsp_adjusted)
            },
        ) {
            DspState.BANDS.forEachIndexed { index, freq ->
                FxSlider(
                    label = if (freq >= 1000) "${freq / 1000} kHz" else "$freq Hz",
                    value = bands[index],
                    range = -12f..12f,
                    onChange = { bands[index] = it; dsp.setBand(index, it) },
                )
            }
        }

        EffectCard(
            title = stringResource(R.string.dsp_clarity),
            subtitle = if (clarity > 0f) "${clarity.toInt()} / 14" else stringResource(R.string.dsp_ir_off),
            checked = clarity > 0f,
            onChecked = { on ->
                clarity = if (on) 6f else 0f
                dsp.applyClarity(clarity)
            },
        ) {
            FxSlider(
                label = stringResource(R.string.dsp_clarity),
                value = clarity,
                range = 0f..14f,
                onChange = { clarity = it; dsp.applyClarity(it) },
            )
        }

        EffectCard(
            title = stringResource(R.string.dsp_spectrum),
            subtitle = if (spectrum > 0f) "${spectrum.toInt()} / 12" else stringResource(R.string.dsp_ir_off),
            checked = spectrum > 0f,
            onChecked = { on ->
                spectrum = if (on) 6f else 0f
                dsp.applySpectrumExtension(spectrum)
            },
        ) {
            FxSlider(
                label = stringResource(R.string.dsp_spectrum),
                value = spectrum,
                range = 0f..12f,
                onChange = { spectrum = it; dsp.applySpectrumExtension(it) },
            )
        }

        EffectCard(
            title = stringResource(R.string.dsp_bass_title),
            subtitle = if (bassGain > 0f) {
                stringResource(R.string.dsp_bass_summary, bassGain.toInt(), bassFreq.toInt())
            } else {
                stringResource(R.string.dsp_ir_off)
            },
            checked = bassGain > 0f,
            onChecked = { on ->
                bassGain = if (on) 6f else 0f
                dsp.setBass(bassGain, bassFreq)
            },
        ) {
            FxSlider(
                label = stringResource(R.string.dsp_dynamic_bass, bassGain.toInt()),
                value = bassGain,
                range = 0f..12f,
                onChange = { bassGain = it; dsp.setBass(it, bassFreq) },
            )
            FxSlider(
                label = stringResource(R.string.dsp_bass_freq, bassFreq.toInt()),
                value = bassFreq,
                range = 30f..150f,
                onChange = { bassFreq = it; dsp.setBass(bassGain, it) },
            )
        }

        EffectCard(
            title = stringResource(R.string.dsp_stereo),
            subtitle = if (width != 100f || haas > 0f) {
                "${width.toInt()} %" + if (haas > 0f) " · ${haas.toInt()} ms" else ""
            } else {
                stringResource(R.string.dsp_ir_off)
            },
            checked = width != 100f || haas > 0f,
            onChecked = { on ->
                width = if (on) 140f else 100f
                haas = if (on) 3f else 0f
                dsp.applyStereoWidth(width)
                dsp.setHaas(haas)
            },
        ) {
            FxSlider(
                label = stringResource(R.string.dsp_width, width.toInt()),
                value = width,
                range = 40f..200f,
                onChange = { width = it; dsp.applyStereoWidth(it) },
            )
            FxSlider(
                label = stringResource(R.string.dsp_haas, haas.toInt()),
                value = haas,
                range = 0f..10f,
                onChange = { haas = it; dsp.setHaas(it) },
            )
        }

        EffectCard(
            title = stringResource(R.string.dsp_reverb),
            subtitle = if (reverbWet > 0f) "${reverbWet.toInt()} %" else stringResource(R.string.dsp_ir_off),
            checked = reverbWet > 0f,
            onChecked = { on ->
                reverbWet = if (on) 25f else 0f
                dsp.setReverb(reverbWet, reverbRoom)
            },
        ) {
            FxSlider(
                label = stringResource(R.string.dsp_reverb_wet, reverbWet.toInt()),
                value = reverbWet,
                range = 0f..100f,
                onChange = { reverbWet = it; dsp.setReverb(it, reverbRoom) },
            )
            FxSlider(
                label = stringResource(R.string.dsp_reverb_room, reverbRoom.toInt()),
                value = reverbRoom,
                range = 0f..100f,
                onChange = { reverbRoom = it; dsp.setReverb(reverbWet, it) },
            )
        }

        EffectCard(
            title = stringResource(R.string.dsp_compressor),
            subtitle = if (compOn) {
                stringResource(R.string.dsp_comp_summary, compThreshold.toInt(), compRatio)
            } else {
                stringResource(R.string.dsp_ir_off)
            },
            checked = compOn,
            onChecked = { on -> compOn = on; dsp.setCompressor(on, compThreshold, compRatio) },
        ) {
            FxSlider(
                label = stringResource(R.string.dsp_threshold, compThreshold.toInt()),
                value = compThreshold,
                range = -40f..0f,
                onChange = { compThreshold = it; dsp.setCompressor(true, it, compRatio) },
            )
            FxSlider(
                label = stringResource(R.string.dsp_ratio, compRatio),
                value = compRatio,
                range = 1f..10f,
                onChange = { compRatio = it; dsp.setCompressor(true, compThreshold, it) },
            )
        }

        EffectCard(
            title = stringResource(R.string.dsp_agc),
            subtitle = if (agcOn) stringResource(R.string.dsp_on) else stringResource(R.string.dsp_ir_off),
            checked = agcOn,
            onChecked = { on -> agcOn = on; dsp.setAgc(on) },
        ) {
            Text(
                stringResource(R.string.dsp_agc_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        EffectCard(
            title = stringResource(R.string.dsp_tube),
            subtitle = if (tubeDrive > 0f) {
                stringResource(R.string.dsp_tube_summary, tubeDrive.toInt(), tubeMix.toInt())
            } else {
                stringResource(R.string.dsp_ir_off)
            },
            checked = tubeDrive > 0f,
            onChecked = { on ->
                tubeDrive = if (on) 6f else 0f
                tubeMix = if (on) 40f else 0f
                dsp.setTube(tubeDrive, tubeMix)
            },
        ) {
            FxSlider(
                label = stringResource(R.string.dsp_tube_drive, tubeDrive.toInt()),
                value = tubeDrive,
                range = 0f..12f,
                onChange = { tubeDrive = it; dsp.setTube(it, tubeMix) },
            )
            FxSlider(
                label = stringResource(R.string.dsp_tube_mix, tubeMix.toInt()),
                value = tubeMix,
                range = 0f..100f,
                onChange = { tubeMix = it; dsp.setTube(tubeDrive, it) },
            )
        }

        EffectCard(
            title = stringResource(R.string.dsp_limiter),
            subtitle = if (limiter) stringResource(R.string.dsp_on) else stringResource(R.string.dsp_ir_off),
            checked = limiter,
            onChecked = { on -> limiter = on; dsp.setLimiter(on) },
        ) {
            Text(
                stringResource(R.string.dsp_limiter_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

/** Collapsed row with an optional switch; content appears on tap. */
@Composable
private fun EffectCard(
    title: String,
    subtitle: String,
    checked: Boolean? = null,
    onChecked: ((Boolean) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (checked != null && onChecked != null) {
                    Switch(
                        checked = checked,
                        onCheckedChange = { on ->
                            onChecked(on)
                            if (on && !expanded) expanded = true
                        },
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp
                    else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun FxSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
    )
}
