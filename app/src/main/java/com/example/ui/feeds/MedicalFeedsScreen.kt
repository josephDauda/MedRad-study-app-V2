package com.example.ui.feeds

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class FeedMedia(val url:String,val thumbnail:String?,val kind:String,val filename:String)
private data class FeedPost(val id:String,val author:String,val department:String,val level:String,val title:String,val content:String,val category:String,val postType:String,val createdAt:String,val reactions:Int,val comments:Int,val media:List<FeedMedia>,val resource:String?)

@Composable
fun MedicalFeedsScreen(client: SupabaseClient) {
  var posts by remember { mutableStateOf<List<FeedPost>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }
  val scope=rememberCoroutineScope()

  suspend fun refresh() {
    loading=true; error=null
    try {
      val raw=client.restGet("feed_posts_public?select=id,author_name,author_department,author_level,title,content,category_slug,post_type,created_at,reaction_count,comment_count,resource_url&order=created_at.desc&limit=30").getOrElse { throw it }
      val arr=JSONArray(raw)
      val ids=(0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("id")?.takeIf(String::isNotBlank) }
      val mediaByPost=mutableMapOf<String,MutableList<FeedMedia>>()
      if(ids.isNotEmpty()) {
        val idFilter=ids.joinToString(",")
        val mediaRaw=client.restGet("feed_post_media_public?select=post_id,media_type,secure_url,thumbnail_url,original_filename,sort_order&post_id=in.($idFilter)&order=sort_order.asc").getOrElse { throw it }
        val mediaArr=JSONArray(mediaRaw)
        for(i in 0 until mediaArr.length()) {
          val m=mediaArr.optJSONObject(i) ?: continue
          val pid=m.optString("post_id")
          val url=m.optString("secure_url")
          if(url.startsWith("https://")) mediaByPost.getOrPut(pid){mutableListOf()}.add(FeedMedia(url,m.optString("thumbnail_url").takeIf{it.startsWith("https://")},m.optString("media_type"),m.optString("original_filename")))
        }
      }
      posts=(0 until arr.length()).mapNotNull { i ->
        val p=arr.optJSONObject(i) ?: return@mapNotNull null
        FeedPost(p.optString("id"),p.optString("author_name").ifBlank{"Scholar"},p.optString("author_department"),p.optString("author_level"),p.optString("title"),p.optString("content"),p.optString("category_slug").ifBlank{"general"},p.optString("post_type").ifBlank{"TEXT"},p.optString("created_at"),p.optInt("reaction_count"),p.optInt("comment_count"),mediaByPost[p.optString("id")].orEmpty(),p.optString("resource_url").takeIf{it.startsWith("https://")})
      }
    } catch(e:Exception) {
      error="Medical Feeds could not load from the shared backend. Please retry. "+(e.message?.take(120).orEmpty())
    } finally { loading=false }
  }
  LaunchedEffect(Unit) { refresh() }
  Column(Modifier.fillMaxSize().padding(horizontal=16.dp,vertical=12.dp)) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
      Column(Modifier.weight(1f)) {
        Text("Medical Feeds",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Text("Updates from the MedRad community",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
      }
      IconButton(onClick={scope.launch{refresh()}},enabled=!loading){Icon(Icons.Default.Refresh,contentDescription="Refresh feed")}
    }
    when {
      loading -> Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){CircularProgressIndicator(); }
      error!=null -> Column(Modifier.fillMaxWidth().weight(1f),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Text(error!!,color=MaterialTheme.colorScheme.error);Spacer(Modifier.height(10.dp));Button(onClick={scope.launch{refresh()}}){Text("Retry")}}
      posts.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){Text("No public posts yet. Check back soon.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
      else -> LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=12.dp)) {
        items(posts,key={it.id}) { post ->
          Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
              Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                  Text(post.author,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
                  Text(listOf(post.department,post.level).filter(String::isNotBlank).joinToString(" • "),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(onClick={},label={Text(post.category.replace('_',' '))})
              }
              if(post.title.isNotBlank()) Text(post.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
              if(post.content.isNotBlank()) Text(post.content,style=MaterialTheme.typography.bodyMedium)
              post.media.firstOrNull()?.let { media -> AsyncImage(model=media.thumbnail?:media.url,contentDescription="Feed media",modifier=Modifier.fillMaxWidth().heightIn(max=300.dp)) }
              if(post.resource!=null) Text("Attached resource: "+post.resource,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
              Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("♥ ${post.reactions}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Comments ${post.comments}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatFeedDate(post.createdAt),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }
    }
  }
}

private fun formatFeedDate(raw:String):String {
  return try { SimpleDateFormat("MMM d • h:mm a",Locale.getDefault()).format(Date.from(java.time.Instant.parse(raw))) }
  catch(_:Exception) { raw.take(16).replace("T"," ") }
}
