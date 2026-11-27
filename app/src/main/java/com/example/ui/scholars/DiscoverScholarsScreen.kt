package com.example.ui.scholars

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.supabase.SupabaseClient
import com.example.ui.theme.AcademicNavy
import com.example.ui.theme.MedicalTeal
import kotlinx.coroutines.launch
import org.json.JSONArray

private data class Scholar(val id: String, val name: String, val department: String, val level: String, val bio: String, val interests: String)

@Composable
fun DiscoverScholarsScreen(client: SupabaseClient) {
    var scholars by remember { mutableStateOf(emptyList<Scholar>()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    suspend fun load() {
        loading = true; error = null
        try {
            val collected = JSONArray(); var offset = 0
            while (true) {
                val page = JSONArray(client.restGet("community_profiles_public?select=id,display_name,department,academic_level,bio,academic_interests&order=created_at.desc&limit=100&offset=$offset").getOrElse { throw it })
                for (i in 0 until page.length()) collected.put(page.optJSONObject(i))
                if (page.length() < 100) break
                offset += page.length()
            }
            scholars = (0 until collected.length()).mapNotNull { i ->
                val row = collected.optJSONObject(i) ?: return@mapNotNull null
                Scholar(row.optString("id"), row.optString("display_name").ifBlank { "Student" }, row.optString("department").ifBlank { "Department not specified" }, row.optString("academic_level").ifBlank { "Level not specified" }, row.optString("bio"), row.optString("academic_interests"))
            }
        } catch (_: Exception) { error = "Directory unavailable. Check your connection and retry." }
        finally { loading = false }
    }
    LaunchedEffect(Unit) { load() }
    val visible = scholars.filter { query.isBlank() || it.name.contains(query, true) || it.department.contains(query, true) || it.level.contains(query, true) || it.interests.contains(query, true) }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandIcon { Icon(Icons.Default.Groups, null, tint = androidx.compose.ui.graphics.Color.White) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("STUDENT COMMUNITY", color = MedicalTeal, fontSize = 10.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold)
                    Text("Discover Scholars", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { scope.launch { load() } }, enabled = !loading) { Icon(Icons.Default.Refresh, "Refresh", tint = MedicalTeal) }
            }
            Spacer(Modifier.height(5.dp)); Text("Meet students across departments and academic levels.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search names, departments or interests…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(16.dp)) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Student directory", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f))
                Surface(color = MedicalTeal.copy(alpha = .1f), shape = RoundedCornerShape(50.dp)) { Text("${visible.size} scholars", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = MedicalTeal, style = MaterialTheme.typography.labelMedium) }
            }
        }
        if (loading) item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MedicalTeal) } }
        else if (error != null) item { Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(error!!, color = MaterialTheme.colorScheme.error); Button(onClick = { scope.launch { load() } }) { Text("Retry") } } }
        else if (visible.isEmpty()) item { EmptyCard(if (scholars.isEmpty()) "No student profiles yet" else "No results found") }
        else items(visible, key = { it.id }) { s ->
            Card(shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Surface(shape = CircleShape, color = MedicalTeal.copy(alpha = .12f), modifier = Modifier.size(48.dp)) { Box(contentAlignment = Alignment.Center) { Text(s.name.split(" ").take(2).mapNotNull { it.firstOrNull() }.joinToString("").uppercase().take(2), color = MedicalTeal, fontWeight = FontWeight.Bold) } }
                    Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(s.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text(s.department, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(color = AcademicNavy.copy(alpha = .08f), shape = RoundedCornerShape(9.dp)) { Text(s.level, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = AcademicNavy, fontWeight = FontWeight.SemiBold) }
                        if (s.bio.isNotBlank()) Text(s.bio, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                        if (s.interests.isNotBlank()) Text("Interests · ${s.interests}", style = MaterialTheme.typography.labelMedium, color = MedicalTeal, maxLines = 2)
                    }
                }
            }
        }
    }
}
@Composable
private fun BrandIcon(content: @Composable () -> Unit) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(AcademicNavy, MedicalTeal))), contentAlignment = Alignment.Center) { content() }
}
@Composable
private fun EmptyCard(title: String) {
    Card(shape = RoundedCornerShape(18.dp)) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Groups, null, tint = MedicalTeal, modifier = Modifier.size(36.dp)); Text(title, fontWeight = FontWeight.SemiBold) } }
}
