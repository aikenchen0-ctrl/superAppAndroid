package com.paifa.ubikitouch.accessibility.scrm

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import com.paifa.ubikitouch.core.model.FloatingChatPrototype
import com.paifa.ubikitouch.accessibility.floatingchat.chat.accountScopedConversations
import com.paifa.ubikitouch.accessibility.floatingchat.chat.homeUnreadDemoThreadSummaries
import com.paifa.ubikitouch.accessibility.floatingchat.chat.homeUnreadThreadSummaries
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.floatingchat.media.normalizedRemoteImageUri
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrmFloatingChatBridgeTest {
    @Test
    fun floatingChatMapsJsonFileResBodyToFilePreviewCardData() {
        val content = """
            wxid_fc4l1owrdktn21:{
              "CdnFileType":7,
              "Des":"47.7KB, zip",
              "FileExt":"zip",
              "Title":"wukong-codex-migration-kit-10000x-logic.risk-key..zip",
              "TotalLen":"48891",
              "Type":74,
              "TypeStr":"[文件]"
            }
        """.trimIndent()
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account",
                    contacts = emptyList(),
                    messagesByConversation = mapOf(
                        "wxid_friend" to listOf(
                            ScrmChatMessage(messageId = 82L, messageType = 49, content = content)
                        )
                    )
                )
            ),
            accounts = listOf(ScrmWechatAccount("wxid_account", "Account", "device-1")),
            devices = listOf(device("device-1", "wxid_account", online = true)),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account"
        )

        val message = conversation.messages.single()
        assertEquals("FilePreview", message.type.name)
        assertEquals("wukong-codex-migration-kit-10000x-logic.risk-key..zip", message.text)
        assertEquals("wukong-codex-migration-kit-10000x-logic.risk-key..zip", message.fileName)
        assertEquals("47.7KB, zip", message.fileSizeLabel)
        assertEquals("Zip", message.fileFormat?.name)
    }

    @Test
    fun floatingChatMapsFileResBodyEvenWhenOuterTypeIsNormalized() {
        val content = """
            {"CdnFileType":7,"Des":"47.7KB, zip","FileExt":"zip","Title":"archive.zip","Type":74}
        """.trimIndent()
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account",
                    contacts = emptyList(),
                    messagesByConversation = mapOf(
                        "wxid_friend" to listOf(
                            ScrmChatMessage(messageId = 83L, messageType = 6, content = content)
                        )
                    )
                )
            ),
            accounts = listOf(ScrmWechatAccount("wxid_account", "Account", "device-1")),
            devices = listOf(device("device-1", "wxid_account", online = true)),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account"
        )

        assertEquals("FilePreview", conversation.messages.single().type.name)
        assertEquals("archive.zip", conversation.messages.single().fileName)
    }

    @Test
    fun floatingChatMapsFileResBodyWhenPayloadIsStoredInExtension() {
        val payload = """{"CdnFileType":7,"Des":"12 KB, pdf","FileExt":"pdf","Title":"guide.pdf","Type":74}"""
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account",
                    contacts = emptyList(),
                    messagesByConversation = mapOf(
                        "wxid_friend" to listOf(
                            ScrmChatMessage(
                                messageId = 84L,
                                messageType = 49,
                                content = "",
                                extensions = listOf(ScrmChatExtension("resBody", payload))
                            )
                        )
                    )
                )
            ),
            accounts = listOf(ScrmWechatAccount("wxid_account", "Account", "device-1")),
            devices = listOf(device("device-1", "wxid_account", online = true)),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account"
        )

        assertEquals("FilePreview", conversation.messages.single().type.name)
        assertEquals("guide.pdf", conversation.messages.single().fileName)
    }

    @Test
    fun floatingChatMapsWeComEnterpriseInviteToEnterpriseInviteCard() {
        val content = """
            {"Des":"一起来使用属于“青羽维龙（深圳）文化科技有限公司”自己的微信，开启全新办公体验吧。","Source":"企业微信","Title":"邀请你加入“青羽维龙（深圳）文化科技有限公司”","Type":5,"TypeStr":"[链接]","Url":"https://work.weixin.qq.com/wework_admin/join?vcode=invite"}
        """.trimIndent()
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account",
                    contacts = emptyList(),
                    messagesByConversation = mapOf(
                        "wxid_friend" to listOf(
                            ScrmChatMessage(messageId = 85L, messageType = 49, content = content)
                        )
                    )
                )
            ),
            accounts = listOf(ScrmWechatAccount("wxid_account", "Account", "device-1")),
            devices = listOf(device("device-1", "wxid_account", online = true)),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account"
        )

        val message = conversation.messages.single()
        assertEquals("EnterpriseInvite", message.type.name)
        assertEquals("邀请你加入“青羽维龙（深圳）文化科技有限公司”", message.text)
        assertEquals("一起来使用属于“青羽维龙（深圳）文化科技有限公司”自己的微信，开启全新办公体验吧。", message.detail)
        assertEquals("https://work.weixin.qq.com/wework_admin/join?vcode=invite", message.resourceUrl)
    }

    @Test
    fun floatingChatMapsOfficialAccountArticleItems() {
        val content = """
            {
              "appMessageItems": [
                {
                  "bannerImageUrl": "https://mmbiz.qpic.cn/banner-1.jpg",
                  "description": "",
                  "detailUrl": "http://mp.weixin.qq.com/s?idx=1",
                  "imageUrl": "https://mmbiz.qpic.cn/avatar-1.jpg",
                  "itemType": 0,
                  "timestamp": 1786677814,
                  "title": "暗战！爆了！彻底捂不住了！"
                },
                {
                  "bannerImageUrl": "https://mmbiz.qpic.cn/banner-2.jpg",
                  "description": "",
                  "detailUrl": "http://mp.weixin.qq.com/s?idx=2",
                  "imageUrl": "https://mmbiz.qpic.cn/avatar-2.jpg",
                  "itemType": 0,
                  "timestamp": 1786677814,
                  "title": "日本广岛突发爆炸事故"
                }
              ],
              "appMessageType": 20,
              "senderNickname": "血饮"
            }
        """.trimIndent()
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account",
                    contacts = emptyList(),
                    messagesByConversation = mapOf(
                        "wxid_friend" to listOf(
                            ScrmChatMessage(
                                messageId = 81L,
                                messageType = 49,
                                content = content
                            )
                        )
                    )
                )
            ),
            accounts = listOf(ScrmWechatAccount("wxid_account", "Account", "device-1")),
            devices = listOf(device("device-1", "wxid_account", online = true)),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account"
        )

        val message = conversation.messages.single()
        assertEquals("Article", message.type.name)
        assertEquals("暗战！爆了！彻底捂不住了！ 等 2 篇", message.text)
        assertEquals("血饮", message.appName)
        assertEquals("https://mmbiz.qpic.cn/banner-1.jpg", message.thumbnailUrl)
        assertEquals("http://mp.weixin.qq.com/s?idx=1", message.resourceUrl)

        val itemsGetter = message::class.java.methods.singleOrNull { it.name == "getArticleItems" }
        assertNotNull("FloatingChatMessage must expose structured articleItems", itemsGetter)
        val items = itemsGetter!!.invoke(message) as List<*>
        assertEquals(2, items.size)
        assertEquals("暗战！爆了！彻底捂不住了！", articleProperty(items[0], "getTitle"))
        assertEquals("https://mmbiz.qpic.cn/banner-1.jpg", articleProperty(items[0], "getBannerImageUrl"))
        assertEquals("https://mmbiz.qpic.cn/avatar-1.jpg", articleProperty(items[0], "getImageUrl"))
        assertEquals(1786677814L, articleProperty(items[0], "getTimestampSeconds"))
        assertEquals("日本广岛突发爆炸事故", articleProperty(items[1], "getTitle"))
    }

    @Test
    fun floatingChatMapsFinderAppMessagesAndTrustedUsernameMetadata() {
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account",
                    contacts = emptyList(),
                    messagesByConversation = mapOf(
                        "wxid_friend" to listOf(
                            ScrmChatMessage(
                                messageId = 1,
                                messageType = 754974769,
                                content = "{\"sphUserName\":\"sph_video\"}"
                            ),
                            ScrmChatMessage(
                                messageId = 2,
                                messageType = 973078577,
                                content = "{\"title\":\"直播\"}",
                                extensions = listOf(ScrmChatExtension("sphUserName", "sph_live"))
                            ),
                            ScrmChatMessage(
                                messageId = 3,
                                messageType = 754974769,
                                content = "{\"title\":\"sph_not_metadata\"}"
                            )
                        )
                    )
                )
            ),
            accounts = listOf(ScrmWechatAccount("wxid_account", "Account", "device-1")),
            devices = listOf(device("device-1", "wxid_account", online = true)),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account"
        )

        assertEquals(
            listOf("ChannelsVideo", "ChannelsLive", "ChannelsVideo"),
            conversation.messages.map { it.type.name }
        )
        assertEquals("sph_video", conversation.messages[0].finderUserName)
        assertEquals("sph_live", conversation.messages[1].finderUserName)
        assertNull(conversation.messages[2].finderUserName)
    }

    @Test
    fun floatingChatPreservesRemoteMessageTypesInsteadOfDowngradingThemToText() {
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account",
                    contacts = emptyList(),
                    messagesByConversation = mapOf(
                        "wxid_friend" to listOf(
                            ScrmChatMessage(messageId = 1, messageType = 3, content = ""),
                            ScrmChatMessage(messageId = 2, messageType = 34, content = ""),
                            ScrmChatMessage(messageId = 3, messageType = 47, content = ""),
                            ScrmChatMessage(messageId = 4, messageType = 48, content = ""),
                            ScrmChatMessage(messageId = 5, messageType = 50, content = "")
                        )
                    )
                )
            ),
            accounts = listOf(ScrmWechatAccount("wxid_account", "Account", "device-1")),
            devices = listOf(device("device-1", "wxid_account", online = true)),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account"
        )

        assertEquals(
            listOf("ImageThumbnail", "Voice", "StickerGif", "Location", "VoiceCall"),
            conversation.messages.map { it.type.name }
        )
        assertEquals(listOf("[图片]", "[语音]", "[表情]", "[位置]", "[语音通话]"), conversation.messages.map { it.text })
    }

    /**
     * 测试流程：发送收藏表情接口按协议使用 Emoji=14，下发的 resBody 仍为 Md5/Thumb/Size。
     * 必须进入图片表情渲染器，并把 Thumb 同时作为缩略图和可下载资源地址。
     */
    @Test
    fun floatingChatMapsEmojiMessageType14ToStickerMedia() {
        val thumb = "http://vweixinf.tc.qq.com/110/20401/stodownload?m=emoji14&filekey=sticker"
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account",
                    contacts = emptyList(),
                    messagesByConversation = mapOf(
                        "wxid_friend" to listOf(
                            ScrmChatMessage(
                                messageId = 6,
                                messageType = 14,
                                content = "{\"Md5\":\"emoji-md5\",\"Thumb\":\"$thumb\",\"Size\":23770}"
                            )
                        )
                    )
                )
            ),
            accounts = listOf(ScrmWechatAccount("wxid_account", "Account", "device-1")),
            devices = listOf(device("device-1", "wxid_account", online = true)),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account"
        )

        val message = conversation.messages.single()
        assertEquals("StickerGif", message.type.name)
        assertEquals("[表情]", message.text)
        assertEquals(thumb, message.thumbnailUrl)
        assertEquals(thumb, message.resourceUrl)
    }

    @Test
    fun floatingChatExtractsStickerThumbForChatRenderingButKeepsPreviewTextAsPlaceholder() {
        val thumb = "http://vweixinf.tc.qq.com/sticker/thumb.png"
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account",
                    contacts = emptyList(),
                    messagesByConversation = mapOf(
                        "wxid_friend" to listOf(
                            ScrmChatMessage(
                                messageId = 7,
                                messageType = 47,
                                content = "{\"Md5\":\"abc\",\"Thumb\":\"$thumb\",\"Size\":23770}"
                            )
                        )
                    )
                )
            ),
            accounts = listOf(ScrmWechatAccount("wxid_account", "Account", "device-1")),
            devices = listOf(device("device-1", "wxid_account", online = true)),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account"
        )

        assertEquals("[表情]", conversation.messages.single().text)
        assertEquals(thumb, conversation.messages.single().thumbnailUrl)
    }

    /**
     * 测试流程：从历史消息接口返回的 resBody 中读取图片表情包，检查真实 Thumb 下载地址
     * 同时进入缩略图和媒体资源字段，聊天气泡、全屏预览和媒体操作均可复用该地址。
     */
    @Test
    fun floatingChatMapsWeChatStickerResBodyThumbAsRenderableMedia() {
        val thumb = "http://vweixinf.tc.qq.com/110/20401/stodownload?m=1c3c326f0d065a84dd2c7ac9638910bb&filekey=3043020101042f302d02016e040253480420316333633332366630643036356138346464326337616339363338393130626202025cda040d00000004627466730000000131&hy=SH&storeid=323032323033333030383431343130303065346437643833346434336661343736366234306230303030303036653031303034666231&ef=1&bizid=1022"
        val output = ByteArrayOutputStream()
        val originalOut = System.out
        val conversation = try {
            System.setOut(PrintStream(output, true, Charsets.UTF_8.name()))
            scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account",
                    contacts = emptyList(),
                    messagesByConversation = mapOf(
                        "wxid_friend" to listOf(
                            ScrmChatMessage(
                                messageId = 9,
                                messageType = 47,
                                content = "{\"Md5\":\"1c3c326f0d065a84dd2c7ac9638910bb\",\"Thumb\":\"$thumb\",\"Size\":23770}"
                            )
                        )
                    )
                )
            ),
            accounts = listOf(ScrmWechatAccount("wxid_account", "Account", "device-1")),
            devices = listOf(device("device-1", "wxid_account", online = true)),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account"
            )
        } finally {
            System.setOut(originalOut)
        }

        val message = conversation.messages.single()
        assertEquals("StickerGif", message.type.name)
        assertEquals("[表情]", message.text)
        assertEquals(thumb, message.thumbnailUrl)
        assertEquals(thumb, message.resourceUrl)
        val debugOutput = output.toString(Charsets.UTF_8.name())
        assertTrue(debugOutput.contains("vweixinf.tc.qq.com"))
        assertTrue(debugOutput.contains("messageType=47"))
        assertTrue(debugOutput.contains("resolvedType=StickerGif"))
        assertTrue(debugOutput.contains("thumbnailUrl=$thumb"))
    }

    /**
     * 测试流程：历史接口遗漏外层 messageType 时，仍返回图片表情包的 Md5/Thumb/Size body。
     * 聊天界面必须识别为 StickerGif，不能把原始 JSON 当成普通文本气泡。
     */
    @Test
    fun floatingChatInfersStickerGifFromMd5AndThumbWhenOuterMessageTypeIsMissing() {
        val thumb = "http://vweixinf.tc.qq.com/sticker/fallback-thumb.png"
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account",
                    contacts = emptyList(),
                    messagesByConversation = mapOf(
                        "wxid_friend" to listOf(
                            ScrmChatMessage(
                                messageId = 10,
                                messageType = 0,
                                content = "{\"Md5\":\"1c3c326f0d065a84dd2c7ac9638910bb\",\"Thumb\":\"$thumb\",\"Size\":23770}"
                            )
                        )
                    )
                )
            ),
            accounts = listOf(ScrmWechatAccount("wxid_account", "Account", "device-1")),
            devices = listOf(device("device-1", "wxid_account", online = true)),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account"
        )

        val message = conversation.messages.single()
        assertEquals("StickerGif", message.type.name)
        assertEquals("[表情]", message.text)
        assertEquals(thumb, message.thumbnailUrl)
        assertEquals(thumb, message.resourceUrl)
    }

    /**
     * 测试流程：部分历史消息把图片表情包错误标记为普通文本 messageType=1，
     * 但 resBody 仍含有 Md5 和 Thumb。聊天界面必须优先按表情包渲染，不能显示原始 JSON。
     */
    @Test
    fun floatingChatInfersStickerGifFromMd5AndThumbWhenOuterMessageTypeIsText() {
        val thumb = "http://vweixinf.tc.qq.com/sticker/text-type-thumb.png"
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account",
                    contacts = emptyList(),
                    messagesByConversation = mapOf(
                        "wxid_friend" to listOf(
                            ScrmChatMessage(
                                messageId = 12,
                                messageType = 1,
                                content = "{\"Md5\":\"1c3c326f0d065a84dd2c7ac9638910bb\",\"Thumb\":\"$thumb\",\"Size\":23770}"
                            )
                        )
                    )
                )
            ),
            accounts = listOf(ScrmWechatAccount("wxid_account", "Account", "device-1")),
            devices = listOf(device("device-1", "wxid_account", online = true)),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account"
        )

        val message = conversation.messages.single()
        assertEquals("StickerGif", message.type.name)
        assertEquals("[表情]", message.text)
        assertEquals(thumb, message.thumbnailUrl)
        assertEquals(thumb, message.resourceUrl)
    }

    @Test
    fun floatingChatKeepsExplicitImageTypeWhenItsBodyAlsoContainsMd5AndThumb() {
        val thumb = "http://vweixinf.tc.qq.com/image/thumbnail.png"
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account",
                    contacts = emptyList(),
                    messagesByConversation = mapOf(
                        "wxid_friend" to listOf(
                            ScrmChatMessage(
                                messageId = 11,
                                messageType = 3,
                                content = "{\"Md5\":\"image-md5\",\"Thumb\":\"$thumb\",\"Size\":23770}"
                            )
                        )
                    )
                )
            ),
            accounts = listOf(ScrmWechatAccount("wxid_account", "Account", "device-1")),
            devices = listOf(device("device-1", "wxid_account", online = true)),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account"
        )

        assertEquals("ImageThumbnail", conversation.messages.single().type.name)
    }

    /**
     * 测试流程：复制消息得到的 resBody 可能是 JSON 后面拼接同一个 Thumb URL。
     * 即使存在尾部 URL，桥接层仍需读取 JSON 内的 Thumb 并按图片表情包渲染。
     */
    @Test
    fun floatingChatParsesStickerResBodyWhenCopiedBodyHasTrailingThumbUrl() {
        val thumb = "http://vweixinf.tc.qq.com/sticker/copied-body-thumb.png"
        val copiedBody = """{"Md5":"sticker-md5","Thumb":"$thumb","Size":23770} $thumb"""
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account",
                    contacts = emptyList(),
                    messagesByConversation = mapOf(
                        "wxid_friend" to listOf(
                            ScrmChatMessage(
                                messageId = 13,
                                messageType = 1,
                                content = copiedBody
                            )
                        )
                    )
                )
            ),
            accounts = listOf(ScrmWechatAccount("wxid_account", "Account", "device-1")),
            devices = listOf(device("device-1", "wxid_account", online = true)),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account"
        )

        val message = conversation.messages.single()
        assertEquals("StickerGif", message.type.name)
        assertEquals("[表情]", message.text)
        assertEquals(thumb, message.thumbnailUrl)
        assertEquals(thumb, message.resourceUrl)
    }

    @Test
    fun floatingChatIgnoresJsonArraysWhileExtractingStickerThumb() {
        val thumb = "http://vweixinf.tc.qq.com/sticker/thumb-array-safe.png"
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account",
                    contacts = emptyList(),
                    messagesByConversation = mapOf(
                        "wxid_friend" to listOf(
                            ScrmChatMessage(
                                messageId = 8,
                                messageType = 47,
                                content = "{\"emoticonMd5\":\"abc\",\"items\":[1,2,3],\"Thumb\":\"$thumb\"}"
                            )
                        )
                    )
                )
            ),
            accounts = listOf(ScrmWechatAccount("wxid_account", "Account", "device-1")),
            devices = listOf(device("device-1", "wxid_account", online = true)),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account"
        )

        assertEquals(thumb, conversation.messages.single().thumbnailUrl)
    }

    @Test
    fun floatingChatMapsReadOnlyHistoryMessagesToConversationThreads() {
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = listOf(
                ScrmContact(id = 1, wxid = "wxid_friend", nickname = "Friend")
            ),
            accountConversations = listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account",
                    contacts = listOf(
                        ScrmContact(id = 1, wxid = "wxid_friend", nickname = "Friend")
                    ),
                    messagesByConversation = mapOf(
                        "wxid_friend" to listOf(
                            ScrmChatMessage(
                                messageId = 101,
                                senderWxid = "wxid_friend",
                                receiverWxid = "wxid_account",
                                content = "来自真实历史",
                                createdAt = "2026-08-10T10:05:00Z"
                            )
                        )
                    )
                )
            ),
            accounts = listOf(ScrmWechatAccount("wxid_account", "Account", "device-1")),
            devices = listOf(device("device-1", "wxid_account", online = true)),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account"
        )

        assertEquals(1, conversation.messages.size)
        assertEquals("来自真实历史", conversation.messages.single().text)
        assertEquals(
            scrmFloatingScopedThreadId(
                scrmFloatingAccountId("device-1", "wxid_account"),
                scrmFloatingContactId("wxid_friend")
            ),
            conversation.messages.single().threadContactId
        )
        assertFalse(conversation.messages.single().fromMe)
    }

    /**
     * 测试流程：同步一条同时含旧 content 与新 voiceText 的语音消息，检查气泡详情。
     * 转文字完成后必须展示服务端 voiceText，不能继续显示旧 content。
     */
    @Test
    fun floatingChatVoiceDetailPrefersTranscribedVoiceText() {
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account",
                    contacts = emptyList(),
                    messagesByConversation = mapOf(
                        "wxid_friend" to listOf(
                            ScrmChatMessage(
                                messageId = 71L,
                                messageType = 34,
                                content = "{\"text\":\"旧语音内容\"}",
                                voiceText = Json.parseToJsonElement("{\"text\":\"后端转写文字\"}")
                            )
                        )
                    )
                )
            ),
            accounts = listOf(ScrmWechatAccount("wxid_account", "Account", "device-1")),
            devices = listOf(device("device-1", "wxid_account", online = true)),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account"
        )

        assertEquals("后端转写文字", conversation.messages.single().detail)
    }

    @Test
    fun floatingChatPreservesClientMessageIdForOutgoingRevokeMatching() {
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account",
                    contacts = emptyList(),
                    messagesByConversation = mapOf(
                        "wxid_friend" to listOf(
                            ScrmChatMessage(
                                messageId = 81L,
                                senderWxid = "wxid_account",
                                receiverWxid = "wxid_friend",
                                direction = 1,
                                content = "待撤销消息",
                                clientMessageId = "request-81"
                            )
                        )
                    )
                )
            ),
            accounts = listOf(ScrmWechatAccount("wxid_account", "Account", "device-1")),
            devices = listOf(device("device-1", "wxid_account", online = true)),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account"
        )

        assertEquals("request-81", conversation.messages.single().clientRequestId)
    }

    @Test
    fun floatingChatMapsAvatarAliasesForContactsGroupsMembersAndAccounts() {
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account_1",
                    contacts = listOf(
                        ScrmContact(
                            id = 1,
                            wxid = "wxid_contact_1",
                            nickname = "Contact",
                            headImgUrl = "https://cdn.example.com/contact.png"
                        )
                    ),
                    chatRooms = listOf(
                        ScrmChatRoom(
                            id = 2,
                            chatRoomId = "room_1@chatroom",
                            name = "Room",
                            avatarUrl = "https://cdn.example.com/room.png"
                        )
                    ),
                    chatRoomMembers = mapOf(
                        "room_1@chatroom" to listOf(
                            ScrmChatRoomMember(
                                id = 3,
                                chatRoomId = "room_1@chatroom",
                                memberWxid = "wxid_member_1",
                                displayName = "Member",
                                headimgurl = "https://cdn.example.com/member.png"
                            )
                        )
                    )
                )
            ),
            accounts = listOf(
                ScrmWechatAccount(
                    wxid = "wxid_account_1",
                    nickname = "Account",
                    clientUuid = "device-1",
                    imageUrl = "https://cdn.example.com/account.png"
                )
            ),
            devices = listOf(device("device-1", "wxid_account_1", online = true)),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account_1"
        )

        assertEquals("https://cdn.example.com/contact.png", conversation.contacts.single().avatarUrl)
        assertEquals("https://cdn.example.com/room.png", conversation.groupContacts.single().avatarUrl)
        assertEquals("https://cdn.example.com/member.png", conversation.groupContacts.single().groupMemberAvatarUrls.single())
        assertEquals("https://cdn.example.com/account.png", conversation.accountContacts.single().avatarUrl)
        assertEquals("https://mmbiz.qpic.cn/protocol-relative.png", normalizedRemoteImageUri("//mmbiz.qpic.cn/protocol-relative.png"))
    }

    @Test
    fun apiContactsAndWechatAccountsReplacePrototypeConversationLists() {
        val selectedAccountId = scrmFloatingAccountId(
            deviceUuid = "device-1",
            weChatId = "wxid_account_1"
        )
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = listOf(
                ScrmContact(
                    id = 1,
                    ownerWxid = "wxid_account_1",
                    wxid = "wxid_friend_1",
                    nickname = "Alice",
                    remarks = "VIP Alice",
                    avatar = "https://cdn.example.com/alice.png",
                    isFriend = 1
                ),
                ScrmContact(
                    id = 2,
                    ownerWxid = "wxid_account_1",
                    wxid = "wxid_friend_2",
                    nickname = "Bob",
                    isFriend = 1
                )
            ),
            accounts = listOf(
                ScrmWechatAccount(
                    wxid = "wxid_account_1",
                    nickname = "Main WeChat",
                    clientUuid = "device-1",
                    accountStatus = 1
                ),
                ScrmWechatAccount(
                    wxid = "wxid_account_2",
                    nickname = "Backup WeChat",
                    clientUuid = "device-2",
                    accountStatus = 1
                )
            ),
            devices = listOf(
                device(uuid = "device-1", weChatId = "wxid_account_1", online = true),
                device(uuid = "device-2", weChatId = "wxid_account_2", online = false)
            ),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account_1"
        )

        assertEquals(
            listOf(
                scrmFloatingScopedThreadId(selectedAccountId, scrmFloatingContactId("wxid_friend_1")),
                scrmFloatingScopedThreadId(selectedAccountId, scrmFloatingContactId("wxid_friend_2"))
            ),
            conversation.contacts.map { contact -> contact.id }
        )
        assertEquals(listOf("VIP Alice", "Bob"), conversation.contacts.map { contact -> contact.name })
        assertEquals("https://cdn.example.com/alice.png", conversation.contacts.first().avatarUrl)
        assertFalse(conversation.contacts.any { contact -> contact.id == "li-si" })
        assertEquals(emptyList<Any>(), conversation.groupContacts)
        assertEquals(emptyList<Any>(), conversation.messages)
        assertEquals(30, conversation.homeUnreadDemoMessages.size)
        assertEquals(true, conversation.homeUnreadDemoMessages.all { message ->
            message.type == com.paifa.ubikitouch.core.model.FloatingChatMessageType.Text && !message.fromMe
        })
        assertEquals(true, conversation.homeUnreadDemoMessages.any { message -> message.threadContactId == conversation.contacts.first().id })

        val accountIds = conversation.accountContacts.map { account -> account.id }
        assertEquals(
            listOf(
                scrmFloatingAccountId(deviceUuid = "device-1", weChatId = "wxid_account_1"),
                scrmFloatingAccountId(deviceUuid = "device-2", weChatId = "wxid_account_2")
            ),
            accountIds
        )
        assertEquals(listOf("Main WeChat", "Backup WeChat"), conversation.accountContacts.map { account -> account.name })
        assertEquals(true, conversation.accountContacts.first().selected)
        assertEquals(false, conversation.accountContacts[1].selected)

        assertEquals(
            ScrmFloatingAccountRoute(deviceUuid = "device-2", weChatId = "wxid_account_2"),
            scrmFloatingAccountRouteForContactId(conversation.accountContacts[1].id)
        )
        assertEquals(
            "wxid_friend_1",
            scrmFloatingContactConversationId(conversation.contacts.first().id)
        )
    }

    @Test
    fun accountConversationDataKeepsAllContactsAndChatroomsScopedToEachAccount() {
        val accountOne = scrmFloatingAccountId("device-1", "wxid_account_1")
        val accountTwo = scrmFloatingAccountId("device-2", "wxid_account_2")
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account_1",
                    contacts = (1..8).map { index ->
                        ScrmContact(
                            id = index,
                            ownerWxid = "wxid_account_1",
                            wxid = "wxid_a_$index",
                            nickname = "A$index",
                            isFriend = 1
                        )
                    },
                    chatRooms = listOf(
                        ScrmChatRoom(
                            id = 1,
                            ownerWxid = "wxid_account_1",
                            chatRoomId = "room_a@chatroom",
                            name = "Account A Room",
                            avatar = "http://mmbiz.qpic.cn/room-a.png",
                            memberCount = 12
                        )
                    ),
                    chatRoomMembers = mapOf(
                        "room_a@chatroom" to (1..10).map { index ->
                            ScrmChatRoomMember(
                                id = index,
                                chatRoomId = "room_a@chatroom",
                                memberWxid = if (index == 1) "wxid_account_1" else "wxid_member_$index",
                                displayName = "Member $index",
                                avatar = "http://mmbiz.qpic.cn/member-$index.png"
                            )
                        }
                    )
                ),
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-2",
                    weChatId = "wxid_account_2",
                    contacts = (1..3).map { index ->
                        ScrmContact(
                            id = index + 100,
                            ownerWxid = "wxid_account_2",
                            wxid = "wxid_b_$index",
                            nickname = "B$index",
                            isFriend = 1
                        )
                    }
                )
            ),
            accounts = listOf(
                ScrmWechatAccount("wxid_account_1", "Account 1", "device-1", accountStatus = 1),
                ScrmWechatAccount("wxid_account_2", "Account 2", "device-2", accountStatus = 1)
            ),
            devices = listOf(
                device("device-1", "wxid_account_1", online = true),
                device("device-2", "wxid_account_2", online = true)
            ),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account_1"
        )

        assertEquals(11, conversation.contacts.size)
        assertEquals(1, conversation.groupContacts.size)
        assertEquals(8, conversation.contacts.count { contact -> contact.id.startsWith("${accountOne}__") })
        assertEquals(3, conversation.contacts.count { contact -> contact.id.startsWith("${accountTwo}__") })
        assertEquals(
            scrmFloatingScopedThreadId(accountOne, scrmFloatingGroupId("room_a@chatroom")),
            conversation.groupContacts.single().id
        )
        assertEquals("https://mmbiz.qpic.cn/room-a.png", conversation.groupContacts.single().avatarUrl)
        assertEquals(
            (1..9).map { index -> "https://mmbiz.qpic.cn/member-$index.png" },
            conversation.groupContacts.single().groupMemberAvatarUrls
        )
        assertEquals("https://mmbiz.qpic.cn/member-1.png", conversation.accountContacts.first().avatarUrl)
        assertEquals("room_a@chatroom", scrmFloatingContactConversationId(conversation.groupContacts.single().id))
        assertEquals(30, conversation.homeUnreadDemoMessages.size)
        assertEquals(true, conversation.homeUnreadDemoMessages.any { message -> message.threadContactId == conversation.groupContacts.single().id })
        val summaries = homeUnreadDemoThreadSummaries(conversation)
        assertEquals(30, summaries.size)
        assertEquals(true, summaries.any { summary -> summary.selection is ChatThreadSelection.Private })
        assertEquals(true, summaries.any { summary -> summary.selection is ChatThreadSelection.GroupChat })
    }

    @Test
    fun wechatAccountAvatarFieldOverridesDerivedMemberAvatarWhenPresent() {
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account_1",
                    contacts = listOf(
                        ScrmContact(
                            id = 1,
                            ownerWxid = "wxid_account_1",
                            wxid = "wxid_account_1",
                            nickname = "Self",
                            avatar = "http://mmbiz.qpic.cn/derived.png"
                        )
                    )
                )
            ),
            accounts = listOf(
                ScrmWechatAccount(
                    wxid = "wxid_account_1",
                    nickname = "Account 1",
                    clientUuid = "device-1",
                    avatar = "http://mmbiz.qpic.cn/account.png",
                    accountStatus = 1
                )
            ),
            devices = listOf(device("device-1", "wxid_account_1", online = true)),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account_1"
        )

        assertEquals("https://mmbiz.qpic.cn/account.png", conversation.accountContacts.single().avatarUrl)
    }

    @Test
    fun duplicateRemoteContactsAndRoomsAreMergedBeforeRenderingRails() {
        val accountId = scrmFloatingAccountId("device-1", "wxid_account_1")
        val contactId = scrmFloatingScopedThreadId(accountId, scrmFloatingContactId("qq-13462583081"))
        val groupId = scrmFloatingScopedThreadId(accountId, scrmFloatingGroupId("room_1@chatroom"))
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-1",
                    weChatId = "wxid_account_1",
                    contacts = listOf(
                        ScrmContact(
                            id = 1,
                            ownerWxid = "wxid_account_1",
                            friendNo = "qq-13462583081",
                            nickname = "No Avatar",
                            isFriend = 1
                        ),
                        ScrmContact(
                            id = 2,
                            ownerWxid = "wxid_account_1",
                            friendNo = "qq-13462583081",
                            nickname = "Has Avatar",
                            avatar = "http://mmbiz.qpic.cn/contact.png",
                            isFriend = 1
                        )
                    ),
                    chatRooms = listOf(
                        ScrmChatRoom(
                            id = 1,
                            ownerWxid = "wxid_account_1",
                            chatRoomId = "room_1@chatroom",
                            name = "Room",
                            memberCount = 9
                        ),
                        ScrmChatRoom(
                            id = 2,
                            ownerWxid = "wxid_account_1",
                            chatRoomId = "room_1@chatroom",
                            name = "Room With Avatar",
                            avatar = "http://mmbiz.qpic.cn/room.png",
                            memberCount = 9
                        )
                    )
                )
            ),
            accounts = listOf(ScrmWechatAccount("wxid_account_1", "Account 1", "device-1", accountStatus = 1)),
            devices = listOf(device("device-1", "wxid_account_1", online = true)),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_account_1"
        )

        assertEquals(listOf(contactId), conversation.contacts.map { contact -> contact.id })
        assertEquals("https://mmbiz.qpic.cn/contact.png", conversation.contacts.single().avatarUrl)
        assertEquals(listOf(groupId), conversation.groupContacts.map { group -> group.id })
        assertEquals("https://mmbiz.qpic.cn/room.png", conversation.groupContacts.single().avatarUrl)
    }

    @Test
    fun selectedSessionAccountIsIncludedWhenAccountListDoesNotContainIt() {
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accounts = emptyList(),
            devices = emptyList(),
            selectedDeviceUuid = "device-selected",
            selectedWeChatId = "wxid_selected"
        )

        val onlyAccount = conversation.accountContacts.single()
        assertEquals(
            scrmFloatingAccountId(deviceUuid = "device-selected", weChatId = "wxid_selected"),
            onlyAccount.id
        )
        assertEquals(true, onlyAccount.selected)
        assertNotNull(scrmFloatingAccountRouteForContactId(onlyAccount.id))
    }

    @Test
    fun chatRoomMemberRoleMarksOwnerAndAdminForGroupManagement() {
        val conversation = scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = listOf(
                ScrmFloatingAccountConversation(
                    deviceUuid = "device-1",
                    weChatId = "wxid_owner",
                    contacts = emptyList(),
                    chatRooms = listOf(
                        ScrmChatRoom(
                            id = 1,
                            chatRoomId = "room_1@chatroom",
                            name = "Room",
                            memberCount = 3
                        )
                    ),
                    chatRoomMembers = mapOf(
                        "room_1@chatroom" to listOf(
                            ScrmChatRoomMember(
                                id = 1,
                                chatRoomId = "room_1@chatroom",
                                memberWxid = "wxid_owner",
                                memberRole = 1
                            ),
                            ScrmChatRoomMember(
                                id = 2,
                                chatRoomId = "room_1@chatroom",
                                memberWxid = "wxid_admin",
                                memberRole = 2
                            )
                        )
                    )
                )
            ),
            accounts = listOf(ScrmWechatAccount("wxid_owner", "Owner", "device-1", accountStatus = 1)),
            devices = listOf(device("device-1", "wxid_owner", online = true)),
            selectedDeviceUuid = "device-1",
            selectedWeChatId = "wxid_owner"
        )

        val members = conversation.groupContacts.single().groupMemberContacts
        assertEquals(true, members.first { it.name == "wxid_owner" }.groupMemberIsOwner)
        assertEquals(true, members.first { it.name == "wxid_admin" }.groupMemberIsAdmin)
    }

    @Test
    fun selectedFloatingAccountRouteOverridesSessionFallbackForRefresh() {
        val selectedAccountId = scrmFloatingAccountId(
            deviceUuid = "device-2",
            weChatId = "wxid_account_2"
        )

        val route = scrmFloatingAccountRouteForSelection(
            selectedAccountId = selectedAccountId,
            fallbackDeviceUuid = "device-1",
            fallbackWeChatId = "wxid_account_1"
        )

        assertEquals(ScrmFloatingAccountRoute("device-2", "wxid_account_2"), route)
    }

    @Test
    fun invalidFloatingAccountRouteFallsBackToSessionRouteForRefresh() {
        val route = scrmFloatingAccountRouteForSelection(
            selectedAccountId = "account-main",
            fallbackDeviceUuid = "device-1",
            fallbackWeChatId = "wxid_account_1"
        )

        assertEquals(ScrmFloatingAccountRoute("device-1", "wxid_account_1"), route)
    }

    private fun device(
        uuid: String,
        weChatId: String,
        online: Boolean
    ): ScrmDevice {
        return ScrmDevice(
            uuid = uuid,
            isOnline = online,
            status = if (online) 1 else 0,
            weChatId = weChatId,
            androidApi = 35,
            appVersionCode = 1,
            updatedAt = "2026-07-13T00:00:00Z"
        )
    }

    private fun articleProperty(item: Any?, getterName: String): Any? {
        requireNotNull(item)
        return item::class.java.methods.single { it.name == getterName }.invoke(item)
    }
}
