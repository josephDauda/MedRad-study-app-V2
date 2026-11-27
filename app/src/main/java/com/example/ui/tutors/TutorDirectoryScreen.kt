package com.example.ui.tutors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
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
import coil.compose.AsyncImage
import com.example.data.supabase.SupabaseClient
import com.example.ui.theme.AcademicNavy
import com.example.ui.theme.MedicalTeal
import com.example.ui.theme.WarningAmber
import kotlinx.coroutines.launch
import org.json.JSONArray
private data class Tutor(val id:String,val name:String,val level:String,val degree:String,val specialty:String,val bio:String,val avatar:String?,val hours:Double,val status:String)
@Composable fun TutorDirectoryScreen(client:SupabaseClient){
 var tutors by remember{mutableStateOf<List<Tutor>>(emptyList())};var loading by remember{mutableStateOf(true)};var error by remember{mutableStateOf<String?>(null)};val scope=rememberCoroutineScope()
 suspend fun load(){loading=true;error=null;try{val arr=JSONArray(client.restGet("tutor_directory_public?select=id,display_name,academic_level,class_of_degree,specializations,bio,avatar_url,daily_available_hours,availability_status&order=display_name.asc&limit=100").getOrElse{throw it});tutors=(0 until arr.length()).mapNotNull{i->val r=arr.optJSONObject(i)?:return@mapNotNull null;Tutor(r.optString("id"),r.optString("display_name").ifBlank{"Tutor"},r.optString("academic_level"),r.optString("class_of_degree"),r.optString("specializations"),r.optString("bio"),r.optString("avatar_url").takeIf{it.startsWith("https://")},r.optDouble("daily_available_hours",2.0),r.optString("availability_status").ifBlank{"AVAILABLE"})}}catch(_:Exception){error="Tutor directory is unavailable. Please retry."}finally{loading=false}}
 LaunchedEffect(Unit){load()}
 Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
 item{Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(AcademicNavy,MedicalTeal))),contentAlignment=Alignment.Center){Icon(Icons.Default.School,null,tint=androidx.compose.ui.graphics.Color.White)};Column(Modifier.weight(1f).padding(start=10.dp)){Text("PEER LEARNING",fontSize=10.sp,letterSpacing=1.1.sp,fontWeight=FontWeight.Bold,color=MedicalTeal);Text("Tutor Directory",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)};IconButton(onClick={scope.launch{load()}},enabled=!loading){Icon(Icons.Default.Refresh,null,tint=MedicalTeal)}}
 Spacer(Modifier.height(6.dp));Text("Get academic guidance from experienced student tutors.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
 item{Card(shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=AcademicNavy)){Row(Modifier.fillMaxWidth().padding(15.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Peer learning",color=androidx.compose.ui.graphics.Color.White,fontWeight=FontWeight.Bold);Text("Study together. Grow together.",color=androidx.compose.ui.graphics.Color.White.copy(alpha=.8f),style=MaterialTheme.typography.bodySmall)};Icon(Icons.Default.School,null,tint=WarningAmber,modifier=Modifier.size(28.dp))}}}
 item{Row(verticalAlignment=Alignment.CenterVertically){Text("Tutors",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));Surface(color=MedicalTeal.copy(alpha=.1f),shape=RoundedCornerShape(50.dp)){Text("${tutors.size} listed",Modifier.padding(horizontal=10.dp,vertical=5.dp),style=MaterialTheme.typography.labelMedium,color=MedicalTeal,fontWeight=FontWeight.SemiBold)}}}
 when{loading->item{Box(Modifier.fillMaxWidth().padding(40.dp),contentAlignment=Alignment.Center){CircularProgressIndicator(color=MedicalTeal)}}
 error!=null->item{Column(Modifier.fillMaxWidth().padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(error!!,color=MaterialTheme.colorScheme.error);Button(onClick={scope.launch{load()}}){Text("Retry")}}}
 tutors.isEmpty()->item{Card(shape=RoundedCornerShape(18.dp)){Column(Modifier.fillMaxWidth().padding(26.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.School,null,tint=MedicalTeal,modifier=Modifier.size(36.dp));Text("No tutors listed yet",fontWeight=FontWeight.SemiBold)}}}
 else->items(tutors,key={it.id}){t->Card(shape=RoundedCornerShape(20.dp),border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f)),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),elevation=CardDefaults.cardElevation(1.dp)){Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Row(verticalAlignment=Alignment.Top){Surface(shape=CircleShape,color=MedicalTeal.copy(alpha=.12f),modifier=Modifier.size(58.dp)){if(t.avatar!=null)AsyncImage(model=t.avatar,contentDescription="Tutor photo",modifier=Modifier.clip(CircleShape))else Box(contentAlignment=Alignment.Center){Text(t.name.split(" ").take(2).mapNotNull{it.firstOrNull()}.joinToString("").uppercase().take(2),fontWeight=FontWeight.Bold,color=MedicalTeal)}}
 Column(Modifier.padding(start=12.dp).weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(t.name,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);Text(listOf(t.level,t.degree).filter(String::isNotBlank).joinToString(" • ").ifBlank{"Peer tutor"},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);val available=t.status.equals("AVAILABLE",true);Surface(color=(if(available)MedicalTeal else WarningAmber).copy(alpha=.12f),shape=RoundedCornerShape(9.dp)){Text(t.status.replace('_',' ').lowercase().replaceFirstChar{it.uppercase()},Modifier.padding(horizontal=8.dp,vertical=4.dp),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.SemiBold,color=if(available)MedicalTeal else WarningAmber)}}
 };if(t.specialty.isNotBlank()){Text("SPECIALTIES",style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(t.specialty,style=MaterialTheme.typography.bodyMedium)};if(t.bio.isNotBlank())Text(t.bio,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=4);HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f));Row(verticalAlignment=Alignment.CenterVertically){Text("Daily availability",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.weight(1f));Text("${t.hours} hrs/day",style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold,color=MedicalTeal)}}}}
 }}}
