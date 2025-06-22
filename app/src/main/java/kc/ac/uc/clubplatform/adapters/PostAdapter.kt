package kc.ac.uc.clubplatform.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kc.ac.uc.clubplatform.databinding.ItemPostBinding
import kc.ac.uc.clubplatform.models.PostInfo
import java.text.SimpleDateFormat
import java.util.*

class PostAdapter(
    private val posts: List<PostInfo>,
    private val onItemClick: (PostInfo) -> Unit
) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemPostBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(posts[position])
    }

    override fun getItemCount() = posts.size

    inner class PostViewHolder(private val binding: ItemPostBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(post: PostInfo) {
            binding.tvTitle.text = if (post.isNotice) "[공지] ${post.title}" else post.title

            // 내용을 마크다운 텍스트에서 일반 텍스트로 변환하여 미리보기
            val plainContent = post.content
                .replace(Regex("#+\\s*"), "") // 헤더 제거
                .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1") // 볼드 제거
                .replace(Regex("\\*(.*?)\\*"), "$1") // 이탤릭 제거
                .replace(Regex("\\[(.*?)\\]\\(.*?\\)"), "$1") // 링크 제거
                .replace(Regex("```[\\s\\S]*?```"), "[코드]") // 코드 블록 제거
                .replace(Regex("`(.*?)`"), "$1") // 인라인 코드 제거
                .trim()

            binding.tvContent.text = plainContent
            binding.tvAuthor.text = post.authorName
            // 기존 formatDate() 유지 (MM-dd 형식)
            binding.tvDate.text = formatDate(post.createdAt)
            binding.tvCommentCount.text = post.commentCount.toString()
            binding.tvViewCount.text = post.viewCount.toString()

            // 공지글은 배경색 변경
            if (post.isNotice) {
                binding.root.setBackgroundResource(android.R.color.holo_blue_light)
                binding.root.alpha = 0.1f
            } else {
                binding.root.setBackgroundResource(android.R.color.white)
                binding.root.alpha = 1.0f
            }

            binding.root.setOnClickListener {
                onItemClick(post)
            }
        }

        // 게시글 목록용 날짜 포맷팅
        private fun formatDate(dateString: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val outputFormat = SimpleDateFormat("yy-MM-dd", Locale.getDefault())
                val date = inputFormat.parse(dateString)
                date?.let { outputFormat.format(it) } ?: dateString
            } catch (e: Exception) {
                dateString
            }
        }
    }
}