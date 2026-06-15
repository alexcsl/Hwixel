package edu.bluejack25_2.hwixel.ui.auth.login

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import edu.bluejack25_2.hwixel.MainDispatcherRule
import edu.bluejack25_2.hwixel.R
import edu.bluejack25_2.hwixel.data.model.User
import edu.bluejack25_2.hwixel.data.repository.AuthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = FakeAuthRepository()
    private val viewModel = LoginViewModel(authRepository)

    @Test
    fun validCredentialsEmitSuccess() = runTest {
        viewModel.login("student@example.com", "Password1!")

        assertEquals(LoginUiState.Success, viewModel.uiState.value)
        assertEquals("student@example.com", authRepository.loginEmail)
    }

    @Test
    fun invalidEmailEmitsErrorBeforeRepositoryCall() = runTest {
        viewModel.login("not-an-email", "Password1!")

        assertEquals(LoginUiState.Error(R.string.error_invalid_email), viewModel.uiState.value)
        assertEquals(null, authRepository.loginEmail)
    }

    @Test
    fun emptyPasswordEmitsErrorBeforeRepositoryCall() = runTest {
        viewModel.login("student@example.com", "")

        assertEquals(LoginUiState.Error(R.string.error_empty_password), viewModel.uiState.value)
        assertEquals(null, authRepository.loginEmail)
    }

    @Test
    fun repositoryFailureEmitsMappedError() = runTest {
        authRepository.loginResult = Result.failure(IllegalStateException("failed"))

        viewModel.login("student@example.com", "Password1!")

        assertEquals(LoginUiState.Error(R.string.error_auth_default), viewModel.uiState.value)
    }

    private class FakeAuthRepository : AuthRepository {
        override val currentUserId: String? = null
        var loginEmail: String? = null
        var loginResult: Result<Unit> = Result.success(Unit)

        override suspend fun login(email: String, password: String): Result<Unit> {
            loginEmail = email
            return loginResult
        }

        override suspend fun register(
            email: String,
            password: String,
            name: String,
            studentId: String
        ): Result<User> {
            return Result.success(User(id = "uid"))
        }

        override fun logout() = Unit
    }
}
