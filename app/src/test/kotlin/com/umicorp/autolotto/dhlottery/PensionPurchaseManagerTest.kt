package com.umicorp.autolotto.dhlottery

import com.umicorp.autolotto.data.NumberConfig720
import com.umicorp.autolotto.data.Slot720
import com.umicorp.autolotto.data.Ticket720
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZonedDateTime

/** connPro는 멱등이 아니므로 결제가 몇 번 호출되는지와 미결·가드가 어떻게 남는지를 직접 검사한다. */
class PensionPurchaseManagerTest {

    private class FakeStore(
        var user: String? = "userA",
        var password: String? = "pw",
    ) : PensionStateStore {
        var lastRound = 0
        var lastOwner: String? = null
        var pending: String? = null
        var ledger: String? = null
        var failPendingSave = false
        var failGuardSave = false
        var failClear = false

        override fun pensionUserId() = user
        override fun pensionPassword() = password
        override fun getPensionLastRound() = lastRound
        override fun getPensionLastOwner() = lastOwner
        override fun setPensionLastPurchase(round: Int, owner: String): Boolean {
            if (failGuardSave) return false
            lastRound = round
            lastOwner = owner
            return true
        }
        override fun getPensionPending() = pending
        override fun setPensionPending(json: String): Boolean {
            if (failPendingSave) return false
            pending = json
            return true
        }
        override fun clearPensionPending(): Boolean {
            if (failClear) return false
            pending = null
            return true
        }
        override fun getPensionLedger() = ledger
        override fun setPensionLedger(json: String): Boolean {
            ledger = json
            return true
        }
    }

    private class FakeGateway(
        val onPurchase: (PensionOrder, Int) -> PurchaseResult720 = { order, round ->
            PurchaseResult720(round, tickets(round, order.amount / PensionOrder.UNIT_PRICE), order.amount)
        },
        val history: () -> List<Ticket720> = { emptyList() },
        val loginError: Exception? = null,
    ) : PensionGateway {
        var purchases = 0
        var logins = 0
        override suspend fun login(userId: String, password: String) {
            logins++
            loginError?.let { throw it }
        }
        override suspend fun purchase(order: PensionOrder, round: Int): PurchaseResult720 {
            purchases++
            return onPurchase(order, round)
        }
        override suspend fun recentTickets() = history()
    }

    private companion object {
        /** 판매 중인 금요일 낮. */
        val OPEN: ZonedDateTime = ZonedDateTime.of(2026, 11, 6, 12, 0, 0, 0, Round720.KST)
        val ROUND = Round720.getUpcomingDrawRound(OPEN)

        fun tickets(round: Int, count: Int) = List(count) { i ->
            Ticket720(round, (i % 5) + 1, "%06d".format(100000 + i), purchaseDate = LocalDateTime.of(2026, 11, 6, 12, 0))
        }

        val ONE_GAME = NumberConfig720.empty().copy(
            slots = listOf(Slot720.FullAuto, Slot720.Unset, Slot720.Unset, Slot720.Unset, Slot720.Unset),
        )
        val TWO_GAMES = NumberConfig720.empty().copy(
            slots = listOf(Slot720.FullAuto, Slot720.FullAuto, Slot720.Unset, Slot720.Unset, Slot720.Unset),
        )
        val UNKNOWN = DhlotteryException("서버 응답 오류 (/connPro.do) — 결과 불명, 재시도 금지")
    }

    private fun manager(store: FakeStore, now: ZonedDateTime = OPEN) = PensionPurchaseManager(store, Mutex()) { now }

    private fun pendingOf(store: FakeStore) = parsePendingAttempt(store.pending)

    private fun spent(store: FakeStore) = parseSpendLedger(store.ledger).sumOf { it.amount }

    @Test
    fun `successful purchase commits guard and ledger and clears pending`() = runBlocking {
        val store = FakeStore()
        val gateway = FakeGateway()
        val outcome = manager(store).purchase(gateway, PensionOrder.Configured(TWO_GAMES), ROUND)

        assertTrue(outcome is PensionOutcome.Purchased)
        assertEquals(1, gateway.purchases)
        assertEquals(ROUND, store.lastRound)
        assertEquals("userA", store.lastOwner)
        assertNull(store.pending)
        assertEquals(2000, spent(store))
    }

