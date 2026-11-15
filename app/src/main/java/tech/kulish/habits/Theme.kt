package tech.kulish.habits

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF0B0D11)
val Lime = Color(0xFFC9F45C)
val Violet = Color(0xFFA99BFF)
val Ink = Color(0xFFF2F4F7)
val Muted = Color(0xFFA3ABB9)
val CardFill = Color(0x0DFFFFFF)
val CardLine = Color(0x14FFFFFF)
val GlassFill = Color(0xD1161920)
val GlassLine = Color(0x1FFFFFFF)
val ChipFill = Color(0x12FFFFFF)

val Unbounded = FontFamily(
    Font(R.font.unbounded_medium, FontWeight.Medium),
    Font(R.font.unbounded_bold, FontWeight.Bold),
)

val TitleStyle = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Ink)
val NumberStyle = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Medium, fontSize = 24.sp, color = Ink)
val LabelStyle = TextStyle(fontSize = 13.sp, color = Muted)

val CardShape = RoundedCornerShape(22.dp)
val PillShape = RoundedCornerShape(50)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Lime,
            onPrimary = Bg,
            secondary = Violet,
            onSecondary = Bg,
            background = Bg,
            onBackground = Ink,
            surface = Color(0xFF161920),
            onSurface = Ink,
            surfaceVariant = Color(0xFF20242D),
            onSurfaceVariant = Muted,
            surfaceContainerHigh = Color(0xFF1B1F27),
            surfaceContainerLow = Color(0xFF161920),
            outline = Color(0xFF6B7383),
            error = Color(0xFFFF8A80),
        ),
        content = content,
    )
}

fun Modifier.card(shape: Shape = CardShape): Modifier =
    this.clip(shape).background(CardFill).border(1.dp, CardLine, shape)

fun Modifier.glass(shape: Shape = CardShape): Modifier =
    this.clip(shape).background(GlassFill).border(1.dp, GlassLine, shape)

/** Dark ground with two soft colour glows that give the translucent surfaces something to sit on. */
@Composable
fun Backdrop(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .drawBehind {
                val r1 = size.width * 0.85f
                val c1 = Offset(size.width * 0.05f, size.height * 0.04f)
                drawCircle(
                    brush = Brush.radialGradient(listOf(Violet.copy(alpha = 0.30f), Color.Transparent), c1, r1),
                    radius = r1, center = c1,
                )
                val r2 = size.width * 0.9f
                val c2 = Offset(size.width * 1.05f, size.height * 0.68f)
                drawCircle(
                    brush = Brush.radialGradient(listOf(Lime.copy(alpha = 0.15f), Color.Transparent), c2, r2),
                    radius = r2, center = c2,
                )
            },
        content = content,
    )
}

/** Selectable pill used for every either/or choice in the app. */
@Composable
fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Lime else ChipFill, label = "pillBg")
    val fg by animateColorAsState(if (selected) Bg else Ink, label = "pillFg")
    Box(
        modifier = modifier
            .heightIn(min = 46.dp)
            .clip(PillShape)
            .background(bg)
            .border(1.dp, if (selected) Lime else CardLine, PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

private fun strokeIcon(d: String, width: Float = 2f): ImageVector =
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = addPathNodes(d),
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()

object AppIcons {
    val Check = strokeIcon("M5 12.5l4.5 4.5L19 7.5", 3f)
    val Plus = strokeIcon("M12 5v14M5 12h14", 2.4f)
    val Minus = strokeIcon("M5 12h14", 2.4f)
    val Back = strokeIcon("M15 6l-6 6 6 6", 2.2f)
    val Forward = strokeIcon("M9 6l6 6-6 6", 2.2f)
    val Close = strokeIcon("M6 6l12 12M18 6L6 18", 2.2f)
    val Today = strokeIcon("M4 6h16M4 12h16M4 18h10")
    val Stats = strokeIcon("M5 20V11M12 20V4M19 20v-6")
    val Skip = strokeIcon("M6 5l8 7-8 7M18 5v14")

    /** Icons a habit can carry; [Habit.icon] is an index into this list. */
    val habit: List<ImageVector> = listOf(
        strokeIcon("M3 12h4l3-8 4 16 3-8h4"),
        strokeIcon("M8.5 20.5a5 5 0 0 1-3.5-8.5l7-7a5 5 0 0 1 7 7l-7 7a5 5 0 0 1-3.5 1.5zM8.5 8.5l7 7"),
        strokeIcon("M12 3c3.5 4.2 6 7 6 10a6 6 0 0 1-12 0c0-3 2.5-5.8 6-10z"),
        strokeIcon("M12 3v6M12 15v6M3 12h6M15 12h6"),
        strokeIcon("M12 21s-7-4.5-7-10a4 4 0 0 1 7-2.5A4 4 0 0 1 19 11c0 5.500-7 10-7 10z"),
        strokeIcon("M5 4h14v16H5zM9 9h6M9 13h6"),
        strokeIcon("M12 4v8l5 3M12 21a9 9 0 1 1 0-18a9 9 0 0 1 0 18z"),
        strokeIcon("M7 3v8a5 5 0 0 0 10 0V3M12 16v5M8 21h8"),
    )

    fun of(index: Int): ImageVector = habit[index.coerceIn(0, habit.size - 1)]
}
