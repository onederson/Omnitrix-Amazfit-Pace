package com.aistudio.omnitrix.smartwatch

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import kotlin.math.abs

class MainActivity : Activity(), GestureDetector.OnGestureListener {

    private lateinit var txtStatus: TextView
    private lateinit var txtAlien: TextView
    private lateinit var mainLayout: RelativeLayout
    private lateinit var imgOmnitrix: ImageView
    private lateinit var imgLogo: ImageView
    private lateinit var selectionLayout: View
    private lateinit var gestureDetector: GestureDetector
    private lateinit var vibrator: Vibrator
    private val handler = Handler(Looper.getMainLooper())

    private val aliens = Array(34) { "Alien ${it + 1}" }
    private var currentIndex = 0
    private var assetImages = ArrayList<String>()
    private var downX = 0f
    private var downY = 0f

    enum class State {
        INACTIVE, SELECTING, TRANSFORMED, LOCKED
    }
    private var currentState = State.INACTIVE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtStatus = findViewById(R.id.txt_status)
        txtAlien = findViewById(R.id.txt_alien)
        mainLayout = findViewById(R.id.main_layout)
        imgOmnitrix = findViewById(R.id.img_omnitrix)
        imgLogo = findViewById(R.id.img_logo)
        selectionLayout = findViewById(R.id.selection_layout)
        