    @Test
    fun `pending is written before payment is requested`() = runBlocking {
        val store = FakeStore()
        var pendingAtPayment: String? = null
        val gateway = FakeGateway(onPurchase = { order, round ->
            pendingAtPayment = store.pending
            PurchaseResult720(round, tickets(round, 1), order.amount)
        })
        manager(store).purchase(gateway, PensionOrder.Configured(ONE_GAME), ROUND)

        assertEquals(ROUND, parsePendingAttempt(pendingAtPayment)?.round)
    }

    @Test
    fun `rejection releases pending without guard so the user can retry`() = runBlocking {
        val store = FakeStore()
        val gateway = FakeGateway(onPurchase = { _, _ -> throw DhlotteryException("주문을 만들지 못했습니다. 결제되지 않았습니다.") })
        val outcome = manager(store).purchase(gateway, PensionOrder.Configured(ONE_GAME), ROUND)

        assertTrue(outcome is PensionOutcome.Rejected)
        assertNull(store.pending)
        assertEquals(0, store.lastRound)
        assertEquals(0, spent(store))
    }

    @Test
    fun `unknown result does not retry and keeps guard and pending`() = runBlocking {
        val store = FakeStore()
        val gateway = FakeGateway(onPurchase = { _, _ -> throw UNKNOWN })
        val outcome = manager(store).purchase(gateway, PensionOrder.Configured(ONE_GAME), ROUND)

        assertTrue(outcome is PensionOutcome.ResultUnknown)
        assertEquals(1, gateway.purchases)
        assertEquals(ROUND, store.lastRound)
        assertTrue(pendingOf(store)!!.inLedger)
        assertEquals(1000, spent(store))
    }

    @Test
    fun `non dhlottery exception counts as unknown because payment may have gone out`() = runBlocking {
        val store = FakeStore()
        val gateway = FakeGateway(onPurchase = { _, _ -> throw IOException("timeout") })
        val outcome = manager(store).purchase(gateway, PensionOrder.Configured(ONE_GAME), ROUND)

        assertTrue(outcome is PensionOutcome.ResultUnknown)
        assertNotNull(store.pending)
    }

    @Test
    fun `unknown result is confirmed when history explains the whole amount`() = runBlocking {
        val store = FakeStore()
        val gateway = FakeGateway(onPurchase = { _, _ -> throw UNKNOWN }, history = { tickets(ROUND, 1) })
        val outcome = manager(store).purchase(gateway, PensionOrder.Configured(ONE_GAME), ROUND)

        val purchased = outcome as PensionOutcome.Purchased
        assertTrue(purchased.result.confirmedByHistory)
        assertNull(store.pending)
        assertEquals(ROUND, store.lastRound)
    }

    @Test
    fun `earlier ticket in the round does not settle an unknown extra purchase`() = runBlocking {
        // 첫 구매 1게임 성공 뒤 추가 구매가 불명이면 티켓 존재만으로 풀어선 안 된다.
        val store = FakeStore()
        manager(store).purchase(FakeGateway(), PensionOrder.Configured(ONE_GAME), ROUND)
        val gateway = FakeGateway(onPurchase = { _, _ -> throw UNKNOWN }, history = { tickets(ROUND, 1) })
        val outcome = manager(store).purchase(gateway, PensionOrder.ExtraAuto(1), ROUND)

        assertTrue(outcome is PensionOutcome.ResultUnknown)
        assertNotNull(store.pending)
    }

    @Test
    fun `retry after unknown never pays again in the same round`() = runBlocking {
        val store = FakeStore()
        manager(store).purchase(FakeGateway(onPurchase = { _, _ -> throw UNKNOWN }), PensionOrder.ExtraAuto(1), ROUND)

        val still = FakeGateway()
        assertEquals(PensionOutcome.PendingUnresolved, manager(store).purchase(still, PensionOrder.ExtraAuto(1), ROUND))
        assertEquals(0, still.purchases)

        val settled = FakeGateway(history = { tickets(ROUND, 1) })
        val outcome = manager(store).purchase(settled, PensionOrder.ExtraAuto(1), ROUND)
        assertTrue((outcome as PensionOutcome.Purchased).result.confirmedByHistory)
        assertEquals(0, settled.purchases)
        assertNull(store.pending)
    }

