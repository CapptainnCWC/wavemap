package io.github.capptainncwc.wavemap.ui.survey

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.capptainncwc.wavemap.AppViewModel
import io.github.capptainncwc.wavemap.R
import io.github.capptainncwc.wavemap.survey.Project
import java.text.DateFormat
import java.util.Date

@Composable
fun SurveyRoot(vm: AppViewModel) {
    val project by vm.project.collectAsStateWithLifecycle()
    val p = project
    if (p == null) ProjectsScreen(vm) else SurveyScreen(vm, p)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectsScreen(vm: AppViewModel) {
    val projects by vm.projects.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Project?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.survey_title)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.new_project)) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.survey_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(projects, key = { it.id }) { p ->
                Card(Modifier.fillMaxWidth().clickable { vm.openProject(p) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.project_summary, p.points.size, p.aps.size) + " · " +
                                    DateFormat.getDateInstance().format(Date(p.created)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { toDelete = p }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                    }
                }
            }
        }
    }

    if (creating) {
        val errorText = stringResource(R.string.create_failed)
        NewProjectDialog(onDismiss = { creating = false }) { name, uri, w, h ->
            creating = false
            vm.createProject(name, uri, w, h, errorText)
        }
    }
    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.delete_project_q)) },
            text = { Text(p.name) },
            confirmButton = { TextButton(onClick = { vm.deleteProject(p); toDelete = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun NewProjectDialog(onDismiss: () -> Unit, onCreate: (String, Uri?, Float, Float) -> Unit) {
    val defaultName = stringResource(R.string.default_project_name)
    var name by remember { mutableStateOf(defaultName) }
    var width by remember { mutableStateOf("20") }
    var height by remember { mutableStateOf("15") }
    val w = width.replace(',', '.').toFloatOrNull()?.takeIf { it in 1f..2000f }
    val h = height.replace(',', '.').toFloatOrNull()?.takeIf { it in 1f..2000f }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && w != null) onCreate(name.ifBlank { defaultName }, uri, w, h ?: w)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_project)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.project_name)) }, singleLine = true)
                OutlinedTextField(
                    width, { width = it },
                    label = { Text(stringResource(R.string.plan_width_m)) },
                    isError = w == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text(
                    stringResource(R.string.plan_width_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = w != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Image, null)
                    Text(stringResource(R.string.choose_plan_image), Modifier.padding(start = 8.dp))
                }
                OutlinedTextField(
                    height, { height = it },
                    label = { Text(stringResource(R.string.plan_height_m)) },
                    isError = h == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedButton(
                    onClick = { if (w != null && h != null) onCreate(name.ifBlank { defaultName }, null, w, h) },
                    enabled = w != null && h != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.GridOn, null)
                    Text(stringResource(R.string.blank_grid), Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
