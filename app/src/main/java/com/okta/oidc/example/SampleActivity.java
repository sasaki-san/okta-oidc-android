/*
 * Copyright (c) 2019, Okta, Inc. and/or its affiliates. All rights reserved.
 * The Okta software accompanied by this notice is provided pursuant to the Apache License,
 * Version 2.0 (the "License.")
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and limitations under the
 * License.
 */

package com.okta.oidc.example;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import com.okta.oidc.AuthenticationPayload;
import com.okta.oidc.AuthorizationStatus;
import com.okta.oidc.OIDCConfig;
import com.okta.oidc.Okta;
import com.okta.oidc.RequestCallback;
import com.okta.oidc.ResultCallback;
import com.okta.oidc.Tokens;
import com.okta.oidc.clients.AuthClient;
import com.okta.oidc.clients.sessions.SessionClient;
import com.okta.oidc.clients.web.WebAuthClient;
import com.okta.oidc.net.params.TokenTypeHint;
import com.okta.oidc.net.response.IntrospectInfo;
import com.okta.oidc.net.response.UserInfo;
import com.okta.oidc.storage.SharedPreferenceStorage;
import com.okta.oidc.storage.security.DefaultEncryptionManager;
import com.okta.oidc.util.AuthorizationException;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.okta.oidc.AuthorizationStatus.EMAIL_VERIFICATION_AUTHENTICATED;
import static com.okta.oidc.AuthorizationStatus.EMAIL_VERIFICATION_UNAUTHENTICATED;

/**
 * Sample to test library functionality. Can be used as a starting reference point.
 */
@SuppressLint("SetTextI18n")
@SuppressWarnings("FieldCanBeLocal")
public class SampleActivity extends AppCompatActivity {
    private static final String TAG = "SampleActivity";
    private static final String PREF_SWITCH = "switch";
    private static final String PREF_NON_WEB = "nonweb";
    /**
     * Authorization client using chrome custom tab as a user agent.
     */
    WebAuthClient mWebAuth;
    /**
     * Authorization client used with Authentication APIs sessionToken.
     */
    AuthClient mAuthClient;
    /**
     * The authorized client to interact with Okta's endpoints.
     */
    SessionClient mSessionClient;

    /**
     * Okta OIDC configuration.
     */
    @VisibleForTesting
    OIDCConfig mOidcConfig;

    SessionClient mSessionNonWebClient;

    private TextView mTvStatus;
    private Button mSignInBrowser;
    private Button mSignOut;
    private Button mGetProfile;

    private Button mRefreshToken;
    private Button mRevokeRefresh;
    private Button mRevokeAccess;
    private Button mIntrospectRefresh;
    private Button mIntrospectAccess;
    private Button mIntrospectId;
    private Button mCheckExpired;
    private Button mCancel;
    private ProgressBar mProgressBar;
    private boolean mIsSessionSignIn;
    @SuppressWarnings("unused")
    private static final String FIRE_FOX = "org.mozilla.firefox";

    private LinearLayout mRevokeContainer;

    /**
     * The payload to send for authorization.
     */
    @VisibleForTesting
    AuthenticationPayload mPayload;

    /**
     * The payload to send for authorization.
     */
    @VisibleForTesting
    SharedPreferenceStorage mStorageOidc;

