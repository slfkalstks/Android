package kc.ac.uc.clubplatform.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kc.ac.uc.clubplatform.databinding.ItemCommentBinding
import kc.ac.uc.clubplatform.models.CommentInfo
import kc.ac.uc.clubplatform.util.DateUtils
import java.text.SimpleDateFormat
import java.util.*

class CommentAdapter(
    private val comments: MutableList<CommentInfo>,
    private val onCommentAction: (String, CommentInfo) -> Unit
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    class CommentViewHolder(
        private val binding: ItemCommentBinding,
        private val onCommentAction: (String, CommentInfo) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: CommentInfo) {
            binding.tvCommentAuthor.text = if (comment.isAnonymous) "익명" else comment.authorName
            binding.tvCommentContent.text = comment.content
            binding.tvCommentDate.text = DateUtils.formatRelativeTime(comment.createdAt)
            binding.tvCommentLikeCount.text = comment.likeCount.toString()

            // 좋아요 버튼 상태
            binding.btnCommentLike.isSelected = comment.isLiked

            // 대댓글 표시 (parentId가 있으면 들여쓰기)
            if (comment.parentId != null) {
                binding.root.setPadding(
                    binding.root.paddingLeft + 60, // 들여쓰기
                    binding.root.paddingTop,
                    binding.root.paddingRight,
                    binding.root.paddingBottom
                )
                binding.ivReplyIndicator.visibility = View.VISIBLE
            } else {
                binding.root.setPadding(
                    16, // 기본 패딩
                    binding.root.paddingTop,
                    binding.root.paddingRight,
                    binding.root.paddingBottom
                )
                binding.ivReplyIndicator.visibility = View.GONE
            }

            // 수정/삭제 메뉴 표시 여부
            binding.btnCommentMenu.visibility = if (comment.canEdit || comment.canDelete) {
                View.VISIBLE
            } else {
                View.GONE
            }

            // 클릭 리스너들
            binding.btnCommentLike.setOnClickListener {
                onCommentAction("like", comment)
            }

            binding.btnCommentReply.setOnClickListener {
                onCommentAction("reply", comment)
            }

            binding.btnCommentMenu.setOnClickListener {
                showCommentMenu(it, comment)
            }
        }

        private fun showCommentMenu(view: View, comment: CommentInfo) {
            val popup = androidx.appcompat.widget.PopupMenu(view.context, view)

            if (comment.canEdit) {
                popup.menu.add(0, 1, 0, "수정")
            }
            if (comment.canDelete) {
                popup.menu.add(0, 2, 0, "삭제")
            }

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> {
                        onCommentAction("edit", comment)
                        true
                    }
                    2 -> {
                        onCommentAction("delete", comment)
                        true
                    }
                    else -> false
                }
            }

            popup.show()
        }

        private fun formatDate(dateString: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val outputFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                val date = inputFormat.parse(dateString)
                outputFormat.format(date ?: Date())
            } catch (e: Exception) {
                dateString
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CommentViewHolder(binding, onCommentAction)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(comments[position])
    }

    override fun getItemCount(): Int = comments.size
}