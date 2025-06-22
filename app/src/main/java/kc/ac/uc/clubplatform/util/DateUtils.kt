package kc.ac.uc.clubplatform.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

object DateUtils {

    // 서버에서 올 수 있는 다양한 날짜 형식들
    private val inputFormats = arrayOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",    // ISO 8601 with milliseconds and Z
        "yyyy-MM-dd'T'HH:mm:ss.SSS",       // ISO 8601 with milliseconds
        "yyyy-MM-dd'T'HH:mm:ss'Z'",        // ISO 8601 with Z
        "yyyy-MM-dd'T'HH:mm:ss",           // ISO 8601 basic
        "yyyy-MM-dd HH:mm:ss",             // SQL timestamp format
        "yyyy-MM-dd",                      // Date only
        "MM/dd/yyyy HH:mm:ss",             // US format
        "dd/MM/yyyy HH:mm:ss"              // EU format
    )

    /**
     * 서버에서 받은 날짜 문자열을 파싱하여 Date 객체로 변환
     */
    fun parseServerDate(dateString: String?): Date? {
        if (dateString.isNullOrBlank()) return null

        for (formatPattern in inputFormats) {
            try {
                val formatter = SimpleDateFormat(formatPattern, Locale.getDefault())
                formatter.timeZone = TimeZone.getDefault()
                return formatter.parse(dateString)
            } catch (e: Exception) {
                // 다음 형식 시도
                continue
            }
        }

        Log.w("DateUtils", "Failed to parse date: $dateString")
        return null
    }

    /**
     * 게시글 상세 화면용 날짜 포맷팅 (yyyy.MM.dd)
     */
    fun formatPostDetailDate(dateString: String?): String {
        val date = parseServerDate(dateString)
        return if (date != null) {
            val formatter = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
            formatter.format(date)
        } else {
            dateString ?: "날짜 없음"
        }
    }

    /**
     * 상대적 시간 포맷팅 (방금 전, 5분 전, 2시간 전, 3일 전 등)
     */
    fun formatRelativeTime(dateString: String?): String {
        val date = parseServerDate(dateString) ?: return dateString ?: "알 수 없음"

        val now = Date()
        val diffInMs = now.time - date.time

        return when {
            diffInMs < 60 * 1000 -> "방금 전"
            diffInMs < 60 * 60 * 1000 -> "${diffInMs / (60 * 1000)}분 전"
            diffInMs < 24 * 60 * 60 * 1000 -> "${diffInMs / (60 * 60 * 1000)}시간 전"
            diffInMs < 7 * 24 * 60 * 60 * 1000 -> "${diffInMs / (24 * 60 * 60 * 1000)}일 전"
            else -> formatPostDetailDate(dateString)
        }
    }

    /**
     * 홈 화면용 날짜 포맷팅 (yy:MM:dd HHmm)
     */
    fun formatHomeDate(dateString: String?): String {
        val date = parseServerDate(dateString)
        return if (date != null) {
            val formatter = SimpleDateFormat("yy-MM-dd HH : mm", Locale.getDefault())
            formatter.format(date)
        } else {
            dateString ?: "날짜 없음"
        }
    }
}