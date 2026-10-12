package com.ozon.books.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozon.books.model.Book
import com.ozon.books.ui.components.BookCoverView
import com.ozon.books.ui.components.CircleIconButton
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
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
            }
            itemsIndexed(recentlyReadBooks, key = { _, b -> b.id }) { index, book ->
                val isFirst = index == 0
                val isLast = index == recentlyReadBooks.size - 1

                val targetTopRadius = if (isFirst) 28.dp else 4.dp
                val targetBottomRadius = if (isLast) 28.dp else 4.dp

                val topRadius by animateDpAsState(targetValue = targetTopRadius, label = "topRadius")
                val bottomRadius by animateDpAsState(targetValue = targetBottomRadius, label = "bottomRadius")

                val shape = remember(topRadius, bottomRadius) {
                    RoundedCornerShape(topRadius, topRadius, bottomRadius, bottomRadius)
                }

                RecentBookCard(
                    book = book,
                    shape = shape,
                    onClick = { onBookClick(book.id) }
                )
            }
        }
    }
}

@Composable
private fun RecentBookCard(
    book: Book,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCoverView(
                book = book,
                modifier = Modifier
                    .width(62.dp)
                    .aspectRatio(0.68f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = "Ch. ${book.currentChapterIndex + 1} of ${book.totalChapters}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${book.progressPercent}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { book.progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Read",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
