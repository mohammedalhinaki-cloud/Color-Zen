package com.colorzen.puzzle.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.colorzen.puzzle.core.PlayerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Play connection state, surfaced in the shop so it never lies about prices. */
sealed interface BillingStatus {
    data object Idle : BillingStatus
    data object Connecting : BillingStatus
    data object Ready : BillingStatus
    /** Play Store unavailable / not signed in / unsupported device. */
    data class Unavailable(val responseCode: Int) : BillingStatus
}

/** One-off events the UI shows as a snackbar or dialog. */
sealed interface BillingEvent {
    data object PurchaseCancelled : BillingEvent
    data class PurchaseFailed(val code: Int) : BillingEvent
    data class Granted(val productId: String) : BillingEvent
    data object Restored : BillingEvent
    data object NothingToRestore : BillingEvent
}

/**
 * Google Play Billing for one-time products and consumables only.
 *
 * Written against Billing Library 9.x (which is additive on top of 8.x). The
 * Play requirement in force since 2026-08-31 is "Billing Library 8 or later".
 * Notable v8 API facts this class respects:
 *
 *  * `enablePendingPurchases()` no-arg is gone -> [PendingPurchasesParams].
 *  * `queryPurchaseHistoryAsync()` is gone -> entitlements come from
 *    [BillingClient.queryPurchasesAsync] plus a local, idempotent grant log.
 *  * `ProductDetailsResponseListener` now yields a `QueryProductDetailsResult`
 *    (`productDetailsList` + `unfetchedProductList`), not a bare list.
 *  * `queryPurchasesAsync(String, ...)` is gone -> [QueryPurchasesParams].
 *
 * The app has no server, so the on-device grant log in [PlayerStore] is what
 * makes consumable delivery idempotent: a token is granted exactly once even if
 * Play redelivers the purchase after a crash.
 */
