package com.appexecutors.amazoncloudwatch

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.appcompat.app.AppCompatActivity
import com.amazonaws.auth.CognitoCachingCredentialsProvider
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.logs.AmazonCloudWatchLogsClient
import com.amazonaws.services.logs.model.*
import com.appexecutors.amazoncloudwatch.Constants.AMAZON_LOG_GROUP
import com.appexecutors.amazoncloudwatch.Constants.AMAZON_LOG_STREAM_GENERAL
import com.appexecutors.amazoncloudwatch.Constants.AMAZON_NEXT_SEQUENCE_TOKEN
import com.appexecutors.amazoncloudwatch.Constants.APP_PREFERENCES
import com.appexecutors.amazoncloudwatch.Constants.AWS_COGNITO_END_POINT
import com.appexecutors.amazoncloudwatch.Constants.YMD_DATE
import com.appexecutors.amazoncloudwatch.Utils.appendLog
import com.appexecutors.amazoncloudwatch.Utils.clearFile
import com.appexecutors.amazoncloudwatch.Utils.getStringDate
import com.appexecutors.amazoncloudwatch.Utils.readLog
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var mAmazonCloudWatchLogsClient: AmazonCloudWatchLogsClient
    private lateinit var mSharedPrefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mSharedPrefs = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE)
        mAmazonCloudWatchLogsClient = getAmazonCloudWatchLogsClient()

        createLogGroupAndStreams()

        button_push_logs.setOnClickListener {
            val mPostObject = JSONObject()
            mPostObject.put("message", "Demo Log")
            pushLogToCloudWatch(mPostObject.toString(), AMAZON_LOG_STREAM_GENERAL)
        }
    }

    private fun getAmazonCloudWatchLogsClient(): AmazonCloudWatchLogsClient {
        val cognitoCachingCredentialsProvider = CognitoCachingCredentialsProvider(applicationContext, AWS_COGNITO_END_POINT, Regions.AP_SOUTH_1)
        val client = AmazonCloudWatchLogsClient(cognitoCachingCredentialsProvider)
        client.setRegion(Region.getRegion("ap-south-1"))
        return client
    }

    private fun pushLogToCloudWatch(mPostObject: String, mLogStream: String){

        val date = Date()
        val inputLogs = InputLogEvent().withTimestamp(date.time)
        val stringDate = getStringDate(YMD_DATE, date)
        inputLogs.message = "$stringDate | $mPostObject"

        val logList = ArrayList<InputLogEvent>()
        readLog(this, logList, mLogStream)
        logList.add(inputLogs)

        push(mPostObject, mLogStream, logList)
    }

    private fun push(mPostObject: String, mLogStream: String, logList: ArrayList<InputLogEvent>) {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.IO){

                try {
                    val logRequest = PutLogEventsRequest(AMAZON_LOG_GROUP, mLogStream, logList)
                    val sequenceToken = getNextSequenceToken(mLogStream)
                    if (sequenceToken.isNotEmpty()) logRequest.sequenceToken = sequenceToken

                    val result = mAmazonCloudWatchLogsClient.putLogEvents(logRequest)
                    val newSequenceToken = result.nextSequenceToken

                    storeNextSequenceToken(mLogStream, newSequenceToken)
                    clearFile(this@MainActivity, mLogStream)
                    Toast.makeText(this@MainActivity, "Log Pushed!", LENGTH_SHORT).show()
                }catch (e: Exception){

                    if (e is InvalidSequenceTokenException){
                        e.printStackTrace()
                        val newSequenceToken =  e.expectedSequenceToken
                        storeNextSequenceToken(mLogStream, newSequenceToken)

                        pushLogToCloudWatch(mPostObject, mLogStream)
                    }else {
                        e.printStackTrace()
                        appendLog(this@MainActivity, logList, mLogStream)
                    }

                }
            }
        }
    }

    /**
     *  Call this method only for first app launch
     */
    private fun createLogGroupAndStreams(){
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.IO) {
                try {
                    val logGroupRequest = CreateLogGroupRequest(AMAZON_LOG_GROUP)
                    mAmazonCloudWatchLogsClient.createLogGroup(logGroupRequest)
                }catch (e: Exception){
                    e.printStackTrace()
                }

                try {
                    val logStream = CreateLogStreamRequest(AMAZON_LOG_GROUP, AMAZON_LOG_STREAM_GENERAL)
                    mAmazonCloudWatchLogsClient.createLogStream(logStream)
                }catch (e: Exception){
                    e.printStackTrace()
                }
            }
        }

    }

    private fun getNextSequenceToken(mLogStream: String): String{
        return mSharedPrefs.getString("$AMAZON_NEXT_SEQUENCE_TOKEN-$mLogStream", "")!!
    }

    private fun storeNextSequenceToken(mLogStream: String, newSequenceToken: String){
        val editor = mSharedPrefs.edit()
        editor.putString("$AMAZON_NEXT_SEQUENCE_TOKEN-$mLogStream", newSequenceToken)
        editor.apply()
    }
}