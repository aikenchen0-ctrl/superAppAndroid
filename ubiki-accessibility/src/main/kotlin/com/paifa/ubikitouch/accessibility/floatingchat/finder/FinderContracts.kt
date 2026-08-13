package com.paifa.ubikitouch.accessibility.floatingchat.finder

import java.net.URI
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Stable Finder OpenAPI paths. Keep transport URLs out of Compose state. */
internal object FinderEndpoints {
    const val POST_TEMPLATE = "/openapi/v1/finder/posts/template"
    const val POSTS = "/openapi/v1/finder/posts"
    const val USER_PAGE = "/openapi/v1/finder/user-page"
    const val LIKES = "/openapi/v1/finder/likes"
    const val COMMENTS = "/openapi/v1/finder/comments"
    const val NAVIGATION = "/openapi/v1/finder/navigation"

    fun relativePath(route: String): String = route.removePrefix("/openapi/v1/").removePrefix("/")
}

/**
 * The OpenAPI contract currently exposes mediaType as an integer without its enum metadata.
 * These values are sent through template validation first, so a backend contract mismatch is
 * returned to the operator instead of being hidden by a client-side fallback.
 */
internal enum class FinderMediaType(val code: Int, val label: String) {
    Image(code = 1, label = "图片"),
    Video(code = 2, label = "视频")
}

@Serializable
internal data class FinderPoi(
    val city: String? = null,
    val name: String? = null,
    val address: String? = null,
    val lat: Float = 0f,
    val lng: Float = 0f,
    val poiId: String? = null,
    val hasCoordinates: Boolean = false
) {
    init {
        require(city == null || city.isNotBlank()) { "poi city cannot be blank" }
        require(name == null || name.isNotBlank()) { "poi name cannot be blank" }
        require(address == null || address.isNotBlank()) { "poi address cannot be blank" }
        require(poiId == null || poiId.isNotBlank()) { "poiId cannot be blank" }
    }
}

@Serializable
internal data class FinderPostRequest(
    val deviceUuid: String,
    val weChatId: String,
    val content: String,
    val medias: List<String>,
    val mediaType: Int,
    val cover: String? = null,
    val poi: FinderPoi? = null
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(content.isNotBlank()) { "content cannot be blank" }
        require(medias.isNotEmpty()) { "medias cannot be empty" }
        require(mediaType > 0) { "mediaType must be greater than 0" }
        require(medias.all(::isAccessibleHttpUrl)) { "all media URLs must use http or https" }
        require(cover == null || isAccessibleHttpUrl(cover)) { "cover must use http or https" }
    }
}

@Serializable
internal data class FinderPostTemplateRequest(
    val deviceUuid: String,
    val weChatId: String,
    val content: String,
    val medias: List<String>,
    val mediaType: Int,
    val cover: String? = null,
    val poi: FinderPoi? = null,
    val includePostRequest: Boolean = true
) {
    init {
        FinderPostRequest(deviceUuid, weChatId, content, medias, mediaType, cover, poi)
    }

    fun toPostRequest(): FinderPostRequest = FinderPostRequest(
        deviceUuid = deviceUuid,
        weChatId = weChatId,
        content = content,
        medias = medias,
        mediaType = mediaType,
        cover = cover,
        poi = poi
    )
}

@Serializable
internal data class FinderPostTemplateResponse(
    val success: Boolean = false,
    val payload: FinderPostRequest? = null,
    val postRequest: FinderPostRequest? = null,
    val warnings: List<String>? = null,
    val message: String? = null
)

/** A successful template response must supply the exact payload submitted to the publish endpoint. */
internal fun validatedFinderPostRequest(response: FinderPostTemplateResponse): FinderPostRequest {
    require(response.success) { response.message ?: "视频号模板校验未通过" }
    return response.postRequest ?: response.payload
        ?: throw IllegalArgumentException("发布模板未返回可提交的发布载荷")
}

@Serializable
internal data class FinderUserPageRequest(
    val deviceUuid: String,
    val weChatId: String,
    val sphUserName: String? = null
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(sphUserName == null || sphUserName.isNotBlank()) { "sphUserName cannot be blank" }
    }
}

