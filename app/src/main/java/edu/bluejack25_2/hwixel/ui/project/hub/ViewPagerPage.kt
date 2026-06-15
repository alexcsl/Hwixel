package edu.bluejack25_2.hwixel.ui.project.hub

import android.view.View
import android.view.ViewGroup

fun View.fillViewPagerPage(): View {
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
    return this
}
