package com.alphawallet.app.viewmodel;

import android.app.Activity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import android.text.TextUtils;
import android.util.Log;

import com.alphawallet.app.C;
import com.alphawallet.app.entity.CreateWalletCallbackInterface;
import com.alphawallet.app.entity.ErrorEnvelope;
import com.alphawallet.app.entity.Operation;
import com.alphawallet.app.entity.SignAuthenticationCallback;
import com.alphawallet.app.entity.Wallet;
import com.alphawallet.app.entity.WalletType;
import com.alphawallet.app.interact.ExportWalletInteract;
import com.alphawallet.app.interact.FetchWalletsInteract;
import com.alphawallet.app.service.KeyService;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;


public class BackupKeyViewModel extends BaseViewModel {
    private final static String TAG = BackupKeyViewModel.class.getSimpleName();

    private final KeyService keyService;
    private final ExportWalletInteract exportWalletInteract;
    private final FetchWalletsInteract fetchWalletsInteract;

    private final MutableLiveData<String> exportedStore = new MutableLiveData<>();
    private final MutableLiveData<ErrorEnvelope> exportWalletError = new MutableLiveData<>();

    public BackupKeyViewModel(
            KeyService keyService,
            ExportWalletInteract exportWalletInteract,
            FetchWalletsInteract fetchWalletsInteract) {
        this.keyService = keyService;
        this.exportWalletInteract = exportWalletInteract;
        this.fetchWalletsInteract = fetchWalletsInteract;
    }

    public LiveData<String> exportedStore() {
        return exportedStore;
    }

    public void exportWallet(Wallet wallet, String keystorePassword, String storePassword) {
        disposable = exportWalletInteract
                .export(wallet, keystorePassword, storePassword)
                .subscribe(exportedStore::postValue, this::onExportWalletError);
    }

    private void onExportWalletError(Throwable throwable) {
        exportWalletError.postValue(
                new ErrorEnvelope(C.ErrorCode.UNKNOWN, TextUtils.isEmpty(throwable.getLocalizedMessage())
                        ? throwable.getMessage() : throwable.getLocalizedMessage()));
    }

    @Override
    protected void onError(Throwable throwable) {
        super.onError(throwable);
    }


    public void upgradeWallet(String keyAddress)
    {
        fetchWalletsInteract.getWallet(keyAddress)
                .map(this::upgradeWallet)
                .flatMap(fetchWalletsInteract::storeWallet)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onWalletUpgraded, this::onError)
                .isDisposed();
    }

    private void onWalletUpgraded(Wallet wallet)
    {
        switch (wallet.authLevel)
        {
            default:
            case TEE_NO_AUTHENTICATION:
                wallet.authLevel = KeyService.AuthenticationLevel.TEE_AUTHENTICATION;
                break;
            case STRONGBOX_NO_AUTHENTICATION:
                wallet.authLevel = KeyService.AuthenticationLevel.STRONGBOX_AUTHENTICATION;
                break;
        }
        Log.d("HVM", "Wallet " + wallet.address + " Upgraded to: " + wallet.authLevel.toString());
    }

    private Wallet upgradeWallet(Wallet wallet)
    {
        switch (wallet.authLevel)
        {
            default:
                break;
            case NOT_SET:
                if (wallet.type == WalletType.HDKEY) wallet.authLevel = KeyService.AuthenticationLevel.TEE_AUTHENTICATION;
                break;
            case TEE_NO_AUTHENTICATION:
                wallet.authLevel = KeyService.AuthenticationLevel.TEE_AUTHENTICATION;
                break;
            case STRONGBOX_NO_AUTHENTICATION:
                wallet.authLevel = KeyService.AuthenticationLevel.STRONGBOX_AUTHENTICATION;
                break;
        }

        return wallet;
    }

    public void upgradeKeySecurity(Wallet wallet, Activity activity, CreateWalletCallbackInterface callback)
    {
        disposable = Single.fromCallable(() -> keyService.upgradeKeySecurity(wallet, activity))
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(callback::keyUpgraded);
    }

    public void getPasswordForKeystore(Wallet wallet, Activity activity, CreateWalletCallbackInterface callback)
    {
        disposable = Completable.fromAction(() ->
                keyService.getPassword(wallet, activity, callback)) //computation thread to give UI a chance to complete all tasks
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe();
    }

    public void getAuthentication(Wallet wallet, Activity activity, SignAuthenticationCallback callback)
    {
        keyService.setRequireAuthentication(); // require authentication for any action involving the keystore
        keyService.getAuthenticationForSignature(wallet, activity, callback);
    }

    public void getSeedPhrase(Wallet wallet, Activity activity, CreateWalletCallbackInterface callback)
    {
        disposable = Completable.fromAction(() ->
                keyService.getMnemonic(wallet, activity, callback)) //computation thread to give UI a chance to complete all tasks
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe();
    }

    public void backupSuccess(Wallet wallet)
    {
        fetchWalletsInteract.updateBackupTime(wallet.address).isDisposed();
    }

    public void resetSignDialog()
    {
        keyService.resetSigningDialog();
    }

    public void completeAuthentication(Operation taskCode)
    {
        keyService.completeAuthentication(taskCode);
    }

    public void failedAuthentication(Operation taskCode)
    {
        keyService.failedAuthentication(taskCode);
    }
}

