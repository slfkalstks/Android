package kc.ac.uc.clubplatform.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// 게시글 목록 응답
data class PostListResponse(
    val success: Boolean,
    val message: String,
    val posts: List<PostInfo>
)

// 게시글 정보 (목록용)
@Parcelize
data class PostInfo(
    val postId: Int,
    val title: String,
    val content: String,
    val authorName: String,
    val createdAt: String,
    val viewCount: Int,
    val commentCount: Int,
    val isNotice: Boolean = false
) : Parcelable

// 게시글 상세 응답
data class PostDetailResponse(
    val success: Boolean,
    val message: String,
    val post: PostDetail
)

// 게시글 상세 정보
@Parcelize
data class PostDetail(
    val postId: Int,
    val title: String,
    val content: String,
    val authorName: String,
    val createdAt: String,
    val updatedAt: String?,
    val viewCount: Int,
    val likeCount: Int,
    val isLiked: Boolean,
    val isScraped: Boolean,
    val isAnonymous: Boolean,
    val isNotice: Boolean? = null,
    val canEdit: Boolean,
    val canDelete: Boolean,
    val commentCount: Int = 0,
    val attachments: List<String>? = null // 🆕 첨부파일 URL 목록 추가
) : Parcelable

// 게시글 생성 요청
data class CreatePostRequest(
    val boardId: Int,
    val title: String,
    val content: String,
    val isAnonymous: Boolean,
    val isNotice: Boolean,
    val attachments: List<String>? = null
)

// 게시글 생성 응답
data class CreatePostResponse(
    val success: Boolean,
    val message: String,
    val postId: Int?,
    val createdAt: String?
)

// 게시글 수정 요청
data class UpdatePostRequest(
    val boardId: Int? = null,
    val title: String? = null,
    val content: String? = null,
    val isNotice: Boolean? = null,
    val attachments: List<String>? = null
)

// 게시글 수정 응답
data class UpdatePostResponse(
    val success: Boolean,
    val message: String,
    val updatedAt: String?
)

// 게시글 삭제 응답
data class DeletePostResponse(
    val success: Boolean,
    val message: String,
    val data: Any? = null
)

// 좋아요 응답
data class LikeResponse(
    val success: Boolean,
    val message: String,
    val isLiked: Boolean,
    val likeCount: Int
)

// 스크랩 응답
data class ScrapResponse(
    val success: Boolean,
    val message: String,
    val isScraped: Boolean
)

// 🆕 수정된 BEST/HOT 게시판 응답 (서버 응답 형식에 맞게 수정)
data class SpecialBoardResponse(
    val success: Boolean,
    val message: String,
    val posts: List<PostInfo>
)

// 댓글 정보
@Parcelize
data class CommentInfo(
    val commentId: Int,
    val content: String,
    val authorName: String,
    val createdAt: String,
    val likeCount: Int,
    val isLiked: Boolean,
    val isAnonymous: Boolean,
    val parentId: Int?, // 대댓글인 경우 부모 댓글 ID
    val canEdit: Boolean,
    val canDelete: Boolean
) : Parcelable

// 댓글 목록 응답
data class CommentListResponse(
    val success: Boolean,
    val message: String,
    val comments: List<CommentInfo>
)

// 댓글 작성 요청
data class CreateCommentRequest(
    val content: String,
    val isAnonymous: Boolean,
    val parentId: Int? = null
)

// 댓글 작성 응답
data class CreateCommentResponse(
    val success: Boolean,
    val message: String,
    val commentId: Int?,
    val createdAt: String?
)

// 댓글 수정 요청
data class UpdateCommentRequest(
    val content: String
)

// 댓글 수정 응답
data class UpdateCommentResponse(
    val success: Boolean,
    val message: String,
    val updatedAt: String?
)

// 댓글 삭제 응답
data class DeleteCommentResponse(
    val success: Boolean,
    val message: String
)

// 댓글 좋아요 응답
data class CommentLikeResponse(
    val success: Boolean,
    val message: String,
    val isLiked: Boolean,
    val likeCount: Int
)

// 🆕 파일 업로드 관련 모델
data class FileUploadResponse(
    val success: Boolean,
    val message: String,
    val files: List<UploadedFileInfo>
)

data class UploadedFileInfo(
    val fileName: String,
    val originalName: String,
    val fileUrl: String,
    val fileSize: Long,
    val contentType: String
)