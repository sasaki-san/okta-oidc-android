package com.okta.oidc.clients;

import com.okta.oidc.AuthenticationPayload;
import com.okta.oidc.clients.sessions.SyncSession;
import com.okta.oidc.results.AuthorizationResult;
import com.okta.oidc.results.Result;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.FragmentActivity;


public interface SyncWebAuth extends BaseAuth<SyncSession> {
    public boolean isInProgress();

    @WorkerThread
    public AuthorizationResult logIn(@NonNull final FragmentActivity activity, @Nullable AuthenticationPayload payload)
            throws InterruptedException;

    public Result signOutFromOkta(@NonNull final FragmentActivity activity)
            throws InterruptedException;

}
