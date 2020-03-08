package com.kingtouch.easypusher

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity

import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.Nullable
import androidx.databinding.DataBindingUtil
import androidx.databinding.ObservableField
import androidx.lifecycle.Observer
import com.kingtouch.easypusher.databinding.ActivityMainBinding
import io.reactivex.Single
import io.reactivex.functions.Consumer
import org.easydarwin.push.MediaStream

class MainActivity : AppCompatActivity() {
    private val REQUEST_CAMERA_PERMISSION = 1000
    private val REQUEST_MEDIA_PROJECTION = 1001
    val HOST = "cloud.easydarwin.org"
    private var mediaStream: MediaStream? = null
    var address = ObservableField<String>("");
    var switcherText = ObservableField<String>("点击推送屏幕");
    var codeMethod = ObservableField<String>("屏幕推送");
    var mBinding: ActivityMainBinding? = null;
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initBinding()
        setContentView(mBinding!!.root)
        mBinding!!.address = address;
        mBinding!!.switcherText = switcherText;
        mBinding!!.codeMethod = codeMethod;
        startService()

        initListener()
    }

    fun initBinding() {
        mBinding = DataBindingUtil.inflate<ActivityMainBinding>(
                LayoutInflater.from(this),
                R.layout.activity_main,
                null,
                false
        )
    }

    fun initListener(){
        mBinding!!.cb.isChecked=PreferenceManager.getDefaultSharedPreferences(this).getBoolean("try_265_encode", false)
        mBinding!!.cb.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView, isChecked -> PreferenceManager.getDefaultSharedPreferences(this@MainActivity).edit().putBoolean("try_265_encode", isChecked).apply() })
    }

    private fun getMediaStream(): Single<MediaStream> {
        val single = RxHelper.single(MediaStream.getBindedMediaStream(this, this), mediaStream)
        return if (mediaStream == null) {
            single.doOnSuccess { ms -> mediaStream = ms }
        } else {
            single
        }
    }


    fun startService() {

        // 启动服务...
        val intent = Intent(this, MediaStream::class.java)
        startService(intent)
        pushObserver()
    }

    @SuppressLint("CheckResult")
    fun pushObserver() {

        getMediaStream().subscribe({ ms ->
            Log.i("zc", "subscribe ")
            ms.observePushingState(this@MainActivity, Observer<MediaStream.PushingState> { pushingState ->
                var info: StringBuffer = StringBuffer("屏幕推送");
                Log.i("zc", "observePushingState ")
                if (pushingState.screenPushing) {
                    codeMethod.set("屏幕推送")
                    // 更改屏幕推送按钮状态.
                    Log.i("zc", "observePushingState isScreenPushing=$ms.isScreenPushing")
                    if (ms.isScreenPushing) {
                        switcherText.set("点击取消推送")
                    } else {
                        switcherText.set("点击推送屏幕")
                    }
                    (mBinding!!.tvPush as Button).isEnabled = true;
                }
                info.append(":\t" + pushingState.msg)
                if (pushingState.state > 0) {
                    info.append(pushingState.url)
                    info.append("\n")
                    if ("avc" == pushingState.videoCodec) {
                        info.append("视频编码方式：" + "H264硬编码")
                    } else if ("hevc" == pushingState.videoCodec) {
                        info.append("视频编码方式：" + "H265硬编码")
                    } else if ("x264" == pushingState.videoCodec) {
                        info.append("视频编码方式：" + "x264")
                    }
                    info.append("\n")
                    info.append("请用vlc播放器打开网络媒体，输入推流链接即可播放屏幕")
                }
                codeMethod.set(info.toString())
            })
        }, { Toast.makeText(this@MainActivity, "创建服务出错!", Toast.LENGTH_SHORT).show() })

    }


    // 推送屏幕.
    fun onPushScreen(view: View) {
        Log.i("zc", " onPushscreen")
        getMediaStream().subscribe(Consumer { mediaStream ->
            if (mediaStream.isScreenPushing) { // 正在推送，那取消推送。
                // 取消推送。
                mediaStream.stopPushScreen()
            } else {    // 没在推送，那启动推送。
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {     // lollipop 以前版本不支持。
                    return@Consumer
                }
                val mMpMngr = applicationContext.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(mMpMngr.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
                // 防止点多次.
                view.isEnabled = false
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            getMediaStream().subscribe { mediaStream -> mediaStream.pushScreen(resultCode, data, HOST, "554", "screen_live") }
        }
    }


}

