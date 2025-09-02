package com.smokerider.app.viewmodel

import com.smokerider.app.data.model.Product
import com.smokerider.app.data.repository.FirestoreOrders
import com.smokerider.app.testutils.MainDispatcherRule
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class OrderViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    @RelaxedMockK
    lateinit var repo: FirestoreOrders

    private lateinit var vm: OrderViewModel

    private fun p(id: String, name: String, price: Double) =
        Product(id = id, name = name, price = price)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        vm = OrderViewModel(repository = repo)
    }

    @After
    fun tearDown() { /* nothing */ }

    @Test
    fun `addItem inserisce un nuovo articolo e aggiorna total e itemCount`() = runTest {
        vm.addItem(p("p1","Marlboro", 5.0), quantity = 2)
        vm.addItem(p("p2","Camel",    4.5), quantity = 1)

        assertEquals(14.5, vm.total.value, 1e-6)
        assertEquals(3, vm.itemCount.value)

        val items = vm.items.value
        assertEquals(2, items.size)
        assertEquals("p1", items[0].productId); assertEquals(2, items[0].quantity)
        assertEquals("p2", items[1].productId); assertEquals(1, items[1].quantity)
    }

    @Test
    fun `addItem su prodotto esistente somma le quantita`() = runTest {
        vm.addItem(p("p1","Marlboro", 5.0), 1)
        vm.addItem(p("p1","Marlboro", 5.0), 3)

        val items = vm.items.value
        assertEquals(1, items.size)
        assertEquals(4, items[0].quantity)
        assertEquals(20.0, vm.total.value, 1e-6)
        assertEquals(4, vm.itemCount.value)
    }

    @Test
    fun `setQuantity imposta la quantita assoluta e rimuove se 0 o meno`() = runTest {
        vm.addItem(p("p1","Marlboro", 5.0), 2)
        vm.setQuantity("p1", 5)
        assertEquals(25.0, vm.total.value, 1e-6)
        assertEquals(5, vm.itemCount.value)

        vm.setQuantity("p1", 0)
        assertTrue(vm.items.value.isEmpty())
        assertEquals(0.0, vm.total.value, 1e-6)
        assertEquals(0, vm.itemCount.value)
    }

    @Test
    fun `increment e decrement modificano la quantita e rimuovono quando scende a zero`() = runTest {
        vm.addItem(p("p1","Marlboro", 5.0), 1)
        vm.increment("p1")
        assertEquals(10.0, vm.total.value, 1e-6); assertEquals(2, vm.itemCount.value)

        vm.decrement("p1")
        assertEquals(5.0, vm.total.value, 1e-6); assertEquals(1, vm.itemCount.value)

        vm.decrement("p1")
        assertTrue(vm.items.value.isEmpty())
        assertEquals(0.0, vm.total.value, 1e-6); assertEquals(0, vm.itemCount.value)
    }

    @Test
    fun `removeItem elimina completamente un prodotto`() = runTest {
        vm.addItem(p("p1","Marlboro", 5.0), 2)
        vm.addItem(p("p2","Camel",    4.5), 1)
        assertEquals(14.5, vm.total.value, 1e-6); assertEquals(3, vm.itemCount.value)

        vm.removeItem("p1")
        val items = vm.items.value
        assertEquals(1, items.size)
        assertEquals("p2", items[0].productId)
        assertEquals(4.5, vm.total.value, 1e-6); assertEquals(1, vm.itemCount.value)
    }

    @Test
    fun `clearOrder svuota il carrello e azzera total e itemCount`() = runTest {
        vm.addItem(p("p1","Marlboro", 5.0), 2)
        vm.addItem(p("p2","Camel",    4.5), 1)
        assertTrue(vm.items.value.isNotEmpty())

        vm.clearOrder()
        assertTrue(vm.items.value.isEmpty())
        assertEquals(0.0, vm.total.value, 1e-6); assertEquals(0, vm.itemCount.value)
    }

    @Test
    fun `createOrder con carrello vuoto chiama onComplete false e non invoca repository`() = runTest {
        var ok: Boolean? = null
        var oid: String? = null

        vm.createOrder(clientId = "c1") { success, id ->
            ok = success; oid = id
        }
        advanceUntilIdle()

        assertEquals(false, ok)
        assertNull(oid)

        verify(exactly = 0) { repo.createOrder(any(), any(), any(), any()) }
    }

    @Test
    fun `createOrder con carrello non vuoto chiama repo e svuota su successo`() = runTest {
        vm.addItem(p("p1","Marlboro", 5.0), 2)
        vm.addItem(p("p2","Camel",    4.5), 1)

        val clientIdSlot = slot<String>()
        val itemsSlot    = slot<List<com.smokerider.app.data.model.OrderItem>>()
        val totalSlot    = slot<Double>()
        val cbSlot       = slot<(Boolean, String?) -> Unit>()

        every {
            repo.createOrder(capture(clientIdSlot), capture(itemsSlot), capture(totalSlot), capture(cbSlot))
        } answers {
            cbSlot.captured(true, "order-123")
        }

        var ok: Boolean? = null
        var oid: String? = null
        vm.createOrder(clientId = "c1") { success, id ->
            ok = success; oid = id
        }
        advanceUntilIdle()

        assertEquals("c1", clientIdSlot.captured)
        assertEquals(2, itemsSlot.captured.size)
        assertEquals(3, itemsSlot.captured.sumOf { it.quantity })
        assertTrue(abs(totalSlot.captured - 14.5) < 1e-6)

        assertEquals(true, ok)
        assertEquals("order-123", oid)

        assertTrue(vm.items.value.isEmpty())
        assertEquals(0.0, vm.total.value, 1e-6); assertEquals(0, vm.itemCount.value)
    }

    @Test
    fun `createOrder fallito NON svuota il carrello`() = runTest {
        vm.addItem(p("p1","Marlboro", 5.0), 1)

        val cbSlot = slot<(Boolean, String?) -> Unit>()
        every { repo.createOrder(any(), any(), any(), capture(cbSlot)) } answers {
            cbSlot.captured(false, null)
        }

        var ok: Boolean? = null
        vm.createOrder(clientId = "c1") { success, _ -> ok = success }
        advanceUntilIdle()

        verify(exactly = 1) { repo.createOrder(any(), any(), any(), any()) }
        assertEquals(false, ok)

        assertFalse(vm.items.value.isEmpty())
        assertEquals(5.0, vm.total.value, 1e-6); assertEquals(1, vm.itemCount.value)
    }
}
