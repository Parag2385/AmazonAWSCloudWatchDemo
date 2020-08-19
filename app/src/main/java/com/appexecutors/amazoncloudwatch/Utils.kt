package com.appexecutors.amazoncloudwatch

import android.content.Context
import android.os.Environment
import com.amazonaws.services.logs.model.InputLogEvent
import com.appexecutors.amazoncloudwatch.Constants.YMD_DATE
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

object Utils {

    fun getStringDate(mFinalFormat: String, mDate: Date?): String {
        if (mDate == null) return "NA"
        return SimpleDateFormat(mFinalFormat, Locale.ENGLISH).format(mDate)
    }

    fun getDateFromString(mFinalFormat: String, mStringToBeConverted: String): Date? {
        return SimpleDateFormat(mFinalFormat, Locale.ENGLISH).parse(mStringToBeConverted)!!
    }

    fun appendLog(context: Context, logs: ArrayList<InputLogEvent>, logStream: String) {
        val destinationURI = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS).toString() + File.separator + "logs"

        val directory = File(destinationURI)

        if (!directory.exists()) directory.mkdir()

        val logFile = File("$destinationURI/$logStream-log.file")
        if (!logFile.exists()) {
            try {
                logFile.createNewFile()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }else{
            val raf = RandomAccessFile(logFile, "rw")
            raf.setLength(0)
        }
        try {
            //BufferedWriter for performance, true to set append to file flag
            val buf = BufferedWriter(FileWriter(logFile, true))
            logs.map {
                buf.append(it.message)
                buf.newLine()
            }
            buf.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun readLog(context: Context, logs: ArrayList<InputLogEvent>, logStream: String){
        val destinationURI = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS).toString() + File.separator + "logs"
        val logFile = File("$destinationURI/$logStream-log.file")

        try {
            val br = BufferedReader(FileReader(logFile))
            var line: String?
            while (br.readLine().also { line = it } != null) {
                val split = line?.split(" | ")
                val date = getDateFromString(YMD_DATE, split!![0].trim())
                val inputLogs = InputLogEvent().withTimestamp(date?.time)
                val stringDate = getStringDate(YMD_DATE, date)
                inputLogs.message = "$stringDate | From File: ${split[1].replace("From File: ", "")}"
                logs.add(inputLogs)
            }
            br.close()
        } catch (e: IOException) {
            //You'll need to add proper error handling here
        }
    }

    fun clearFile(context: Context, logStream: String){
        val destinationURI = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS).toString() + File.separator + "logs"
        val logFile = File("$destinationURI/$logStream-log.file")

        if (logFile.exists()){
            val raf = RandomAccessFile(logFile, "rw")
            raf.setLength(0)
        }
    }
}