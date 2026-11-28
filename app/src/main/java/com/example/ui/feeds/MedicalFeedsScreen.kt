package com.example.ui.feeds

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
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
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class FeedCategory(val slug: String, val name: String, val discipline: String = "General")
private data class FeedMedia(val url: String, val thumbnail: String?, val mediaType: String, val filename: String)
private data class FeedPost(val id: String, val author: String, val authorProfileId: String?, val isMine: Boolean, val followingAuthor: Boolean, val department: String, val level: String, val title: String, val content: String, val category: String, val createdAt: String, val reactions: Int, val comments: Int, val media: List<FeedMedia>, val resource: String?, val featured: Boolean, val liked: Boolean, val saved: Boolean)


private suspend fun uploadComposerAttachment(client: SupabaseClient, context: android.content.Context, bridgeToken: String?, uri: Uri, kind: String): JSONObject {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: when(kind) { "image" -> "image/jpeg"; "video" -> "video/mp4"; else -> "application/pdf" }
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("Could not read selected attachment")
    val limit = when(kind) { "image" -> 5 * 1024 * 1024; "video" -> 25 * 1024 * 1024; else -> 10 * 1024 * 1024 }
    if (bytes.isEmpty() || bytes.size > limit) throw IllegalArgumentException("Attachment exceeds allowed size (" + (limit / (1024 * 1024)) + " MB maximum).")
    val name = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: ("attachment." + if(kind == "image") "jpg" else if(kind == "video") "mp4" else "pdf")
    val sigResult = client.medicalFeedsRequest("get_feed_cloudinary_signature", JSONObject().put("mediaKind", kind), bridgeToken)
    val sigRoot = JSONObject(sigResult.getOrElse { throw it })
    if (!sigRoot.optBoolean("ok")) throw IllegalStateException(sigRoot.optString("error", "Could not prepare media upload"))
    val signature = sigRoot.optJSONObject("data") ?: throw IllegalStateException("Upload signature was missing")
    val uploaded = JSONObject(client.uploadFeedMediaSigned(signature, bytes, name, mime).getOrElse { throw it })
    val secureUrl = uploaded.optString("secure_url")
    if (!secureUrl.startsWith("https://")) throw IllegalStateException("Cloud upload returned an invalid media URL")
            val videoThumb = if(kind == "video") secureUrl.replace(Regex("\\.(mp4|mov|webm)(\\?.*)?$", RegexOption.IGNORE_CASE), ".jpg") else ""
    return JSONObject().put("mediaType", kind).put("cloudinaryPublicId", uploaded.optString("public_id", signature.optString("publicId")))
        .put("secureUrl", secureUrl).put("thumbnailUrl", videoThumb.takeIf { it.isNotBlank() })
        .put("width", uploaded.optInt("width")).put("height", uploaded.optInt("height"))
        .put("durationSeconds", uploaded.optDouble("duration", 0.0).takeIf { it > 0 })
        .put("mimeType", mime).put("bytes", uploaded.optLong("bytes", bytes.size.toLong())).put("originalFilename", name).put("sortOrder", 0)
}

private val FEED_SORTS = listOf("for-you" to "For You", "latest" to "Latest", "trending" to "Trending", "following" to "Following", "saved" to "Saved")
private val FEED_DISCIPLINES = listOf("ALL", "Medicine", "Pharmacy", "Dentistry", "Allied Health", "Academic")
private val FEED_TYPES = listOf("ALL" to "All", "TEXT" to "Text", "IMAGE" to "Images", "VIDEO" to "Videos", "RESOURCE" to "Resources", "POLL" to "Polls")