        gestureDetector = GestureDetector(this, this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        mainLayout.setOnTouchListener { _, event ->
            val handled = gestureDetector.onTouchEvent(event)
            if (handled) {
                return@setOnTouchListener true
            }
            if (event.action == MotionEvent.ACTION_UP) {
                val upX = event.x
                val upY = event.y
                val diffX = upX - downX
                val diffY = upY - downY
                android.util.Log.d("Omnitrix", "onTouchListener ACTION_UP: downX=$downX, upX=$upX, diffX=$diffX, diffY=$diffY")
                val maxIndex = if (assetImages.isNotEmpty()) assetImages.size else aliens.size
                if (currentState == State.SELECTING && abs(diffX) > abs(diffY) && abs(diffX) > 60) {
                    if (diffX > 0) {
                        currentIndex = if (currentIndex > 0) currentIndex - 1 else maxIndex - 1
                    } else {
                        currentIndex = if (currentIndex < maxIndex - 1) currentIndex + 1 else 0
                    }
                    android.util.Log.d("Omnitrix", "Swipe detected: new currentIndex=$currentIndex")
                    vibrate(30)
                    updateUI()
                    return@setOnTouchListener true
                }
            }
            true
        }

        loadAssetImages()
        updateUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun updateUI() {
        when (currentState) {
            State.INACTIVE -> {
                mainLayout.setBackgroundColor(Color.BLACK)
                imgLogo.setImageResource(R.drawable.ic_omnitrix_inactive)
                imgLogo.visibility = View.VISIBLE
                selectionLayout.visibility = View.GONE
            }
            State.SELECTING -> {
                imgOmnitrix.setBackgroundResource(0)
                
                android.util.Log.d("Omnitrix", "updateUI State.SELECTING: currentIndex=$currentIndex, assetImages size=${assetImages.size}")
                if (assetImages.isNotEmpty()) {
                    imgLogo.visibility = View.VISIBLE
                    selectionLayout.visibility = View.GONE
                    mainLayout.setBackgroundColor(Color.BLACK)
                    
                    if (currentIndex >= assetImages.size) {
                        currentIndex = 0
                    }
                    val currentFileName = assetImages[currentIndex]
                    txtAlien.text = currentFileName.substringBeforeLast('.').uppercase()
                    android.util.Log.d("Omnitrix", "Loading image full screen: $currentFileName")
                    
                    try {
                        val bitmap = assets.open("aliens/$currentFileName").use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }
                        if (bitmap != null) {
                            imgLogo.setImageBitmap(bitmap)
                        } else {
                            imgLogo.setImageResource(R.drawable.ic_omnitrix_symbol_locked)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        imgLogo.setImageResource(R.drawable.ic_omnitrix_symbol_locked)
                    }
                } else {
                    imgLogo.visibility = View.GONE
                    selectionLayout.visibility = View.VISIBLE
                    mainLayout.setBackgroundResource(R.drawable.ic_omnitrix_active)
                    
                    if (currentIndex >= aliens.size) {
                        currentIndex = 0
                    }
                    txtAlien.text = aliens[currentIndex]
                    val resName = "ic_alien_custom_${currentIndex + 1}"
                    val drawableRes = resources.getIdentifier(resName, "drawable", packageName)
                    val finalRes = if (drawableRes != 0) drawableRes else R.drawable.ic_omnitrix_symbol_locked
                    imgOmnitrix.setImageResource(finalRes)
                }
            }
            State.TRANSFORMED -> {
                imgLogo.visibility = View.VISIBLE
                imgLogo.setImageResource(R.drawable.ic_omnitrix_transformed)
                selectionLayout.visibility = View.GONE
                mainLayout.setBackgroundColor(Color.BLACK)
            }
            State.LOCKED -> {
                imgLogo.visibility = View.VISIBLE
                imgLogo.setImageResource(R.drawable.ic_omnitrix_locked)
                selectionLayout.visibility = View.GONE
                mainLayout.setBackgroundColor(Color.BLACK)
            }
        }
    }

    private fun transform() {
        if (currentState != State.SELECTING) return

        currentState = State.TRANSFORMED
        vibrate(500) 
        updateUI()

        handler.postDelayed({
            if (currentState == State.TRANSFORMED) {
                detransform()
            }
        }, 10000)
    }

    private fun detransform() {
        if (currentState != State.TRANSFORMED) return

        currentState = State.LOCKED
        vibrate(800) 
        updateUI()
        
        handler.postDelayed({
            if (currentState == State.LOCKED) {
                currentState = State.SELECTING
                updateUI()
            }
        }, 5000)
    }

    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        if (currentState != State.SELECTING) return false
        val startX = e1?.x ?: downX
        val startY = e1?.y ?: downY
        val diffX = e2.x - startX
        val diffY = e2.y - startY
        android.util.Log.d("Omnitrix", "onFling: startX=$startX, e2.x=${e2.x}, diffX=$diffX, velocityX=$velocityX")
        
        val maxIndex = if (assetImages.isNotEmpty()) assetImages.size else aliens.size
        if (abs(diffX) > abs(diffY) && abs(diffX) > 50 && abs(velocityX) > 50) {
            if (diffX > 0) {
                currentIndex = if (currentIndex > 0) currentIndex - 1 else maxIndex - 1
            } else {
                currentIndex = if (currentIndex < maxIndex - 1) currentIndex + 1 else 0
            }
            android.util.Log.d("Omnitrix", "onFling triggered: new currentIndex=$currentIndex")
            vibrate(30)
            updateUI()
            return true
        }
        return false
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        when (currentState) {
            State.INACTIVE -> {
                currentState = State.SELECTING
                vibrate(100)
                updateUI()
            }
            State.SELECTING -> {
                transform()
            }
            State.TRANSFORMED -> {
                detransform()
            }
            State.LOCKED -> {
                // Não faz nada no estado travado
            }
        }
        return true
    }

    private fun vibrate(duration: Long) {
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(duration)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    override fun onDown(e: MotionEvent): Boolean {
        downX = e.x
        downY = e.y
        android.util.Log.d("Omnitrix", "onDown: downX=$downX, downY=$downY")
        return true
    }
    override fun onShowPress(e: MotionEvent) {}
    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean = false
    override fun onLongPress(e: MotionEvent) {}

    private fun loadAssetImages() {
        assetImages.clear()
        try {
            val files = assets.list("aliens")
            if (files != null) {
                val imageExtensions = setOf("png", "jpg", "jpeg", "bmp", "webp")
                val filteredFiles = files.filter { file ->
                    val ext = file.substringAfterLast('.', "").lowercase()
                    imageExtensions.contains(ext)
                }.sortedWith { f1, f2 ->
                    val n1 = f1.substringBeforeLast('.').toIntOrNull()
                    val n2 = f2.substringBeforeLast('.').toIntOrNull()
                    if (n1 != null && n2 != null) {
                        n1.compareTo(n2)
                    } else {
                        f1.compareTo(f2, ignoreCase = true)
                    }
                }
                assetImages.addAll(filteredFiles)
                android.util.Log.d("Omnitrix", "Loaded ${assetImages.size} images from assets/aliens")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
