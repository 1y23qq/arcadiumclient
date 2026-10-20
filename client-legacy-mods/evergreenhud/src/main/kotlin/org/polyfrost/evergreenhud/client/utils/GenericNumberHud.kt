package org.polyfrost.evergreenhud.client.utils

import org.polyfrost.evergreenhud.utils.decimalFormat
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch

private const val DEFAULT_ACCURACY = 2
private const val DEFAULT_TRAILING_ZEROS = true

open class GenericNumberHud(
    title: String,
    category: Category,
    prefix: String = "$title: ",
    suffix: String = "",
    id: String = "${title.replace(' ', '_').lowercase()}.json",
    defaultValue: Float = 0f,
) : CachedTextHud(
    title,
    category,
    prefix,
    suffix,
    id,
    decimalFormat(DEFAULT_ACCURACY, DEFAULT_TRAILING_ZEROS).format(defaultValue),
) {

    @Slider(title = "Accuracy", min = 0F, max = 8F, step = 1F)
    var accuracy = DEFAULT_ACCURACY

    @Switch(title = "Trailing Zeros")
    var trailingZeros = DEFAULT_TRAILING_ZEROS

    private val df get() = decimalFormat(accuracy, trailingZeros)
    protected var value = defaultValue

    protected fun format(number: Number): String = df.format(number)

    protected fun updateWithNumber(number: Float) {
        value = number
        updateWithText(format(number))
    }

    override fun setup() {
        super.setup()

        if (isReal) {
            addCallback("accuracy") { _: Int ->
                updateWithNumber(this.value)
                false
            }
            addCallback("trailingZeros") { _: Boolean ->
                updateWithNumber(this.value)
                false
            }
        }
    }
}