class BillingManager(
    private val context: Context,
    private val store: PlayerStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _status = MutableStateFlow<BillingStatus>(BillingStatus.Idle)
    val status: StateFlow<BillingStatus> = _status.asStateFlow()

    private val _events = MutableStateFlow<BillingEvent?>(null)
    val events: StateFlow<BillingEvent?> = _events.asStateFlow()

    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<String, ProductDetails>> = _productDetails.asStateFlow()

    private var client: BillingClient? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0

    // ------------------------------------------------------------------ setup

    private fun buildClient(): BillingClient = BillingClient.newBuilder(context)
        .setListener { billingResult, purchases -> onPurchasesUpdated(billingResult, purchases) }
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build(),
        )
        .build()

    /** Safe to call repeatedly; starts Play connection and refreshes state. */
    fun start() {
        if (_status.value == BillingStatus.Connecting || _status.value == BillingStatus.Ready) {
            if (client?.isReady == true) {
                refresh()
                return
            }
        }
        val billingClient = client ?: buildClient().also { client = it }
        _status.value = BillingStatus.Connecting
        billingClient.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        reconnectAttempt = 0
                        _status.value = BillingStatus.Ready
                        refresh()
                    } else {
                        _status.value = BillingStatus.Unavailable(billingResult.responseCode)
                        Log.w(TAG, "setup failed: ${billingResult.responseCode} ${billingResult.debugMessage}")
                    }
                }

                override fun onBillingServiceDisconnected() {
                    _status.value = BillingStatus.Connecting
                    scheduleReconnect()
                }
            },
        )
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            while (reconnectAttempt < MAX_RECONNECT) {
                reconnectAttempt++
                delay(RECONNECT_BASE_DELAY_MS * reconnectAttempt)
                val billingClient = client ?: return@launch
                if (billingClient.isReady) {
                    _status.value = BillingStatus.Ready
                    refresh()
                    return@launch
                }
                billingClient.startConnection(
                    object : BillingClientStateListener {
                        override fun onBillingSetupFinished(billingResult: BillingResult) {
                            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                reconnectAttempt = 0
                                _status.value = BillingStatus.Ready
                                refresh()
                            }
                        }

                        override fun onBillingServiceDisconnected() = scheduleReconnect()
                    },
                )
                return@launch
            }
            _status.value = BillingStatus.Unavailable(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
        }
    }

    // ----------------------------------------------------------------- queries

    /** Re-reads owned purchases and product prices from Play. */
    fun refresh() {
        val billingClient = client ?: return
        if (!billingClient.isReady) {
            start()
            return
        }
        queryOwnedPurchases(billingClient)
        queryProducts(billingClient)
    }

    private fun queryOwnedPurchases(billingClient: BillingClient) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "queryPurchases failed: ${billingResult.responseCode}")
                return@queryPurchasesAsync
            }
            var granted = 0
            purchases.forEach { purchase ->
                if (grant(purchase)) granted++
            }
            // Anything Play still lists as active but unconsumed is re-delivered
            // here; grant() is idempotent, so this is safe.
            if (granted > 0) store.persist()
        }
    }

    private fun queryProducts(billingClient: BillingClient) {
        val products = ProductIds.all.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        billingClient.queryProductDetailsAsync(params) { billingResult, result ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "queryProductDetails failed: ${billingResult.responseCode}")
                return@queryProductDetailsAsync
            }
            val fetched = result.productDetailsList.associateBy { it.productId }
            _productDetails.value = fetched
            val missing = result.unfetchedProductList.size
            if (missing > 0) {
                // Almost always "products not created/activated in Play Console
                // yet" or "signed in with an account that cannot see them".
                Log.w(TAG, "$missing product(s) could not be fetched from Play")
            }
        }
    }

    /** Localised price for [productId], or null while Play has not answered. */
    fun formattedPrice(productId: String): String? =
        _productDetails.value[productId]
            ?.oneTimePurchaseOfferDetails
            ?.formattedPrice

    fun detailsFor(productId: String): ProductDetails? = _productDetails.value[productId]

    // -------------------------------------------------------------- purchasing

    /**
     * Launches the Play purchase sheet.
     * @return false when the flow could not even be started.
     */
    fun purchase(activity: Activity, productId: String): Boolean {
        val billingClient = client
        val details = _productDetails.value[productId]
        if (billingClient == null || !billingClient.isReady || details == null) {
            start()
            _events.value = BillingEvent.PurchaseFailed(
                BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            )
            return false
        }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build(),
                ),
            )
            .build()
        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "launchBillingFlow failed: ${result.responseCode}")
            _events.value = BillingEvent.PurchaseFailed(result.responseCode)
            return false
        }
        return true
    }

    /** "Restore purchases" - re-reads entitlements from the signed-in account. */
    fun restorePurchases() {
        val billingClient = client
        if (billingClient == null || !billingClient.isReady) {
            start()
            return
        }
        val before = store.ownedProducts.value.size + store.hintCoins.value
        queryOwnedPurchases(billingClient)
        // queryPurchasesAsync is asynchronous; report once it settles.
        scope.launch {
            delay(RESTORE_REPORT_DELAY_MS)
            val after = store.ownedProducts.value.size + store.hintCoins.value
            _events.value = if (after > before) BillingEvent.Restored else BillingEvent.NothingToRestore
        }
    }

    private fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases.isNullOrEmpty()) {
                    refresh()
                    return
                }
                var lastGranted: String? = null
                purchases.forEach { purchase ->
                    if (grant(purchase)) lastGranted = purchase.products.firstOrNull()
                }
                store.persist()
                if (lastGranted != null) {
                    _events.value = BillingEvent.Granted(lastGranted)
                }
                refresh()
            }

            BillingClient.BillingResponseCode.USER_CANCELED ->
                _events.value = BillingEvent.PurchaseCancelled

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // Play thinks we own it but our local log may have been cleared:
                // re-read and grant.
                client?.let { queryOwnedPurchases(it) }
                _events.value = BillingEvent.Restored
            }

            else -> {
                Log.w(TAG, "purchasesUpdated: ${billingResult.responseCode} ${billingResult.debugMessage}")
                _events.value = BillingEvent.PurchaseFailed(billingResult.responseCode)
            }
        }
    }

    /**
     * Applies one purchase to local entitlements. Idempotent per purchase token,
     * and consumes consumables so Play stops redelivering them.
     *
     * @return true when this call actually granted something new.
     */
    private fun grant(purchase: Purchase): Boolean {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            // PENDING: the user still has to complete payment. Grant nothing.
            return false
        }
        var grantedSomething = false

        for (productId in purchase.products) {
            if (productId in ProductIds.consumables) {
                // Consumable: grant once per token, then consume.
                if (store.markTokenGranted(purchase.purchaseToken)) {
                    when (productId) {
                        ProductIds.HINT_PACK_10 -> store.addHintCoins(Catalogue.hintAmount(productId))
                    }
                    grantedSomething = true
                    consume(purchase)
                }
            } else {
                if (store.markOwned(productId)) {
                    grantedSomething = true
                    if (!purchase.isAcknowledged) acknowledge(purchase)
                } else if (!purchase.isAcknowledged) {
                    acknowledge(purchase)
                }
            }
        }

        if (grantedSomething) {
            Log.i(TAG, "granted ${purchase.products} (token ${purchase.purchaseToken.take(8)}...)")
        }
        return grantedSomething
    }

    private fun acknowledge(purchase: Purchase) {
        val billingClient = client ?: return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "acknowledge failed: ${result.responseCode}")
            }
        }
    }

    private fun consume(purchase: Purchase) {
        val billingClient = client ?: return
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.consumeAsync(params) { result, _ ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                // Not fatal: grant() already logged the token, so a redelivery
                // will not double-grant, and the next refresh retries.
                Log.w(TAG, "consume failed: ${result.responseCode}")
            }
        }
    }

    fun consumeEvent() {
        _events.value = null
    }

    fun release() {
        reconnectJob?.cancel()
        client?.endConnection()
        client = null
        _status.value = BillingStatus.Idle
    }

    private companion object {
        const val TAG = "ColorZenBilling"
        const val MAX_RECONNECT = 4
        const val RECONNECT_BASE_DELAY_MS = 1_000L
        const val RESTORE_REPORT_DELAY_MS = 1_200L
    }
}
