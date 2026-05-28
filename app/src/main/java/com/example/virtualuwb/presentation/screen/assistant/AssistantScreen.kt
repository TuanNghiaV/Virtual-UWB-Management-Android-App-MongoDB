package com.example.virtualuwb.presentation.screen.assistant

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.virtualuwb.domain.model.AssistantMessage
import com.example.virtualuwb.presentation.viewmodel.MapUiState
import com.example.virtualuwb.presentation.viewmodel.MapViewModel
import kotlin.math.max

// ── Palette ──────────────────────────────────────────────────────────────────
private val Brand       = Color(0xFF5B63F6)
private val BrandLight  = Color(0xFF818CF8)
private val BrandSurface= Color(0xFFEEF0FF)
private val BgPage      = Color(0xFFF5F6FF)
private val BgCard      = Color.White
private val BorderColor = Color(0xFFE4E6F0)
private val TextPrimary = Color(0xFF1A1D3B)
private val TextSub     = Color(0xFF6B7280)
private val UserBubble  = Color(0xFF5B63F6)
private val DotColor    = Color(0xFFBFC4FB)

// ── Screen ───────────────────────────────────────────────────────────────────
@Composable
fun AssistantScreen(
    uiState: MapUiState,
    mapViewModel: MapViewModel,
    modifier: Modifier = Modifier,
    viewModel: AssistantViewModel = viewModel()
) {
    val messages   by viewModel.messages.collectAsState()
    val inputText  by viewModel.inputText.collectAsState()
    val isLoading  by viewModel.isLoading.collectAsState()
    val listState  = rememberLazyListState()

    val targetTag   = uiState.selectedTag ?: uiState.tags.firstOrNull()
    val suggestions = buildSuggestions(targetTag?.name)

    val introMessage          = messages.firstOrNull()?.takeIf { !it.isUser }
    val conversationMessages  = if (introMessage != null) messages.drop(1) else messages
    val hasUserMessages       = conversationMessages.any { it.isUser }

    // Auto-scroll to bottom
    LaunchedEffect(conversationMessages.size, isLoading) {
        if (conversationMessages.isNotEmpty() || isLoading) {
            val extra = if (isLoading) 1 else 0
            val total = (if (introMessage != null) 1 else 0) +
                        (if (!hasUserMessages && introMessage != null) 1 else 0) +
                        conversationMessages.size + extra
            listState.animateScrollToItem(max(total - 1, 0))
        }
    }

    // Detect keyboard open/closed via WindowInsets.ime
    val imeBottom = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)
    val keyboardVisible = imeBottom > 0

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage)
    ) {
        // ── Header pinned at top ───────────────────────────────────────────
        Column(modifier = Modifier.fillMaxSize()) {
            AssistantHeader()

            // ── Chat feed fills remaining space ───────────────────────────
            // contentPadding.bottom reserves space so last message is not
            // hidden behind the floating composer + nav bar.
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 14.dp,
                    end = 16.dp,
                    bottom = 240.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                introMessage?.let { greeting ->
                    item(key = greeting.id) {
                        GreetingCard(greeting.text)
                    }
                }

                if (!hasUserMessages && introMessage != null) {
                    item(key = "suggestions") {
                        SuggestionChipsSection(
                            suggestions = suggestions,
                            onSuggestionClick = { viewModel.sendMessage(uiState, mapViewModel, it) }
                        )
                    }
                }

                items(conversationMessages, key = { it.id }) { message ->
                    ChatBubble(message)
                }

                if (isLoading) {
                    item(key = "loading") {
                        ThinkingBubble()
                    }
                }
            }
        }

        // ── Composer overlaid at bottom ───────────────────────────────────
        // When keyboard is hidden  → 156.dp clears the floating bottom nav.
        // When keyboard is visible → imePadding() pushes it above the keyboard;
        //   small extra gap keeps it from touching the IME edge.
        AssistantComposer(
            inputText    = inputText,
            isLoading    = isLoading,
            onTextChange = { viewModel.onInputTextChanged(it) },
            onSend       = { viewModel.sendMessage(uiState, mapViewModel) },
            modifier     = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .then(
                    if (keyboardVisible) {
                        Modifier
                            .imePadding()
                            .padding(bottom = 8.dp)
                    } else {
                        Modifier.padding(bottom = 156.dp)
                    }
                )
        )
    }
}

// ── Header ───────────────────────────────────────────────────────────────────
@Composable
private fun AssistantHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF4A52E8), Color(0xFF7B82F5))
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Avatar circle
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape)
                    .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "AI Assistant",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 17.sp
                    )
                )
                Text(
                    text = "UWB Tracker · Powered by Gemini",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White.copy(alpha = 0.75f)
                    )
                )
            }
        }
    }
}

// ── Greeting Card ────────────────────────────────────────────────────────────
@Composable
private fun GreetingCard(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        SmallAvatar()
        Spacer(Modifier.width(9.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(0.88f),
            color = BgCard,
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Brand,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "UWB Assistant",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Brand,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, lineHeight = 22.sp)
                )
            }
        }
    }
}

// ── Suggestion Chips ─────────────────────────────────────────────────────────
private data class SuggestionItem(val label: String, val icon: ImageVector)

