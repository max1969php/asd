package com.asd.desktop.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asd.player.ui.theme.Asd

/** Barra di scorrimento a blocco, come il resto di ASD: grigia, acida sotto il mouse. */
val AsdScrollbarStyle = ScrollbarStyle(
    minimalHeight = 32.dp,
    thickness = 10.dp,
    shape = RectangleShape,
    hoverDurationMillis = 200,
    unhoverColor = Asd.Smudge.copy(alpha = 0.55f),
    hoverColor = Asd.Acid,
)

/** La barra sul lato destro di un elenco (LazyColumn) che sta in un Box. */
@Composable
fun BoxScope.ListScrollbar(state: LazyListState) {
    VerticalScrollbar(
        rememberScrollbarAdapter(state),
        style = AsdScrollbarStyle,
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 2.dp, horizontal = 2.dp),
    )
}

/** La barra sul lato destro di una colonna con verticalScroll che sta in un Box. */
@Composable
fun BoxScope.ColumnScrollbar(state: ScrollState) {
    VerticalScrollbar(
        rememberScrollbarAdapter(state),
        style = AsdScrollbarStyle,
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 2.dp, horizontal = 2.dp),
    )
}
