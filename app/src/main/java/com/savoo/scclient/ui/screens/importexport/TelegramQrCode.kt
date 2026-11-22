package com.savoo.scclient.ui.screens.importexport

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.savoo.scclient.R

@Composable
internal fun TelegramQrCode(link: String) {
    val qr = remember(link) {
        runCatching {
            val matrix = QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 480, 480)
            val pixels = IntArray(matrix.width * matrix.height) { index ->
                if (matrix[index % matrix.width, index / matrix.width]) android.graphics.Color.BLACK
                else android.graphics.Color.WHITE
            }
            Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888).asImageBitmap()
        }.getOrNull()
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (qr != null) {
            Image(
                bitmap = qr,
                contentDescription = stringResource(R.string.telegram_qr_sign_in),
                modifier = Modifier.size(184.dp).background(Color.White, RoundedCornerShape(16.dp)).padding(12.dp),
                filterQuality = FilterQuality.None,
            )
            Text(
                text = stringResource(R.string.telegram_qr_instructions),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(stringResource(R.string.telegram_qr_error), color = MaterialTheme.colorScheme.error)
        }
    }
}