private fun buildSuggestions(tagName: String?): List<SuggestionItem> =
    if (tagName != null) listOf(
        SuggestionItem("Where is $tagName?",  Icons.Filled.LocationOn),
        SuggestionItem("Guide me to $tagName", Icons.Filled.Route),
        SuggestionItem("Is $tagName safe?",   Icons.Filled.Shield),
        SuggestionItem("Any tags in danger?", Icons.Filled.Warning)
    ) else listOf(
        SuggestionItem("Show system status",  Icons.Filled.WifiTethering),
        SuggestionItem("Any tags in danger?", Icons.Filled.Warning),
        SuggestionItem("What can you track?", Icons.Filled.Sensors),
        SuggestionItem("Show recent events",  Icons.Filled.Map)
    )

@Composable
private fun SuggestionChipsSection(
    suggestions: List<SuggestionItem>,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Try asking",
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextSub,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            ),
            modifier = Modifier.padding(start = 2.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            suggestions.forEach { item ->
                StyledSuggestionChip(item = item, onClick = { onSuggestionClick(item.label) })
            }
        }
    }
}

@Composable
private fun StyledSuggestionChip(item: SuggestionItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(BgCard)
            .border(1.dp, BorderColor, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = Brand,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelMedium.copy(
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

// ── Chat Bubble ──────────────────────────────────────────────────────────────
@Composable
private fun ChatBubble(message: AssistantMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!message.isUser) {
            SmallAvatar()
            Spacer(Modifier.width(8.dp))
        }

        val bubbleShape = if (message.isUser)
            RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
        else
            RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)

        Surface(
            modifier = Modifier
                .fillMaxWidth(0.80f)
                .wrapContentHeight(),
            color      = if (message.isUser) UserBubble else BgCard,
            shape      = bubbleShape,
            border     = if (message.isUser) null else androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
            shadowElevation = if (message.isUser) 0.dp else 1.dp
        ) {
            Text(
                text     = message.text,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                style    = MaterialTheme.typography.bodyMedium.copy(
                    color      = if (message.isUser) Color.White else TextPrimary,
                    lineHeight = 21.sp
                )
            )
        }
    }
}

// ── Thinking Bubble ──────────────────────────────────────────────────────────
@Composable
private fun ThinkingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmallAvatar()
        Spacer(Modifier.width(8.dp))
        Surface(
            color  = BgCard,
            shape  = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                TypingDot(delayMs = 0)
                TypingDot(delayMs = 150)
                TypingDot(delayMs = 300)
            }
        }
    }
}

@Composable
private fun TypingDot(delayMs: Int) {
    val transition = rememberInfiniteTransition(label = "dot_$delayMs")
    val alpha by transition.animateFloat(
        initialValue   = 0.3f,
        targetValue    = 1f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(durationMillis = 500, delayMillis = delayMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha_$delayMs"
    )
    Box(
        modifier = Modifier
            .size(7.dp)
            .background(Brand.copy(alpha = alpha), CircleShape)
    )
}

// ── Small Avatar ─────────────────────────────────────────────────────────────
@Composable
private fun SmallAvatar() {
    Box(
        modifier = Modifier
            .size(26.dp)
            .background(BrandSurface, CircleShape)
            .border(1.dp, Color(0xFFD1D5FA), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = Brand,
            modifier = Modifier.size(13.dp)
        )
    }
}

// ── Composer ─────────────────────────────────────────────────────────────────
@Composable
private fun AssistantComposer(
    inputText    : String,
    isLoading    : Boolean,
    onTextChange : (String) -> Unit,
    onSend       : () -> Unit,
    modifier     : Modifier = Modifier
) {
    val canSend = inputText.isNotBlank() && !isLoading

    Surface(
        modifier        = modifier.fillMaxWidth(),
        color           = BgCard,
        shadowElevation = 10.dp,
        shape           = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Text field
            TextField(
                value        = inputText,
                onValueChange = onTextChange,
                placeholder  = {
                    Text(
                        "Ask about tags, zones or events…",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSub)
                    )
                },
                modifier     = Modifier.weight(1f),
                colors       = TextFieldDefaults.colors(
                    focusedContainerColor   = Color(0xFFF0F1FD),
                    unfocusedContainerColor = Color(0xFFF0F1FD),
                    disabledContainerColor  = Color(0xFFF0F1FD),
                    focusedIndicatorColor   = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor        = TextPrimary,
                    unfocusedTextColor      = TextPrimary,
                ),
                shape        = RoundedCornerShape(22.dp),
                maxLines     = 4,
                enabled      = !isLoading,
                textStyle    = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )

            // Send button
            FilledIconButton(
                onClick  = onSend,
                enabled  = canSend,
                colors   = IconButtonDefaults.filledIconButtonColors(
                    containerColor         = if (canSend) Brand else Color(0xFFE5E7EB),
                    contentColor           = Color.White,
                    disabledContainerColor = Color(0xFFE5E7EB),
                    disabledContentColor   = Color(0xFF9CA3AF)
                ),
                modifier = Modifier.size(42.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color       = Color.White,
                        modifier    = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector     = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        modifier        = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
