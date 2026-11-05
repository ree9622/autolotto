package com.umicorp.autolotto.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.umicorp.autolotto.data.SecureStore
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 네이티브 AlarmManager 자가연쇄 스케줄러 (원본 `android_alarm_manager_plus` 기반 SchedulerService 포트).
 *
 * 원본은 별도 isolate에서 `oneShotAt`을 쓰고 각 콜백 끝에서 다음 주를 재등록했다(자가연쇄).
 * 네이티브는 `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP)` + BroadcastReceiver로 같은 의미를
 * 재현한다. 실제 재등록은 각 Worker가 작업 끝에서 호출한다(원본 콜백 말미 재등록과 1:1).
 *
 * 알람 id: 1001 자동구매(사용자설정 요일/시/분), 1002 결과확인(고정 토 21:00),
 * 1003 연금복권 자동구매(사용자설정), 1004 연금복권 결과확인(고정 목 21:00). 두 복권은 서로 독립이다.
 *
 * 시각은 기기 로컬 존(원본 `DateTime.now()`/`DateTime()`와 동일 — 사용자가 고른 벽시계대로 발화).
 * 한국 기기에선 KST. 회차계산만 KST 고정(PurchaseService).
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)
    private val store = SecureStore(context)

    /** 자동구매 알람(1001) 등록 — SecureStore의 사용자설정 요일/시/분 → 다음 발생. */
    fun scheduleAutoPurchase() {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val triggerAt = AlarmTimes.nextAutoPurchaseMillis(
            store.getAutoPurchaseDay(),
            store.getAutoPurchaseHour(),
            store.getAutoPurchaseMinute(),
            now,
        )
        setExact(REQ_PURCHASE, triggerAt, AutoPurchaseReceiver::class.java)
    }

    /** 결과확인 알람(1002) 등록 — 다음 토요일 21:00 고정. */
    fun scheduleCheckResult() {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val triggerAt = AlarmTimes.nextSaturday21Millis(now)
        setExact(REQ_CHECK_RESULT, triggerAt, CheckResultReceiver::class.java)
    }

    /** 연금복권 자동구매 알람(1003) 등록 — 연금복권 전용 요일/시/분의 다음 발생. */
    fun schedulePensionAutoPurchase() {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val triggerAt = AlarmTimes.nextAutoPurchaseMillis(
            store.getPensionAutoDay(),
            store.getPensionAutoHour(),
            store.getPensionAutoMinute(),
            now,
        )
        setExact(REQ_PENSION_PURCHASE, triggerAt, PensionAutoPurchaseReceiver::class.java)
    }

    /** 연금복권 결과확인 알람(1004) 등록 — 다음 목요일 21:00 고정. */
    fun schedulePensionCheckResult() {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        setExact(REQ_PENSION_CHECK, AlarmTimes.nextThursday21Millis(now), PensionCheckResultReceiver::class.java)
    }

    /** 알람 id 취소 (1001~1004). */
    fun cancel(reqCode: Int) {
        val pi = pendingIntent(reqCode, receiverFor(reqCode), PendingIntent.FLAG_NO_CREATE)
        if (pi != null) {
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    /** 6/45 알람만 취소 — 6/45 자동구매를 꺼도 연금복권 자동구매는 계속된다. */
    fun cancelLotto() {
        cancel(REQ_PURCHASE)
        cancel(REQ_CHECK_RESULT)
    }

    /** 연금복권 알람만 취소. */
    fun cancelPension() {
        cancel(REQ_PENSION_PURCHASE)
        cancel(REQ_PENSION_CHECK)
    }

    fun cancelAll() {
        cancelLotto()
        cancelPension()
    }

    /** 부팅 후 복원 / 일괄 재등록 — 복권별로 자동구매가 켜져 있을 때만 알람을 다시 건다. */
    fun rescheduleAll() {
        if (store.getAutoEnabled()) {
            scheduleAutoPurchase()
            scheduleCheckResult()
        }
        if (store.getPensionAutoEnabled()) {
            schedulePensionAutoPurchase()
            schedulePensionCheckResult()
        }
    }

    private fun setExact(reqCode: Int, triggerAtMillis: Long, receiver: Class<*>) {
        val pi = pendingIntent(reqCode, receiver, PendingIntent.FLAG_UPDATE_CURRENT)!!
        // 정확 알람 권한이 없으면(사용자가 끔) setExact*는 SecurityException → 비정확(allowWhileIdle)으로 폴백.
        // ponytail: 폴백 한도는 "약간의 지연 허용". 정확발화가 필수면 설정에서 권한 유도(ExactAlarmPermission).
        if (ExactAlarmPermission.canScheduleExactAlarms(context)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun pendingIntent(reqCode: Int, receiver: Class<*>, flag: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            reqCode,
            Intent(context, receiver),
            flag or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun receiverFor(reqCode: Int): Class<*> = when (reqCode) {
        REQ_CHECK_RESULT -> CheckResultReceiver::class.java
        REQ_PENSION_PURCHASE -> PensionAutoPurchaseReceiver::class.java
        REQ_PENSION_CHECK -> PensionCheckResultReceiver::class.java
        else -> AutoPurchaseReceiver::class.java
    }

    companion object {
        const val REQ_PURCHASE = 1001
        const val REQ_CHECK_RESULT = 1002
        const val REQ_PENSION_PURCHASE = 1003
        const val REQ_PENSION_CHECK = 1004
    }
}
