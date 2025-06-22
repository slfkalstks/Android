package kc.ac.uc.clubplatform.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kc.ac.uc.clubplatform.databinding.ItemSearchResultBinding
import kc.ac.uc.clubplatform.models.PostInfo
import java.text.SimpleDateFormat
import java.util.*

class SearchResultAdapter(
    private val posts: List<PostInfo>,
    private val onItemClick: (PostInfo) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.SearchResultViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val binding = ItemSearchResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SearchResultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        holder.bind(posts[position])
    }

    override fun getItemCount() = posts.size

    inner class SearchResultViewHolder(private val binding: ItemSearchResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(post: PostInfo) {
            binding.tvTitle.text = if (post.isNotice) "[공지] ${post.title}" else post.title

            // 마크다운 텍스트를 일반 텍스트로 변환하여 표시
            val plainContent = post.content
                .replace(Regex("#+\\s*"), "") // 헤더 제거
                .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1") // 볼드 제거
                .replace(Regex("\\*(.*?)\\*"), "$1") // 이탤릭 제거
                .replace(Regex("\\[(.*?)\\]\\(.*?\\)"), "$1") // 링크 제거
                .replace(Regex("```[\\s\\S]*?```"), "[코드]") // 코드 블록 제거
                .replace(Regex("`(.*?)`"), "$1") // 인라인 코드 제거
                .replace("\\n", " ") // 줄바꿈을 공백으로 변경
                .trim()

            binding.tvContent.text = plainContent
            binding.tvAuthor.text = post.authorName
            binding.tvDate.text = formatDate(post.createdAt)

            binding.root.setOnClickListener {
                onItemClick(post)
            }
        }

        // 검색 결과용 날짜 포맷팅 (yyyy-MM-dd) - 기존 유지
        private fun formatDate(dateString: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = inputFormat.parse(dateString)
                date?.let { outputFormat.format(it) } ?: dateString
            } catch (e: Exception) {
                dateString
            }
        }
    }
}