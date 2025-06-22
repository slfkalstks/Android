package kc.ac.uc.clubplatform.fragments

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
import kc.ac.uc.clubplatform.activity.BoardActivity
import kc.ac.uc.clubplatform.adapters.BoardAdapter
import kc.ac.uc.clubplatform.api.ApiClient
import kc.ac.uc.clubplatform.databinding.FragmentBoardListBinding
import kc.ac.uc.clubplatform.models.Board
import kc.ac.uc.clubplatform.models.BoardInfo
import kotlinx.coroutines.launch

class BoardListFragment : Fragment() {
    private var _binding: FragmentBoardListBinding? = null
    private val binding get() = _binding!!

    private lateinit var boardAdapter: BoardAdapter
    private val boards = mutableListOf<Board>()
    private var clubId: Int = -1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBoardListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ 기존 방식대로 SharedPreferences에서 clubId 가져오기
        val sharedPreferences = requireContext().getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
        clubId = sharedPreferences.getInt("current_club_id", -1)

        setupRecyclerView()
        loadBoardList()
    }

    private fun setupRecyclerView() {
        boardAdapter = BoardAdapter(boards) { board ->
            val intent = Intent(requireContext(), BoardActivity::class.java).apply {
                putExtra("board_type", board.type)
                putExtra("board_name", board.name)
                putExtra("board_id", board.id)
                putExtra("club_id", clubId)
            }
            startActivity(intent)
        }

        binding.rvBoards.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBoards.adapter = boardAdapter
    }

    private fun loadBoardList() {
        // ✅ 기존 검증 로직 복원
        if (clubId == -1) {
            Toast.makeText(requireContext(), "동아리 정보가 없습니다", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                Log.d("BoardListFragment", "🔍 게시판 목록 조회 시작: clubId=$clubId")
                val response = ApiClient.apiService.getBoardsByClub(clubId)

                if (response.isSuccessful && response.body()?.success == true) {
                    val boardInfoList = response.body()?.boards ?: emptyList<BoardInfo>()
                    Log.d("BoardListFragment", "✅ 서버 응답 성공: ${boardInfoList.size}개 게시판")
                    updateBoardList(boardInfoList)
                } else {
                    Log.e("BoardListFragment", "❌ 서버 응답 실패: ${response.body()?.message}")
                    showDefaultBoards()
                    Toast.makeText(requireContext(), "서버에서 게시판 목록을 가져올 수 없어 기본 게시판을 표시합니다", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("BoardListFragment", "❌ 네트워크 에러", e)
                showDefaultBoards()
                Toast.makeText(requireContext(), "네트워크 오류로 인해 기본 게시판을 표시합니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateBoardList(boardInfoList: List<BoardInfo>) {
        boards.clear()

        // 서버에서 가져온 일반 게시판들만 추가 (특수 게시판 제거)
        boardInfoList.forEach { boardInfo ->
            boards.add(Board(
                id = boardInfo.boardId,  // boardId 사용
                name = boardInfo.name,
                type = boardInfo.type,
                description = getBoardDescription(boardInfo.type)
            ))
        }

        // 🆕 특수 게시판들을 마지막에 구분해서 추가
        addSpecialBoards()

        boardAdapter.notifyDataSetChanged()
    }

    private fun addSpecialBoards() {
        // 인기 게시글
        boards.add(Board(
            id = -1, // 특수 게시판은 실제 boardId가 없으므로 -1 사용
            name = "인기 게시글",
            type = "hot",
            description = "인기 있는 게시글 모아보기"
        ))

        // 베스트 게시글
        boards.add(Board(
            id = -1,
            name = "베스트 게시글",
            type = "best",
            description = "좋아요를 많이 받은 게시글 모아보기"
        ))

        // 내 게시글
        boards.add(Board(
            id = -1,
            name = "내 게시글",
            type = "my_posts",
            description = "내가 작성한 게시글 모아보기"
        ))

        // 댓글 단 게시글
        boards.add(Board(
            id = -1,
            name = "댓글 단 게시글",
            type = "my_comments",
            description = "내가 댓글을 작성한 게시글 모아보기"
        ))

        // 스크랩한 게시글
        boards.add(Board(
            id = -1,
            name = "스크랩한 게시글",
            type = "my_scraps",
            description = "내가 스크랩한 게시글 모아보기"
        ))
    }

    private fun showDefaultBoards() {
        boards.clear()
        // 특수 게시판만 표시
        addSpecialBoards()
        boardAdapter.notifyDataSetChanged()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            loadBoardList()
        }
    }

    private fun getBoardDescription(type: String): String {
        return when (type) {
            "general" -> "자유롭게 소통하는 게시판"
            "notice" -> "동아리 공지사항을 확인하는 게시판"
            "tips" -> "유용한 정보를 공유하는 게시판"
            "hot" -> "인기 있는 게시글 모아보기"
            "best" -> "좋아요를 많이 받은 게시글 모아보기"
            "my_posts" -> "내가 작성한 게시글 모아보기"
            "my_comments" -> "내가 댓글을 작성한 게시글 모아보기"
            "my_scraps" -> "내가 스크랩한 게시글 모아보기"
            else -> "게시판"
        }
    }

    private fun getBoardIdFromType(type: String): Int {
        // boards 리스트에서 해당 타입의 boardId 찾기
        return boards.find { it.type == type }?.id ?: -1
    }

    override fun onResume() {
        super.onResume()
        // 다른 화면에서 돌아왔을 때 게시판 목록 새로고침
        loadBoardList()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}