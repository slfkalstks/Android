package kc.ac.uc.clubplatform.activity

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.noties.markwon.Markwon
import kc.ac.uc.clubplatform.R
import kc.ac.uc.clubplatform.adapters.CommentAdapter
import kc.ac.uc.clubplatform.adapters.PostAdapter
import kc.ac.uc.clubplatform.adapters.AttachedFileAdapter
import kc.ac.uc.clubplatform.api.ApiClient
import kc.ac.uc.clubplatform.databinding.ActivityBoardBinding
import kc.ac.uc.clubplatform.models.*
import kc.ac.uc.clubplatform.util.DateUtils
import kotlinx.coroutines.launch

class BoardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBoardBinding
    private lateinit var boardType: String
    private lateinit var boardName: String
    private var postId: Int? = null
    private var boardId: Int? = null
    private var clubId: Int = -1
    private var anonymousCounter = 0 // 익명 번호 카운터
    private var listCommentCount: Int = -1  // 목록에서 받은 댓글수
    private var listViewCount: Int = -1     // 목록에서 받은 조회수
    private var hasListData: Boolean = false // 목록 데이터 존재 여부
    private val anonymousMap = mutableMapOf<String, String>() // userId -> 익명번호 매핑
    private val comments = mutableListOf<CommentInfo>()
    private lateinit var commentsAdapter: CommentAdapter
    private val posts = mutableListOf<PostInfo>()
    private lateinit var postAdapter: PostAdapter
    private lateinit var markwon: Markwon
    private var currentPost: PostDetail? = null

    private val writePostLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // 새 게시글이 작성되었으면 목록 새로고침 및 결과 전달
            loadPostList()
            setResult(RESULT_OK)
        }
    }

    private val editPostLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // 게시글이 수정되었으면 상세 정보 새로고침 및 결과 전달
            postId?.let { loadPostDetail(it) }
            setResult(RESULT_OK)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBoardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Intent에서 데이터 가져오기
        boardType = intent.getStringExtra("board_type") ?: "general"
        boardName = intent.getStringExtra("board_name") ?: "게시판"
        boardId = intent.getIntExtra("board_id", -1)
        clubId = intent.getIntExtra("club_id", -1)
        postId = if (intent.hasExtra("post_id")) intent.getIntExtra("post_id", -1) else null

        // 🔧 추가: 목록에서 온 데이터 받기
        listCommentCount = intent.getIntExtra("list_comment_count", -1)
        listViewCount = intent.getIntExtra("list_view_count", -1)
        hasListData = intent.getBooleanExtra("has_list_data", false)

        Log.d("BoardActivity", "🚀 onCreate 시작")
        Log.d("BoardActivity", "boardType: $boardType, postId: $postId")
        Log.d("BoardActivity", "Intent 댓글수: $listCommentCount, Intent 조회수: $listViewCount")
        Log.d("BoardActivity", "hasListData: $hasListData")

        // Markwon 초기화
        markwon = Markwon.create(this)

        setupUI()

        // postId가 있으면 게시글 상세 보기, 없으면 게시글 목록 보기
        if (postId != null) {
            showPostDetail(postId!!)
        } else {
            showPostList()
        }
    }

    private fun setupUI() {
        binding.tvBoardName.text = boardName

        // 뒤로 가기 버튼
        binding.ivBack.setOnClickListener {
            finish()
        }

        // 더보기 메뉴
        binding.ivMore.setOnClickListener {
            showMoreMenu()
        }

        // 댓글 어댑터 초기화 - 실제 CommentAdapter 구조에 맞게 수정
        commentsAdapter = CommentAdapter(comments) { action, comment ->
            when (action) {
                "like" -> toggleCommentLike(comment.commentId)
                "reply" -> showReplyDialog(comment)
                "edit" -> showEditCommentDialog(comment)
                "delete" -> showDeleteCommentDialog(comment)
            }
        }

        binding.rvComments.layoutManager = LinearLayoutManager(this)
        binding.rvComments.adapter = commentsAdapter
    }

    private fun showPostDetail(postId: Int) {
        // 게시글 작성 버튼 숨기기
        binding.fabWritePost.hide()

        binding.rvPosts.visibility = View.GONE
        binding.layoutPostDetail.visibility = View.VISIBLE

        loadPostDetail(postId)
        setupPostDetailActions()

        // 게시판 이름이 기본값인 경우 정보 로드
        lifecycleScope.launch {
            loadBoardInfoIfNeeded()
        }
    }

    private fun loadPostDetail(postId: Int) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getPostDetail(postId)
                if (response.isSuccessful && response.body()?.success == true) {
                    val post = response.body()?.post
                    if (post != null) {
                        displayPostDetail(post)
                        currentPost = post
                        // 댓글 목록도 함께 로드
                        loadComments(postId)
                    }
                } else {
                    showToast("게시글을 불러올 수 없습니다")
                    finish()
                }
            } catch (e: Exception) {
                Log.e("BoardActivity", "Error loading post detail", e)
                showToast("게시글을 불러오는 중 오류가 발생했습니다")
                finish()
            }
        }
    }

    // 댓글 목록 조회
    private fun loadComments(postId: Int) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getComments(postId)
                if (response.isSuccessful && response.body()?.success == true) {
                    val commentList = response.body()?.comments ?: emptyList()
                    updateCommentList(commentList)
                }
            } catch (e: Exception) {
                Log.e("BoardActivity", "Error loading comments", e)
            }
        }
    }

    private fun updateCommentList(commentList: List<CommentInfo>) {
        Log.d("BoardActivity", "💬 댓글 목록 업데이트: ${commentList.size}개")
        comments.clear()
        comments.addAll(commentList)
        commentsAdapter.notifyDataSetChanged()

        // 댓글 수 업데이트 - 실제 댓글 개수로 표시
        val actualCommentCount = commentList.size
        binding.tvPostCommentCount.text = actualCommentCount.toString()
        Log.d("BoardActivity", "💬 실제 댓글수로 업데이트: $actualCommentCount")

        // currentPost의 commentCount도 업데이트
        currentPost = currentPost?.copy(commentCount = actualCommentCount)
    }

    private fun createComment(postId: Int, content: String) {
        lifecycleScope.launch {
            try {
                val request = CreateCommentRequest(
                    content = content,
                    isAnonymous = false,
                    parentId = null
                )

                val response = ApiClient.apiService.createComment(postId, request)
                if (response.isSuccessful && response.body()?.success == true) {
                    binding.etComment.setText("")
                    loadComments(postId) // 댓글 목록 새로고침
                    showToast("댓글이 작성되었습니다")
                } else {
                    val errorMessage = response.body()?.message ?: "댓글 작성에 실패했습니다"
                    showToast(errorMessage)
                }
            } catch (e: Exception) {
                Log.e("BoardActivity", "Error creating comment", e)
                showToast("댓글 작성 중 오류가 발생했습니다")
            }
        }
    }

    private fun showReplyDialog(parentComment: CommentInfo) {
        val editText = EditText(this).apply {
            hint = "대댓글을 입력하세요"
            setPadding(32, 32, 32, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("대댓글 작성")
            .setView(editText)
            .setPositiveButton("작성") { _, _ ->
                val content = editText.text.toString().trim()
                if (content.isNotEmpty()) {
                    currentPost?.let { post ->
                        createReply(post.postId, content, parentComment.commentId)
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun createReply(postId: Int, content: String, parentId: Int) {
        lifecycleScope.launch {
            try {
                val request = CreateCommentRequest(
                    content = content,
                    isAnonymous = false,
                    parentId = parentId
                )

                val response = ApiClient.apiService.createComment(postId, request)
                if (response.isSuccessful && response.body()?.success == true) {
                    loadComments(postId) // 댓글 목록 새로고침
                    showToast("대댓글이 작성되었습니다")
                } else {
                    val errorMessage = response.body()?.message ?: "대댓글 작성에 실패했습니다"
                    showToast(errorMessage)
                }
            } catch (e: Exception) {
                Log.e("BoardActivity", "Error creating reply", e)
                showToast("대댓글 작성 중 오류가 발생했습니다")
            }
        }
    }

    private fun showEditCommentDialog(comment: CommentInfo) {
        val editText = EditText(this).apply {
            setText(comment.content)
            setPadding(32, 32, 32, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("댓글 수정")
            .setView(editText)
            .setPositiveButton("수정") { _, _ ->
                val content = editText.text.toString().trim()
                if (content.isNotEmpty()) {
                    currentPost?.let { post ->
                        updateComment(post.postId, comment.commentId, content)
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun updateComment(postId: Int, commentId: Int, content: String) {
        lifecycleScope.launch {
            try {
                val request = UpdateCommentRequest(content = content)
                val response = ApiClient.apiService.updateComment(postId, commentId, request)
                if (response.isSuccessful && response.body()?.success == true) {
                    loadComments(postId) // 댓글 목록 새로고침
                    showToast("댓글이 수정되었습니다")
                } else {
                    val errorMessage = response.body()?.message ?: "댓글 수정에 실패했습니다"
                    showToast(errorMessage)
                }
            } catch (e: Exception) {
                Log.e("BoardActivity", "Error updating comment", e)
                showToast("댓글 수정 중 오류가 발생했습니다")
            }
        }
    }

    private fun showDeleteCommentDialog(comment: CommentInfo) {
        AlertDialog.Builder(this)
            .setTitle("댓글 삭제")
            .setMessage("댓글을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                currentPost?.let { post ->
                    deleteComment(post.postId, comment.commentId)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun deleteComment(postId: Int, commentId: Int) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.deleteComment(postId, commentId)
                if (response.isSuccessful && response.body()?.success == true) {
                    loadComments(postId) // 댓글 목록 새로고침
                    showToast("댓글이 삭제되었습니다")
                } else {
                    val errorMessage = response.body()?.message ?: "댓글 삭제에 실패했습니다"
                    showToast(errorMessage)
                }
            } catch (e: Exception) {
                Log.e("BoardActivity", "Error deleting comment", e)
                showToast("댓글 삭제 중 오류가 발생했습니다")
            }
        }
    }

    private fun toggleCommentLike(commentId: Int) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.likeComment(currentPost!!.postId, commentId)
                if (response.isSuccessful && response.body()?.success == true) {
                    val likeResponse = response.body()!!

                    // 댓글 목록에서 해당 댓글 찾아서 좋아요 상태 업데이트
                    val commentIndex = comments.indexOfFirst { it.commentId == commentId }
                    if (commentIndex != -1) {
                        comments[commentIndex] = comments[commentIndex].copy(
                            isLiked = likeResponse.isLiked,
                            likeCount = likeResponse.likeCount
                        )
                        commentsAdapter.notifyItemChanged(commentIndex)
                    }
                } else {
                    showToast("댓글 좋아요 처리 중 오류가 발생했습니다")
                }
            } catch (e: Exception) {
                Log.e("BoardActivity", "Error toggling comment like", e)
                showToast("댓글 좋아요 처리 중 오류가 발생했습니다")
            }
        }
    }

    private fun displayPostDetail(post: PostDetail) {
        Log.d("BoardActivity", "📝 displayPostDetail 시작")
        Log.d("BoardActivity", "서버 댓글수: ${post.commentCount}, Intent 댓글수: $listCommentCount")

        // 기본 정보 설정
        binding.tvPostTitle.text = post.title
        binding.tvPostAuthor.text = if (post.isAnonymous) "익명" else post.authorName
        binding.tvPostDate.text = DateUtils.formatHomeDate(post.createdAt)

        // 마크다운 렌더링
        markwon.setMarkdown(binding.tvPostContent, post.content)

        // 조회수 설정 (서버 값 우선, 증가된 값이므로)
        binding.tvPostViewCount.text = post.viewCount.toString()

        // 댓글수 설정 (Intent 값 우선 사용)
        if (hasListData && listCommentCount >= 0) {
            // 목록에서 온 데이터가 있으면 그것을 사용 (더 정확함)
            Log.d("BoardActivity", "✅ Intent 댓글수 사용: $listCommentCount")
            binding.tvPostCommentCount.text = listCommentCount.toString()
        } else {
            // 목록 데이터가 없으면 서버 값 사용
            Log.d("BoardActivity", "✅ 서버 댓글수 사용: ${post.commentCount}")
            binding.tvPostCommentCount.text = post.commentCount.toString()
        }

        // 🆕 첨부파일 표시
        displayAttachments(post.attachments)

        // 좋아요/스크랩 버튼 상태 업데이트
        updateLikeButton(post.isLiked, post.likeCount)
        updateScrapButton(post.isScraped)

        // 수정/삭제 권한에 따른 메뉴 표시
        binding.ivMore.visibility = if (post.canEdit || post.canDelete) View.VISIBLE else View.GONE

        Log.d("BoardActivity", "📝 displayPostDetail 완료")
        Log.d("BoardActivity", "최종 표시 - 조회수: ${binding.tvPostViewCount.text}, 댓글수: ${binding.tvPostCommentCount.text}")
        Log.d("BoardActivity", "날짜 형식: ${binding.tvPostDate.text}")
    }

    // 🆕 첨부파일 표시 메서드
    private fun displayAttachments(attachments: List<String>?) {
        if (attachments.isNullOrEmpty()) {
            binding.rvPostAttachments.visibility = View.GONE
            return
        }

        // 첨부파일 URL을 UploadedFileInfo 형태로 변환
        val attachmentInfos = attachments.map { url ->
            val fileName = url.substringAfterLast('/')
            val originalName = fileName.substringBefore('_', fileName) // UUID 제거 시도

            UploadedFileInfo(
                fileName = fileName,
                originalName = if (originalName.isBlank()) fileName else originalName,
                fileUrl = url,
                fileSize = 0, // 크기 정보 없음
                contentType = getContentTypeFromUrl(url)
            )
        }.toMutableList()

        // 첨부파일 어댑터 설정
        val attachmentAdapter = AttachedFileAdapter(
            files = attachmentInfos,
            onDeleteClick = { }, // 상세 화면에서는 삭제 불가
            onFileClick = { fileInfo ->
                handleAttachmentClick(fileInfo)
            },
            showDeleteButton = false // 🆕 상세 화면에서는 삭제 버튼 숨김
        )

        binding.rvPostAttachments.layoutManager = LinearLayoutManager(this)
        binding.rvPostAttachments.adapter = attachmentAdapter
        binding.rvPostAttachments.visibility = View.VISIBLE
    }

    // 🆕 URL에서 컨텐츠 타입 추출
    private fun getContentTypeFromUrl(url: String): String {
        val extension = url.substringAfterLast('.').lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    // 🆕 첨부파일 클릭 처리
    private fun handleAttachmentClick(fileInfo: UploadedFileInfo) {
        when {
            fileInfo.contentType.startsWith("image/") -> {
                // 이미지인 경우 이미지 뷰어로 보기
                val intent = Intent(this, ImageViewerActivity::class.java).apply {
                    putExtra("image_url", "${ApiClient.BASE_URL.trimEnd('/')}${fileInfo.fileUrl}")
                    putExtra("image_name", fileInfo.originalName)
                }
                startActivity(intent)
            }
            else -> {
                // 다른 파일들은 다운로드
                downloadAttachment(fileInfo)
            }
        }
    }

    // 🆕 첨부파일 다운로드
    private fun downloadAttachment(fileInfo: UploadedFileInfo) {
        try {
            val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val fileUrl = "${ApiClient.BASE_URL.trimEnd('/')}${fileInfo.fileUrl}"

            val request = DownloadManager.Request(Uri.parse(fileUrl)).apply {
                setTitle("파일 다운로드")
                setDescription("${fileInfo.originalName} 다운로드 중...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileInfo.originalName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            downloadManager.enqueue(request)
            showToast("다운로드를 시작합니다")

        } catch (e: Exception) {
            Log.e("BoardActivity", "Download failed", e)
            showToast("다운로드에 실패했습니다")
        }
    }

    private fun showMoreMenu() {
        val post = currentPost ?: return

        val popup = PopupMenu(this, binding.ivMore)
        if (post.canEdit) {
            popup.menu.add(0, 1, 0, "수정")
        }
        if (post.canDelete) {
            popup.menu.add(0, 2, 0, "삭제")
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> editPost(post)
                2 -> showDeleteConfirmDialog(post)
            }
            true
        }

        popup.show()
    }

    private fun editPost(post: PostDetail) {
        val intent = Intent(this, WritePostActivity::class.java).apply {
            putExtra("board_type", boardType)
            putExtra("board_name", boardName)
            putExtra("board_id", boardId)
            putExtra("club_id", clubId)
            putExtra("edit_mode", true)
            putExtra("post_id", post.postId)
            putExtra("post_title", post.title)
            putExtra("post_content", post.content)
            putExtra("post_is_notice", post.isNotice)
        }
        editPostLauncher.launch(intent)
    }

    private fun showDeleteConfirmDialog(post: PostDetail) {
        AlertDialog.Builder(this)
            .setTitle("게시글 삭제")
            .setMessage("게시글을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ -> deletePost(post) }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun deletePost(post: PostDetail) {
        if (post.postId <= 0) return

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.deletePost(post.postId)
                if (response.isSuccessful && response.body()?.success == true) {
                    showToast("게시글이 삭제되었습니다")
                    setResult(RESULT_OK)
                    finish()
                } else {
                    val errorMessage = response.body()?.message ?: "게시글 삭제에 실패했습니다"
                    showToast(errorMessage)
                }
            } catch (e: Exception) {
                Log.e("BoardActivity", "Error deleting post", e)
                showToast("게시글 삭제 중 오류가 발생했습니다")
            }
        }
    }

    private fun setupPostDetailActions() {
        // 좋아요 버튼
        binding.btnLike.setOnClickListener {
            currentPost?.let { post ->
                toggleLike(post.postId)
            }
        }

        // 스크랩 버튼
        binding.btnScrap.setOnClickListener {
            currentPost?.let { post ->
                toggleScrap(post.postId)
            }
        }

        // 댓글 전송 버튼 - 실제 API 구현으로 교체
        binding.btnSendComment.setOnClickListener {
            val commentText = binding.etComment.text.toString().trim()
            if (commentText.isNotEmpty()) {
                currentPost?.let { post ->
                    createComment(post.postId, commentText)
                }
            }
        }
    }

    private fun toggleLike(postId: Int) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.likePost(postId)
                if (response.isSuccessful && response.body()?.success == true) {
                    val likeResponse = response.body()!!
                    updateLikeButton(likeResponse.isLiked, likeResponse.likeCount)

                    // 현재 게시글 정보 업데이트
                    currentPost = currentPost?.copy(
                        isLiked = likeResponse.isLiked,
                        likeCount = likeResponse.likeCount
                    )
                } else {
                    showToast("좋아요 처리 중 오류가 발생했습니다")
                }
            } catch (e: Exception) {
                Log.e("BoardActivity", "Error toggling like", e)
                showToast("좋아요 처리 중 오류가 발생했습니다")
            }
        }
    }

    private fun toggleScrap(postId: Int) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.scrapPost(postId)
                if (response.isSuccessful && response.body()?.success == true) {
                    val scrapResponse = response.body()!!
                    updateScrapButton(scrapResponse.isScraped)

                    // 현재 게시글 정보 업데이트
                    currentPost = currentPost?.copy(isScraped = scrapResponse.isScraped)
                } else {
                    showToast("스크랩 처리 중 오류가 발생했습니다")
                }
            } catch (e: Exception) {
                Log.e("BoardActivity", "Error toggling scrap", e)
                showToast("스크랩 처리 중 오류가 발생했습니다")
            }
        }
    }

    private fun updateLikeButton(isLiked: Boolean, likeCount: Int) {
        val likeIcon = if (isLiked) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
        val likeColor = if (isLiked) ContextCompat.getColor(this, R.color.colorPrimary)
        else ContextCompat.getColor(this, R.color.dark_gray)

        binding.btnLike.setCompoundDrawablesWithIntrinsicBounds(likeIcon, 0, 0, 0)
        binding.btnLike.setTextColor(likeColor)
        binding.btnLike.text = likeCount.toString()
    }

    private fun updateScrapButton(isScraped: Boolean) {
        val scrapIcon = if (isScraped) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark_border
        val scrapColor = if (isScraped) ContextCompat.getColor(this, R.color.colorPrimary)
        else ContextCompat.getColor(this, R.color.dark_gray)

        binding.btnScrap.setCompoundDrawablesWithIntrinsicBounds(scrapIcon, 0, 0, 0)
        binding.btnScrap.setTextColor(scrapColor)
    }

    private fun showPostList() {
        // 더보기 버튼 숨기기
        binding.ivMore.visibility = View.GONE

        // 게시글 작성 버튼 표시
        binding.fabWritePost.show()
        binding.fabWritePost.setOnClickListener {
            val intent = Intent(this, WritePostActivity::class.java)
            intent.putExtra("board_type", boardType)
            intent.putExtra("board_name", boardName)
            intent.putExtra("board_id", boardId)
            intent.putExtra("club_id", clubId)
            writePostLauncher.launch(intent)
        }

        // 게시판 이름이 기본값인 경우 정보 로드
        lifecycleScope.launch {
            loadBoardInfoIfNeeded()
        }

        loadPostList()

        binding.rvPosts.visibility = View.VISIBLE
        binding.layoutPostDetail.visibility = View.GONE
    }

    private fun loadPostList() {
        lifecycleScope.launch {
            try {
                loadBoardsAndPosts()
            } catch (e: Exception) {
                Log.e("BoardActivity", "Error loading posts", e)
                showToast("게시글을 불러오는 중 오류가 발생했습니다: ${e.message}")
            }
        }
    }

    // 🆕 새로운 게시판 API 처리가 포함된 수정된 메서드
    private suspend fun loadBoardsAndPosts() {
        try {
            // 특수 게시판인지 확인
            if (isSpecialBoard(boardType)) {
                // 특수 게시판의 경우 boardId 없이 바로 게시글 로드
                boardName = getBoardNameFromType(boardType)
                binding.tvBoardName.text = boardName
                loadBoardPosts(-1) // boardId는 -1로 전달 (특수 게시판 표시)
            } else {
                // 일반 게시판의 경우 기존 로직 유지 - 서버에서 게시판 목록 조회
                val response = ApiClient.apiService.getBoardsByClub(clubId)
                if (response.isSuccessful && response.body()?.success == true) {
                    val boards = response.body()?.boards ?: emptyList()
                    val targetBoard = boards.find { it.type == boardType }

                    if (targetBoard != null) {
                        boardId = targetBoard.boardId
                        boardName = targetBoard.name
                        binding.tvBoardName.text = boardName
                        loadBoardPosts(targetBoard.boardId)
                    } else {
                        showToast("해당 게시판을 찾을 수 없습니다")
                    }
                } else {
                    showToast("게시판 정보를 불러올 수 없습니다")
                }
            }
        } catch (e: Exception) {
            Log.e("BoardActivity", "Error loading boards", e)
            showToast("게시판을 불러오는 중 오류가 발생했습니다")
        }
    }

    // 특수 게시판인지 확인하는 헬퍼 메서드
    private fun isSpecialBoard(boardType: String): Boolean {
        return boardType in listOf("hot", "best", "my_posts", "my_comments", "my_scraps")
    }

    // 🆕 새로운 게시판 API 처리가 포함된 수정된 메서드
    private suspend fun loadBoardPosts(boardId: Int) {
        try {
            val response = when (boardType) {
                // 🆕 새로 추가된 특수 게시판 타입들 처리
                "hot" -> {
                    // 인기 게시글 API 호출 (실제로는 기존 API 사용)
                    ApiClient.apiService.getPostsByBoard(-1, "hot")
                }
                "best" -> {
                    // 베스트 게시글 API 호출 (실제로는 기존 API 사용)
                    ApiClient.apiService.getPostsByBoard(-1, "best")
                }
                "my_posts" -> {
                    // 내 게시글 API 호출
                    ApiClient.apiService.getMyPosts()
                }
                "my_comments" -> {
                    // 댓글 단 게시글 API 호출
                    ApiClient.apiService.getMyComments()
                }
                "my_scraps" -> {
                    // 스크랩한 게시글 API 호출
                    ApiClient.apiService.getMyScraps()
                }
                else -> {
                    // 일반 게시판 게시글 조회
                    ApiClient.apiService.getPostsByBoard(boardId, boardType)
                }
            }

            if (response.isSuccessful && response.body()?.success == true) {
                // 모든 응답이 PostListResponse 형태이므로 통일된 처리
                val postList = response.body()?.posts ?: emptyList()
                updatePostList(postList)
            } else {
                showToast("게시글을 불러올 수 없습니다")
            }
        } catch (e: Exception) {
            Log.e("BoardActivity", "Error loading board posts", e)
            showToast("게시글을 불러오는 중 오류가 발생했습니다")
        }
    }

    // 🆕 게시판 타입에서 이름을 가져오는 헬퍼 메서드 추가
    private fun getBoardNameFromType(type: String): String {
        return when (type) {
            "hot" -> "인기 게시글"
            "best" -> "베스트 게시글"
            "my_posts" -> "내 게시글"
            "my_comments" -> "댓글 단 게시글"
            "my_scraps" -> "스크랩한 게시글"
            else -> "게시판"
        }
    }

    private suspend fun loadBoardInfoIfNeeded() {
        // boardName이 기본값이고 boardId가 있는 경우 게시판 정보 조회
        if (boardName == "게시판" && boardId != null && boardId != -1) {
            try {
                val response = ApiClient.apiService.getBoardsByClub(clubId)
                if (response.isSuccessful && response.body()?.success == true) {
                    val boards = response.body()?.boards ?: emptyList()
                    val targetBoard = boards.find { it.boardId == boardId }

                    if (targetBoard != null) {
                        boardName = targetBoard.name
                        binding.tvBoardName.text = boardName
                    }
                }
            } catch (e: Exception) {
                Log.e("BoardActivity", "Error loading board info", e)
            }
        }
    }

    private fun updatePostList(postList: List<PostInfo>) {
        posts.clear()
        posts.addAll(postList)

        postAdapter = PostAdapter(posts) { post ->
            // 게시글 클릭 시 댓글수도 함께 전달
            val intent = Intent(this, BoardActivity::class.java)
            intent.putExtra("board_type", boardType)
            intent.putExtra("board_name", boardName)
            intent.putExtra("post_id", post.postId)
            intent.putExtra("board_id", boardId)
            intent.putExtra("club_id", clubId)

            // 🔧 추가: 댓글수와 조회수 전달
            intent.putExtra("list_comment_count", post.commentCount)
            intent.putExtra("list_view_count", post.viewCount)
            intent.putExtra("has_list_data", true)  // 목록에서 온 데이터임을 표시

            startActivityForResult(intent, 1001)
        }

        binding.rvPosts.layoutManager = LinearLayoutManager(this)
        binding.rvPosts.adapter = postAdapter
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            // 하위 게시글에서 변경사항이 있었으면 목록 새로고침
            loadPostList()
        }
    }
}