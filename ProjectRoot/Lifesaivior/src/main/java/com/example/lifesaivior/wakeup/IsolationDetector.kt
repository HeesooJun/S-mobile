package com.example.lifesaivior.wakeup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.ServiceState
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat

class IsolationDetector(
    private val context: Context,
    private val onIsolated: () -> Unit, // 고립 감지 시 실행할 함수
    private val onRecovered: () -> Unit // 복구 시 실행할 함수 (선택 사항)
) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private var isInternetConnected = false
    private var isCellularServiceAvailable = false

    // 네트워크 상태 감지 콜백
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            isInternetConnected = true
            checkStatus()
        }

        override fun onLost(network: Network) {
            isInternetConnected = false
            checkStatus()
        }
    }

    // 통신 상태 감지 (API 31 이상용)
    private var telephonyCallback: Any? = null

    // 통신 상태 감지 (API 30 이하용)
    private var phoneStateListener: PhoneStateListener? = null

    fun startMonitoring() {
        // 1. 인터넷 모니터링 시작
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        // 2. 초기 인터넷 상태 확인
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        isInternetConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        // 3. 셀룰러(통화) 모니터링 시작
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            registerTelephonyListener()
        } else {
            // 권한이 없으면 일단 연결된 것으로 간주하거나, 권한 요청 로직 필요
            isCellularServiceAvailable = true
        }

        checkStatus()
    }

    fun stopMonitoring() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            unregisterTelephonyListener()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun registerTelephonyListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 (API 31) 이상
            telephonyCallback = object : TelephonyCallback(), TelephonyCallback.ServiceStateListener {
                override fun onServiceStateChanged(serviceState: ServiceState) {
                    updateCellularStatus(serviceState)
                }
            }
            telephonyManager.registerTelephonyCallback(context.mainExecutor, telephonyCallback as TelephonyCallback)
        } else {
            // Android 11 (API 30) 이하
            phoneStateListener = object : PhoneStateListener() {
                override fun onServiceStateChanged(serviceState: ServiceState) {
                    updateCellularStatus(serviceState)
                }
            }
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE)
        }
    }

    private fun unregisterTelephonyListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let {
                telephonyManager.unregisterTelephonyCallback(it as TelephonyCallback)
            }
        } else {
            phoneStateListener?.let {
                telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
            }
        }
    }

    private fun updateCellularStatus(serviceState: ServiceState) {
        val state = serviceState.state
        // STATE_IN_SERVICE: 정상 서비스 중
        // STATE_EMERGENCY_ONLY: 긴급 통화만 가능 (완전 고립으로 볼지 여부는 선택)
        // STATE_OUT_OF_SERVICE: 서비스 불가 (신호 없음)
        // STATE_POWER_OFF: 비행기 모드 등 꺼짐

        isCellularServiceAvailable = (state == ServiceState.STATE_IN_SERVICE)
        // 만약 긴급 통화도 안 되는 상황만 잡고 싶다면 아래 조건 사용:
        // isCellularServiceAvailable = (state == ServiceState.STATE_IN_SERVICE || state == ServiceState.STATE_EMERGENCY_ONLY)

        checkStatus()
    }

    private fun checkStatus() {
        // 인터넷도 없고(False) + 셀룰러도 없을 때(False) -> 고립(True)
        if (!isInternetConnected && !isCellularServiceAvailable) {
            onIsolated() // -> 여기서 센서/음성 감지 시작!
        } else {
            onRecovered() // -> 다시 연결되면 감지 중단
        }
    }
}