    private ScheduledExecutorService mExecutor = Executors.newSingleThreadScheduledExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.sample_activity);
        mCancel = findViewById(R.id.cancel);
        mCheckExpired = findViewById(R.id.check_expired);
        mSignInBrowser = findViewById(R.id.sign_in);
        mSignOut = findViewById(R.id.sign_out);
        mRevokeContainer = findViewById(R.id.revoke_token);
        mRevokeAccess = findViewById(R.id.revoke_access);
        mRevokeRefresh = findViewById(R.id.revoke_refresh);
        mRefreshToken = findViewById(R.id.refresh_token);
        mGetProfile = findViewById(R.id.get_profile);
        mProgressBar = findViewById(R.id.progress_horizontal);
        mTvStatus = findViewById(R.id.status);
        mIntrospectRefresh = findViewById(R.id.introspect_refresh);
        mIntrospectAccess = findViewById(R.id.introspect_access);
        mIntrospectId = findViewById(R.id.introspect_id);

        mStorageOidc = new SharedPreferenceStorage(this);
        mIsSessionSignIn = getSharedPreferences(SampleActivity.class.getName(), MODE_PRIVATE)
                .getBoolean(PREF_NON_WEB, true);

        mCheckExpired.setOnClickListener(v -> {
            SessionClient client = getSessionClient();
            try {
                mTvStatus.setText(client.getTokens().isAccessTokenExpired() ? "token expired" :
                        "token not expired");
            } catch (AuthorizationException e) {
                Log.d(TAG, "", e);
            }
        });

        mIntrospectRefresh.setOnClickListener(v -> {
            showNetworkProgress(true);
            SessionClient client = getSessionClient();
            String refreshToken;
            try {
                refreshToken = client.getTokens().getRefreshToken();
                client.introspectToken(refreshToken, TokenTypeHint.REFRESH_TOKEN,
                        new RequestCallback<IntrospectInfo, AuthorizationException>() {
                            @Override
                            public void onSuccess(@NonNull IntrospectInfo result) {
                                mTvStatus.setText("RefreshToken active: " + result.isActive());
                                mProgressBar.setVisibility(View.GONE);
                            }

                            @Override
                            public void onError(String error, AuthorizationException exception) {
                                mTvStatus.setText("RefreshToken Introspect error");
                                mProgressBar.setVisibility(View.GONE);
                            }
                        }
                );
            } catch (AuthorizationException e) {
                Log.d(TAG, "", e);
            }
        });

        mIntrospectAccess.setOnClickListener(v -> {
            showNetworkProgress(true);
            SessionClient client = getSessionClient();
            try {
                client.introspectToken(
                        client.getTokens().getAccessToken(), TokenTypeHint.ACCESS_TOKEN,
                        new RequestCallback<IntrospectInfo, AuthorizationException>() {
                            @Override
                            public void onSuccess(@NonNull IntrospectInfo result) {
                                mTvStatus.setText("AccessToken active: " + result.isActive());
                                mProgressBar.setVisibility(View.GONE);
                            }

                            @Override
                            public void onError(String error, AuthorizationException exception) {
                                mTvStatus.setText("AccessToken Introspect error");
                                mProgressBar.setVisibility(View.GONE);
                            }
                        }
                );
            } catch (AuthorizationException e) {
                Log.d(TAG, "", e);
            }
        });

        mIntrospectId.setOnClickListener(v -> {
            showNetworkProgress(true);
            SessionClient client = getSessionClient();
            try {
                client.introspectToken(
                        client.getTokens().getIdToken(), TokenTypeHint.ID_TOKEN,
                        new RequestCallback<IntrospectInfo, AuthorizationException>() {
                            @Override
                            public void onSuccess(@NonNull IntrospectInfo result) {
                                mTvStatus.setText("IdToken active: " + result.isActive());
                                mProgressBar.setVisibility(View.GONE);
                            }

                            @Override
                            public void onError(String error, AuthorizationException exception) {
                                mTvStatus.setText("IdToken Introspect error");
                                mProgressBar.setVisibility(View.GONE);
                            }
                        }
                );
            } catch (AuthorizationException e) {
                Log.d(TAG, "", e);
            }
        });

        mGetProfile.setOnClickListener(v -> getProfile());

        mRefreshToken.setOnClickListener(v -> {
            showNetworkProgress(true);
            SessionClient client = getSessionClient();
            client.refreshToken(new RequestCallback<Tokens, AuthorizationException>() {
                @Override
                public void onSuccess(@NonNull Tokens result) {
                    mTvStatus.setText("token refreshed");
                    showNetworkProgress(false);
                }

                @Override
                public void onError(String error, AuthorizationException exception) {
                    mTvStatus.setText(exception.errorDescription);
                    showNetworkProgress(false);
                }
            });
        });

        mRevokeRefresh.setOnClickListener(v -> {
            SessionClient client = getSessionClient();
            try {
                Tokens tokens = client.getTokens();
                if (tokens != null && tokens.getRefreshToken() != null) {
                    mProgressBar.setVisibility(View.VISIBLE);
                    client.revokeToken(client.getTokens().getRefreshToken(),
                            new RequestCallback<Boolean, AuthorizationException>() {
                                @Override
                                public void onSuccess(@NonNull Boolean result) {

                                    String status = "Revoke refresh token : " + result;
                                    Log.d(TAG, status);
                                    mTvStatus.setText(status);
                                    mProgressBar.setVisibility(View.GONE);
                                }

                                @Override
                                public void onError(String error,
                                                    AuthorizationException exception) {
                                    Log.d(TAG, exception.error +
                                            " revokeRefreshToken onError " + error, exception);
                                    mTvStatus.setText(error);
                                    mProgressBar.setVisibility(View.GONE);
                                }
                            });
                }
            } catch (AuthorizationException e) {
                Log.d(TAG, "", e);
            }
        });

        mRevokeAccess.setOnClickListener(v -> {
            SessionClient client = getSessionClient();
            try {
                Tokens tokens = client.getTokens();
                if (tokens != null && tokens.getAccessToken() != null) {
                    mProgressBar.setVisibility(View.VISIBLE);
                    client.revokeToken(client.getTokens().getAccessToken(),
                            new RequestCallback<Boolean, AuthorizationException>() {
                                @Override
                                public void onSuccess(@NonNull Boolean result) {
                                    String status = "Revoke Access token : " + result;
                                    Log.d(TAG, status);
                                    mTvStatus.setText(status);
                                    mProgressBar.setVisibility(View.GONE);
                                }

                                @Override
                                public void onError(String error,
                                                    AuthorizationException exception) {
                                    Log.d(TAG, exception.error +
                                            " revokeAccessToken onError " + error, exception);
                                    mTvStatus.setText(error);
                                    mProgressBar.setVisibility(View.GONE);
                                }
                            });
                }
            } catch (AuthorizationException e) {
                Log.d(TAG, "", e);
            }
        });

        mSignOut.setOnClickListener(v -> {
            showNetworkProgress(true);
            WebAuthClient webAuthClient = getWebAuthClient();
            webAuthClient.signOutOfOkta(this);

        });

        mSignInBrowser.setOnClickListener(v -> {
            showNetworkProgress(true);
            WebAuthClient client = getWebAuthClient();
            client.signIn(this, mPayload);
        });

        //Example of using JSON file to create config
        mOidcConfig = new OIDCConfig.Builder()
                .withJsonFile(this, R.raw.okta_oidc_config)
                .create();

        //use custom connection factory
        MyConnectionFactory factory = new MyConnectionFactory();
        factory.setClientType(MyConnectionFactory.USE_SYNC_OK_HTTP);

        Okta.WebAuthBuilder builder = new Okta.WebAuthBuilder()
                .withConfig(mOidcConfig)
                .withContext(getApplicationContext())
                .withStorage(mStorageOidc)
                .withCallbackExecutor(null)
                .withEncryptionManager(new DefaultEncryptionManager(this))
                .setRequireHardwareBackedKeyStore(!isEmulator())
                .withTabColor(0)
                .withOktaHttpClient(factory.build())
                .supportedBrowsers(FIRE_FOX);

        mWebAuth = builder.create();

        mSessionClient = mWebAuth.getSessionClient();

        mAuthClient = new Okta.AuthBuilder()
                .withConfig(mOidcConfig)
                .withContext(getApplicationContext())
                .withStorage(new SharedPreferenceStorage(this))
                .withEncryptionManager(new DefaultEncryptionManager(this))
                .setRequireHardwareBackedKeyStore(false)
                .withCallbackExecutor(null)
                .create();

        mSessionNonWebClient = mAuthClient.getSessionClient();

        if (getSessionClient().isAuthenticated()) {
            showAuthenticatedMode();
        }

        mCancel.setOnClickListener(v -> {
            getWebAuthClient().cancel(); //cancel web auth requests
            getSessionClient().cancel(); //cancel session requests
            showNetworkProgress(false);
        });
        setupCallback();
    }

    /**
     * Sets callback.
     */
    @VisibleForTesting
    void setupCallback() {
        ResultCallback<AuthorizationStatus, AuthorizationException> callback =
                new ResultCallback<AuthorizationStatus, AuthorizationException>() {
                    @Override
                    public void onSuccess(@NonNull AuthorizationStatus status) {
                        Log.d("SampleActivity", "AUTHORIZED");
                        if (status == AuthorizationStatus.AUTHORIZED) {
                            mTvStatus.setText("authentication authorized");
                            showAuthenticatedMode();
                            showNetworkProgress(false);
                            mIsSessionSignIn = false;
                            mProgressBar.setVisibility(View.GONE);
                        } else if (status == AuthorizationStatus.SIGNED_OUT) {
                            //this only clears the session.
                            mTvStatus.setText("signedOutOfOkta");
                            showNetworkProgress(false);
                            // clear session

            SessionClient sessionClient = getSessionClient();
            sessionClient.clear();
            mTvStatus.setText("clear data");
            showSignedOutMode();

                        } else if (status == EMAIL_VERIFICATION_AUTHENTICATED
                                || status == EMAIL_VERIFICATION_UNAUTHENTICATED) {
                            //Result is email verification. sign in again.
                            getWebAuthClient().signIn(SampleActivity.this, mPayload);
                        }
                    }

                    @Override
                    public void onCancel() {
                        mProgressBar.setVisibility(View.GONE);
                        Log.d(TAG, "CANCELED!");
                        mTvStatus.setText("canceled");
                    }

                    @Override
                    public void onError(@Nullable String msg, AuthorizationException error) {
                        mProgressBar.setVisibility(View.GONE);
                        Log.d("SampleActivity", error.error +
                                " onActivityResult onError " + msg, error);
                        mTvStatus.setText(msg);
                    }
                };
        mWebAuth.registerCallback(callback, this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mWebAuth.isInProgress()) {
            showNetworkProgress(true);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        showNetworkProgress(false);
        getSharedPreferences(SampleActivity.class.getName(), MODE_PRIVATE).edit()
                .putBoolean(PREF_SWITCH, true).apply();
        getSharedPreferences(SampleActivity.class.getName(), MODE_PRIVATE).edit()
                .putBoolean(PREF_NON_WEB, mIsSessionSignIn).apply();

    }

    private SessionClient getSessionClient() {

        if (mIsSessionSignIn) {
            return mSessionNonWebClient;
        }
        return mSessionClient;
    }

    private WebAuthClient getWebAuthClient() {
        return mWebAuth;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mExecutor.shutdownNow();
    }

    private void showNetworkProgress(boolean visible) {
        mProgressBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        mCancel.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void showAuthenticatedMode() {
        mGetProfile.setVisibility(View.VISIBLE);
        mSignOut.setVisibility(View.VISIBLE);
        mRefreshToken.setVisibility(View.VISIBLE);
        mRevokeContainer.setVisibility(View.VISIBLE);
        mSignInBrowser.setVisibility(View.GONE);
    }

    private void showSignedOutMode() {
        mSignInBrowser.setVisibility(View.VISIBLE);
        mGetProfile.setVisibility(View.GONE);
        mSignOut.setVisibility(View.GONE);
        mRefreshToken.setVisibility(View.GONE);
        mRevokeContainer.setVisibility(View.GONE);
        mTvStatus.setText("");
    }

    private void getProfile() {
        showNetworkProgress(true);
        SessionClient client = getSessionClient();
        client.getUserProfile(new RequestCallback<UserInfo, AuthorizationException>() {
            @Override
            public void onSuccess(@NonNull UserInfo result) {
                mTvStatus.setText(result.toString());
                showNetworkProgress(false);
            }

            @Override
            public void onError(String error, AuthorizationException exception) {
                Log.d(TAG, error, exception.getCause());
                mTvStatus.setText("Error : " + exception.errorDescription);
                showNetworkProgress(false);
            }
        });
    }

    /**
     * Check if the device is a emulator.
     *
     * @return true if it is emulator
     */
    public static boolean isEmulator() {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Google")
                || Build.PRODUCT.contains("sdk_gphone")
                || Build.DEVICE.contains("generic");
    }
}
