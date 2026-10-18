package com.umicorp.autolotto.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umicorp.autolotto.AppContainer
import com.umicorp.autolotto.data.DepositAccount
import com.umicorp.autolotto.data.DepositResult
import com.umicorp.autolotto.dhlottery.DepositService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 예치금 충전 화면 UI 상태.
 */
sealed interface DepositUiState {
    data object Idle : DepositUiState
    data object Loading : DepositUiState
    data class Success(val account: DepositAccount) : DepositUiState
    data class Error(val message: String) : DepositUiState
}

/**
 * 예치금 충전 ViewModel.
 *
 * 케이뱅크 전용 가상계좌 발급 신청 및 발급된 계좌 정보 상태 관리,
 * 실시간 예치금 잔액 조회 연동을 담당합니다.
 */
class DepositViewModel(private val container: AppContainer) : ViewModel() {

    val isLoggedIn = container.isLoggedIn
    val balance = container.balance

    private val _selectedAmount = MutableStateFlow(10_000)
    val selectedAmount: StateFlow<Int> = _selectedAmount.asStateFlow()

    private val _customAmount = MutableStateFlow("")
    val customAmount: StateFlow<String> = _customAmount.asStateFlow()

    private val _uiState = MutableStateFlow<DepositUiState>(DepositUiState.Idle)
    val uiState: StateFlow<DepositUiState> = _uiState.asStateFlow()

    val presetAmounts = DepositService.ALLOWED_PRESET_AMOUNTS

    /** 프리셋 금액 선택 (5,000, 10,000, 20,000 등). */
    fun selectPresetAmount(amount: Int) {
        _selectedAmount.value = amount
        _customAmount.value = ""
        if (_uiState.value is DepositUiState.Error) {
            _uiState.value = DepositUiState.Idle
        }
    }

    /** 직접 입력 금액 설정. */
    fun setCustomAmount(input: String) {
        val digits = input.filter { it.isDigit() }
        _customAmount.value = digits
        val parsed = digits.toIntOrNull() ?: 0
        _selectedAmount.value = parsed
        if (_uiState.value is DepositUiState.Error) {
            _uiState.value = DepositUiState.Idle
        }
    }

    /** 현재 선택된 금액으로 가상계좌 발급 요청. */
    fun requestVirtualAccount() {
        val amount = _selectedAmount.value
        if (amount < DepositService.MIN_DEPOSIT_AMOUNT) {
            _uiState.value = DepositUiState.Error("최소 충전 금액은 5,000원입니다.")
            return
        }

        viewModelScope.launch {
            _uiState.value = DepositUiState.Loading
            when (val result = container.depositService.requestVirtualAccount(amount)) {
                is DepositResult.Success -> {
                    _uiState.value = DepositUiState.Success(result.account)
                }
                is DepositResult.Failure -> {
                    _uiState.value = DepositUiState.Error(result.message)
                }
            }
        }
    }

    /** 상태 초기화 (재시도 등). */
    fun reset() {
        _uiState.value = DepositUiState.Idle
    }

    /** 예치금 잔액 새로고침. */
    fun refreshBalance() {
        viewModelScope.launch {
            container.refreshBalance()
        }
    }
}
