package com.vitranslate.pdf.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vitranslate.pdf.repository.AiProvider

enum class SelectedEngineType {
    GOOGLE_DEFAULT,
    OPENAI,
    GEMINI
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiEngineSetupDialog(
    initialEngineType: SelectedEngineType = SelectedEngineType.GOOGLE_DEFAULT,
    initialApiKey: String = "",
    initialModelName: String = "gpt-4o-mini",
    initialEndpoint: String = "https://api.openai.com/v1/chat/completions",
    onDismissRequest: () -> Unit,
    onApplySession: (
        engineType: SelectedEngineType,
        apiKey: String,
        modelName: String,
        endpoint: String
    ) -> Unit
) {
    var selectedType by remember { mutableStateOf(initialEngineType) }
    var apiKeyText by remember { mutableStateOf(initialApiKey) }
    var modelNameText by remember { mutableStateOf(initialModelName) }
    var endpointText by remember { mutableStateOf(initialEndpoint) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Cấu hình AI Dịch (LLM)",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Bảo mật API Key: Mã khóa API token chỉ được giữ tạm thời trong bộ nhớ RAM cho phiên dịch hiện tại, KHÔNG BAO GIỜ lưu vào bộ nhớ máy hay file nhật ký.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Text(
                    text = "Chọn công cụ dịch:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedType == SelectedEngineType.GOOGLE_DEFAULT,
                        onClick = { selectedType = SelectedEngineType.GOOGLE_DEFAULT }
                    )
                    Text("Google Translate (Mặc định, miễn phí)")
                }

                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedType == SelectedEngineType.OPENAI,
                        onClick = {
                            selectedType = SelectedEngineType.OPENAI
                            if (modelNameText.isBlank() || modelNameText.contains("gemini")) {
                                modelNameText = "gpt-4o-mini"
                            }
                        }
                    )
                    Text("OpenAI / Custom LLM (GPT-4o, Ollama, etc.)")
                }

                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedType == SelectedEngineType.GEMINI,
                        onClick = {
                            selectedType = SelectedEngineType.GEMINI
                            if (modelNameText.isBlank() || modelNameText.contains("gpt")) {
                                modelNameText = "gemini-1.5-flash"
                            }
                        }
                    )
                    Text("Google Gemini (Gemini 1.5/2.0 Flash)")
                }

                if (selectedType != SelectedEngineType.GOOGLE_DEFAULT) {
                    OutlinedTextField(
                        value = apiKeyText,
                        onValueChange = { apiKeyText = it },
                        label = { Text("API Key / Token (Tạm thời)") },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Text(if (isPasswordVisible) "Ẩn" else "Hiện")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = modelNameText,
                        onValueChange = { modelNameText = it },
                        label = { Text("Tên Model (VD: gpt-4o-mini / gemini-1.5-flash)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (selectedType == SelectedEngineType.OPENAI) {
                        OutlinedTextField(
                            value = endpointText,
                            onValueChange = { endpointText = it },
                            label = { Text("Endpoint API URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApplySession(selectedType, apiKeyText, modelNameText, endpointText)
                    onDismissRequest()
                }
            ) {
                Text("Áp dụng cho phiên này")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismissRequest) {
                Text("Hủy")
            }
        }
    )
}
