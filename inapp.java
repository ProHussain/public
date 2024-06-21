package com.heart.me.update.utils;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.heart.me.utils.SpManager;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

public class InAppPurchaseUtils implements PurchasesUpdatedListener, BillingClientStateListener {
    private static final String TAG = "InAppPurchaseUtils";
    private BillingClient billingClient;
    private Activity activity;
    private PurchaseListener purchaseListener;
    List<ProductDetails> productDetailsList;

    public InAppPurchaseUtils(Activity activity, PurchaseListener purchaseListener) {
        this.activity = activity;
        this.purchaseListener = purchaseListener;
        productDetailsList = new ArrayList<>();
        setUpBillingClient();
    }

    public List<ProductDetails> getProductDetailsList() {
        return productDetailsList;
    }

    public void setUpBillingClient() {
        billingClient = BillingClient.newBuilder(activity)
                .setListener(this)
                .enablePendingPurchases()
                .build();
        billingClient.startConnection(this);
    }

    @Override
    public void onBillingServiceDisconnected() {
        Timber.d("Billing service disconnected");
        billingClient.startConnection(this);
    }

    @Override
    public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
        int responseCode = billingResult.getResponseCode();
        if (responseCode == BillingClient.BillingResponseCode.OK) {
            Timber.d("Billing setup successful");
            querySubscription();
            queryPurchase();
        } else {
            Timber.e("Billing setup failed with code: %s", responseCode);
        }
    }

    private void queryPurchase() {
        if (billingClient.isReady()) {
            QueryPurchasesParams queryPurchasesParams = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build();
            billingClient.queryPurchasesAsync(queryPurchasesParams, (billingResult, purchases) -> {
               if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                   if (purchases.isEmpty()) {
                       SpManager.setPremium(false);
                       purchaseListener.onPurchaseCancelled();
                   } else {
                       for (Purchase purchase : purchases) {
                           if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                               SpManager.setPremium(true);
                                 purchaseListener.onPurchaseSuccess(purchase);
                           }
                       }
                   }
               } else {
                   Timber.e("Failed to retrieve purchases: %s", billingResult.getResponseCode());
               }
            });
        } else {
            Timber.e("Billing client not ready");
        }
    }

    private void querySubscription() {
        List<QueryProductDetailsParams.Product> products = new ArrayList<>();
        products.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId("basic_main_plan")
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        );
        products.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId("discount_unit")
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        );
        QueryProductDetailsParams params =
                QueryProductDetailsParams.newBuilder()
                        .setProductList(products)
                        .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, productDetailsList) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && productDetailsList != null) {
                Timber.d("Product Details: %s", productDetailsList.size());
                this.productDetailsList = productDetailsList;
                purchaseListener.onFetchSkuDetailsSuccess();
            } else {
                Timber.e("Failed to retrieve product details for");
            }
        });
    }

    public void purchaseSubscription(ProductDetails product) {
        List<BillingFlowParams.ProductDetailsParams> productDetailsParamsList = new ArrayList<>();
        productDetailsParamsList.add(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .setOfferToken(product.getSubscriptionOfferDetails().get(0).getOfferToken().toString())
                        .build()
        );

        BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build();

        if (billingClient.isReady()) {
            BillingResult billingResult = billingClient.launchBillingFlow(activity, billingFlowParams);
            if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                Timber.e("Failed to launch billing flow: %s", billingResult.getResponseCode());
            }
        } else {
            Timber.e("Billing client not ready");
        }
    }

    @Override
    public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                // Handle purchase
                purchaseListener.onPurchaseSuccess(purchase);
            }
        } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            // Handle user cancelation
            purchaseListener.onPurchaseCancelled();
        } else {
            // Handle other errors
            purchaseListener.onPurchaseError();
        }
    }

    public interface PurchaseListener {
        void onPurchaseSuccess(Purchase purchase);

        void onPurchaseCancelled();

        void onPurchaseError();

        void onFetchSkuDetailsSuccess();
    }
}
