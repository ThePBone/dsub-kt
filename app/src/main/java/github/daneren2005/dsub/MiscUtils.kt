package github.daneren2005.dsub

import android.animation.ValueAnimator
import android.os.Build
import android.text.Html
import android.text.Spanned
import android.util.TypedValue
import android.view.View
import androidx.annotation.AttrRes
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.color.DynamicColors
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

private val isSamsung by lazy {
    Build.MANUFACTURER.equals("samsung", ignoreCase = true)
}

val isDynamicColorAvailable by lazy {
    DynamicColors.isDynamicColorAvailable() || (isSamsung && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
}

fun View.setBackgroundFromAttribute(@AttrRes attrRes: Int) {
    val a = TypedValue()
    context.theme.resolveAttribute(attrRes, a, true)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && a.isColorType) {
        setBackgroundColor(a.data)
    } else {
        background = ResourcesCompat.getDrawable(context.resources, a.resourceId, context.theme)
    }
}

fun File.ensureIsDirectory() = if(isDirectory) this else null
fun File.ensureIsFile() = if(isFile) this else null
