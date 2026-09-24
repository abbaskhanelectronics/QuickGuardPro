package com.abbaskhanelectronics.quickguardpro.admin.ui

import com.abbaskhanelectronics.quickguardpro.admin.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object QgColors {
    val Bg = Color(0xFF121316)
    val Surface = Color(0xFF1B1C21)
    val SurfaceHigh = Color(0xFF24262D)
    val Outline = Color(0xFF34373F)
    val Red = Color(0xFFD32F2F)
    val RedDark = Color(0xFF8E1616)
    val Silver = Color(0xFFC9CDD4)
    val Muted = Color(0xFF8C919B)
    val Green = Color(0xFF2E9E5B)
    val Amber = Color(0xFFE0A030)
    val Blue = Color(0xFF3D7BD9)
}

private val scheme = darkColorScheme(
    primary = QgColors.Red,
    onPrimary = Color.White,
    primaryContainer = QgColors.RedDark,
    onPrimaryContainer = Color.White,
    secondary = QgColors.Silver,
    onSecondary = QgColors.Bg,
    background = QgColors.Bg,
    onBackground = Color(0xFFECEDEF),
    surface = QgColors.Surface,
    onSurface = Color(0xFFECEDEF),
    surfaceVariant = QgColors.SurfaceHigh,
    onSurfaceVariant = QgColors.Silver,
    outline = QgColors.Outline,
    error = Color(0xFFFF5A52),
    surfaceContainer = QgColors.Surface,
    surfaceContainerHigh = QgColors.SurfaceHigh,
    surfaceContainerHighest = QgColors.SurfaceHigh,
    surfaceContainerLow = QgColors.Surface,
)

@Composable
fun QgTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}

val CardShape = RoundedCornerShape(16.dp)

@Composable
fun QgCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = QgColors.Surface),
        border = BorderStroke(1.dp, QgColors.Outline),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun BrandHeader(subtitle: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Brush.horizontalGradient(listOf(QgColors.RedDark, QgColors.SurfaceHigh)))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_qg_foreground),
            contentDescription = null,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text("QUICK GUARD PRO", fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 1.5.sp)
            Text(subtitle ?: "Abbas Khan Electronics", color = QgColors.Silver, fontSize = 13.sp)
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = QgColors.Muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
    )
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = QgColors.Muted, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Text(
            value,
            color = valueColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun StatusChip(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

fun statusColor(status: String): Color = when (status) {
    "ACTIVE", "UNLOCKED", "ONLINE", "EXECUTED" -> QgColors.Green
    "DUE_SOON", "DUE_TODAY", "PENDING", "DELIVERED" -> QgColors.Amber
    "OVERDUE", "TEMPORARILY_RESTRICTED", "LOCKED", "FAILED", "OFFLINE" -> QgColors.Red
    "FULLY_PAID", "RELEASED" -> QgColors.Blue
    else -> QgColors.Muted
}

fun statusLabel(status: String): String = status.replace('_', ' ')

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (danger) QgColors.Red else QgColors.Silver,
                    contentColor = if (danger) Color.White else QgColors.Bg,
                ),
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = QgColors.SurfaceHigh,
    )
}

@Composable
fun BigButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = QgColors.Red,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
    ) { Text(text, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp) }
}

fun formatPkr(v: Long): String = "Rs " + String.format(java.util.Locale.US, "%,d", v)
