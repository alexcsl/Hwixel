package edu.bluejack252.hwixel.data.source.remote

import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun <T> Task<T>.awaitResult(): T = suspendCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { exception -> continuation.resumeWithException(exception) }
}
