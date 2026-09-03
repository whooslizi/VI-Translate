package com.vitranslate.pdf.ui.components

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.vitranslate.pdf.model.TargetLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlsView(
    selectedLanguage: TargetLanguage,
    onLanguageSelected: (TargetLanguage) -> Unit,
    overwrite: Boolean,
    onOverwriteChange: (Boolean) -> Unit,
    customSaveDirectory: String?,
    onPickSaveDirectory: () -> Unit,
    isTranslating: Boolean,
    onStartTranslation: () -> Unit,
    onCancelTranslation: () -> Unit,
    engineType: String = "google",
    onEngineTypeChange: (String) -> Unit = {},
    onOpenLlmSettings: () -> Unit = {}
) {
    var expandedLang by remember { mutableStateOf(false) }
    var expandedEngine by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val folderDisplayName = remember(customSaveDirectory) {
        if (customSaveDirectory.isNullOrBlank()) {
            "Mặc định (Bộ nhớ ứng dụng)"
        } else if (customSaveDirectory.startsWith("content://")) {
            try {
                val uri = Uri.parse(customSaveDirectory)
                val docTree = DocumentFile.fromTreeUri(context, uri)
                docTree?.name ?: uri.lastPathSegment?.substringAfterLast(":") ?: customSaveDirectory
            } catch (_: Exception) {
                customSaveDirectory
            }
        } else {
            customSaveDirectory
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Engine Picker & LLM Config Button
            Text(
                text = "Công cụ dịch",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExposedDropdownMenuBox(
                    expanded = expandedEngine,
                    onExpandedChange = { if (!isTranslating) expandedEngine = !expandedEngine },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = if (engineType == "openai") "🤖 OpenAI / Custom LLM" else "🌐 Google Translate (Miễn phí)",
                        onValueChange = {},
                        readOnly = true,
                        enabled = !isTranslating,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedEngine)
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = !isTranslating)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = expandedEngine,
                        onDismissRequest = { expandedEngine = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("🌐 Google Translate (Miễn phí)", style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                onEngineTypeChange("google")
                                expandedEngine = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("🤖 OpenAI / Custom LLM (GPT-4o, DeepSeek)", style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                onEngineTypeChange("openai")
                                expandedEngine = false
                            }
                        )
                    }
                }

                if (engineType == "openai") {
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = onOpenLlmSettings,
                        enabled = !isTranslating
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Cấu hình AI LLM",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Dịch sang",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))

            ExposedDropdownMenuBox(
                expanded = expandedLang,
                onExpandedChange = { if (!isTranslating) expandedLang = !expandedLang }
            ) {
                OutlinedTextField(
                    value = selectedLanguage.name,
                    onValueChange = {},
                    readOnly = true,
                    enabled = !isTranslating,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLang)
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = !isTranslating)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = expandedLang,
                    onDismissRequest = { expandedLang = false }
                ) {
                    TargetLanguage.SUPPORTED_LANGUAGES.forEach { language ->
                        DropdownMenuItem(
                            text = {
                                Text(language.name, style = MaterialTheme.typography.bodyMedium)
                            },
                            onClick = {
                                onLanguageSelected(language)
                                expandedLang = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lưu vào",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = folderDisplayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = onPickSaveDirectory,
                    enabled = !isTranslating
                ) {
                    Text("Đổi", style = MaterialTheme.typography.labelLarge)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !isTranslating) { onOverwriteChange(!overwrite) }
            ) {
                Checkbox(
                    checked = overwrite,
                    onCheckedChange = { if (!isTranslating) onOverwriteChange(it) },
                    enabled = !isTranslating
                )
                Text(
                    text = "Ghi đè file đã dịch trước đó",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = { if (isTranslating) onCancelTranslation() else onStartTranslation() },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTranslating) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                Text(
                    text = if (isTranslating) "Huỷ dịch" else "Bắt đầu dịch",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
