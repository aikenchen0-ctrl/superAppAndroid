package com.paifa.univerge.accessibility.floatingchat.moments

import com.paifa.univerge.accessibility.AppMomentComment
import com.paifa.univerge.accessibility.AppMomentMedia
import com.paifa.univerge.accessibility.AppMomentPost
import com.paifa.univerge.accessibility.MomentMediaKind
import com.paifa.univerge.accessibility.scrmMomentPostsFromTaskData
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentsIosParityTest {
    /**
     * 测试流程：SCRM 返回同时带图片和 linkInfo 的朋友圈，重新打开浮层后确认图片和链接都保留。
     */
    @Test
    fun parserKeepsIosStyleLinkAlongsideMediaAndCacheMapping() {
        val payload = Json.parseToJsonElement(
            """
            {
              "moments": [{
                "circleId": 321,
                "nickname": "Alice",
                "authorWxid": "wxid_alice",
                "content": "查看本周资料",
                "images": ["https://cdn.example.com/moment.jpg"],
                "linkInfo": {
                  "url": "https://cc2.cx/docs?id=321",
                  "title": "本周资料"
                }
              }]
            }
            """.trimIndent()
        )

        val post = scrmMomentPostsFromTaskData(payload).single()

        assertEquals(MomentMediaKind.Image, post.media?.kind)
        assertEquals("https://cc2.cx/docs?id=321", post.linkUrl)
        assertEquals("本周资料", post.linkTitle)
        assertEquals(post.linkUrl, post.toLocalMomentPost().toAppMomentPost().linkUrl)
    }

    /**
     * 测试流程：同步带真实标识的朋友圈详情，关闭并重新打开悬浮聊天后，
     * 确认评论、回复和删除本人评论所需的远端标识仍可从本地缓存读取。
     */
    @Test
    fun cacheMappingRetainsRemoteIdentifiersNeededForMomentActions() {
        val post = AppMomentPost(
            id = "scrm-moment:998",
            author = "Alice",
            authorWxId = "wxid_alice",
            content = "带真实标识的动态",
            time = "刚刚",
            circleId = 998L,
            publishTime = 1_725_000_000L,
            comments = listOf(
                AppMomentComment(
                    id = 77L,
                    author = "Bob",
                    authorWxId = "wxid_bob",
                    replyTo = "Alice",
                    replyCommentId = 66L,
                    text = "收到"
                )
            )
        )

        val restored = post.toLocalMomentPost().toAppMomentPost()

        assertEquals(post.authorWxId, restored.authorWxId)
        assertEquals(post.circleId, restored.circleId)
        assertEquals(post.publishTime, restored.publishTime)
        assertEquals(post.comments, restored.comments)
    }

    /**
     * 测试流程：打开自己的动态或自己的评论，确认不会进入评论输入态；其他人的动态和评论仍可正常评论。
     */
    @Test
    fun currentAccountCannotCommentOwnMomentOrReplyToOwnComment() {
        val ownPost = AppMomentPost(
            author = "我",
            authorWxId = "wxid_owner",
            content = "我的动态",
            time = "刚刚"
        )
        val otherPost = ownPost.copy(author = "Alice", authorWxId = "wxid_alice")
        val ownComment = AppMomentComment(author = "我", authorWxId = "wxid_owner", text = "我的评论")
        val otherComment = AppMomentComment(author = "Bob", authorWxId = "wxid_bob", text = "别人的评论")

        assertFalse(canCurrentAccountCommentOnMoment(ownPost, "wxid_owner"))
        assertFalse(canCurrentAccountCommentOnMoment(otherPost, "wxid_owner", ownComment))
        assertTrue(canCurrentAccountCommentOnMoment(otherPost, "wxid_owner"))
        assertTrue(canCurrentAccountCommentOnMoment(otherPost, "wxid_owner", otherComment))
    }

    /**
     * 测试流程：点击朋友圈链接并确认，只有 http/https 地址才可交给 Android 外部浏览器。
     */
    @Test
    fun externalLinkNormalizationOnlyAllowsWebSchemes() {
        assertEquals("https://cc2.cx/docs", normalizeMomentExternalLink(" https://cc2.cx/docs "))
        assertNull(normalizeMomentExternalLink("intent://unsafe"))
        assertNull(normalizeMomentExternalLink("file:///storage/emulated/0/private"))
        assertNull(normalizeMomentExternalLink("https:///missing-host"))
    }

    /**
     * 测试流程：只有链接而没有图片、视频和正文的远端朋友圈也必须出现在时间线中。
     */
    @Test
    fun parserAcceptsLinkOnlyMomentPayload() {
        val payload = Json.parseToJsonElement(
            """
            {
              "moments": [{
                "circleId": 322,
                "nickname": "Alice",
                "linkUrl": "https://cc2.cx/only-link",
                "linkTitle": "仅链接动态"
              }]
            }
            """.trimIndent()
        )

        val post = scrmMomentPostsFromTaskData(payload).single()

        assertEquals("https://cc2.cx/only-link", post.linkUrl)
        assertEquals("仅链接动态", post.linkTitle)
    }

    @Test
    fun parserUsesIosCompatibleNestedLinkSourceNameAsTitle() {
        val payload = Json.parseToJsonElement(
            """
            {
              "moments": [{
                "circleId": 323,
                "nickname": "Alice",
                "linkInfo": {
                  "url": "https://cc2.cx/source",
                  "sourceName": "嵌套来源"
                }
              }]
            }
            """.trimIndent()
        )

        val post = scrmMomentPostsFromTaskData(payload).single()

        assertEquals("嵌套来源", post.linkTitle)
    }
}
