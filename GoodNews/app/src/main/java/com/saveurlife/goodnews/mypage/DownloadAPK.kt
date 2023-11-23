package com.saveurlife.goodnews.mypage

import android.content.Context
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class DownloadAPK(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val url = inputData.getString(APK_URL)
            val destinationPath = inputData.getString(APK_DESTINATION_PATH)

            if (url != null && destinationPath != null) {
                downloadFile(url, destinationPath)
                Result.success()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun downloadFile(urlString: String, destinationPath: String) {
        var input: InputStream? = null
        var connection: HttpURLConnection? = null

        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.connect()

            val fileName = "app-release.apk"
            val fullPath = "$destinationPath/$fileName"

            input = connection.inputStream
            File(fullPath).outputStream().use { output ->
                input.copyTo(output)
            }
        } finally {
            input?.close()
            connection?.disconnect()
        }
    }

    companion object {
        // 다운 받을 곳
        const val APK_URL = "https://saveurlife.kr/images"
        // 저장할 곳
        val APK_DESTINATION_PATH =  Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString()
    }
}