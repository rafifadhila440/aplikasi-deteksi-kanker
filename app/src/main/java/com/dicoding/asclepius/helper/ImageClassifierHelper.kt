package com.dicoding.asclepius.helper

import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import android.net.Uri
import android.graphics.ImageDecoder
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import com.dicoding.asclepius.R
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.classifier.Classifications
import org.tensorflow.lite.task.vision.classifier.ImageClassifier

@Suppress("DEPRECATION")
class ImageClassifierHelper(
    var threshold: Float = 0.1f,
    var maxResults: Int = 3,
    val modelName: String = "cancer_classification.tflite",
    val context: Context,
    val classifierListener: ClassifierListener?)
{
    private var imageClassifier: ImageClassifier? = null

    init {
        setupImageClassifier()
    }

    private fun setupImageClassifier() {
        // TODO: Menyiapkan Image Classifier untuk memproses gambar.
        val optionsBuilder = ImageClassifier.ImageClassifierOptions.builder()
            .setScoreThreshold(threshold)
            .setMaxResults(maxResults)
        val baseOptionsBuilder = BaseOptions.builder()
            .setNumThreads(4)
        optionsBuilder.setBaseOptions(baseOptionsBuilder.build())

        try {
            imageClassifier = ImageClassifier.createFromFileAndOptions(
                context,
                modelName,
                optionsBuilder.build()
            )
        }
        catch (e: IllegalAccessException) {
            classifierListener?.onError(context.getString(R.string.image_classifier_failed))
            Log.e(TAG, e.message.toString())
        }
    }

    fun classifyStaticImage(imageUri: Uri) {
        // TODO: mengklasifikasikan imageUri dari gambar statis.
        if (imageClassifier == null){
            setupImageClassifier()
        }

        val bitmap = uriToBitmap(imageUri)
        val tensorImage = processImage(bitmap)
        val results = classifyImage(tensorImage)
        notifyListener(results)
    }

    private fun uriToBitmap(image: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, image)
            ImageDecoder.decodeBitmap(source)
        }
        else {
            MediaStore.Images.Media.getBitmap(context.contentResolver, image)
        }.copy(Bitmap.Config.ARGB_8888, true)
    }

    private fun classifyImage(tensorImage: TensorImage): List<Classifications>? {
        val inferenceTime = SystemClock.uptimeMillis()
        val results = imageClassifier?.classify(tensorImage)
        val elapsedTime = SystemClock.uptimeMillis() - inferenceTime
        classifierListener?.let { listener ->
            listener.onResults(results, elapsedTime)
        }
        return results
    }

    private fun processImage(bitmap: Bitmap): TensorImage = ImageProcessor.Builder()
        .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
        .add(CastOp(DataType.UINT8))
        .build()
        .process(TensorImage.fromBitmap(bitmap))

    private fun notifyListener(results: List<Classifications>?) {
        classifierListener?.onResults(results, 0)
    }

    companion object {
        private const val TAG = "ImageClassifierHelper"
    }

    interface ClassifierListener {
        fun onError(error: String)
        fun onResults(
            results: List<Classifications>?,
            inferenceTime: Long
        )
    }
}