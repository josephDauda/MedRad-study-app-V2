package com.example.ui.feeds
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.supabase.SupabaseClient
import com.example.ui.theme.AcademicNavy
import com.example.ui.theme.MedicalTeal
import com.example.ui.theme.WarningAmber
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
private data class FeedCategory(val slug:String,val name:String)
private data class FeedMedia(val url:String,val thumbnail:String?,val kind:String,val filename:String)
private data class FeedPost(val id:String,val author:String,val department:String,val level:String,val title:String,val content:String,val category:String,val postType:String,val createdAt:String,val reactions:Int,val comments:Int,val media:List<FeedMedia>,val resource:String?)
@Composable fun MedicalFeedsScreen(client:SupabaseClient){
 var posts by remember{mutableStateOf<List<FeedPost>>(emptyList())};var categories by remember{mutableStateOf(listOf(FeedCategory("general","General")))}
 var selected by remember{mutableStateOf("all")};var search by remember{mutableStateOf("")};var loading by remember{mutableStateOf(true)};var error by remember{mutableStateOf<String?>(null)}
 val scope=rememberCoroutineScope();val uri=LocalUriHandler.current
 suspend fun refresh(){loading=true;error=null;try{
  runCatching{val ca=JSONArray(client.restGet("community_categories?select=slug,name&is_active=eq.true&order=sort_order.asc").getOrElse{throw it});val c=(0 until ca.length()).mapNotNull{i->ca.optJSONObject(i)?.let{FeedCategory(it.optString("slug"),it.optString("name"))}};if(c.isNotEmpty())categories=c}
  val arr=JSONArray(client.restGet("feed_posts_public?select=id,author_name,author_department,author_level,title,content,category_slug,post_type,created_at,reaction_count,comment_count,resource_url&order=created_at.desc&limit=30").getOrElse{throw it})
  val ids=(0 until arr.length()).mapNotNull{arr.optJSONObject(it)?.optString("id")?.takeIf(String::isNotBlank)};val media=mutableMapOf<String,MutableList<FeedMedia>>()
  if(ids.isNotEmpty()){val ma=JSONArray(client.restGet("feed_post_media_public?select=post_id,media_type,secure_url,thumbnail_url,original_filename,sort_order&post_id=in.(${ids.joinToString(",")})&order=sort_order.asc").getOrElse{throw it};for(i in 0 until ma.length()){val m=ma.optJSONObject(i)?:continue;val url=m.optString("secure_url");if(url.startsWith("https://"))media.getOrPut(m.optString("post_id")){mutableListOf()}.add(FeedMedia(url,m.optString("thumbnail_url").takeIf{it.startsWith("https://")},m.optString("media_type"),m.optString("original_filename")))}}
  posts=(0 until arr.length()).mapNotNull{i->val p=arr.optJSONObject(i)?:return@mapNotNull null;FeedPost(p.optString("id"),p.optString("author_name").ifBlank{"Scholar"},p.optString("author_department"),p.optString("author_level"),p.optString("title"),p.optString("content"),p.optString("category_slug").ifBlank{"general"},p.optString("post_type"),p.optString("created_at"),p.optInt("reaction_count"),p.optInt("comment_count"),media[p.optString("id")].orEmpty(),p.optString("resource_url").takeIf{it.startsWith("https://")})}
 }catch(e:Exception){error="Feed unavailable. Please check your connection and retry."}finally{loading=false}}
 LaunchedEffect(Unit){refresh()}
 val shown=posts.filter{(selected=="all"||it.category==selected)&&(search.isBlank()||it.title.contains(search,true)||it.content.contains(search,true)||it.author.contains(search,true))}
 Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)){
 LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
 item{Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(AcademicNavy,MedicalTeal))),contentAlignment=Alignment.Center){Icon(Icons.Default.Newspaper,null,tint=androidx.compose.ui.graphics.Color.White)};Column(Modifier.weight(1f).padding(start=10.dp)){Text("MEDICAL COMMUNITY",fontSize=10.sp,letterSpacing=1.2.sp,fontWeight=FontWeight.Bold,color=MedicalTeal);Text("Medical Feeds",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)};IconButton(onClick={scope.launch{refresh()}},enabled=!loading){Icon(Icons.Default.Refresh,null,tint=MedicalTeal)}}
 Spacer(Modifier.height(5.dp));Text("Research, resources and updates from your community.",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)}
 item{OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Search posts, topics or scholars…")},leadingIcon={Icon(Icons.Default.Search,null)},shape=RoundedCornerShape(16.dp))}
 item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=="all",{selected="all"},label={Text("All posts")},shape=RoundedCornerShape(12.dp));categories.forEach{FilterChip(selected==it.slug,{selected=it.slug},label={Text(it.name)},shape=RoundedCornerShape(12.dp))}}}
 item{Row(verticalAlignment=Alignment.CenterVertically){Text("Community feed",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));Surface(color=MedicalTeal.copy(alpha=.1f),shape=RoundedCornerShape(50)){Text("${shown.size} posts",Modifier.padding(horizontal=10.dp,vertical=5.dp),color=MedicalTeal,style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.SemiBold)}}}
 when{loading->item{Box(Modifier.fillMaxWidth().padding(40.dp),contentAlignment=Alignment.Center){CircularProgressIndicator(color=MedicalTeal)}}
 error!=null->item{Column(Modifier.fillMaxWidth().padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(error!!,color=MaterialTheme.colorScheme.error);Button(onClick={scope.launch{refresh()}}){Text("Retry")}}}
 shown.isEmpty()->item{Card(shape=RoundedCornerShape(18.dp)){Column(Modifier.fillMaxWidth().padding(26.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.Forum,null,tint=MedicalTeal,modifier=Modifier.size(34.dp));Text(if(posts.isEmpty())"No community posts yet" else "No posts match",fontWeight=FontWeight.SemiBold);Text("Check back for updates.",color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
 else->items(shown,key={it.id}){p->Card(shape=RoundedCornerShape(20.dp),border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f)),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),elevation=CardDefaults.cardElevation(1.dp)){
 Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
 Row(verticalAlignment=Alignment.CenterVertically){Surface(shape=CircleShape,color=MedicalTeal.copy(alpha=.12f),modifier=Modifier.size(42.dp)){Box(contentAlignment=Alignment.Center){Text(p.author.split(" ").take(2).mapNotNull{it.firstOrNull()}.joinToString("").uppercase().take(2),fontWeight=FontWeight.Bold,color=MedicalTeal)}}
 Column(Modifier.padding(start=10.dp).weight(1f)){Text(p.author,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleSmall);Text(listOf(p.department,p.level).filter(String::isNotBlank).joinToString(" • ").ifBlank{"MedRad community"},style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
 Surface(color=WarningAmber.copy(alpha=.12f),shape=RoundedCornerShape(9.dp)){Text(p.category.replace('_',' '),Modifier.padding(horizontal=8.dp,vertical=5.dp),style=MaterialTheme.typography.labelSmall,color=WarningAmber,fontWeight=FontWeight.SemiBold)}}
 if(p.title.isNotBlank())Text(p.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
 if(p.content.isNotBlank())Text(p.content,style=MaterialTheme.typography.bodyMedium,lineHeight=21.sp)
 p.media.firstOrNull()?.let{AsyncImage(model=it.thumbnail?:it.url,contentDescription="Feed media",modifier=Modifier.fillMaxWidth().heightIn(max=320.dp).clip(RoundedCornerShape(14.dp)))}
 p.resource?.let{OutlinedButton(onClick={uri.openUri(it)},shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Link,null);Spacer(Modifier.width(6.dp));Text("Open attached resource")}}
 HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f))
 Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Favorite,null,tint=MedicalTeal,modifier=Modifier.size(17.dp));Text(" ${p.reactions}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.width(14.dp));Icon(Icons.Default.Forum,null,tint=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.size(17.dp));Text(" ${p.comments} comments",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.weight(1f));Text(date(p.createdAt),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
 }}}}
 }
}
private fun date(raw:String)=try{SimpleDateFormat("MMM d • h:mm a",Locale.getDefault()).format(Date.from(java.time.Instant.parse(raw)))}catch(_:Exception){raw.take(16).replace("T"," ")}
