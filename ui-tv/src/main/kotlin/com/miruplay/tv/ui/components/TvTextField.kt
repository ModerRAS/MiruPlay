package com.miruplay.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.miruplay.tv.ui.theme.*

@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    requestInitialFocus: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            kotlinx.coroutines.delay(120)
            focusRequester.requestFocus()
        }
    }
    val borderWidth by animateFloatAsState(
        targetValue = if (isFocused) 3f else 1f,
        label = "borderW"
    )
    
    Column(modifier = modifier) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .border(
                    width = borderWidth.dp,
                    color = if (isFocused) FocusBorder else Color.White.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp)
                // 遥控器修复：聚焦后的 BasicTextField 会吞掉 DPAD 上下（caret 处理），
                // 焦点永远出不去。在 preview 阶段拦截上下键强制走焦点搜索；
                // 左右仍留给 caret 移动，OK 键仍交给输入法。
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            focusManager.moveFocus(FocusDirection.Down); true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            focusManager.moveFocus(FocusDirection.Up); true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                        android.view.KeyEvent.KEYCODE_ENTER -> {
                            // 焦点移过来时不弹键盘，按 OK 才真正开始编辑
                            if (isFocused) keyboardController?.show()
                            false
                        }
                        else -> false
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        isFocused = state.isFocused
                        // 遥控器只是把焦点移过来（比如从左侧菜单按右键进来）时不要弹输入法，
                        // 否则整屏被键盘盖住；真正要编辑由 OK 键触发。
                        if (state.isFocused) keyboardController?.hide()
                    },
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 16.sp
                ),
                cursorBrush = SolidColor(FocusBorder),
                visualTransformation = if (isPassword) {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                } else {
                    androidx.compose.ui.text.input.VisualTransformation.None
                },
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = if (isPassword) "••••••••" else label,
                            color = TextSecondary.copy(alpha = 0.5f),
                            fontSize = 16.sp
                        )
                    }
                    innerTextField()
                }
            )
        }
    }
}
