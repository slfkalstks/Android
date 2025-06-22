package kc.ac.uc.clubplatform.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kc.ac.uc.clubplatform.R
import kc.ac.uc.clubplatform.databinding.ItemAttachedFileBinding
import kc.ac.uc.clubplatform.models.UploadedFileInfo
import kc.ac.uc.clubplatform.api.ApiClient

class AttachedFileAdapter(
    private val files: MutableList<UploadedFileInfo>,
    private val onDeleteClick: (UploadedFileInfo) -> Unit,
    private val onFileClick: (UploadedFileInfo) -> Unit = {},
    private val showDeleteButton: Boolean = true // 🆕 삭제 버튼 표시 여부
) : RecyclerView.Adapter<AttachedFileAdapter.FileViewHolder>() {

    inner class FileViewHolder(
        private val binding: ItemAttachedFileBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(fileInfo: UploadedFileInfo) {
            binding.tvFileName.text = fileInfo.originalName
            binding.tvFileSize.text = formatFileSize(fileInfo.fileSize)

            // 🆕 파일 타입 표시
            binding.tvFileType.text = when {
                fileInfo.contentType.startsWith("image/") -> "이미지 • 탭하여 보기"
                fileInfo.contentType == "application/pdf" -> "PDF • 탭하여 다운로드"
                fileInfo.contentType.contains("word") -> "문서 • 탭하여 다운로드"
                fileInfo.contentType.contains("excel") -> "스프레드시트 • 탭하여 다운로드"
                else -> "파일 • 탭하여 다운로드"
            }

            // 🆕 이미지 미리보기 처리
            if (fileInfo.contentType.startsWith("image/")) {
                binding.ivFileIcon.visibility = View.GONE
                binding.ivImagePreview.visibility = View.VISIBLE

                // Glide로 이미지 로드
                val imageUrl = "${ApiClient.BASE_URL.trimEnd('/')}${fileInfo.fileUrl}"
                Glide.with(binding.root.context)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_error)
                    .centerCrop()
                    .into(binding.ivImagePreview)
            } else {
                binding.ivFileIcon.visibility = View.VISIBLE
                binding.ivImagePreview.visibility = View.GONE
                binding.ivFileIcon.setImageResource(getFileIcon(fileInfo.contentType))
            }

            // 파일 클릭 리스너 (다운로드/보기)
            binding.root.setOnClickListener {
                onFileClick(fileInfo)
            }

            // 🆕 삭제 버튼 표시/숨김 처리
            if (showDeleteButton) {
                binding.btnDeleteFile.visibility = View.VISIBLE
                binding.btnDeleteFile.setOnClickListener {
                    onDeleteClick(fileInfo)
                }
            } else {
                binding.btnDeleteFile.visibility = View.GONE
            }
        }

        private fun formatFileSize(sizeInBytes: Long): String {
            if (sizeInBytes == 0L) return "" // 크기 정보 없는 경우

            return when {
                sizeInBytes < 1024 -> "${sizeInBytes}B"
                sizeInBytes < 1024 * 1024 -> "${sizeInBytes / 1024}KB"
                else -> "${"%.1f".format(sizeInBytes / (1024.0 * 1024.0))}MB"
            }
        }

        private fun getFileIcon(contentType: String): Int {
            return when {
                contentType.startsWith("image/") -> R.drawable.ic_image_placeholder
                contentType == "application/pdf" -> android.R.drawable.ic_menu_edit
                contentType.contains("word") -> android.R.drawable.ic_menu_edit
                contentType.contains("excel") || contentType.contains("sheet") -> android.R.drawable.ic_menu_edit
                contentType == "text/plain" -> android.R.drawable.ic_menu_edit
                else -> android.R.drawable.ic_menu_save
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemAttachedFileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(files[position])
    }

    override fun getItemCount(): Int = files.size
}