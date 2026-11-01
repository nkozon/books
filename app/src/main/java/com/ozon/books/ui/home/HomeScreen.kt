package com.ozon.books.ui.home

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
            contentPadding = contentPadding
        ) {
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(recentlyReadBooks, key = { it.id }) { book ->
                        RecentBookCard(
                            book = book,
                            onClick = { onBookClick(book.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentBookCard(
    book: Book,
    onClick: () -> Unit
) {
    val progress = book.progressPercent.coerceIn(0, 100)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.width(204.dp)
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
