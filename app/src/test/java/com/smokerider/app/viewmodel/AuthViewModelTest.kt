package com.smokerider.app.viewmodel

import com.smokerider.app.data.model.User
import com.smokerider.app.data.repository.AuthRepository
import com.smokerider.app.testutils.MainDispatcherRule
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AuthViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    @RelaxedMockK
    lateinit var repo: AuthRepository

    private lateinit var vm: AuthViewModel

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        vm = AuthViewModel(repository = repo)
    }

    @After
    fun tearDown() { /* nothing */ }

    @Test
    fun `register con input valido imposta successMessage`() = runTest {
        coEvery {
            repo.registerUser(
                email = any(), password = any(), role = any(),
                city = any(), street = any(), identityDocument = any(),
                latitude = any(), longitude = any()
            )
        } returns Result.success(User(uid = "uX", email = "x@mail.com", role = "customer"))

        vm.register(email = "test@mail.com", password = "12345", role = "customer")
        advanceUntilIdle()

        val state = vm.ui.value
        assertFalse(state.loading)
        assertEquals("Registrazione avvenuta con successo. Effettua il login.", state.successMessage)
        assertNull(state.error)
        assertFalse(state.signedIn)
    }

    @Test
    fun `register fallito mostra messaggio di errore`() = runTest {
        coEvery {
            repo.registerUser(any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.failure(IllegalArgumentException("Email già registrata"))

        vm.register(email = "a@mail.com", password = "12345", role = "customer")
        advanceUntilIdle()

        val state = vm.ui.value
        assertFalse(state.loading)
        assertEquals("Email già registrata", state.error)
        assertFalse(state.signedIn)
    }

    @Test
    fun `login valido aggiorna signedIn e user e chiama updateOnlineStatus`() = runTest {
        coEvery { repo.loginUser("abc@mail.com", "12345") } returns
                Result.success(User(uid = "u1", email = "abc@mail.com", role = "customer"))
        coEvery { repo.updateOnlineStatus("u1", true) } returns Unit

        vm.login("abc@mail.com", "12345")
        advanceUntilIdle()

        val state = vm.ui.value
        assertTrue(state.signedIn)
        assertEquals("abc@mail.com", state.user?.email)

        coVerify { repo.updateOnlineStatus("u1", true) }
    }

    @Test
    fun `login fallito mostra errore`() = runTest {
        coEvery { repo.loginUser(any(), any()) } returns
                Result.failure(IllegalArgumentException("Credenziali non valide"))

        vm.login("abc@mail.com", "wrong")
        advanceUntilIdle()

        val state = vm.ui.value
        assertFalse(state.signedIn)
        assertEquals("Credenziali non valide", state.error)
    }

    @Test
    fun `clearError azzera campo error`() = runTest {
        vm.register(email = "x@mail.com", password = "", role = "customer")
        advanceUntilIdle()
        assertNotNull(vm.ui.value.error)

        vm.clearError()
        assertNull(vm.ui.value.error)
    }

    @Test
    fun `clearSuccessMessage azzera campo successMessage`() = runTest {
        coEvery { repo.registerUser(any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(User(uid = "uX", email = "x@mail.com", role = "customer"))
        vm.register(email = "a@mail.com", password = "12345", role = "customer")
        advanceUntilIdle()
        assertNotNull(vm.ui.value.successMessage)

        vm.clearSuccessMessage()
        assertNull(vm.ui.value.successMessage)
    }
}
