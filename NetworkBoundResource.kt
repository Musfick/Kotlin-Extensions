package com.ezzetechltd.briiapp.utils

import kotlinx.coroutines.flow.*
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException


sealed class ErrorType {
    data class UnKnownError(val msg:String): ErrorType()
    data class IOError(val msg:String): ErrorType()
    data class UnauthorizedError(val msg:String): ErrorType()
    data class TimeOurError(val msg:String): ErrorType()
    data class BadRequest(val msg: String): ErrorType()
    data class HTTPError(val msg: String): ErrorType()
    data class ConflictError(val msg: String) : ErrorType()
    data class Forbidden(val msg: String) : ErrorType()
    data class NotFound(val msg: String) : ErrorType()
    data class InternalServerError(val msg: String) : ErrorType()
}

inline fun <ResultType, RequestType> networkBoundResource(
        crossinline query: () -> Flow<ResultType>,
        crossinline fetch: suspend () -> RequestType,
        crossinline saveFetchResult: suspend (RequestType) -> Unit,
        crossinline onFetchFailed: (Exception) -> Unit = { Unit },
        crossinline shouldFetch: (ResultType) -> Boolean = { true }
) = flow<Resource<ResultType>> {
    emit(Resource.loading(null))
    val data = query().first()

    val flow = if (shouldFetch(data)) {
        emit(Resource.loading(data))
        try {
            saveFetchResult(fetch())
            query().map { Resource.success(it) }
        } catch (e:HttpException) {
            onFetchFailed(e)
            query().map { Resource.error(ErrorType.HTTPError(e.localizedMessage!!) , data) }
        }catch (e:IOException){
            onFetchFailed(e)
            query().map { Resource.error(ErrorType.IOError(e.localizedMessage!!) , data) }
        }
    } else {
        query().map { Resource.success(it) }
    }

    emitAll(flow)
}

fun <T : Any> safeApiCall(onLoading: () -> T? = {null}, fetch: suspend () -> T, onSuccess: (T) -> Unit = {}, onFailed: () -> T? = {null}) = flow<Resource<T>> {

    emit(Resource.loading(onLoading.invoke()))
    try {
        val response = fetch.invoke()
        onSuccess(response)
        emit(Resource.success(response))
    }catch (e:Exception){
        emit(Resource.error(getErrorType(e), onFailed.invoke()))
    }
}

fun getErrorType(e: Exception): ErrorType {
    return when (e) {
        is HttpException -> getHttpError(e.code())
        is SocketTimeoutException -> ErrorType.TimeOurError(e.localizedMessage!!)
        is IOException -> ErrorType.IOError(e.localizedMessage!!)
        else -> ErrorType.UnKnownError(e.localizedMessage!!)
    }
}

fun getHttpError(code: Int): ErrorType {
    return when (code) {
        400 -> ErrorType.BadRequest("Bed Request")
        401 -> ErrorType.UnauthorizedError("Unauthorized")
        403 -> ErrorType.Forbidden("Forbidden")
        404 -> ErrorType.NotFound("Not Found")
        505 -> ErrorType.InternalServerError("Internal Server Error")
        else -> ErrorType.UnKnownError("UnknownError")
    }
}

fun copyStreamToFile(inputStream: InputStream, outputStream: FileOutputStream) {
    inputStream.use { input ->
        outputStream.use { output ->
            val buffer = ByteArray(4 * 1024) // buffer size
            while (true) {
                val byteCount = input.read(buffer)
                if (byteCount < 0) break
                output.write(buffer, 0, byteCount)
            }
            output.flush()
        }
    }
}
