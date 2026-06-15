package edu.bluejack25_2.hwixel.ui.auth.register

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import edu.bluejack25_2.hwixel.MainDispatcherRule
import edu.bluejack25_2.hwixel.R
import edu.bluejack25_2.hwixel.data.model.User
import edu.bluejack25_2.hwixel.data.repository.AuthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RegisterViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = FakeAuthRepository()
    private val viewModel = RegisterViewModel(authRepository)

    @Test
    fun weakPasswordRejectedBeforeRepositoryCall() = runTest {
        viewModel.register(
            name = "Student",
            studentId = "2500001",
            email = "student@example.com",
            password = "weak",
            confirmPassword = "weak"
        )

        assertEquals(RegisterUiState.Error(R.string.error_weak_password), viewModel.uiState.value)
        assertEquals(null, authRepository.registeredEmail)
    }

    @Test
    fun successfulRegisterWritesUserProfileThroughRepository() = runTest {
        viewModel.register(
            name = "Student",
            studentId = "2500001",
            email = "student@example.com",
            password = "Password1!",
            confirmPassword = "Password1!"
        )

        assertEquals(RegisterUiState.Success, viewModel.uiState.value)
        assertEquals("student@example.com", authRepository.registeredEmail)
        assertEquals("Student", authRepository.registeredName)
        assertEquals("2500001", authRepository.registeredStudentId)
    }

    @Test
    fun mismatchedPasswordsEmitErrorBeforeRepositoryCall() = runTest {
        viewModel.register(
            name = "Student",
            studentId = "2500001",
            email = "student@example.com",
            password = "Password1!",
            confirmPassword = "Password2!"
        )

        assertEquals(RegisterUiState.Error(R.string.error_password_mismatch), viewModel.uiState.value)
        assertEquals(null, authRepository.registeredEmail)
    }

    private class FakeAuthRepository : AuthRepository {
        override val currentUserId: String? = null
        var registeredEmail: String? = null
        var registeredName: String? = null
        var registeredStudentId: String? = null

        override suspend fun login(email: String, password: String): Result<Unit> {
            return Result.success(Unit)
        }

        override suspend fun register(
            email: String,
            password: String,
            name: String,
            studentId: String
        ): Result<User> {
            registeredEmail = email
            registeredName = name
            registeredStudentId = studentId
            return Result.success(User(id = "uid", name = name, studentId = studentId, email = email))
        }

        override fun logout() = Unit
    }
}
