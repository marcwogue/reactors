package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.ApiKeyEntity
import com.example.ui.viewmodel.LanguageViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: LanguageViewModel,
    modifier: Modifier = Modifier
) {
    val activeLang by viewModel.activeLanguage.collectAsState()
    val apiKeys by viewModel.apiKeys.collectAsState()
    val settings by viewModel.settings.collectAsState()

    // Key inputs
    var newKey by remember { mutableStateOf("") }
    var keyLabel by remember { mutableStateOf("") }

    // User profile settings
    var tempUserName by remember { mutableStateOf("") }

    // Active Lang settings fields
    var langName by remember { mutableStateOf("") }
    var langEmoji by remember { mutableStateOf("") }
    var interlocutorName by remember { mutableStateOf("") }
    var langLevel by remember { mutableStateOf("A1") }
    var wakeTime1 by remember { mutableStateOf("09:00") }
    var wakeTime2 by remember { mutableStateOf("14:00") }
    var wakeTime3 by remember { mutableStateOf("18:00") }
    var wakeTime4 by remember { mutableStateOf("21:00") }
    var personality by remember { mutableStateOf("") }
    var exerciseFreq by remember { mutableStateOf("Chaque jour") }

    // Update user profile field when settings change
    LaunchedEffect(settings.userName) {
        tempUserName = settings.userName
    }

    // Update form when active language switches
    LaunchedEffect(activeLang) {
        if (activeLang != null) {
            val lang = activeLang!!
            val times = lang.wakeUpTimes.split(",").map { it.trim() }
            wakeTime1 = times.getOrNull(0) ?: "09:00"
            wakeTime2 = times.getOrNull(1) ?: "14:00"
            wakeTime3 = times.getOrNull(2) ?: "18:00"
            wakeTime4 = times.getOrNull(3) ?: "21:00"
            personality = lang.interlocutorPersonality
            exerciseFreq = lang.exerciseFrequency
            langName = lang.name
            langEmoji = lang.flagEmoji
            interlocutorName = lang.interlocutorName
            langLevel = lang.currentLevel
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres généraux", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // API Keys Banner Status
            item {
                if (apiKeys.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Clé API Hugging Face requise !",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Pour que l'IA fonctionne, vous devez ajouter au moins une clé d'API Hugging Face. L'obtention d'une clé est entièrement gratuite sur huggingface.co.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                            )
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Service IA connecté avec ${apiKeys.size} clé(s) active(s) ! 🚀",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // --- USER PROFILE SETTINGS ---
            item {
                Text("Mon profil", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Informations personnelles", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = tempUserName,
                            onValueChange = { tempUserName = it },
                            label = { Text("Votre prénom / nom d'utilisateur") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                viewModel.updateUserName(tempUserName)
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Enregistrer mon nom")
                            }
                        }
                    }
                }
            }

            // --- THEME SELECTION ---
            item {
                Text("Thème de l'application", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Personnalisation visuelle", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        val currentTheme = settings.themeMode
                        val themeOptions = listOf(
                            "light" to ("Clair" to Icons.Default.WbSunny),
                            "dark" to ("Sombre" to Icons.Default.Brightness4),
                            "system" to ("Système" to Icons.Default.Settings)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            themeOptions.forEach { (mode, pair) ->
                                val (label, icon) = pair
                                val isSelected = currentTheme == mode
                                OutlinedButton(
                                    onClick = { viewModel.updateThemeMode(mode) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    ),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    ),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- LLM MODEL SELECTION ---
            item {
                Text("Modèle d'IA utilisé (Hugging Face)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                val models = listOf(
                    "Qwen/Qwen2.5-7B-Instruct" to "Qwen 2.5 7B (Excellent multilingue)",
                    "meta-llama/Llama-3.2-3B-Instruct" to "Llama 3.2 3B (Rapide & fluide)",
                    "mistralai/Mistral-7B-Instruct-v0.3" to "Mistral 7B v0.3 (Robuste)"
                )

                models.forEach { (modelId, label) ->
                    val isSelected = settings.activeModel == modelId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else Color.Transparent
                            )
                            .clickable { viewModel.updateActiveModel(modelId) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { viewModel.updateActiveModel(modelId) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(modelId, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // --- MANAGE API KEYS ---
            item {
                Text("Gestion des clés API Hugging Face", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = newKey,
                            onValueChange = { newKey = it },
                            label = { Text("Coller votre clé d'API (hf_...)") },
                            placeholder = { Text("hf_abcde12345...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = keyLabel,
                            onValueChange = { keyLabel = it },
                            label = { Text("Nom personnalisé de la clé (ex: Clé perso)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (newKey.isNotBlank()) {
                                    viewModel.addApiKey(newKey, keyLabel)
                                    newKey = ""
                                    keyLabel = ""
                                }
                            },
                            enabled = newKey.isNotBlank(),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Ajouter la clé")
                        }
                    }
                }
            }

            // API Keys List
            if (apiKeys.isNotEmpty()) {
                item {
                    Text("Clés enregistrées", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                items(apiKeys) { key ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(key.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "hf_•••••" + key.apiKey.takeLast(6),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.deleteApiKey(key.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // --- CURRENT LANGUAGE SPECIFIC CONFIG ---
            if (activeLang != null) {
                val lang = activeLang!!

                item {
                    Text(
                        text = "Paramètres d'étude : ${lang.name} ${lang.flagEmoji}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Language name and Emoji
                            Text(
                                "Informations sur la langue",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = langName,
                                    onValueChange = { langName = it },
                                    label = { Text("Nom de la langue") },
                                    modifier = Modifier.weight(2f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = langEmoji,
                                    onValueChange = { langEmoji = it },
                                    label = { Text("Drapeau Émoji") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Custom Row Selection for Language Level (READ ONLY)
                            Text(
                                "Niveau d'étude actuel (Lecture seule)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Le niveau de l'apprenant est calculé uniquement par l'IA chaque dimanche à 00h00, basé sur vos conversations et exercices de la semaine.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val levels = listOf("A1", "A2", "B1", "B2", "C1", "C2")
                                levels.forEach { lvl ->
                                    val isSelected = langLevel == lvl
                                    OutlinedButton(
                                        onClick = { /* Lecture seule */ },
                                        enabled = isSelected,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                            disabledContainerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            disabledContentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        ),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                                        ),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(lvl, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = { viewModel.forceWeeklyLevelEvaluation(lang.id) },
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Psychology,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text("Simuler l'évaluation IA (Dimanche 00h)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Interlocutor Name
                            OutlinedTextField(
                                value = interlocutorName,
                                onValueChange = { interlocutorName = it },
                                label = { Text("Nom de votre interlocuteur IA") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Personality behavior description override
                            OutlinedTextField(
                                value = personality,
                                onValueChange = { personality = it },
                                label = { Text("Comportement, Intérêts & Personnalité de l'IA") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // 4 Wake up times management
                            Text(
                                "Heures de réveil de l'interlocuteur IA (max 4)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "L'interlocuteur vous enverra des rappels de pratique à ces heures.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = wakeTime1,
                                    onValueChange = { wakeTime1 = it },
                                    label = { Text("H1") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = wakeTime2,
                                    onValueChange = { wakeTime2 = it },
                                    label = { Text("H2") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = wakeTime3,
                                    onValueChange = { wakeTime3 = it },
                                    label = { Text("H3") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = wakeTime4,
                                    onValueChange = { wakeTime4 = it },
                                    label = { Text("H4") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Exercise Frequency
                            Text(
                                "Fréquence de réception des fiches d'exercices",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val frequencies = listOf("Chaque jour", "Tous les 2 jours", "Chaque message")
                            frequencies.forEach { freq ->
                                val isSelected = exerciseFreq == freq
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { exerciseFreq = freq }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = isSelected, onClick = { exerciseFreq = freq })
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(freq, style = MaterialTheme.typography.bodyMedium)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    val formattedTimes = listOf(wakeTime1, wakeTime2, wakeTime3, wakeTime4)
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                        .joinToString(",")

                                    viewModel.updateLanguageConfig(
                                        name = langName,
                                        flagEmoji = langEmoji,
                                        level = langLevel,
                                        interlocutorName = interlocutorName,
                                        personality = personality,
                                        times = formattedTimes,
                                        freq = exerciseFreq
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Save, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Enregistrer les configurations de langue")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
