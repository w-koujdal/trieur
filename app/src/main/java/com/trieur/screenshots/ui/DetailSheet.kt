package com.trieur.screenshots.ui

import com.trieur.screenshots.tr
import com.trieur.screenshots.data.appLabel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trieur.screenshots.data.Category
import com.trieur.screenshots.data.Detected
import com.trieur.screenshots.data.Labels
import com.trieur.screenshots.data.Screenshot
import com.trieur.screenshots.data.Source
import com.trieur.screenshots.data.detect
import com.trieur.screenshots.data.findHighlights
import com.trieur.screenshots.data.searchTokens
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailSheet(
    shot: Screenshot,
    query: String,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    /** null = revenir au classement automatique. */
    onCategoryChange: (Category?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val highlightColor = MaterialTheme.colorScheme.tertiaryContainer
    val onHighlightColor = MaterialTheme.colorScheme.onTertiaryContainer
    val annotated = remember(shot.text, query, highlightColor) {
        buildAnnotatedString {
            append(shot.text)
            findHighlights(shot.text, searchTokens(query)).forEach { range ->
                addStyle(
                    SpanStyle(background = highlightColor, color = onHighlightColor, fontWeight = FontWeight.Bold),
                    range.first,
                    range.last + 1,
                )
            }
        }
    }
    val detected = remember(shot.text) { detect(shot.text) }
    val subtitle = remember(shot) {
        val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(shot.dateTaken))
        val where = Source.fromName(shot.source).label
        listOfNotNull(where, shot.sourceApp?.let(::appLabel), date).joinToString(" · ")
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(shot.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
            Text(
                "📍 " + displayPath(shot),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            CategoryPicker(shot, onCategoryChange, Modifier.padding(top = 8.dp))
            val seen = remember(shot.labels) { Labels.display(Labels.decode(shot.labels)).take(6) }
            if (seen.isNotEmpty()) {
                Text(
                    tr("Sur l'image : ", "In the image: ") + seen.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Actions en haut : toujours visibles, même panneau à moitié ouvert et texte long.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Button(onClick = { openItem(context, shot) }, modifier = Modifier.weight(1f)) { Text(tr("Ouvrir", "Open")) }
                OutlinedButton(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = tr("Partager", "Share"))
                }
                if (canDelete && !Source.fromName(shot.source).isFile) {
                    OutlinedButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = tr("Mettre à la corbeille", "Move to trash"))
                    }
                }
            }

            if (detected.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    detected.forEach { item ->
                        AssistChip(
                            onClick = { openDetected(context, item) },
                            label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = {
                                Icon(item.icon, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize))
                            },
                        )
                    }
                }
            }

            Box(
                Modifier
                    .weight(1f, fill = false)
                    .padding(top = 12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (shot.text.isBlank()) {
                    Text(tr("Aucun texte reconnu sur cette image.", "No text found in this image."), style = MaterialTheme.typography.bodyMedium)
                } else {
                    SelectionContainer { Text(annotated, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

/** Puce de catégorie : un menu permet d'en choisir une autre ou de revenir à l'automatique. */
@Composable
private fun CategoryPicker(shot: Screenshot, onChange: (Category?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val current = Category.fromName(shot.category)
    Box(modifier) {
        FilterChip(
            selected = shot.categoryLocked,
            onClick = { open = true },
            label = { Text(if (shot.categoryLocked) "${current.label} " + tr("(choisie)", "(chosen)") else current.label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = tr("Changer de catégorie", "Change category")) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (shot.categoryLocked) {
                DropdownMenuItem(
                    text = { Text(tr("Automatique", "Automatic")) },
                    onClick = { onChange(null); open = false },
                )
                HorizontalDivider()
            }
            Category.entries.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c.label) },
                    leadingIcon = if (c == current) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else null,
                    onClick = { onChange(c); open = false },
                )
            }
        }
    }
}

private val Detected.icon: ImageVector
    get() = when (this) {
        is Detected.Link -> Icons.AutoMirrored.Filled.ArrowForward
        is Detected.Email -> Icons.Filled.Email
        is Detected.Phone -> Icons.Filled.Call
        is Detected.Code -> Icons.Filled.Lock
    }