    @Test
    fun `failed history lookup keeps the attempt unresolved`() = runBlocking {
        val store = FakeStore()
        val gateway = FakeGateway(onPurchase = { _, _ -> throw UNKNOWN }, history = { throw IOException("down") })
        val outcome = manager(store).purchase(gateway, PensionOrder.Configured(ONE_GAME), ROUND)

        assertTrue(outcome is PensionOutcome.ResultUnknown)
        assertNotNull(store.pending)
    }

    @Test
    fun `partial success with a rejected remainder records only what was paid`() = runBlocking {
        val store = FakeStore()
        val gateway = FakeGateway(onPurchase = { _, round ->
            PurchaseResult720(round, tickets(round, 1), 1000, PartialFailure(1, DhlotteryException("번호 확인 실패")))
        })
        val outcome = manager(store).purchase(gateway, PensionOrder.Configured(TWO_GAMES), ROUND)

        assertTrue(outcome is PensionOutcome.Purchased)
        assertNull(store.pending)
        assertEquals(1000, spent(store))
    }

    @Test
    fun `partial success with an unknown remainder keeps pending and records the full attempt`() = runBlocking {
        val store = FakeStore()
        val gateway = FakeGateway(onPurchase = { _, round ->
            PurchaseResult720(round, tickets(round, 1), 1000, PartialFailure(1, UNKNOWN))
        })
        val outcome = manager(store).purchase(gateway, PensionOrder.Configured(TWO_GAMES), ROUND)

        assertTrue(outcome is PensionOutcome.Purchased)
        assertTrue(pendingOf(store)!!.inLedger)
        assertEquals(2000, spent(store))
    }

    @Test
    fun `failure to save the guard is reported and leaves pending as the last defence`() = runBlocking {
        val store = FakeStore().apply { failGuardSave = true }
        val outcome = manager(store).purchase(FakeGateway(), PensionOrder.Configured(ONE_GAME), ROUND)

        assertTrue(outcome is PensionOutcome.RecordFailed)
        assertNotNull(store.pending)
    }

    @Test
    fun `failure to save pending stops before payment`() = runBlocking {
        val store = FakeStore().apply { failPendingSave = true }
        val gateway = FakeGateway()
        val outcome = manager(store).purchase(gateway, PensionOrder.Configured(ONE_GAME), ROUND)

        assertEquals(PensionOutcome.PreparationFailed, outcome)
        assertEquals(0, gateway.purchases)
    }

    @Test
    fun `everything given up leaves no guard so numbers can be changed`() = runBlocking {
        val store = FakeStore()
        val gateway = FakeGateway(onPurchase = { _, round -> PurchaseResult720(round, emptyList(), 0) })
        val outcome = manager(store).purchase(gateway, PensionOrder.Configured(ONE_GAME), ROUND)

        assertEquals(PensionOutcome.NothingBought, outcome)
        assertNull(store.pending)
        assertEquals(0, store.lastRound)
    }

    @Test
    fun `pending from another account is never settled by this account`() = runBlocking {
        val store = FakeStore(user = "userB")
        store.pending = PendingAttempt(ROUND, OPEN.toLocalDate().toEpochDay(), 1000, false, "userA").toJson()
        val gateway = FakeGateway(history = { tickets(ROUND, 5) })

        assertEquals(PensionOutcome.PendingUnresolved, manager(store).purchase(gateway, PensionOrder.Configured(ONE_GAME), ROUND))
        assertFalse(manager(store).reconcile(tickets(ROUND, 5), "userB"))
        assertEquals(0, gateway.purchases)
        assertNotNull(store.pending)
    }

    @Test
    fun `tickets fetched for a previous account do not settle pending`() = runBlocking {
        val store = FakeStore()
        store.pending = PendingAttempt(ROUND, OPEN.toLocalDate().toEpochDay(), 1000, false, "userA").toJson()

        assertFalse(manager(store).reconcile(tickets(ROUND, 1), "userB"))
        assertNotNull(store.pending)
    }

    @Test
    fun `settling an old round does not lower a newer guard`() = runBlocking {
        val store = FakeStore().apply {
            lastRound = ROUND
            lastOwner = "userA"
            pending = PendingAttempt(ROUND - 1, OPEN.toLocalDate().toEpochDay() - 7, 1000, false, "userA").toJson()
        }

        assertTrue(manager(store).reconcile(tickets(ROUND - 1, 1), "userA"))
        assertEquals(ROUND, store.lastRound)
        assertNull(store.pending)
    }

