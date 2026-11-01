package com.ozon.books.ui.home

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ozon.books.model.Book
import com.ozon.books.ui.components.BookCoverView
import com.ozon.books.ui.components.EmptyTabState

@Composable
fun HomeScreen(
    recentlyReadBooks: List<Book>,
    hasBooksInLibrary: Boolean,
    onBookClick: (String) -> Unit,
    onNavigateToLibrary: () -> Unit,
    onImportBookClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    if (recentlyReadBooks.isEmpty()) {
        EmptyTabState(
            icon = Icons.AutoMirrored.Rounded.MenuBook,
            message = "No books read recently",
            subMessage = if (hasBooksInLibrary) {
                "You haven't opened any books yet. Choose a book from your library to start reading!"
            } else {
                "Your reading history will appear here. Import an EPUB file from your device storage to get started."
            },
            actionText = if (hasBooksInLibrary) "Browse Library" else "Import EPUB",
            onActionClick = if (hasBooksInLibrary) onNavigateToLibrary else onImportBookClick,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding()
            )
        ) {
            item {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    val isMobile = maxWidth < 600.dp
                    val direction = LocalLayoutDirection.current
                    val rowPadding = if (isMobile) PaddingValues(horizontal = 16.dp) else PaddingValues(
                        start = contentPadding.calculateStartPadding(direction),
                        end = contentPadding.calculateEndPadding(direction)
                    )
                    val visibleColumns = if (isMobile && maxWidth >= 320.dp && recentlyReadBooks.size > 1) 2 else 1
                    val cardWidth = if (isMobile) {
                        minOf(204.dp, ((maxWidth - 32.dp - 20.dp * (visibleColumns - 1)) / visibleColumns)
                            .coerceAtLeast(0.dp))
                    } else 204.dp
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = rowPadding,
                        horizontalArrangement = if (isMobile) {
                            Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally)
                        } else Arrangement.spacedBy(20.dp)
                    ) {
                        items(recentlyReadBooks, key = { it.id }) { book ->
                            RecentBookCard(
                                book = book,
                                modifier = Modifier.width(cardWidth),
                                onClick = { onBookClick(book.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentBookCard(
    book: Book,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val progress = book.progressPercent.coerceIn(0, 100)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            BookCoverView(
                book = book,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                Text(
                    text = "$progress%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
