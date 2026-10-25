// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.api.ui.v1.page

data class ModConfigTarget(val id: String, val category: String? = null)

object OneConfigPages {
    @JvmStatic
    @JvmOverloads
    fun modConfig(id: String, category: String? = null) = ModConfigTarget(id, category)
}
