package app.logdate.wear.v2.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent
import androidx.compose.ui.platform.testTag
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.VerticalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.VerticalPagerScaffold
import kotlinx.coroutines.launch

object HomeTags {
    const val PAGER = "home_pager"
}

/**
 * Home: Today on the first page and the moments of the last 7 days on the second. Record is
 * Today's edge button, which reaches its full size only at the end of a list, so the two pages
 * never share one list.
 *
 * Turning the crown up at the top of the moment list returns to Today, and turning it down at the
 * end of Today opens the list. While [pagingLocked], Home stays on Today, where Stop is.
 *
 * The pager's own crown handling is off. By default it takes every crown turn to change page, so
 * a list inside a page would never scroll; here the lists scroll and [pageForCrown] decides when
 * a turn changes page.
 */
@Composable
fun HomeScreen(
    pagingLocked: Boolean,
    today: @Composable (TransformingLazyColumnState) -> Unit,
    flow: @Composable (TransformingLazyColumnState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { HomePage.entries.size })
    val todayList = rememberTransformingLazyColumnState()
    val flowList = rememberTransformingLazyColumnState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagingLocked) {
        if (pagingLocked && pagerState.currentPage != HomePage.TODAY.ordinal) {
            pagerState.animateScrollToPage(HomePage.TODAY.ordinal)
        }
    }
    VerticalPagerScaffold(pagerState = pagerState, modifier = modifier.testTag(HomeTags.PAGER)) {
        VerticalPager(state = pagerState, userScrollEnabled = !pagingLocked, rotaryScrollableBehavior = null) { index ->
            val page = HomePage.entries[index]
            val list = if (page == HomePage.TODAY) todayList else flowList
            AnimatedPage(index, pagerState) {
                Box(
                    modifier =
                        Modifier.fillMaxSize().onPreRotaryScrollEvent { event ->
                            if (pagerState.isScrollInProgress) return@onPreRotaryScrollEvent true
                            val target =
                                pageForCrown(
                                    current = page,
                                    scrollPixels = event.verticalScrollPixels,
                                    canScrollBackward = list.canScrollBackward,
                                    canScrollForward = list.canScrollForward,
                                    locked = pagingLocked,
                                ) ?: return@onPreRotaryScrollEvent false
                            scope.launch { pagerState.animateScrollToPage(target.ordinal) }
                            true
                        },
                ) {
                    when (page) {
                        HomePage.TODAY -> today(todayList)
                        HomePage.FLOW -> flow(flowList)
                    }
                }
            }
        }
    }
}