@Composable
fun MedicalFeedsScreen(client: SupabaseClient) {
    var posts by remember { mutableStateOf(emptyList<FeedPost>()) }
    var categories by remember { mutableStateOf(listOf(FeedCategory("general", "General"))) }
    var category by remember { mutableStateOf("all") }
    var discipline by remember { mutableStateOf("ALL") }
    var type by remember { mutableStateOf("ALL") }
    var sort by remember { mutableStateOf("for-you") }
    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(false) }
    var nextOffset by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var bridgeToken by remember { mutableStateOf<String?>(null) }
    var bridgeExpiry by remember { mutableLongStateOf(0L) }
    var verifyDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<Pair<String, JSONObject>?>(null) }
    var composer by remember { mutableStateOf(false) }
    var editingPost by remember { mutableStateOf<FeedPost?>(null) }
    var deletingPost by remember { mutableStateOf<FeedPost?>(null) }
    var reportingPost by remember { mutableStateOf<FeedPost?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var commentTarget by remember { mutableStateOf<FeedPost?>(null) }
    var commentList by remember { mutableStateOf(listOf<JSONObject>()) }
    var commentLoading by remember { mutableStateOf(false) }
    var commentDraft by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val context = LocalContext.current

    fun mapPost(p: JSONObject): FeedPost {
        val mediaJson = p.optJSONArray("media") ?: JSONArray()
        val media = (0 until mediaJson.length()).mapNotNull { idx ->
            mediaJson.optJSONObject(idx)?.let { m ->
                val link = m.optString("secureUrl", m.optString("secure_url"))
                if (link.startsWith("https://")) FeedMedia(link, m.optString("thumbnailUrl", m.optString("thumbnail_url")).takeIf { it.startsWith("https://") }, m.optString("mediaType", m.optString("media_type", "image")), m.optString("originalFilename", m.optString("original_filename"))) else null
            }
        }
        return FeedPost(
            p.optString("id"), p.optString("authorName", p.optString("author_name")).ifBlank { "Scholar" },
            p.optString("authorProfileId", p.optString("author_profile_id")).takeIf { it.isNotBlank() },
            p.optBoolean("isMine", p.optBoolean("is_mine")), p.optBoolean("viewerFollowsAuthor", p.optBoolean("viewer_follows_author")),
            p.optString("authorDepartment", p.optString("author_department")), p.optString("authorLevel", p.optString("author_level")),
            p.optString("title"), p.optString("content"), p.optString("categorySlug", p.optString("category_slug", "general")),
            p.optString("createdAt", p.optString("created_at")), p.optInt("reactionCount", p.optInt("reaction_count")),
            p.optInt("commentCount", p.optInt("comment_count")), media,
            p.optString("resourceUrl", p.optString("resource_url")).takeIf { it.startsWith("https://") },
            p.optBoolean("isFeatured", p.optBoolean("is_featured")) || p.optBoolean("isAnnouncement", p.optBoolean("is_announcement")),
            p.optBoolean("viewerHasLiked", p.optBoolean("viewer_has_liked")),
            p.optBoolean("viewerHasBookmarked", p.optBoolean("viewer_has_bookmarked"))
        )
    }

    suspend fun refresh() {
        loading = true; error = null
        try {
            val catsRes = client.medicalFeedsRequest("list_feed_categories")
            val catsJson = JSONObject(catsRes.getOrElse { throw it })
            val catArr = catsJson.optJSONArray("data")
            if (catArr != null) {
                val newCats = (0 until catArr.length()).mapNotNull { i -> catArr.optJSONObject(i)?.let { FeedCategory(it.optString("slug"), it.optString("name"), it.optString("disciplineGroup", it.optString("discipline_group", "General"))) } }
                if (newCats.isNotEmpty()) categories = newCats
            }
            val payload = JSONObject().put("categorySlug", category).put("disciplineGroup", discipline).put("postType", type).put("sortBy", sort).put("searchQuery", search.trim()).put("limit", 20).put("offset", 0)
            val result = client.medicalFeedsRequest("list_feed_posts", payload, bridgeToken.takeIf { bridgeExpiry > System.currentTimeMillis() })
            val json = JSONObject(result.getOrElse { throw it })
            if (!json.optBoolean("ok")) throw IllegalStateException(json.optString("error", "Feed service returned an error"))
            val data = json.optJSONObject("data") ?: JSONObject()
            val arr = data.optJSONArray("posts") ?: JSONArray()
            posts = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::mapPost) }
            hasMore = data.optBoolean("hasMore", false)
            nextOffset = data.optInt("nextOffset", posts.size)
        } catch (e: Exception) { error = e.message?.take(200) ?: "Medical Feeds is temporarily unavailable." }
        finally { loading = false }
    }

    suspend fun loadMore() {
        if (loadingMore || loading || !hasMore) return
        loadingMore = true
        try {
            val payload = JSONObject().put("categorySlug", category).put("disciplineGroup", discipline).put("postType", type).put("sortBy", sort).put("searchQuery", search.trim()).put("limit", 20).put("offset", nextOffset)
            val result = client.medicalFeedsRequest("list_feed_posts", payload, bridgeToken.takeIf { bridgeExpiry > System.currentTimeMillis() })
            val json = JSONObject(result.getOrElse { throw it })
            if (!json.optBoolean("ok")) throw IllegalStateException(json.optString("error", "Could not load more posts"))
            val data = json.optJSONObject("data") ?: JSONObject()
            val arr = data.optJSONArray("posts") ?: JSONArray()
            val more = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::mapPost) }
            posts = (posts + more).distinctBy { it.id }
            hasMore = data.optBoolean("hasMore", false)
            nextOffset = data.optInt("nextOffset", nextOffset + more.size)
        } catch (e: Exception) { message = e.message ?: "Could not load more posts" }
        finally { loadingMore = false }
    }

    suspend fun invokeAction(action: String, payload: JSONObject) {
        if (bridgeToken.isNullOrBlank() || bridgeExpiry < System.currentTimeMillis() + 15000) {
            pendingAction = action to payload
            verifyDialog = true
            return
        }
        val result = client.medicalFeedsRequest(action, payload, bridgeToken)
        val json = JSONObject(result.getOrElse { throw it })
        if (!json.optBoolean("ok")) throw IllegalStateException(json.optString("error", "Action was unsuccessful"))
        message = "Updated successfully"
        refresh()
    }

    LaunchedEffect(category, discipline, type, sort, search) {
        if ((sort == "following" || sort == "saved") && (bridgeToken.isNullOrBlank() || bridgeExpiry <= System.currentTimeMillis() + 15000)) {
            loading = false
            verifyDialog = true
        } else refresh()
    }

    if (verifyDialog) {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var authError by remember { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { if (!busy) { verifyDialog = false; pendingAction = null } },
            title = { Text("Verify community access") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter your existing MedRad account details to enable posting and community actions. Your password is not stored.")
                OutlinedTextField(email, { email = it }, label = { Text("Account email") }, singleLine = true)
                OutlinedTextField(password, { password = it }, label = { Text("Account password") }, singleLine = true)
                authError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(enabled = !busy && email.contains("@") && password.isNotBlank(), onClick = {
                busy = true
                scope.launch {
                    try {
                        val result = client.medicalFeedsRequest("feed_verify_session", JSONObject().put("email", email.trim().lowercase()).put("password", password))
                        val json = JSONObject(result.getOrElse { throw it })
                        if (!json.optBoolean("ok")) throw IllegalStateException(json.optString("error", "Account verification failed"))
                        val data = json.optJSONObject("data") ?: JSONObject()
                        bridgeToken = data.optString("token").takeIf { it.isNotBlank() }
                        bridgeExpiry = data.optLong("expiresAt", System.currentTimeMillis() + 10 * 60 * 1000)
                        password = ""; verifyDialog = false
                        val queued = pendingAction; pendingAction = null
                        if (queued != null) invokeAction(queued.first, queued.second)
                    } catch (e: Exception) { authError = e.message ?: "Verification failed" }
                    finally { busy = false }
                }
            }) { Text(if (busy) "Checking…" else "Verify") } },
            dismissButton = { TextButton(onClick = { verifyDialog = false; pendingAction = null }) { Text("Cancel") } }
        )
    }

    if (editingPost != null) {
        val target = editingPost!!
        var editTitle by remember(target.id) { mutableStateOf(target.title) }
        var editContent by remember(target.id) { mutableStateOf(target.content) }
        var editResource by remember(target.id) { mutableStateOf(target.resource.orEmpty()) }
        var saving by remember { mutableStateOf(false) }
        var editError by remember { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { if (!saving) editingPost = null }, title = { Text("Edit post") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(editTitle, { editTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(editContent, { editContent = it }, label = { Text("Post") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(editResource, { editResource = it }, label = { Text("HTTPS resource URL") }, modifier = Modifier.fillMaxWidth())
                editError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { Button(enabled = !saving && editContent.isNotBlank(), onClick = {
                saving = true
                scope.launch { try {
                    invokeAction("update_feed_post", JSONObject().put("postId", target.id).put("title", editTitle.trim()).put("content", editContent.trim()).put("resourceUrl", editResource.trim().takeIf { it.startsWith("https://") }))
                    editingPost = null
                } catch (e: Exception) { editError = e.message ?: "Update failed" } finally { saving = false } }
            }) { Text(if (saving) "Saving…" else "Save changes") } },
            dismissButton = { TextButton(onClick = { editingPost = null }, enabled = !saving) { Text("Cancel") } }
        )
    }
    if (deletingPost != null) {
        val target = deletingPost!!
        AlertDialog(onDismissRequest = { deletingPost = null }, title = { Text("Delete post?") }, text = { Text("This will permanently remove your post from the community feed.") },
            confirmButton = { TextButton(onClick = { scope.launch { try { invokeAction("delete_feed_post", JSONObject().put("postId", target.id)); deletingPost = null } catch (e: Exception) { message = e.message } } }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deletingPost = null }) { Text("Cancel") } })
    }
    if (reportingPost != null) {
        val target = reportingPost!!
        var reportReason by remember(target.id) { mutableStateOf("SPAM") }
        var reportDetails by remember(target.id) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { reportingPost = null }, title = { Text("Report post") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tell moderators why this post needs review.")
                listOf("SPAM", "HARASSMENT", "MISINFORMATION", "INAPPROPRIATE", "OTHER").forEach { reason -> FilterChip(reportReason == reason, { reportReason = reason }, label = { Text(reason.replace('_', ' ')) }) }
                OutlinedTextField(reportDetails, { reportDetails = it }, label = { Text("Additional details (optional)") }, modifier = Modifier.fillMaxWidth())
            } },
            confirmButton = { Button(onClick = { scope.launch { try { invokeAction("report_feed_content", JSONObject().put("targetType", "POST").put("targetId", target.id).put("reason", reportReason).put("details", reportDetails.trim())); reportingPost = null } catch (e: Exception) { message = e.message } } }) { Text("Submit report") } },
            dismissButton = { TextButton(onClick = { reportingPost = null }) { Text("Cancel") } })
    }

    if (composer) {
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        var resource by remember { mutableStateOf("") }
        var tags by remember { mutableStateOf("") }
        var postCategory by remember { mutableStateOf(categories.firstOrNull()?.slug ?: "general") }
        var kind by remember { mutableStateOf("TEXT") }
        var busy by remember { mutableStateOf(false) }
        var publishError by remember { mutableStateOf<String?>(null) }
        var attachmentUri by remember { mutableStateOf<Uri?>(null) }
        var attachmentKind by remember { mutableStateOf("") }
        var attachmentName by remember { mutableStateOf("") }
        val imageVideoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
            if (picked != null) { attachmentUri = picked; attachmentKind = if ((context.contentResolver.getType(picked) ?: "").startsWith("video/")) "video" else "image"; attachmentName = picked.lastPathSegment?.substringAfterLast('/') ?: "Selected media" }
        }
        val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
            if (picked != null) { attachmentUri = picked; attachmentKind = "document"; attachmentName = picked.lastPathSegment?.substringAfterLast('/') ?: "PDF document" }
        }
        AlertDialog(onDismissRequest = { if (!busy) composer = false }, title = { Text("Create post") },
            text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("TEXT", "RESOURCE").forEach { FilterChip(kind == it, { kind = it }, label = { Text(if (it == "TEXT") "Discussion" else "Resource") }) } }
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(content, { content = it }, label = { Text("Your post") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(resource, { resource = it }, label = { Text("HTTPS resource URL (optional)") }, modifier = Modifier.fillMaxWidth())
                Text("Attachments", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { imageVideoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }, enabled = !busy) { Text("Photo / video") }
                    OutlinedButton(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }, enabled = !busy) { Text("PDF notes") }
                }
                attachmentUri?.let { selected ->
                    Card(shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MedicalTeal.copy(alpha = .35f)), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (attachmentKind == "image") AsyncImage(model = selected, contentDescription = "Selected image", modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)))
                            else Icon(if (attachmentKind == "document") Icons.Default.PictureAsPdf else Icons.Default.PlayArrow, null, tint = MedicalTeal, modifier = Modifier.size(32.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) { Text(attachmentName, maxLines = 2, style = MaterialTheme.typography.bodySmall); Text(attachmentKind.uppercase(), color = MedicalTeal, style = MaterialTheme.typography.labelSmall) }
                            IconButton(onClick = { attachmentUri = null; attachmentKind = ""; attachmentName = "" }, enabled = !busy) { Icon(Icons.Default.Refresh, "Remove attachment") }
                        }
                    }
                }
                OutlinedTextField(tags, { tags = it }, label = { Text("Hashtags (optional)") }, modifier = Modifier.fillMaxWidth())
                Text("Category", fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) { categories.forEach { c -> FilterChip(postCategory == c.slug, { postCategory = c.slug }, label = { Text(c.name) }) } }
                if (busy) {\n                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = MedicalTeal)\n                    Text(if (attachmentUri != null) "Uploading attachment and publishing…" else "Publishing…", style = MaterialTheme.typography.labelMedium, color = MedicalTeal)\n                }\n                publishError?.let { Text(it, color = if (it.startsWith("Uploading")) MedicalTeal else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            } },
            confirmButton = { Button(enabled = !busy && content.isNotBlank(), onClick = {
                busy = true
                scope.launch {
                    try {
                        if (attachmentUri != null && (bridgeToken.isNullOrBlank() || bridgeExpiry <= System.currentTimeMillis() + 15000)) {
                            verifyDialog = true
                            publishError = "Verify your community account, then publish again to upload the attachment."
                            return@launch
                        }
                        val hashtagList = tags.split(Regex("[,\\s]+")).map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }.distinct().take(10)
                        val body = JSONObject().put("categorySlug", postCategory).put("postType", when (attachmentKind) { "image" -> "IMAGE"; "video" -> "VIDEO"; "document" -> "RESOURCE"; else -> kind }).put("visibility", "PUBLIC")
                            .put("title", title.trim()).put("content", content.trim())
                            .put("resourceUrl", resource.trim().takeIf { it.startsWith("https://") }).put("hashtags", JSONArray(hashtagList))
                        if (attachmentUri != null) {
                            publishError = "Uploading attachment…"
                            val mediaItem = uploadComposerAttachment(client, context, bridgeToken, attachmentUri!!, attachmentKind)
                            body.put("media", JSONArray().put(mediaItem))
                        }
                        invokeAction("create_feed_post", body); composer = false
                    } catch (e: Exception) { publishError = e.message ?: "Publishing failed" }
                    finally { busy = false }
                }
            }) { Text(if (busy) "Please wait…" else "Publish") } },
            dismissButton = { TextButton(onClick = { composer = false }) { Text("Cancel") } }
        )
    }

    if (commentTarget != null) {
        AlertDialog(onDismissRequest = { commentTarget = null }, title = { Text("Add a comment") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Comments are connected to the shared community feed.")
                OutlinedTextField(commentDraft, { commentDraft = it }, label = { Text("Comment") }, modifier = Modifier.fillMaxWidth())
                if (commentLoading) repeat(3) { FeedCommentSkeleton() }
                else if (commentList.isEmpty()) Text("No comments yet. Start the discussion.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                else commentList.forEach { c -> Text(c.optString("authorName", "Scholar") + ": " + c.optString("content"), style = MaterialTheme.typography.bodySmall) }
            } },
            confirmButton = { TextButton(enabled = commentDraft.isNotBlank(), onClick = {
                val p = commentTarget ?: return@TextButton
                scope.launch { try { invokeAction("create_feed_comment", JSONObject().put("postId", p.id).put("content", commentDraft.trim())); commentDraft = ""; commentTarget = null } catch (e: Exception) { message = e.message } }
            }) { Text("Send") } }, dismissButton = { TextButton(onClick = { commentTarget = null }) { Text("Close") } })
    }

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandIcon { Icon(Icons.Default.Newspaper, null, tint = androidx.compose.ui.graphics.Color.White) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) { Text("MEDICAL COMMUNITY", color = MedicalTeal, fontSize = 10.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold); Text("Medical Feeds", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                IconButton(onClick = { scope.launch { refresh() } }, enabled = !loading) { Icon(Icons.Default.Refresh, "Refresh", tint = MedicalTeal) }
            }
            Text("Share knowledge, discover resources and connect with scholars.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search posts or scholars…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(15.dp))
            Spacer(Modifier.height(7.dp))
            Button(onClick = { composer = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Create a post") }
        }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { FEED_SORTS.forEach { (key, label) -> FilterChip(sort == key, { sort = key }, label = { Text(label) }) } } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { FEED_DISCIPLINES.forEach { d -> FilterChip(discipline == d, { discipline = d }, label = { Text(d) }) } } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { FEED_TYPES.forEach { (key, label) -> FilterChip(type == key, { type = key }, label = { Text(label) }) } } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(category == "all", { category = "all" }, label = { Text("All categories") }); categories.forEach { c -> FilterChip(category == c.slug, { category = c.slug }, label = { Text(c.name) }) } } }
        item { Text("Community feed • ${posts.size} posts", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
        if (loading) {
            items(3) { FeedSkeletonCard() }
        }
        else if (error != null) item { Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(error!!, color = MaterialTheme.colorScheme.error); Button(onClick = { scope.launch { refresh() } }) { Text("Retry") } } }
        else if (posts.isEmpty()) item { EmptyCard("No posts here yet", "Try another filter or start the first discussion.") }
        else items(posts, key = { it.id }) { post ->
            Card(shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MedicalTeal.copy(alpha = .12f), modifier = Modifier.size(42.dp)) { Box(contentAlignment = Alignment.Center) { Text(post.author.split(" ").take(2).mapNotNull { it.firstOrNull() }.joinToString("").uppercase().take(2), fontWeight = FontWeight.Bold, color = MedicalTeal) } }
                        Column(Modifier.weight(1f).padding(start = 10.dp)) { Text(post.author, fontWeight = FontWeight.Bold); Text(listOf(post.department, post.level).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "MedRad community" }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) }
                        if (!post.isMine && !post.authorProfileId.isNullOrBlank()) TextButton(onClick = { scope.launch { try { invokeAction("toggle_follow_author", JSONObject().put("targetProfileId", post.authorProfileId)) } catch (e: Exception) { message = e.message } } }, contentPadding = PaddingValues(horizontal = 5.dp)) { Text(if (post.followingAuthor) "Following" else "Follow", style = MaterialTheme.typography.labelMedium) }
                        if (post.featured) Surface(color = WarningAmber.copy(alpha = .12f), shape = RoundedCornerShape(8.dp)) { Text("FEATURED", Modifier.padding(6.dp), color = WarningAmber, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                        Box { IconButton(onClick = { postMenuOpen = true }) { Icon(Icons.Default.MoreVert, "Post options") }; DropdownMenu(expanded = postMenuOpen, onDismissRequest = { postMenuOpen = false }) { if (post.isMine) { DropdownMenuItem(text = { Text("Edit post") }, onClick = { postMenuOpen = false; editingPost = post }); DropdownMenuItem(text = { Text("Delete post", color = MaterialTheme.colorScheme.error) }, onClick = { postMenuOpen = false; deletingPost = post }) } else DropdownMenuItem(text = { Text("Report post") }, onClick = { postMenuOpen = false; reportingPost = post }) } }
                    }
                    Surface(color = MedicalTeal.copy(alpha = .1f), shape = RoundedCornerShape(8.dp)) { Text(post.category.replace('_', ' '), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = MedicalTeal, style = MaterialTheme.typography.labelSmall) }
                    if (post.title.isNotBlank()) Text(post.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (post.content.isNotBlank()) Text(post.content, style = MaterialTheme.typography.bodyMedium, lineHeight = 21.sp)
                    post.media.forEach { m -> if (m.mediaType == "document") Card(shape = RoundedCornerShape(13.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.PictureAsPdf, null, tint = MedicalTeal, modifier = Modifier.size(30.dp)); Column(Modifier.weight(1f).padding(horizontal = 8.dp)) { Text(m.filename.ifBlank { "Study document" }, fontWeight = FontWeight.SemiBold, maxLines = 2); Text("PDF / document", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) }; TextButton(onClick = { uri.openUri(m.url) }) { Text("Open") } } } else AsyncImage(model = m.thumbnail ?: m.url, contentDescription = if(m.mediaType == "video") "Video attachment thumbnail" else "Post image", modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(RoundedCornerShape(13.dp))) }
                    post.resource?.let { link -> OutlinedButton(onClick = { uri.openUri(link) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Link, null); Spacer(Modifier.width(5.dp)); Text("Open attached resource") } }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { scope.launch { try { invokeAction("toggle_feed_reaction", JSONObject().put("postId", post.id)) } catch (e: Exception) { message = e.message } }) { Icon(Icons.Default.Favorite, null, tint = MedicalTeal); Text(" Helpful ${post.reactions}") }
                        TextButton(onClick = {
                            commentTarget = post; commentList = emptyList(); commentLoading = true
                            scope.launch {
                                try {
                                    val res = client.medicalFeedsRequest("list_feed_comments", JSONObject().put("postId", post.id).put("limit", 20).put("offset", 0), bridgeToken)
                                    val j = JSONObject(res.getOrElse { throw it })
                                    val arr = j.optJSONObject("data")?.optJSONArray("comments") ?: JSONArray()
                                    commentList = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                                } catch (e: Exception) { message = e.message ?: "Unable to load comments" }
                                finally { commentLoading = false }
                            }
                        }) { Icon(Icons.Default.Forum, null); Text(" ${post.comments}") }
                        IconButton(onClick = { scope.launch { try { invokeAction("toggle_feed_bookmark", JSONObject().put("postId", post.id)) } catch (e: Exception) { message = e.message } }) { Icon(if (post.saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "Save", tint = if (post.saved) MedicalTeal else MaterialTheme.colorScheme.onSurfaceVariant) }
                        IconButton(onClick = { runCatching { uri.openUri("https://medradstudyweb.vercel.app/community/feeds?post=${post.id}") } }) { Icon(Icons.Default.Share, "Share") }
                    }
                    Text(formatDate(post.createdAt), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item {
            if (hasMore) {
                OutlinedButton(onClick = { scope.launch { loadMore() } }, enabled = !loadingMore, modifier = Modifier.fillMaxWidth()) {
                    if (loadingMore) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(if (loadingMore) "Loading more…" else "Load more posts")
                }
            } else if (posts.isNotEmpty()) {
                Text("You're all caught up", modifier = Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    message?.let { Text(it, modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun BrandIcon(content: @Composable () -> Unit) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(AcademicNavy, MedicalTeal))), contentAlignment = Alignment.Center) { content() }
}
@Composable
private fun EmptyCard(title: String, subtitle: String) {
    Card(shape = RoundedCornerShape(18.dp)) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Forum, null, tint = MedicalTeal, modifier = Modifier.size(34.dp)); Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}
private fun formatDate(raw: String): String = try { SimpleDateFormat("MMM d • h:mm a", Locale.getDefault()).format(Date.from(java.time.Instant.parse(raw))) } catch (_: Exception) { raw.take(16).replace("T", " ") }
@Composable
private fun FeedSkeletonCard() {
    val shimmer = rememberInfiniteTransition(label = "feed-skeleton")
    val shimmerOffset by shimmer.animateFloat(
        initialValue = -500f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "skeleton-shimmer"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f)
    val brush = Brush.linearGradient(
        colors = listOf(base.copy(alpha = 0.55f), base.copy(alpha = 0.9f), highlight, base.copy(alpha = 0.65f)),
        start = Offset(shimmerOffset, 0f),
        end = Offset(shimmerOffset + 480f, 480f)
    )
    @Composable fun Placeholder(modifier: Modifier, shape: RoundedCornerShape = RoundedCornerShape(8.dp)) {
        Box(modifier.clip(shape).background(brush))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Placeholder(Modifier.size(42.dp), RoundedCornerShape(50.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Placeholder(Modifier.fillMaxWidth(0.48f).height(14.dp))
                    Placeholder(Modifier.fillMaxWidth(0.32f).height(10.dp))
                }
                Placeholder(Modifier.width(46.dp).height(22.dp))
            }
            Placeholder(Modifier.fillMaxWidth(0.82f).height(17.dp))
            Placeholder(Modifier.fillMaxWidth().height(12.dp))
            Placeholder(Modifier.fillMaxWidth(0.93f).height(12.dp))
            Placeholder(Modifier.fillMaxWidth(0.65f).height(12.dp))
            Placeholder(Modifier.fillMaxWidth().height(150.dp), RoundedCornerShape(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Placeholder(Modifier.width(66.dp).height(28.dp))
                Placeholder(Modifier.width(66.dp).height(28.dp))
                Spacer(Modifier.weight(1f))
                Placeholder(Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun FeedCommentSkeleton() {
    val transition = rememberInfiniteTransition(label = "comment-skeleton")
    val sweep by transition.animateFloat(
        initialValue = -300f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "comment-shimmer"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val brush = Brush.linearGradient(
        listOf(base.copy(alpha = 0.5f), base.copy(alpha = 0.85f), MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
        start = Offset(sweep, 0f),
        end = Offset(sweep + 300f, 80f)
    )
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth(0.35f).height(10.dp).clip(RoundedCornerShape(6.dp)).background(brush))
        Box(Modifier.fillMaxWidth(0.92f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(brush))
    }
}
