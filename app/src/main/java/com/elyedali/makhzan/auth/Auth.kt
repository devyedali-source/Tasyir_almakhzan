package com.elyedali.makhzan.auth

import android.app.Activity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.elyedali.makhzan.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

object Auth {
    /** حساب المسؤول الذي يستقبل طلبات التفعيل ويفعّل المحلات */
    const val ADMIN_EMAIL = "elyedalimoctar@gmail.com"

    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    fun isAdmin(): Boolean = auth.currentUser?.email?.trim()?.lowercase() == ADMIN_EMAIL

    /** تسجيل الدخول بحساب Google. يُرجع رسالة خطأ بالعربية أو null عند النجاح. */
    suspend fun signInWithGoogle(activity: Activity): String? {
        return try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(activity.getString(R.string.default_web_client_id))
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val result = CredentialManager.create(activity).getCredential(activity, request)
            val cred = result.credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val google = GoogleIdTokenCredential.createFrom(cred.data)
                auth.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).await()
                null
            } else {
                "نوع الحساب غير مدعوم"
            }
        } catch (e: GetCredentialCancellationException) {
            "تم إلغاء تسجيل الدخول"
        } catch (e: NoCredentialException) {
            "لا يوجد حساب Google على هذا الهاتف"
        } catch (e: Exception) {
            "تعذر تسجيل الدخول. تأكد من الاتصال بالإنترنت.\n(${e.javaClass.simpleName}: ${e.message ?: ""})"
        }
    }

    suspend fun signOut(activity: Activity) {
        auth.signOut()
        runCatching { CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest()) }
    }
}
