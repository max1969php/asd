package com.asd.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.asd.player.ui.theme.Asd
import kotlin.math.min

/** La "A" cerchiata, disegnata a mano libera (più o meno). */
@Composable
fun AnarchyLogo(modifier: Modifier = Modifier, color: Color = Asd.Spray, size: Dp = 48.dp) {
    Canvas(modifier.size(size)) {
        val s = min(this.size.width, this.size.height)
        val stroke = s * 0.09f
        val c = Offset(s / 2, s / 2)
        drawCircle(color, radius = s * 0.40f, center = c, style = Stroke(stroke))
        // Le gambe della A sforano il cerchio, come da tradizione.
        val top = Offset(s * 0.50f, s * 0.02f)
        drawLine(color, top, Offset(s * 0.12f, s * 0.98f), stroke, StrokeCap.Square)
        drawLine(color, top, Offset(s * 0.88f, s * 0.98f), stroke, StrokeCap.Square)
        drawLine(color, Offset(s * 0.05f, s * 0.62f), Offset(s * 0.95f, s * 0.58f), stroke, StrokeCap.Square)
    }
}

/** Timbro storto, tipo etichetta di nastro adesivo. */
@Composable
fun Stamp(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Asd.Spray,
    rotation: Float = -4f,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier
            .rotate(rotation)
            .border(2.dp, color)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Bottone pieno con ombra netta sfalsata: niente ripple morbidi, solo blocchi. */
@Composable
fun PunkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    container: Color = Asd.Acid,
    content: Color = Asd.Toner,
    shadow: Color = Asd.Spray,
    enabled: Boolean = true,
) {
    Box(modifier.padding(end = 5.dp, bottom = 5.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(5.dp, 5.dp)
                .background(if (enabled) shadow else Asd.Concrete)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(if (enabled) container else Asd.Concrete)
                .border(2.dp, Asd.Toner)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = content)
        }
    }
}

/** Chip filtro come un pezzo di nastro: attivo = evidenziatore, inattivo = contorno. */
@Composable
fun TapeChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) Asd.Toner else Asd.Paper,
        modifier = modifier
            .background(if (selected) Asd.Acid else Color.Transparent)
            .border(1.5.dp, if (selected) Asd.Acid else Asd.Smudge)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

object Slogans {
    val all = listOf(
        "0€ al mese. Per sempre.",
        "Niente abbonamenti. Niente algoritmi. Niente padroni.",
        "Il cloud è solo il computer di qualcun altro.",
        "La tua musica non è un servizio.",
        "Nessun account. Nessuna traccia.",
        "Offline è il nuovo punk.",
        "Skippa le pubblicità: non ce ne sono.",
        "Fatto in casa. Come le tue cassette.",
    )

    fun random(): String = all.random()
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
