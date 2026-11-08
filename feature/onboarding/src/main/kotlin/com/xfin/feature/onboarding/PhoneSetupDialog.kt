package com.xfin.feature.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Shows the QR code for the phone form, and how the login check on the TV is going. */
@Composable
internal fun PhoneSetupDialog(state: AddSourceUiState, onDismiss: () -> Unit) {
    val phone = state.phone ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 760.dp).padding(24.dp),
        title = { Text("Set up from your phone") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                QrCode(
                    text = phone.address,
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Step(1, "Connect your phone to the same Wi‑Fi as this TV.")
                    Step(2, "Open the camera and point it at the code.")
                    Step(3, "Tap the link, fill in the details and tap Save.")
                    Spacer(Modifier.size(6.dp))
                    Status(state, phone)
                    Text(
                        phone.address.substringBefore("/?"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun Step(number: Int, text: String) {
    Row {
        Text("$number.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(24.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Status(state: AddSourceUiState, phone: PhoneSetupUiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            state.importing -> {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (state.imported > 0) "Importing… ${"%,d".format(state.imported)} channels" else "Checking the login…",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            state.error != null -> {
                Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(10.dp))
                Text("${state.error} Fix it on your phone and tap Save again.", color = MaterialTheme.colorScheme.error)
            }
            phone.opened -> {
                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Phone connected. Waiting for you to tap Save…", color = MaterialTheme.colorScheme.primary)
            }
            else -> Text("Waiting for your phone…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A QR code drawn module by module, so it stays sharp at any size. Always dark on white, as scanners expect. */
@Composable
private fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix: BitMatrix = remember(text) {
        QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
        )
    }
    Canvas(modifier.background(Color.White)) {
        val cell = size.minDimension / matrix.width
        // A hair of overlap hides the seams anti-aliasing would leave between neighbouring modules.
        val module = Size(cell + 0.5f, cell + 0.5f)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), module)
            }
        }
    }
}
