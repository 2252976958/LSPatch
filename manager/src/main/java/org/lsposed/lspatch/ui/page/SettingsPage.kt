package org.lsposed.lspatch.ui.page

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Ballot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.lsposed.lspatch.R
import org.lsposed.lspatch.config.MyKeyStore
import org.lsposed.lspatch.ui.component.settings.SettingsItem
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage() {
    Scaffold(
        topBar = { TopBar() }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            KeyStore()
        }
    }
}

@Composable
private fun TopBar() {
    SmallTopAppBar(
        title = { Text(stringResource(R.string.page_settings)) }
    )
}

@Composable
private fun KeyStore() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dropDownExpanded by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    Box {
        SettingsItem(
            onClick = { dropDownExpanded = !dropDownExpanded },
            icon = Icons.Outlined.Ballot,
            title = stringResource(R.string.settings_keystore),
            desc = stringResource(if (MyKeyStore.useDefault) R.string.settings_keystore_default else R.string.settings_keystore_custom)
        )
        DropdownMenu(expanded = dropDownExpanded, onDismissRequest = { dropDownExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_keystore_default)) },
                onClick = {
                    scope.launch { MyKeyStore.reset() }
                    dropDownExpanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_keystore_custom)) },
                onClick = { showDialog = true }
            )
        }
    }

    if (showDialog) {
        var wrongKeystore by rememberSaveable { mutableStateOf(false) }
        var wrongPassword by rememberSaveable { mutableStateOf(false) }
        var wrongAliasName by rememberSaveable { mutableStateOf(false) }
        var wrongAliasPassword by rememberSaveable { mutableStateOf(false) }

        var path by rememberSaveable { mutableStateOf("") }
        var password by rememberSaveable { mutableStateOf("") }
        var alias by rememberSaveable { mutableStateOf("") }
        var aliasPassword by rememberSaveable { mutableStateOf("") }

        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            context.contentResolver.openInputStream(uri).use { input ->
                MyKeyStore.tmpFile.outputStream().use { output ->
                    input?.copyTo(output)
                }
            }
            path = uri.path ?: ""
        }

        AlertDialog(
            onDismissRequest = { dropDownExpanded = false; showDialog = false },
            confirmButton = {
                TextButton(
                    content = { Text(stringResource(android.R.string.ok)) },
                    onClick = {
                        wrongKeystore = false
                        wrongPassword = false
                        wrongAliasName = false
                        wrongAliasPassword = false

                        if (path.isEmpty()) {
                            wrongKeystore = true
                            return@TextButton
                        }
                        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
                        try {
                            MyKeyStore.tmpFile.inputStream().use { input ->
                                keyStore.load(input, password.toCharArray())
                            }
                        } catch (e: IOException) {
                            wrongKeystore = true
                            if (e.message == "KeyStore integrity check failed.") {
                                wrongPassword = true
                            }
                            return@TextButton
                        }
                        if (!keyStore.containsAlias(alias)) {
                            wrongAliasName = true
                            return@TextButton
                        }
                        try {
                            keyStore.getKey(alias, aliasPassword.toCharArray())
                        } catch (e: GeneralSecurityException) {
                            wrongAliasPassword = true
                            return@TextButton
                        }

                        scope.launch { MyKeyStore.setCustom(password, alias, aliasPassword) }
                        dropDownExpanded = false
                        showDialog = false
                    })
            },
            dismissButton = {
                TextButton(
                    content = { Text(stringResource(android.R.string.cancel)) },
                    onClick = { dropDownExpanded = false; showDialog = false }
                )
            },
            title = {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.settings_keystore_dialog_title),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column {
                    val interactionSource = remember { MutableInteractionSource() }
                    LaunchedEffect(interactionSource) {
                        interactionSource.interactions.collect { interaction ->
                            if (interaction is PressInteraction.Release) {
                                launcher.launch("*/*")
                            }
                        }
                    }

                    val wrongText = when {
                        wrongAliasPassword -> stringResource(R.string.settings_keystore_wrong_alias_password)
                        wrongAliasName -> stringResource(R.string.settings_keystore_wrong_alias)
                        wrongPassword -> stringResource(R.string.settings_keystore_wrong_password)
                        wrongKeystore -> stringResource(R.string.settings_keystore_wrong_keystore)
                        else -> null
                    }
                    Text(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 8.dp),
                        text = wrongText ?: stringResource(R.string.settings_keystore_desc),
                        color = if (wrongText != null) MaterialTheme.colorScheme.error else Color.Unspecified
                    )

                    OutlinedTextField(
                        value = path,
                        onValueChange = { path = it },
                        readOnly = true,
                        label = { Text(stringResource(R.string.settings_keystore_file)) },
                        placeholder = { Text(stringResource(R.string.settings_keystore_file)) },
                        singleLine = true,
                        isError = wrongKeystore,
                        interactionSource = interactionSource
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.settings_keystore_password)) },
                        singleLine = true,
                        isError = wrongPassword
                    )
                    OutlinedTextField(
                        value = alias,
                        onValueChange = { alias = it },
                        label = { Text(stringResource(R.string.settings_keystore_alias)) },
                        singleLine = true,
                        isError = wrongAliasName
                    )
                    OutlinedTextField(
                        value = aliasPassword,
                        onValueChange = { aliasPassword = it },
                        label = { Text(stringResource(R.string.settings_keystore_alias_password)) },
                        singleLine = true,
                        isError = wrongAliasPassword
                    )
                }
            }
        )
    }
}
