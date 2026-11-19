package com.example.dinex

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal const val MAX_PROFILE_PHOTO_BYTES = 6 * 1024 * 1024

@OptIn(ExperimentalEncodingApi::class, ExperimentalResourceApi::class)
@Composable
internal fun ProfilePhoto(name: String, encoded: String, size: Dp, modifier: Modifier = Modifier) {
    val image = remember(encoded) {
        if (encoded.isBlank()) null else runCatching { Base64.decode(encoded).decodeToImageBitmap() }.getOrNull()
    }
    Box(modifier.size(size).clip(CircleShape).background(Green), contentAlignment = Alignment.Center) {
        if (image != null) Image(image, "Foto de perfil de $name", Modifier.size(size), contentScale = ContentScale.Crop)
        else Text(name.firstOrNull()?.uppercase() ?: "D", color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value * .34f).sp)
    }
}

@OptIn(ExperimentalEncodingApi::class, ExperimentalResourceApi::class)
internal fun validatedProfilePhoto(file: TransferFile): String {
    require(file.bytes.isNotEmpty() && file.bytes.size <= MAX_PROFILE_PHOTO_BYTES) { "La imagen debe pesar menos de 6 MB." }
    val bytes = file.bytes
    val isPng = bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte()
    val isJpeg = bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
    val isWebp = bytes.size >= 12 && bytes.copyOfRange(0, 4).decodeToString() == "RIFF" && bytes.copyOfRange(8, 12).decodeToString() == "WEBP"
    require(isPng || isJpeg || isWebp) { "Elige una imagen PNG, JPG o WebP." }
    runCatching { bytes.decodeToImageBitmap() }.getOrElse { error("No pudimos abrir esa imagen.") }
    return Base64.encode(bytes)
}
