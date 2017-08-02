@file:JvmName("RxActivity")

package com.hendraanggrian.rx.activity

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SparseArray
import com.hendraanggrian.kota.content.isNotAvailable
import com.hendraanggrian.kota.util.hasKey
import com.hendraanggrian.rx.activity.internal.ActivityResultEmitter
import com.hendraanggrian.rx.activity.internal.ActivityStartable
import io.reactivex.Observable
import java.util.*

/**
 * @author Hendra Anggrian (hendraanggrian@gmail.com)
 */
object RxActivity {

    /**
     * Max range of request code to pass [android.support.v4.app.FragmentActivity.checkForValidRequestCode] precondition.
     */
    private val MAX_REQUEST_CODE = 65535 // 16-bit int

    /**
     * Weak reference of Random to generate random number
     * below [RxActivity.MAX_REQUEST_CODE]
     * and not already queued in [RxActivity.QUEUES].
     */
    internal var RANDOM: Random? = null

    /**
     * Collection of reactive emitters that will emits one-by-one on activity result.
     * Once emitted, emitter will no longer exists in this collection.
     */
    internal val QUEUES = SparseArray<ActivityResultEmitter<*>>()

    internal fun <T> createStarter(type: Int, startable: ActivityStartable, intent: Intent, options: Bundle?): Observable<T> = Observable.create { e ->
        if (intent.isNotAvailable(startable.context)) {
            e.onError(ActivityNotFoundException("No activity for this intent found."))
        } else {
            if (RANDOM == null) {
                RANDOM = Random()
            }
            val requestCode = RANDOM!!.nextInt(MAX_REQUEST_CODE)
            QUEUES.append(requestCode, ActivityResultEmitter.getInstance(type, e))
            if (Build.VERSION.SDK_INT >= 16) {
                startable.startActivityForResult(intent, requestCode, options)
            } else {
                startable.startActivityForResult(intent, requestCode)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (QUEUES.hasKey(requestCode)) {
            val e = QUEUES.get(requestCode) as ActivityResultEmitter<Any>
            if (!e.isDisposed) {
                val result = ActivityResult(requestCode, resultCode, data)
                when (e.type) {
                    ActivityResultEmitter.TYPE_RESULT -> e.onNext(result)
                    ActivityResultEmitter.TYPE_OK -> if (resultCode == Activity.RESULT_OK)
                        e.onNext(result.data!!)
                    else
                        e.onError(ActivityCanceledException(result.toString()))
                }
                e.onComplete()
            }
            QUEUES.remove(requestCode)
        }
    }
}