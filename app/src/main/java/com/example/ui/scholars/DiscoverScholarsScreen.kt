package com.example.ui.scholars
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
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
private data class Scholar(val id:String,val name:String,val department:String,val level:String,val bio:String,val interests:String)
@Composable fun DiscoverScholarsScreen(client:SupabaseClient){
 var people by remember{mutableStateOf<List<Scholar>>(emptyList())};var q by remember{mutableStateOf("")};var loading by remember{mutableStateOf(true)};var error by remember{mutableStateOf<String?>(null)};val scope=rememberCoroutineScope()
 suspend fun load(){loading=true;error=null;try{val all=JSONArray();var offset=0;while(true){val page=JSONArray(client.restGet("community_profiles_public?select=id,display_name,department,academic_level,bio,academic_interests&order=created_at.desc&limit=100&offset=$offset").getOrElse{throw it});for(i in 0 until page.length())all.put(page.optJSONObject(i));if(page.length()<100)break;offset+=page.length()};people=(0 until all.length()).mapNotNull{i->val r=all.optJSONObject(i)?:return@mapNotNull null;Scholar(r.optString("id"),r.optString("display_name").ifBlank{"Student"},r.optString("department").ifBlank{"Department not specified"},r.optString("academic_level").ifBlank{"Level not specified"},r.optString("bio"),r.optString("academic_interests"))}}catch(_:Exception){error="Directory is unavailable. Check your connection and retry."}finally{loading=false}}
 LaunchedEffect(Unit){load()};val filtered=people.filter{q.isBlank()||it.name.contains(q,true)||it.department.contains(q,true)||it.level.contains(q,true)||it.interests.contains(q,true)}
 Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
 item{Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(AcademicNavy,MedicalTeal))),contentAlignment=Alignment.Center){Icon(Icons.Default.Groups,null,tint=androidx.compose.ui.graphics.Color.White)};Column(Modifier.weight(1f).padding(start=10.dp)){Text("STUDENT COMMUNITY",fontSize=10.sp,letterSpacing=1.1.sp,fontWeight=FontWeight.Bold,color=MedicalTeal);Text("Discover Scholars",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)};IconButton(onClick={scope.launch{load()}},enabled=!loading){Icon(Icons.Default.Refresh,null,tint=MedicalTeal)}}
 Spacer(Modifier.height(6.dp));Text("Meet fellow students across departments and levels.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
 item{OutlinedTextField(q,{q=it},Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Search names, departments or interests…")},leadingIcon={Icon(Icons.Default.Search,null)},shape=RoundedCornerShape(16.dp))}
 item{Row(verticalAlignment=Alignment.CenterVertically){Text("Student directory",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));Surface(color=MedicalTeal.copy(alpha=.1f),shape=RoundedCornerShape(50.dp)){Text("${filtered.size} scholars",Modifier.padding(horizontal=10.dp,vertical=5.dp),style=MaterialTheme.typography.labelMedium,color=MedicalTeal,fontWeight=FontWeight.SemiBold)}}}
 when{loading->item{Box(Modifier.fillMaxWidth().padding(40.dp),contentAlignment=Alignment.Center){CircularProgressIndicator(color=MedicalTeal)}}
 error!=null->item{Column(Modifier.fillMaxWidth().padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(error!!,color=MaterialTheme.colorScheme.error);Button(onClick={scope.launch{load()}}){Text("Retry")}}}
 filtered.isEmpty()->item{Card(shape=RoundedCornerShape(18.dp)){Column(Modifier.fillMaxWidth().padding(26.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.Groups,null,tint=MedicalTeal,modifier=Modifier.size(36.dp));Text(if(people.isEmpty())"No student profiles yet" else "No results found",fontWeight=FontWeight.SemiBold)}}}
 else->items(filtered,key={it.id}){s->Card(shape=RoundedCornerShape(18.dp),border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f)),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),elevation=CardDefaults.cardElevation(1.dp)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.Top){Surface(shape=CircleShape,color=MedicalTeal.copy(alpha=.12f),modifier=Modifier.size(48.dp)){Box(contentAlignment=Alignment.Center){Text(s.name.split(" ").take(2).mapNotNull{it.firstOrNull()}.joinToString("").uppercase().take(2),fontWeight=FontWeight.Bold,color=MedicalTeal)}}
 Column(Modifier.padding(start=12.dp).weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(s.name,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleSmall);Text(s.department,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Surface(color=AcademicNavy.copy(alpha=.08f),shape=RoundedCornerShape(9.dp)){Text(s.level,Modifier.padding(horizontal=8.dp,vertical=4.dp),style=MaterialTheme.typography.labelSmall,color=AcademicNavy,fontWeight=FontWeight.SemiBold)};if(s.bio.isNotBlank())Text(s.bio,style=MaterialTheme.typography.bodySmall,maxLines=3);if(s.interests.isNotBlank())Text("Interests · ${s.interests}",style=MaterialTheme.typography.labelMedium,color=MedicalTeal,maxLines=2)}}}}
 }}}
