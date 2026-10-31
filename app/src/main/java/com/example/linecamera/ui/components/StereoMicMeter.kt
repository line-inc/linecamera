package com.example.linecamera.ui.components

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.sqrt

@SuppressLint("MissingPermission")
@Composable
fun StereoMicMeter(
    isRecording: Boolean,
    audioGain: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    var leftDb by remember { mutableStateOf(-60f) }
    var rightDb by remember { mutableStateOf(-60f) }

    LaunchedEffect(isRecording, audioGain) {
        if (audioGain <= 0.05f) {
            leftDb = -60f
            rightDb = -60f
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_STEREO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(2048)

            var audioRecord: AudioRecord? = null
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
                if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.startRecording()
                    val buffer = ShortArray(bufferSize)

                    while (isActive) {
                        val readSize = audioRecord.read(buffer, 0, bufferSize)
                        if (readSize > 0) {
                            var sumLeft = 0.0
                            var sumRight = 0.0
                            var count = 0

                            var i = 0
                            while (i < readSize - 1) {
                                val l = buffer[i].toDouble() * audioGain
                                val r = buffer[i + 1].toDouble() * audioGain
                                sumLeft += l * l
                                sumRight += r * r
                                count++
                                i += 2
                            }

                            if (count > 0) {
                                val rmsLeft = sqrt(sumLeft / count)
                                val rmsRight = sqrt(sumRight / count)

                                val dbL = if (rmsLeft > 0) 20 * log10(rmsLeft / 32767.0) else -60.0
                                val dbR = if (rmsRight > 0) 20 * log10(rmsRight / 32767.0) else -60.0

                                leftDb = dbL.toFloat().coerceIn(-60f, 0f)
                                rightDb = dbR.toFloat().coerceIn(-60f, 0f)
                            }
                        }
                        delay(100)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val isMuted = audioGain <= 0.05f
    val leftFraction = if (isMuted) 0f else ((leftDb + 60f) / 60f).coerceIn(0f, 1f)
    val rightFraction = if (isMuted) 0f else ((rightDb + 60f) / 60f).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = if (isMuted) "MIC MUTED" else "MIC L/R",
                color = if (isMuted) Color.Red else Color.Green,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.DarkGray)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(leftFraction)
                            .align(Alignment.BottomCenter)
                            .background(if (leftDb > -6f) Color.Red else Color(0xFF38D352))
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.DarkGray)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(rightFraction)
                            .align(Alignment.BottomCenter)
                            .background(if (leftDb > -6f) Color.Red else Color(0xFF38D352))
                    )
                }
            }
        }
    }
}
