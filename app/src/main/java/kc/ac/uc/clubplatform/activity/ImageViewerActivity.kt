package kc.ac.uc.clubplatform.activity

import android.app.DownloadManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kc.ac.uc.clubplatform.R
import kc.ac.uc.clubplatform.databinding.ActivityImageViewerBinding

class ImageViewerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityImageViewerBinding
    private var imageUrl: String = ""
    private var imageName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Intent에서 데이터 가져오기
        imageUrl = intent.getStringExtra("image_url") ?: ""
        imageName = intent.getStringExtra("image_name") ?: "image.jpg"

        setupUI()
        loadImage()
    }

    private fun setupUI() {
        // 툴바 설정
        binding.tvImageName.text = imageName

        // 뒤로가기 버튼
        binding.ivBack.setOnClickListener {
            finish()
        }

        // 다운로드 버튼
        binding.ivDownload.setOnClickListener {
            downloadImage()
        }

        // 이미지 클릭 시 UI 토글
        binding.ivImage.setOnClickListener {
            toggleUI()
        }
    }

    private fun loadImage() {
        if (imageUrl.isNotEmpty()) {
            Glide.with(this)
                .load(imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_image_error)
                .into(binding.ivImage)
        }
    }

    private fun downloadImage() {
        try {
            val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager

            val request = DownloadManager.Request(Uri.parse(imageUrl)).apply {
                setTitle("이미지 다운로드")
                setDescription("$imageName 다운로드 중...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, imageName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            downloadManager.enqueue(request)
            Toast.makeText(this, "다운로드를 시작합니다", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("ImageViewerActivity", "Download failed", e)
            Toast.makeText(this, "다운로드에 실패했습니다", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleUI() {
        val isVisible = binding.toolbarLayout.alpha == 1f
        val alpha = if (isVisible) 0f else 1f

        binding.toolbarLayout.animate()
            .alpha(alpha)
            .setDuration(200)
            .start()
    }
}