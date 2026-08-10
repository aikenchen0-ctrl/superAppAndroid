package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.core.gesture.BackGestureOption
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackGestureOptionCommitTest {
    @Test
    fun onlyPresentedSelectedOptionConsumesTheBackCommit() {
        assertTrue(
            shouldConsumeBackGestureOption(
                option = BackGestureOption.FunctionOne,
                optionWasPresented = true
            )
        )
        assertTrue(
            shouldConsumeBackGestureOption(
                option = BackGestureOption.FunctionTwo,
                optionWasPresented = true
            )
        )
        assertFalse(
            shouldConsumeBackGestureOption(
                option = BackGestureOption.None,
                optionWasPresented = true
            )
        )
        assertFalse(
            shouldConsumeBackGestureOption(
                option = BackGestureOption.FunctionOne,
                optionWasPresented = false
            )
        )
    }
}
