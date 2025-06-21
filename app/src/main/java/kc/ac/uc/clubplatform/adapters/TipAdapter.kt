package kc.ac.uc.clubplatform.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kc.ac.uc.clubplatform.databinding.ItemTipBinding
import kc.ac.uc.clubplatform.models.PostInfo
import kc.ac.uc.clubplatform.util.DateUtils  // 🔧 추가

class TipAdapter(
    private val tips: List<PostInfo>,
    private val onItemClick: (PostInfo) -> Unit
) : RecyclerView.Adapter<TipAdapter.TipViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TipViewHolder {
        val binding = ItemTipBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TipViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TipViewHolder, position: Int) {
        holder.bind(tips[position])
    }

    override fun getItemCount() = tips.size

    inner class TipViewHolder(private val binding: ItemTipBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(post: PostInfo) {
            binding.tvTitle.text = post.title
            binding.tvContent.text = post.content
            binding.tvAuthor.text = post.authorName

            // 🔧 DateUtils 사용하여 yy:MM:dd HHmm 형식으로 변경
            binding.tvDate.text = DateUtils.formatHomeDate(post.createdAt)

            binding.tvCommentCount.text = maxOf(0, post.commentCount).toString()
            binding.tvViewCount.text = maxOf(0, post.viewCount).toString()

            binding.root.setOnClickListener {
                onItemClick(post)
            }
        }
    }
}