package com.example.virtualuwb.presentation.screen.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.virtualuwb.domain.model.AssistantMessage
import com.example.virtualuwb.presentation.viewmodel.MapUiState
import com.example.virtualuwb.presentation.viewmodel.MapViewModel
import kotlin.math.max

private val ScreenBackground = Color(0xFFF9FAFB)
private val LightBorder = Color(0xFFE5E7EB)
private val UserMessageBubble = Color(0xFF5B63F6)
private val AssistantMessageBubble = Color.White
private val AssistantAvatarColor = Color(0xFFE9EDFF)
private val SubtitleColor = Color(0xFF6B7280)

@Composable
fun AssistantScreen(
    uiState: MapUiState,
    mapViewModel: MapViewModel,
    modifier: Modifier = Modifier,
    viewModel: AssistantViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val listState = rememberLazyListState()

    val targetTag = uiState.selectedTag ?: uiState.tags.firstOrNull()
    val suggestions = if (targetTag != null) {
        val tagName = targetTag.name
        listOf(
            "Where is $tagName?",
            "Guide me to $tagName",
            "Is $tagName safe?",
            "Any tags in danger?"
        )
    } else {
        listOf(
            "Show system status",
            "Any tags in danger?",
            "Show recent events",
            "What can you track?"
        )
    }

    val introMessage = messages.firstOrNull()?.takeIf { !it.isUser }
    val conversationMessages = if (introMessage != null) messages.drop(1) else messages
    val hasUserMessages = conversationMessages.any { it.isUser }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(conversationMessages.size, isLoading) {
        if (conversationMessages.isNotEmpty() || isLoading) {
            val loadingOffset = if (isLoading) 1 else 0
            val itemCount = (if (introMessage != null) 1 else 0) +
                           (if (!hasUserMessages && introMessage != null) 1 else 0) +
                           conversationMessages.size +
                           loadingOffset
            listState.animateScrollToItem(max(itemCount - 1, 0))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBackground)
    ) {
        // Compact Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Text(
                text = "AI Assistant",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Ask about tags, zones, guidance, or recent events",
                style = MaterialTheme.typography.bodyMedium,
                color = SubtitleColor
            )
        }

        // Single unified chat feed with greeting, suggestions, and all messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Greeting message as first item
            introMessage?.let { greeting ->
                item(key = greeting.id) {
                    ChatMessageRow(greeting)
                }
            }

            // Suggestion chips (only if no user messages yet)
            if (!hasUserMessages && introMessage != null) {
                item(key = "suggestions") {
                    SuggestionChipsItem(
                        suggestions = suggestions,
                        onSuggestionClick = { viewModel.sendMessage(uiState, mapViewModel, it) }
                    )
                }
            }

            // Conversation messages
            items(conversationMessages, key = { it.id }) { message ->
                ChatMessageRow(message)
            }

            // Loading indicator
            if (isLoading) {
                item(key = "loading") {
                    AssistantLoadingIndicator()
                }
            }
        }

        // Bottom Composer
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 8.dp,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(color = LightBorder, thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { viewModel.onInputTextChanged(it) },
                        placeholder = { Text("Ask about tags or zones...") },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF4F5F7),
                            unfocusedContainerColor = Color(0xFFF4F5F7),
                            disabledContainerColor = Color(0xFFF4F5F7),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        shape = RoundedCornerShape(20.dp),
                        maxLines = 3,
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { viewModel.sendMessage(uiState, mapViewModel) })
                    )

                    val canSend = inputText.isNotBlank() && !isLoading
                    FilledIconButton(
                        onClick = { viewModel.sendMessage(uiState, mapViewModel) },
                        enabled = canSend,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (canSend) UserMessageBubble else Color(0xFFE5E7EB),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFFE5E7EB),
                            disabledContentColor = Color(0xFF9CA3AF)
                        ),
                        modifier = Modifier.size(40.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionChipsItem(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Try asking",
            style = MaterialTheme.typography.labelMedium,
            color = SubtitleColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            suggestions.forEach { suggestion ->
                SuggestionChip(
                    onClick = { onSuggestionClick(suggestion) },
                    label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = Color.White,
                        labelColor = Color(0xFF374151)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LightBorder)
                )
            }
        }
    }
}

@Composable
private fun ChatMessageRow(message: AssistantMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!message.isUser) {
            AssistantAvatar()
            Spacer(modifier = Modifier.width(8.dp))
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth(if (message.isUser) 0.78f else 0.78f)
                .wrapContentHeight(),
            color = if (message.isUser) UserMessageBubble else AssistantMessageBubble,
            contentColor = if (message.isUser) Color.White else MaterialTheme.colorScheme.onSurface,
            shape = if (message.isUser) {
                RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
            } else {
                RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
            },
            border = if (message.isUser) null else androidx.compose.foundation.BorderStroke(1.dp, LightBorder),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun AssistantLoadingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistantAvatar()
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.48f)
                .wrapContentHeight(),
            color = AssistantMessageBubble,
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, LightBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = UserMessageBubble,
                    trackColor = Color(0xFFDDE2FB)
                )
                Text("Thinking...", style = MaterialTheme.typography.labelSmall, color = SubtitleColor)
            }
        }
    }
}

@Composable
private fun AssistantAvatar() {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(AssistantAvatarColor, CircleShape)
            .border(1.dp, Color(0xFFD9DEFA), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = UserMessageBubble,
            modifier = Modifier.size(14.dp)
        )
    }
}