    @Test
    fun `settlement that cannot clear pending does not count the amount twice`() = runBlocking {
        val store = FakeStore().apply {
            failClear = true
            pending = PendingAttempt(ROUND, OPEN.toLocalDate().toEpochDay(), 1000, false, "userA").toJson()
        }

        assertFalse(manager(store).reconcile(tickets(ROUND, 1), "userA"))
        assertTrue(pendingOf(store)!!.inLedger)
        assertEquals(1000, spent(store))

        store.failClear = false
        assertTrue(manager(store).reconcile(tickets(ROUND, 1), "userA"))
        assertEquals(1000, spent(store))
    }

    @Test
    fun `sale closed stops before login`() = runBlocking {
        val thursdayEvening = ZonedDateTime.of(2026, 11, 5, 18, 0, 0, 0, Round720.KST)
        val gateway = FakeGateway()
        val outcome = manager(FakeStore(), thursdayEvening).purchase(gateway, PensionOrder.Configured(ONE_GAME), null)

        assertEquals(PensionOutcome.SaleClosed, outcome)
        assertEquals(0, gateway.logins)
    }

    @Test
    fun `round shown in the dialog must match the round being sold`() = runBlocking {
        val gateway = FakeGateway()
        val outcome = manager(FakeStore()).purchase(gateway, PensionOrder.Configured(ONE_GAME), ROUND - 1)

        assertEquals(PensionOutcome.RoundChanged, outcome)
        assertEquals(0, gateway.purchases)
    }

    @Test
    fun `first purchase is blocked once bought but extra purchase is allowed`() = runBlocking {
        val store = FakeStore().apply { lastRound = ROUND; lastOwner = "userA" }
        val gateway = FakeGateway()

        assertEquals(PensionOutcome.AlreadyPurchased, manager(store).purchase(gateway, PensionOrder.Configured(ONE_GAME), ROUND))
        assertEquals(0, gateway.purchases)
        assertTrue(manager(store).purchase(gateway, PensionOrder.ExtraSet, ROUND) is PensionOutcome.Purchased)
        assertEquals(1, gateway.purchases)
    }

    @Test
    fun `guard of another account does not block this account`() = runBlocking {
        val store = FakeStore(user = "userB").apply { lastRound = ROUND; lastOwner = "userA" }
        val outcome = manager(store).purchase(FakeGateway(), PensionOrder.Configured(ONE_GAME), ROUND)

        assertTrue(outcome is PensionOutcome.Purchased)
        assertEquals("userB", store.lastOwner)
    }

    @Test
    fun `missing credentials and failed login never reach payment`() = runBlocking {
        val noUser = FakeGateway()
        assertEquals(
            PensionOutcome.NotLoggedIn,
            manager(FakeStore(user = null)).purchase(noUser, PensionOrder.Configured(ONE_GAME), ROUND),
        )
        assertEquals(0, noUser.logins)

        val store = FakeStore()
        val badLogin = FakeGateway(loginError = DhlotteryException("로그인 실패"))
        assertTrue(manager(store).purchase(badLogin, PensionOrder.Configured(ONE_GAME), ROUND) is PensionOutcome.LoginFailed)
        assertEquals(0, badLogin.purchases)
        assertNull(store.pending)
    }

    @Test
    fun `failure classification treats only unmarked dhlottery errors as not paid`() {
        assertTrue(classifyPensionFailure(DhlotteryException("번호 확인 실패")) is PensionFailure.Rejected)
        assertTrue(classifyPensionFailure(UNKNOWN) is PensionFailure.Unknown)
        assertTrue(classifyPensionFailure(IOException("x")) is PensionFailure.Unknown)
    }

    @Test
    fun `corrupt pending is treated as absent and valid pending round trips`() {
        assertNull(parsePendingAttempt("{broken"))
        assertNull(parsePendingAttempt("""{"amount":1000}"""))
        val attempt = PendingAttempt(340, 20000L, 3000, true, "userA")
        assertEquals(attempt, parsePendingAttempt(attempt.toJson()))
    }
}
