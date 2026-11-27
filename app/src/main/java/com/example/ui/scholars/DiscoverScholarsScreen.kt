package com.example.ui.scholars
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.launch
import org.json.JSONArray

private data class Scholar(val id:String,val name:String,val department:String,val level:String,val bio:String,val interests:String)

@Composable
fun DiscoverScholarsScreen(client: SupabaseClient) {
  var scholars by remember { mutableStateOf<List<Scholar>>(emptyList()) }
  var query by remember { mutableStateOf("") }
  var loading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()
  suspend fun loadDirectory() {
    loading = true; error = null
    client.restGet("community_profiles_public?select=id,display_name,department,academic_level,bio,academic_interests&order=created_at.desc&limit=100")
      .onSuccess { raw ->
        val array = JSONArray(raw)
        scholars = (0 until array.length()).mapNotNull { i ->
          val row = array.optJSONObject(i) ?: return@mapNotNull null
          Scholar(row.optString("id"), row.optString("display_name").ifBlank { "MedRad student" },
            row.optString("department").ifBlank { "Department not specified" },
            row.optString("academic_level").ifBlank { "Level not specified" },
            row.optString("bio"), row.optString("academic_interests"))
        }
      }.onFailure { error = "The community directory isn't available yet. Check your connection or try again later." }
    loading = false
  }
  LaunchedEffect(Unit) { loadDirectory() }
  val filtered = scholars.filter { s ->
    val q=query.trim()
    q.isBlank() || s.name.contains(q,true) || s.department.contains(q,true) || s.level.contains(q,true) || s.interests.contains(q,true)
  }
  Column(Modifier.fillMaxSize().padding(16.dp)) {
    Text("Discover Scholars",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
    Text("Find fellow students by name, department, level, or interests.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Search students…")},leadingIcon={Icon(Icons.Default.Search,null)})
    Spacer(Modifier.height(12.dp))
    when {
      loading -> Column(Modifier.fillMaxWidth().padding(36.dp),horizontalAlignment=Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.height(8.dp)); Text("Loading student directory…") }
      error != null -> Column(Modifier.fillMaxWidth().padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally) { Text(error!!,color=MaterialTheme.colorScheme.error); Button(onClick={scope.launch{loadDirectory()}}){Text("Retry")} }
      filtered.isEmpty() -> Text(if(scholars.isEmpty()) "No student profiles are available yet." else "No students match your search.",Modifier.padding(20.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
      else -> {
        Text("${filtered.size} student${if(filtered.size==1) "" else "s"}",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.weight(1f)) {
          items(filtered,key={it.id}) { s ->
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow),modifier=Modifier.fillMaxWidth()) {
              Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.size(44.dp)) {
                  Box(contentAlignment=Alignment.Center){Text(s.name.split(" ").take(2).mapNotNull{it.firstOrNull()}.joinToString("").uppercase(),fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onPrimaryContainer)}
                }
                Column(Modifier.padding(start=12.dp).weight(1f)) {
                  Text(s.name,fontWeight=FontWeight.SemiBold)
                  Text("${s.department} • ${s.level}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                  if(s.bio.isNotBlank()) Text(s.bio,style=MaterialTheme.typography.bodySmall,maxLines=2,modifier=Modifier.padding(top=4.dp))
                  if(s.interests.isNotBlank()) Text("Interests: ${s.interests}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary,maxLines=2,modifier=Modifier.padding(top=3.dp))
                }
              }
            }
          }
        }
      }
    }
  }
}