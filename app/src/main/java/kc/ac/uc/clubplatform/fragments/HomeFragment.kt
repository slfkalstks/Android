// fragments/HomeFragment.kt
package kc.ac.uc.clubplatform.fragments

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kc.ac.uc.clubplatform.databinding.FragmentHomeBinding
import kc.ac.uc.clubplatform.activity.BoardActivity
import kc.ac.uc.clubplatform.activity.NotificationActivity
import kc.ac.uc.clubplatform.activity.ProfileActivity
import kc.ac.uc.clubplatform.activity.SearchActivity
import kc.ac.uc.clubplatform.activity.ClubJoinActivity
import kc.ac.uc.clubplatform.adapters.NoticeAdapter
import kc.ac.uc.clubplatform.adapters.TipAdapter
import kc.ac.uc.clubplatform.adapters.ClubListAdapter
import kc.ac.uc.clubplatform.models.*
import kc.ac.uc.clubplatform.api.ApiClient
import kc.ac.uc.clubplatform.databinding.DialogClubListBinding
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var noticeBoard: BoardInfo? = null
    private var secondBoard: BoardInfo? = null // Tips 또는 다른 게시판

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHeader()
        loadCurrentClubData()
    }

    private fun setupHeader() {
        // 검색 아이콘 클릭 시 검색 페이지로 이동
        binding.ivSearch.setOnClickListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }

        // 알림 아이콘 클릭 시 알림 페이지로 이동
        binding.ivNotification.setOnClickListener {
            startActivity(Intent(requireContext(), NotificationActivity::class.java))
        }

        // 프로필 아이콘 클릭 시 프로필 페이지로 이동
        binding.ivProfile.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }

        // 동아리명 클릭 시 동아리 목록 다이얼로그 표시
        binding.tvClubName.setOnClickListener {
            showClubListDialog()
        }
    }

    private fun loadCurrentClubData() {
        // SharedPreferences에서 현재 동아리 정보 로드
        val sharedPreferences = requireContext().getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
        val currentClubId = sharedPreferences.getInt("current_club_id", -1)
        val currentClubName = sharedPreferences.getString("current_club_name", "")

        if (currentClubId != -1 && !currentClubName.isNullOrEmpty()) {
            // 저장된 동아리 정보가 있으면 바로 표시
            binding.tvClubName.text = currentClubName
            loadClubBoards(currentClubId)
        } else {
            // 저장된 정보가 없으면 API로 동아리 목록 조회
            loadMyClubsAndSetCurrent()
        }
    }

    private fun loadMyClubsAndSetCurrent() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getMyClubs()

                if (response.isSuccessful && response.body()?.success == true) {
                    val clubs = response.body()?.data ?: emptyList()

                    if (clubs.isNotEmpty()) {
                        // 저장된 동아리 정보 다시 확인
                        val sharedPreferences = requireContext().getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
                        val savedClubId = sharedPreferences.getInt("current_club_id", -1)
                        val savedClubName = sharedPreferences.getString("current_club_name", "")

                        // 저장된 동아리가 현재 가입된 동아리 목록에 있는지 확인
                        val savedClub = clubs.find { it.clubId == savedClubId }

                        val currentClub = if (savedClub != null && !savedClubName.isNullOrEmpty()) {
                            // 저장된 동아리가 유효하면 그대로 사용 (기존 사용자)
                            savedClub
                        } else {
                            // 저장된 동아리가 없거나 유효하지 않으면 첫 번째 동아리 설정
                            val firstClub = clubs.first()
                            saveCurrentClub(firstClub.clubId, firstClub.name)
                            firstClub
                        }

                        binding.tvClubName.text = currentClub.name
                        loadClubBoards(currentClub.clubId)
                    } else {
                        // 가입된 동아리가 없는 경우
                        binding.tvClubName.text = "동아리 없음"
                        showEmptyState()
                    }
                } else {
                    // API 호출 실패 시 기본값 설정
                    binding.tvClubName.text = "동아리 정보 없음"
                    showEmptyState()
                }
            } catch (e: Exception) {
                // 네트워크 오류 시 기본값 설정
                binding.tvClubName.text = "네트워크 오류"
                showEmptyState()
                Log.e("HomeFragment", "Failed to load club data", e)
            }
        }
    }

    private fun loadClubBoards(clubId: Int) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getBoardsByClub(clubId)

                if (response.isSuccessful && response.body()?.success == true) {
                    val boards = response.body()?.boards ?: emptyList()

                    // 공지사항 게시판과 두 번째 게시판 찾기
                    noticeBoard = boards.find { it.type == "notice" }
                    secondBoard = boards.find { it.type in listOf("tips", "general", "hot", "best") }

                    // 공지사항 로드
                    noticeBoard?.let { board ->
                        loadBoardPosts(board, true)
                    } ?: showEmptyNotices()

                    // 두 번째 게시판 로드 (Tips 등)
                    secondBoard?.let { board ->
                        loadBoardPosts(board, false)
                    } ?: showEmptyTips()

                } else {
                    Log.e("HomeFragment", "Failed to load boards: ${response.message()}")
                    showEmptyState()
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Exception loading boards", e)
                showEmptyState()
            }
        }
    }

    private fun loadBoardPosts(board: BoardInfo, isNotice: Boolean) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getPostsByBoard(board.boardId, board.type)

                if (response.isSuccessful && response.body()?.success == true) {
                    val posts = response.body()?.posts ?: emptyList()

                    // PostInfo를 Post 모델로 변환
                    val convertedPosts = posts.take(3)

                    if (isNotice) {
                        setupNoticesRecyclerView(convertedPosts, board)
                    } else {
                        setupTipsRecyclerView(convertedPosts, board)
                    }

                } else {
                    if (isNotice) showEmptyNotices() else showEmptyTips()
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "Exception loading posts for board ${board.boardId}", e)
                if (isNotice) showEmptyNotices() else showEmptyTips()
            }
        }
    }

    private fun setupNoticesRecyclerView(notices: List<PostInfo>, board: BoardInfo) {
        val adapter = NoticeAdapter(notices) { post ->
            // 🔧 공지사항 클릭 시 댓글수도 함께 전달
            val intent = Intent(requireContext(), BoardActivity::class.java)
            intent.putExtra("board_type", board.type)
            intent.putExtra("post_id", post.postId)
            intent.putExtra("board_id", board.boardId)

            // 🎯 추가: 댓글수와 조회수 전달
            intent.putExtra("list_comment_count", post.commentCount)
            intent.putExtra("list_view_count", post.viewCount)
            intent.putExtra("has_list_data", true)

            startActivity(intent)
        }

        binding.rvNotices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNotices.adapter = adapter

        // 공지사항 더보기 버튼 클릭 이벤트
        binding.btnMoreNotices.setOnClickListener {
            val intent = Intent(requireContext(), BoardActivity::class.java)
            intent.putExtra("board_type", board.type)
            intent.putExtra("board_id", board.boardId)
            startActivity(intent)
        }
    }

    private fun setupTipsRecyclerView(tips: List<PostInfo>, board: BoardInfo) {
        // 두 번째 섹션 제목을 게시판 이름으로 동적 설정
        binding.tvTipsTitle.text = board.name

        val adapter = TipAdapter(tips) { post ->
            // 🔧 팁 클릭 시 댓글수도 함께 전달
            val intent = Intent(requireContext(), BoardActivity::class.java)
            intent.putExtra("board_type", board.type)
            intent.putExtra("post_id", post.postId)
            intent.putExtra("board_id", board.boardId)

            // 🎯 추가: 댓글수와 조회수 전달
            intent.putExtra("list_comment_count", post.commentCount)
            intent.putExtra("list_view_count", post.viewCount)
            intent.putExtra("has_list_data", true)

            startActivity(intent)
        }

        binding.rvTips.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTips.adapter = adapter

        // 팁 더보기 버튼 클릭 이벤트
        binding.btnMoreTips.setOnClickListener {
            val intent = Intent(requireContext(), BoardActivity::class.java)
            intent.putExtra("board_type", board.type)
            intent.putExtra("board_id", board.boardId)
            startActivity(intent)
        }
    }

    private fun showClubListDialog() {
        val dialog = Dialog(requireContext())
        val dialogBinding = DialogClubListBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        // 다이얼로그 크기 설정
        val window = dialog.window
        window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // 동아리 목록 로드
        loadMyClubs(dialogBinding, dialog)

        // 닫기 버튼
        dialogBinding.btnClose.setOnClickListener {
            dialog.dismiss()
        }

        // 동아리 추가 버튼
        dialogBinding.btnAddClub.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(requireContext(), ClubJoinActivity::class.java)
            startActivity(intent)
        }

        dialog.show()
    }

    private fun loadMyClubs(dialogBinding: DialogClubListBinding, dialog: Dialog) {
        lifecycleScope.launch {
            try {
                dialogBinding.progressBar.visibility = View.VISIBLE

                val response = ApiClient.apiService.getMyClubs()

                if (response.isSuccessful && response.body()?.success == true) {
                    val clubs = response.body()?.data ?: emptyList()

                    if (clubs.isNotEmpty()) {
                        setupClubRecyclerView(dialogBinding, clubs, dialog)
                        dialogBinding.tvNoClubs.visibility = View.GONE
                        dialogBinding.rvClubs.visibility = View.VISIBLE
                    } else {
                        dialogBinding.tvNoClubs.visibility = View.VISIBLE
                        dialogBinding.rvClubs.visibility = View.GONE
                    }
                } else {
                    Toast.makeText(requireContext(), "동아리 목록을 불러올 수 없습니다", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "네트워크 오류: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                dialogBinding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun setupClubRecyclerView(dialogBinding: DialogClubListBinding, clubs: List<Club>, dialog: Dialog) {
        val adapter = ClubListAdapter(clubs) { selectedClub ->
            // 동아리 선택 시 현재 동아리로 설정
            saveCurrentClub(selectedClub.clubId, selectedClub.name)

            // UI 즉시 업데이트
            binding.tvClubName.text = selectedClub.name
            loadClubBoards(selectedClub.clubId)

            Toast.makeText(requireContext(), "${selectedClub.name}(으)로 전환되었습니다", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialogBinding.rvClubs.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.rvClubs.adapter = adapter
    }

    private fun saveCurrentClub(clubId: Int, clubName: String) {
        val sharedPreferences = requireContext().getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putInt("current_club_id", clubId)
            .putString("current_club_name", clubName)
            .apply()
    }


    private fun showEmptyState() {
        showEmptyNotices()
        showEmptyTips()
    }

    private fun showEmptyNotices() {
        binding.rvNotices.adapter = NoticeAdapter(emptyList()) { }
    }

    private fun showEmptyTips() {
        binding.rvTips.adapter = TipAdapter(emptyList()) { }
    }

    private fun formatDate(dateString: String): String {
        // API에서 받아온 날짜 문자열을 적절한 형식으로 변환
        // 예: "2025-06-21T10:30:00" -> "2025-06-21"
        return try {
            if (dateString.contains("T")) {
                dateString.split("T")[0]
            } else {
                dateString
            }
        } catch (e: Exception) {
            dateString
        }
    }

    override fun onResume() {
        super.onResume()
        // 다른 화면에서 돌아왔을 때 동아리 정보가 변경되었을 수 있으므로 다시 로드
        refreshCurrentClubData()
    }

    private fun refreshCurrentClubData() {
        val sharedPreferences = requireContext().getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
        val currentClubId = sharedPreferences.getInt("current_club_id", -1)
        val currentClubName = sharedPreferences.getString("current_club_name", "")

        if (currentClubId != -1 && !currentClubName.isNullOrEmpty()) {
            binding.tvClubName.text = currentClubName
            // 데이터 새로고침은 필요시에만 (성능 고려)
            if (binding.rvNotices.adapter == null) {
                loadClubBoards(currentClubId)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}