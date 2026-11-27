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
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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

private data class FeedCategory(val slug: String, val name: String)
private data class FeedMedia(val url: String, val thumbnail: String?)
private data class FeedPost(val id: String, val author: String, val department: String, val level: String, val title: String, val content: String, val category: String, val createdAt: String, val reactions: Int, val comments: Int, val media: List<FeedMedia>, val resource: String?)

@Composable
fun MedicalFeedsScreen(client: SupabaseClient) {
    var posts by remember { mutableStateOf(emptyList<FeedPost>()) }
    var categories by remember { mutableStateOf(listOf(FeedCategory("general", "General"))) }
    var selected by remember { mutableStateOf("all") }
    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    suspend fun refresh() {
        loading = true
        error = null
        try {
            runCatching {
                val raw = JSONArray(client.restGet("community_categories?select=slug,name&is_active=eq.true&order=sort_order.asc").getOrElse { throw it })
                val fetched = (0 until raw.length()).mapNotNull { i -> raw.optJSONObject(i)?.let { FeedCategory(it.optString("slug"), it.optString("name")) } }
                if (fetched.isNotEmpty()) categories = fetched
            }
            val rawPosts = JSONArray(client.restGet("feed_posts_public?select=id,author_name,author_department,author_level,title,content,category_slug,created_at,reaction_count,comment_count,resource_url&order=created_at.desc&limit=30").getOrElse { throw it })
            val ids = (0 until rawPosts.length()).mapNotNull { rawPosts.optJSONObject(it)?.optString("id")?.takeIf { id -> id.isNotBlank() } }
            val mediaByPost = mutableMapOf<String, MutableList<FeedMedia>>()
            if (ids.isNotEmpty()) {
                val mediaQuery = "feed_post_media_public?select=post_id,secure_url,thumbnail_url,sort_order&post_id=in.(" + ids.joinToString(",") + ")&order=sort_order.asc"
                val rawMedia = JSONArray(client.restGet(mediaQuery).getOrElse { throw it })
                for (i in 0 until rawMedia.length()) {
                    val row = rawMedia.optJSONObject(i) ?: continue
                    val url = row.optString("secure_url")
                    if (url.startsWith("https://")) mediaByPost.getOrPut(row.optString("post_id")) { mutableListOf() }
                        .add(FeedMedia(url, row.optString("thumbnail_url").takeIf { it.startsWith("https://") }))
                }
            }
            posts = (0 until rawPosts.length()).mapNotNull { i ->
                val p = rawPosts.optJSONObject(i) ?: return@mapNotNull null
                FeedPost(p.optString("id"), p.optString("author_name").ifBlank { "Scholar" }, p.optString("author_department"),
                    p.optString("author_level"), p.optString("title"), p.optString("content"), p.optString("category_slug").ifBlank { "general" },
                    p.optString("created_at"), p.optInt("reaction_count"), p.optInt("comment_count"), mediaByPost[p.optString("id")].orEmpty(),
                    p.optString("resource_url").takeIf { it.startsWith("https://") })
            }
        } catch (_: Exception) {
            error = "Feed unavailable. Check your connection and retry."
        } finally { loading = false }
    }

    LaunchedEffect(Unit) { refresh() }
    val visible = posts.filter { post ->
        (selected == "all" || post.category == selected) &&
            (search.isBlank() || post.title.contains(search, true) || post.content.contains(search, true) || post.author.contains(search, true))
    }

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandIcon { Icon(Icons.Default.Newspaper, null, tint = androidx.compose.ui.graphics.Color.White) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("MEDICAL COMMUNITY", color = MedicalTeal, fontSize = 10.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold)
                    Text("Medical Feeds", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { scope.launch { refresh() } }, enabled = !loading) { Icon(Icons.Default.Refresh, "Refresh", tint = MedicalTeal) }
            }
            Spacer(Modifier.height(5.dp))
            Text("Research, resources and updates from your community.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Search posts or scholars…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(16.dp))
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected == "all", { selected = "all" }, label = { Text("All posts") })
                categories.forEach { cat -> FilterChip(selected == cat.slug, { selected = cat.slug }, label = { Text(cat.name) }) }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Community feed", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Surface(color = MedicalTeal.copy(alpha = .1f), shape = RoundedCornerShape(50.dp)) {
                    Text("${visible.size} posts", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = MedicalTeal, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (loading) item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MedicalTeal) } }
        else if (error != null) item {
            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Button(onClick = { scope.launch { refresh() } }) { Text("Retry") }
            }
        } else if (visible.isEmpty()) item { EmptyCard("No posts to display", "Try another category or check back later.") }
        else items(visible, key = { it.id }) { post ->
            Card(shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MedicalTeal.copy(alpha = .12f), modifier = Modifier.size(42.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(post.author.split(" ").take(2).mapNotNull { it.firstOrNull() }.joinToString("").uppercase().take(2), fontWeight = FontWeight.Bold, color = MedicalTeal) }
                        }
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(post.author, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text(listOf(post.department, post.level).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "MedRad community" }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
                        Surface(color = WarningAmber.copy(alpha = .12f), shape = RoundedCornerShape(9.dp)) {
                            Text(post.category.replace('_', ' '), Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = WarningAmber, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (post.title.isNotBlank()) Text(post.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (post.content.isNotBlank()) Text(post.content, style = MaterialTheme.typography.bodyMedium, lineHeight = 21.sp)
                    post.media.firstOrNull()?.let { m -> AsyncImage(model = m.thumbnail ?: m.url, contentDescription = "Feed attachment", modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(RoundedCornerShape(14.dp))) }
                    post.resource?.let { link -> OutlinedButton(onClick = { uriHandler.openUri(link) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Link, null); Spacer(Modifier.width(6.dp)); Text("Open attached resource") } }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, null, tint = MedicalTeal, modifier = Modifier.size(17.dp))
                        Text(" ${post.reactions}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(14.dp))
                        Icon(Icons.Default.Forum, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                        Text(" ${post.comments} comments", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.weight(1f))
                        Text(formatDate(post.createdAt), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandIcon(content: @Composable () -> Unit) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(AcademicNavy, MedicalTeal))), contentAlignment = Alignment.Center, content = content)
}

@Composable
private fun EmptyCard(title: String, subtitle: String) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Forum, null, tint = MedicalTeal, modifier = Modifier.size(34.dp))
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatDate(raw: String): String = try {
    SimpleDateFormat("MMM d • h:mm a", Locale.getDefault()).format(Date.from(java.time.Instant.parse(raw)))
} catch (_: Exception) { raw.take(16).replace("T", " ") }