@Serializable
internal data class FinderLikeRequest(
    val deviceUuid: String,
    val weChatId: String,
    val feedId: Long,
    val type: Int = DefaultFinderFeedType,
    val isCancel: Boolean
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(feedId > 0L) { "feedId must be greater than 0" }
    }
}

@Serializable
internal data class FinderCommentRequest(
    val deviceUuid: String,
    val weChatId: String,
    val feedId: Long,
    val nonceId: String,
    val feedAuth: String,
    val type: Int = DefaultFinderFeedType,
    val content: String,
    val media: String? = null,
    val replyCommentId: Long = 0L,
    val replyUsername: String? = null,
    val replyNickname: String? = null,
    val findText: String = ""
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        require(feedId > 0L) { "feedId must be greater than 0" }
        require(nonceId.isNotBlank()) { "nonceId cannot be blank" }
        require(feedAuth.isNotBlank()) { "feedAuth cannot be blank" }
        require(content.isNotBlank()) { "comment content cannot be blank" }
        require(media == null || isAccessibleHttpUrl(media)) { "comment media must use http or https" }
        require(replyCommentId >= 0L) { "replyCommentId cannot be negative" }
        require(replyUsername == null || replyUsername.isNotBlank()) { "replyUsername cannot be blank" }
        require(replyNickname == null || replyNickname.isNotBlank()) { "replyNickname cannot be blank" }
        require(replyCommentId == 0L || !replyUsername.isNullOrBlank()) {
            "replyUsername is required when replying to a comment"
        }
        require(replyCommentId == 0L || !replyNickname.isNullOrBlank()) {
            "replyNickname is required when replying to a comment"
        }
    }
}

@Serializable
internal enum class FinderNavigationAction {
    @SerialName("home")
    Home,
    @SerialName("search")
    Search
}

@Serializable
internal data class FinderNavigationRequest(
    val deviceUuid: String,
    val weChatId: String,
    val action: FinderNavigationAction,
    val keyword: String? = null
) {
    init {
        require(deviceUuid.isNotBlank()) { "deviceUuid cannot be blank" }
        require(weChatId.isNotBlank()) { "weChatId cannot be blank" }
        if (action == FinderNavigationAction.Search) {
            require(normalizedKeyword != null) { "search keyword must contain 1 to 100 characters" }
        }
    }

    val normalizedKeyword: String?
        get() {
            if (action != FinderNavigationAction.Search) return null
            val normalized = keyword?.trim().orEmpty()
            require(normalized.isNotEmpty()) { "search keyword must contain 1 to 100 characters" }
            require(normalized.length <= FinderSearchKeywordMaxLength) {
                "search keyword cannot exceed $FinderSearchKeywordMaxLength characters"
            }
            return normalized
        }
}

internal const val DefaultFinderFeedType = 1
internal const val FinderSearchKeywordMaxLength = 100

internal fun isAccessibleHttpUrl(value: String): Boolean {
    val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return false
    val hasHttpScheme = uri.scheme.equals("http", ignoreCase = true) ||
        uri.scheme.equals("https", ignoreCase = true)
    return hasHttpScheme && !uri.host.isNullOrBlank()
}

internal fun finderParseMediaUrls(value: String): List<String> {
    val urls = value.lines().map(String::trim).filter(String::isNotEmpty)
    require(urls.isNotEmpty()) { "at least one media URL is required" }
    require(urls.all(::isAccessibleHttpUrl)) { "media URL must use http or https" }
    return urls.distinct()
}

internal fun finderComposeContent(content: String, topics: String): String {
    val normalizedContent = content.trim()
    require(normalizedContent.isNotEmpty()) { "content cannot be blank" }
    val normalizedTopics = topics
        .split(',', '，', '\n')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString(" ") { topic -> if (topic.startsWith('#')) topic else "#$topic" }
    return listOf(normalizedContent, normalizedTopics).filter(String::isNotEmpty).joinToString(" ")
}
