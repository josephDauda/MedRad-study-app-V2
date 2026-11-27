package com.example.ui.tutors

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.launch
import org.json.JSONArray

private data class Tutor(val id:String,val name:String,val level:String,val degree:String,val specialties:String,val bio:String,val avatar:String?,val hours:Double,val availability:String)

@Composable
fun TutorDirectoryScreen(client:SupabaseClient) {
  var tutors by remember { mutableStateOf<List<Tutor>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }
  val scope=rememberCoroutineScope()
  suspend fun refresh() {
    loading=true; error=null
    try {
      val raw=client.restGet("tutor_directory_public?select=id,display_name,academic_level,class_of_degree,specializations,bio,avatar_url,daily_available_hours,availability_status&order=display_name.asc&limit=100").getOrElse{throw it}
      val arr=JSONArray(raw)
      tutors=(0 until arr.length()).mapNotNull { i ->
        val r=arr.optJSONObject(i)?:return@mapNotNull null
        val avatar=r.optString("avatar_url").takeIf{it.startsWith("https://")}
        Tutor(r.optString("id"),r.optString("display_name").ifBlank{"Tutor"},r.optString("academic_level"),r.optString("class_of_degree"),r.optString("specializations"),r.optString("bio"),avatar,r.optDouble("daily_available_hours",2.0),r.optString("availability_status").ifBlank{"AVAILABLE"})
      }
    } catch(_:Exception) { error="Tutor directory is unavailable right now. Please try again." }
    finally { loading=false }
  }
  LaunchedEffect(Unit){refresh()}
  Column(Modifier.fillMaxSize().padding(16.dp)) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("Tutors",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Text("Available tutors from the shared MedRad backend",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
      }
      IconButton(onClick={scope.launch{refresh()}},enabled=!loading){Icon(Icons.Default.Refresh,contentDescription="Refresh tutors")}
    }
    when {
      loading -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
      error!=null -> Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Text(error!!,color=MaterialTheme.colorScheme.error);Spacer(Modifier.height(8.dp));Button(onClick={scope.launch{refresh()}}){Text("Retry")}}
      tutors.isEmpty() -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("No tutors are currently listed.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
      else -> LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=12.dp)) {
        items(tutors,key={it.id}) { tutor ->
          Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
            Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.Top) {
              Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.size(56.dp)) {
                if(tutor.avatar!=null) AsyncImage(model=tutor.avatar,contentDescription="Tutor profile photo")
                else Box(contentAlignment=Alignment.Center){Text(tutor.name.split(" ").take(2).mapNotNull{it.firstOrNull()}.joinToString("").uppercase(),fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onPrimaryContainer)}
              }
              Column(Modifier.padding(start=12.dp).weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(tutor.name,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.titleMedium)
                Text(listOf(tutor.level,tutor.degree).filter(String::isNotBlank).joinToString(" • "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                AssistChip(onClick={},label={Text(tutor.availability.replace('_',' ').lowercase().replaceFirstChar{it.uppercase()})})
                if(tutor.specialties.isNotBlank()) Text("Specialties: "+tutor.specialties,style=MaterialTheme.typography.bodyMedium)
                if(tutor.bio.isNotBlank()) Text(tutor.bio,style=MaterialTheme.typography.bodySmall)
                Text("Daily availability: ${tutor.hours} hrs",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
              }
            }
          }
        }
      }
    }
  }
}
