package com.vitranslate.pdf.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AdvancedEngineSetupDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val downloadUrl = "https://github.com/whooslizi/VI-Translate/releases/latest"

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Thiết lập trình dịch nâng cao",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Trình dịch nâng cao mang đến khả năng tái tạo công thức toán học, bảng biểu và xử lý hình ảnh (OCR) với chất lượng cao hơn.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Để giữ ứng dụng chính nhẹ và nhanh, thành phần nâng cao được phát hành dưới dạng một ứng dụng phụ trợ độc lập.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Nhấn nút bên dưới để tải về file cài đặt (.apk) của trình dịch nâng cao từ GitHub.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                    onDismiss()
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Tải về gói nâng cao (APK)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
