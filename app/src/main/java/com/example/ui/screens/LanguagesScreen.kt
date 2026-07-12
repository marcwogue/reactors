package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.LanguageEntity
import com.example.ui.viewmodel.LanguageViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagesScreen(
    viewModel: LanguageViewModel,
    modifier: Modifier = Modifier
) {
    val languages by viewModel.allLanguages.collectAsState()
    val activeLang by viewModel.activeLanguage.collectAsState()

    var showAddForm by remember { mutableStateOf(false) }

    // Add Form Fields
    var langId by remember { mutableStateOf("") }
    var langName by remember { mutableStateOf("") }
    var flagEmoji by remember { mutableStateOf("") }
    var interlocutorName by remember { mutableStateOf("") }
    var interlocutorPersonality by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mes Langues d'Étude", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Banner
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Un interlocuteur par langue ! 🗣️🌍",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Basculez librement d'une langue à l'autre. Chaque nouvelle langue démarre une discussion indépendante avec un nouvel ami IA adapté à votre niveau.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Quick add trigger
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Langues en cours",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = { showAddForm = !showAddForm },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (showAddForm) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (showAddForm) "Fermer" else "Ajouter une langue", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Expanded Add Language Form Pane
            item {
                AnimatedVisibility(visible = showAddForm) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "✨ Nouveau programme d'apprentissage",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = langId,
                                    onValueChange = { langId = it },
                                    label = { Text("Code (ex: fr, es)") },
                                    placeholder = { Text("fr") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = flagEmoji,
                                    onValueChange = { flagEmoji = it },
                                    label = { Text("Drapeau Emoji") },
                                    placeholder = { Text("🇫🇷") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = langName,
                                onValueChange = { langName = it },
                                label = { Text("Nom de la Langue (ex: Français)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = interlocutorName,
                                onValueChange = { interlocutorName = it },
                                label = { Text("Nom de l'interlocuteur IA (ex: Sarah)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = interlocutorPersonality,
                                onValueChange = { interlocutorPersonality = it },
                                label = { Text("Personnalité & Centres d'intérêt de l'IA") },
                                placeholder = { Text("Une amie très bavarde de Paris, passionnée de mode, d'art et de cuisine...") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    if (langId.isNotBlank() && langName.isNotBlank() && flagEmoji.isNotBlank()) {
                                        viewModel.addNewLanguage(
                                            id = langId,
                                            name = langName,
                                            emoji = flagEmoji,
                                            personality = interlocutorPersonality,
                                            contactName = interlocutorName
                                        )
                                        // Reset fields
                                        langId = ""
                                        langName = ""
                                        flagEmoji = ""
                                        interlocutorName = ""
                                        interlocutorPersonality = ""
                                        showAddForm = false
                                    }
                                },
                                enabled = langId.isNotBlank() && langName.isNotBlank() && flagEmoji.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Créer & Commencer la discussion")
                            }
                        }
                    }
                }
            }

            // Grid or List of Languages
            items(languages) { language ->
                val isActive = activeLang?.id == language.id

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectLanguage(language.id) }
                        .border(
                            width = if (isActive) 2.dp else 0.dp,
                            color = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Flag Sphere
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = language.flagEmoji, fontSize = 32.sp)
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Details
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = language.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                // CEFR badge
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = language.currentLevel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Ami(e) IA : ${language.interlocutorName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = language.interlocutorPersonality,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Right pointing / Active status icon
                        if (isActive) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = "Langue Active",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
