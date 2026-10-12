package com.ozon.books.ui.library

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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozon.books.model.Book
import com.ozon.books.ui.components.BookCoverView
import com.ozon.books.ui.components.BookSortOrder
import com.ozon.books.ui.components.CircleIconButton
import com.ozon.books.ui.components.EmptyTabState

@Composable
fun LibraryScreen(
    books: List<Book>,
    searchQuery: String,
    sortOrder: BookSortOrder,
    onBookClick: (String) -> Unit,
    onDeleteBook: (String) -> Unit,
    onImportBookClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    var bookToDelete by remember { mutableStateOf<Book?>(null) }

    val filteredAndSortedBooks = remember(books, searchQuery, sortOrder) {
        val filtered = if (searchQuery.isBlank()) {
            books
        } else {
            books.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.author.contains(searchQuery, ignoreCase = true)
            }
        }

        when (sortOrder) {
            BookSortOrder.RECENT -> filtered.sortedByDescending { it.addedTimestamp }
            BookSortOrder.TITLE -> filtered.sortedBy { it.title.lowercase() }
            BookSortOrder.AUTHOR -> filtered.sortedBy { it.author.lowercase() }
        }
    }

    if (books.isEmpty()) {
        EmptyTabState(
            icon = Icons.AutoMirrored.Rounded.MenuBook,
            message = "Your library is empty",
            subMessage = "Import an EPUB file from your device storage to begin reading.",
            actionText = "Import EPUB",
            onActionClick = onImportBookClick,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        )
    } else if (filteredAndSortedBooks.isEmpty()) {
        EmptyTabState(
            icon = Icons.Rounded.Search,
            message = "No matching books",
            subMessage = "No books match \"$searchQuery\". Try searching for another title or author.",
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
            itemsIndexed(filteredAndSortedBooks, key = { _, b -> b.id }) { index, book ->
                val isFirst = index == 0
                val isLast = index == filteredAndSortedBooks.size - 1

                val targetTopRadius = if (isFirst) 28.dp else 4.dp
                val targetBottomRadius = if (isLast) 28.dp else 4.dp

                val topRadius by animateDpAsState(targetValue = targetTopRadius, label = "topRadius")
                val bottomRadius by animateDpAsState(targetValue = targetBottomRadius, label = "bottomRadius")

                val shape = remember(topRadius, bottomRadius) {
                    RoundedCornerShape(topRadius, topRadius, bottomRadius, bottomRadius)
                }

                LibraryBookItem(
                    book = book,
                    shape = shape,
                    onClick = { onBookClick(book.id) },
                    onDeleteClick = { bookToDelete = book }
                )
            }
        }
    }

    // Confirmation dialog for book deletion
    bookToDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { bookToDelete = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = "Delete Book",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove \"${book.title}\" from your library? Its extracted files will be deleted.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteBook(book.id)
                        bookToDelete = null
                    }
                ) {
                    Text(text = "Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) {
                    Text(text = "Cancel")
                }
            }
        )
    }
}

@Composable
private fun LibraryBookItem(
    book: Book,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
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

                if (book.isRecentlyRead) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = "Ch. ${book.currentChapterIndex + 1}/${book.totalChapters}",
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
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = "${book.totalChapters} ${if (book.totalChapters == 1) "chapter" else "chapters"}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            CircleIconButton(
                onClick = onDeleteClick,
                icon = Icons.Rounded.Delete,
                contentDescription = "Delete book",
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.error,
                iconSize = 20.dp
            )
        }
    }
}
