package kc.ac.uc.clubplatform.activity

import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.noties.markwon.Markwon
import io.noties.markwon.editor.MarkwonEditor
import io.noties.markwon.editor.MarkwonEditorTextWatcher
import kc.ac.uc.clubplatform.api.ApiClient
import kc.ac.uc.clubplatform.databinding.ActivityWritePostBinding
import kc.ac.uc.clubplatform.models.CreatePostRequest
import kc.ac.uc.clubplatform.models.UpdatePostRequest
import kc.ac.uc.clubplatform.models.UploadedFileInfo
import kc.ac.uc.clubplatform.adapters.AttachedFileAdapter
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class WritePostActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWritePostBinding
    private lateinit var boardType: String
    private lateinit var boardName: String
    private var boardId: Int = -1
    private var clubId: Int = -1
    private var postId: Int = -1
    private var isEditMode: Boolean = false
    private lateinit var markwon: Markwon
    private lateinit var editor: MarkwonEditor

    // 🆕 파일 업로드 관련
    private val selectedFiles = mutableListOf<Uri>()
    private val uploadedFiles = mutableListOf<UploadedFileInfo>()
    private lateinit var attachedFileAdapter: AttachedFileAdapter

    // 파일 선택 런처
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                when {
                    data.clipData != null -> {
                        // 여러 파일 선택
                        for (i in 0 until data.clipData!!.itemCount) {
                            val uri = data.clipData!!.getItemAt(i).uri
                            selectedFiles.add(uri)
                        }
                    }
                    data.data != null -> {
                        // 단일 파일 선택
                        selectedFiles.add(data.data!!)
                    }
                }
                updateFileList()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWritePostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 인텐트에서 정보 가져오기
        boardType = intent.getStringExtra("board_type") ?: "general"
        boardName = intent.getStringExtra("board_name") ?: "게시판"
        boardId = intent.getIntExtra("board_id", -1)
        clubId = intent.getIntExtra("club_id", -1)
        isEditMode = intent.getBooleanExtra("edit_mode", false)

        if (isEditMode) {
            postId = intent.getIntExtra("post_id", -1)
            loadEditData()
        }

        // 현재 동아리 ID 가져오기
        if (clubId == -1) {
            val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
            clubId = sharedPreferences.getInt("current_club_id", -1)
        }

        setupMarkdown()
        setupUI()
        setupListeners()
        setupFileRecyclerView()
    }

    private fun loadEditData() {
        binding.etTitle.setText(intent.getStringExtra("post_title") ?: "")
        binding.etContent.setText(intent.getStringExtra("post_content") ?: "")
        binding.cbNotice.isChecked = intent.getBooleanExtra("post_is_notice", false)
    }

    private fun setupMarkdown() {
        markwon = Markwon.create(this)
        editor = MarkwonEditor.create(markwon)

        binding.etContent.addTextChangedListener(
            MarkwonEditorTextWatcher.withPreRender(
                editor, Executors.newCachedThreadPool(), binding.etContent
            )
        )
    }

    private fun setupUI() {
        binding.tvBoardName.text = boardName
        binding.btnComplete.text = if (isEditMode) "수정" else "완료"

        // 공지 체크박스는 notice 게시판이나 관리자만 표시
        if (boardType != "notice") {
            binding.cbNotice.visibility = View.GONE
        }

        // 익명 체크박스는 일반 게시판에서만 표시
        if (boardType == "notice") {
            binding.cbAnonymous?.visibility = View.GONE
        }
    }

    private fun setupListeners() {
        // 완료 버튼 클릭 리스너
        binding.btnComplete.setOnClickListener {
            if (validateInput()) {
                uploadFilesAndCreatePost()
            }
        }

        // 취소 버튼 클릭 리스너
        binding.ivCancel.setOnClickListener {
            finish()
        }

        // 🆕 파일 업로드 버튼 - 실제 구현
        binding.btnUploadFile.setOnClickListener {
            openFilePicker()
        }
    }

    private fun setupFileRecyclerView() {
        attachedFileAdapter = AttachedFileAdapter(
            files = uploadedFiles,
            onDeleteClick = { fileInfo ->
                // 파일 삭제
                uploadedFiles.remove(fileInfo)
                attachedFileAdapter.notifyDataSetChanged()
                updateFileVisibility()
            },
            onFileClick = { fileInfo ->
                // 🆕 파일 클릭 시 다운로드/보기
                handleFileClick(fileInfo)
            }
        )

        binding.rvAttachedFiles.layoutManager = LinearLayoutManager(this)
        binding.rvAttachedFiles.adapter = attachedFileAdapter
        updateFileVisibility()
    }

    // 🆕 파일 클릭 처리 (다운로드/보기)
    private fun handleFileClick(fileInfo: UploadedFileInfo) {
        when {
            fileInfo.contentType.startsWith("image/") -> {
                // 이미지인 경우 이미지 뷰어로 보기
                openImageViewer(fileInfo)
            }
            else -> {
                // 다른 파일들은 다운로드
                downloadFile(fileInfo)
            }
        }
    }

    // 🆕 이미지 뷰어 열기
    private fun openImageViewer(fileInfo: UploadedFileInfo) {
        val intent = Intent(this, ImageViewerActivity::class.java).apply {
            putExtra("image_url", "${ApiClient.BASE_URL.trimEnd('/')}${fileInfo.fileUrl}")
            putExtra("image_name", fileInfo.originalName)
        }
        startActivity(intent)
    }

    // 🆕 파일 다운로드
    private fun downloadFile(fileInfo: UploadedFileInfo) {
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
            Toast.makeText(this, "다운로드를 시작합니다", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("WritePostActivity", "Download failed", e)
            Toast.makeText(this, "다운로드에 실패했습니다", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

            // 파일 타입 제한
            val mimeTypes = arrayOf(
                "image/*",
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "text/plain"
            )
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        }

        filePickerLauncher.launch(Intent.createChooser(intent, "파일 선택"))
    }

    private fun updateFileList() {
        if (selectedFiles.isNotEmpty()) {
            uploadSelectedFiles() // 메서드 이름 수정
        }
    }

    private fun updateFileVisibility() {
        binding.rvAttachedFiles.visibility = if (uploadedFiles.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun uploadSelectedFiles() { // 메서드 이름 변경
        lifecycleScope.launch {
            try {
                showLoading(true, "파일 업로드 중...")

                val fileParts = mutableListOf<MultipartBody.Part>()

                selectedFiles.forEach { uri ->
                    val file = createTempFileFromUri(uri)
                    if (file != null) {
                        val requestFile = file.asRequestBody(getContentType(uri).toMediaTypeOrNull())
                        val filePart = MultipartBody.Part.createFormData("files", file.name, requestFile)
                        fileParts.add(filePart)
                    }
                }

                val response = ApiClient.apiService.uploadFiles(fileParts)

                if (response.isSuccessful && response.body()?.success == true) {
                    val newFiles = response.body()?.files ?: emptyList()
                    uploadedFiles.addAll(newFiles)
                    attachedFileAdapter.notifyDataSetChanged()
                    updateFileVisibility()

                    selectedFiles.clear()
                    Toast.makeText(this@WritePostActivity, "${newFiles.size}개 파일 업로드 완료", Toast.LENGTH_SHORT).show()
                } else {
                    val errorMessage = response.body()?.message ?: "파일 업로드에 실패했습니다"
                    Toast.makeText(this@WritePostActivity, errorMessage, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("WritePostActivity", "Error uploading files", e)
                Toast.makeText(this@WritePostActivity, "파일 업로드 중 오류가 발생했습니다", Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun createTempFileFromUri(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val fileName = getFileName(uri)
            val tempFile = File(cacheDir, fileName)

            FileOutputStream(tempFile).use { output ->
                inputStream?.copyTo(output)
            }

            tempFile
        } catch (e: Exception) {
            Log.e("WritePostActivity", "Error creating temp file", e)
            null
        }
    }

    private fun getFileName(uri: Uri): String {
        var fileName = "file"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                fileName = cursor.getString(nameIndex)
            }
        }
        return fileName
    }

    private fun getContentType(uri: Uri): String {
        return contentResolver.getType(uri) ?: "application/octet-stream"
    }

    private fun uploadFilesAndCreatePost() {
        if (selectedFiles.isNotEmpty()) {
            // 선택된 파일이 있으면 먼저 업로드
            lifecycleScope.launch {
                uploadFiles()
                // 업로드 완료 후 게시글 작성/수정
                if (isEditMode) {
                    updatePost()
                } else {
                    createPost()
                }
            }
        } else {
            // 파일이 없으면 바로 게시글 작성/수정
            if (isEditMode) {
                updatePost()
            } else {
                createPost()
            }
        }
    }

    private fun validateInput(): Boolean {
        val title = binding.etTitle.text.toString().trim()
        val content = binding.etContent.text.toString().trim()

        if (title.isEmpty()) {
            binding.etTitle.error = "제목을 입력해주세요"
            binding.etTitle.requestFocus()
            return false
        }

        if (content.isEmpty()) {
            binding.etContent.error = "내용을 입력해주세요"
            binding.etContent.requestFocus()
            return false
        }

        if (boardId == -1) {
            Toast.makeText(this, "게시판 정보가 없습니다", Toast.LENGTH_SHORT).show()
            return false
        }

        if (isEditMode && postId == -1) {
            Toast.makeText(this, "게시글 정보가 없습니다", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun createPost() {
        val title = binding.etTitle.text.toString().trim()
        val content = binding.etContent.text.toString().trim()
        val isAnonymous = binding.cbAnonymous?.isChecked ?: false
        val isNotice = binding.cbNotice.isChecked

        // 공지글 권한 확인 (실제로는 서버에서 권한 확인)
        if (isNotice && boardType != "notice") {
            Toast.makeText(this, "이 게시판에서는 공지글을 작성할 수 없습니다", Toast.LENGTH_SHORT).show()
            return
        }

        // 🆕 업로드된 파일 URL들을 첨부
        val attachments = uploadedFiles.map { it.fileUrl }

        val request = CreatePostRequest(
            boardId = boardId,
            title = title,
            content = content,
            isAnonymous = isAnonymous,
            isNotice = isNotice,
            attachments = attachments
        )

        lifecycleScope.launch {
            try {
                showLoading(true, "게시글 작성 중...")

                val response = ApiClient.apiService.createPost(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(this@WritePostActivity, "게시글이 작성되었습니다", Toast.LENGTH_SHORT).show()

                    // 결과를 반환하고 액티비티 종료
                    setResult(RESULT_OK)
                    finish()
                } else {
                    val errorMessage = response.body()?.message ?: "게시글 작성에 실패했습니다"
                    Toast.makeText(this@WritePostActivity, errorMessage, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("WritePostActivity", "Error creating post", e)
                Toast.makeText(this@WritePostActivity, "게시글 작성 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun updatePost() {
        val title = binding.etTitle.text.toString().trim()
        val content = binding.etContent.text.toString().trim()
        val isNotice = binding.cbNotice.isChecked

        // 공지글 권한 확인 (실제로는 서버에서 권한 확인)
        if (isNotice && boardType != "notice") {
            Toast.makeText(this, "이 게시판에서는 공지글을 작성할 수 없습니다", Toast.LENGTH_SHORT).show()
            return
        }

        // 🆕 업로드된 파일 URL들을 첨부
        val attachments = uploadedFiles.map { it.fileUrl }

        val request = UpdatePostRequest(
            boardId = boardId,
            title = title,
            content = content,
            isNotice = isNotice,
            attachments = attachments
        )

        lifecycleScope.launch {
            try {
                showLoading(true, "게시글 수정 중...")

                val response = ApiClient.apiService.updatePost(postId, request)

                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(this@WritePostActivity, "게시글이 수정되었습니다", Toast.LENGTH_SHORT).show()

                    // 결과를 반환하고 액티비티 종료
                    setResult(RESULT_OK)
                    finish()
                } else {
                    val errorMessage = response.body()?.message ?: "게시글 수정에 실패했습니다"
                    Toast.makeText(this@WritePostActivity, errorMessage, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("WritePostActivity", "Error updating post", e)
                Toast.makeText(this@WritePostActivity, "게시글 수정 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(isLoading: Boolean, message: String = "") {
        if (isLoading) {
            binding.btnComplete.isEnabled = false
            binding.btnComplete.text = message.ifEmpty {
                if (isEditMode) "수정 중..." else "작성 중..."
            }
        } else {
            binding.btnComplete.isEnabled = true
            binding.btnComplete.text = if (isEditMode) "수정" else "완료"
        }
    }
}