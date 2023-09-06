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
import com.okta.oidc.net.response.UserInfo;
import com.okta.oidc.storage.SharedPreferenceStorage;
import com.okta.oidc.storage.security.DefaultEncryptionManager;
import com.okta.oidc.util.AuthorizationException;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.concurrent.TimeUnit;

/**
 * Sample to test library functionality. Can be used as a starting reference point.
 */
@SuppressLint("SetTextI18n")
@SuppressWarnings("FieldCanBeLocal")
public class SampleActivity extends AppCompatActivity {
    private static final String TAG = "SampleActivity";
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

    private TextView mTvStatus;
    private Button mSignInBrowser;
    private Button mSignOut;
    private Button mGetProfile;

    private Button mRefreshToken;

    private Button mCallApi;
    private Button mCancel;
    private ProgressBar mProgressBar;

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

    private OkHttpClient mOkHttpClient;

    private static final String API_END_POINT = "https://yusasaki-apimanagement.azure-api.net/api";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.sample_activity);
        mCancel = findViewById(R.id.cancel);
        mSignInBrowser = findViewById(R.id.sign_in);
        mSignOut = findViewById(R.id.sign_out);
        mRefreshToken = findViewById(R.id.refresh_token);
        mCallApi = findViewById(R.id.call_api);
        mGetProfile = findViewById(R.id.get_profile);
        mProgressBar = findViewById(R.id.progress_horizontal);
        mTvStatus = findViewById(R.id.status);

        mStorageOidc = new SharedPreferenceStorage(this);

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

        mCallApi.setOnClickListener(v -> callApi());

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
                .withOktaHttpClient(factory.build());

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

        if (getSessionClient().isAuthenticated()) {
            showAuthenticatedMode();
        }

        mCancel.setOnClickListener(v -> {
            getWebAuthClient().cancel(); //cancel web auth requests
            getSessionClient().cancel(); //cancel session requests
            showNetworkProgress(false);
        });
        setupCallback();

        mOkHttpClient = new OkHttpClient.Builder()
                .connectTimeout(15_000, TimeUnit.MILLISECONDS)
                .readTimeout(10_000, TimeUnit.MILLISECONDS)
                .build();
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
    }

    private SessionClient getSessionClient() {
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
        mCallApi.setVisibility(View.VISIBLE);
        mSignInBrowser.setVisibility(View.GONE);
    }

    private void showSignedOutMode() {
        mSignInBrowser.setVisibility(View.VISIBLE);
        mGetProfile.setVisibility(View.GONE);
        mSignOut.setVisibility(View.GONE);
        mRefreshToken.setVisibility(View.GONE);
        mCallApi.setVisibility(View.GONE);
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

    public interface CallApiCallback {
        void onAccessTokenReceived(String accessToken);
        void onRefreshTokenExpiredErrorReceived();
        void onError(Exception e);
    }

    private void withAccessToken(CallApiCallback callback) {

        SessionClient client = getSessionClient();

        try {
            // アクセストークンが有効期限内かどうか確認
            if (client.getTokens().isAccessTokenExpired()) {
                // アクセストークンの有効期限が切れているので、リフレッシュトークンを使ってトークンを更新
                client.refreshToken(new RequestCallback<Tokens, AuthorizationException>() {
                    @Override
                    public void onSuccess(@NonNull Tokens result) {
                        callback.onAccessTokenReceived(result.getAccessToken());
                    }

                    @Override
                    public void onError(String error, AuthorizationException exception) {
                        if(Objects.equals(exception.error, "invalid_grant") && Objects.requireNonNull(exception.errorDescription).contains("expired")) {
                            // リフレッシュトークンの有効期限切れ。
                            callback.onRefreshTokenExpiredErrorReceived();
                        } else {
                            // リフレッシュトークン取得中にエラー発生。
                            callback.onError(exception);
                        }
                    }
                });
            } else {
                // アクセストークンが有効期限内なのでそのまま返却
                callback.onAccessTokenReceived(client.getTokens().getAccessToken());
            }
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    private String callApiWithAccessToken(String url, String accessToken) throws Exception {

        Request.Builder requestBuilder = new Request.Builder().url(url);
        requestBuilder.addHeader("Authorization", "Bearer " + accessToken);
        requestBuilder = requestBuilder.get();

        Request request = requestBuilder.build();

        Call mCall = mOkHttpClient.newCall(request);

        final CountDownLatch latch = new CountDownLatch(1);
        final Exception[] exception = new Exception[1];
        final Response[] response = new Response[1];

        mCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                exception[0] = e;
                latch.countDown();
            }

            @Override
            public void onResponse(Call call, Response r) {
                response[0] = r;
                latch.countDown();
            }
        });

        latch.await();

        if (exception[0] != null) {
            throw exception[0];
        }

        if(response[0].code() != 200) {
            String error = "Error status code:" + response[0].code() + ", Message: " + response[0].message();
            throw new Exception(error);
        }

        assert response[0].body() != null;
        return response[0].body().string();
    }

    private void callApi() {

        showNetworkProgress(true);

        withAccessToken(new CallApiCallback() {
            @Override
            public void onAccessTokenReceived(String accessToken) {
                Log.d(TAG, accessToken);

                // Access Token取得成功。 API呼び出し
                String result;
                try {
                    result = callApiWithAccessToken(API_END_POINT, accessToken);
                    Log.d(TAG, result);
                    mTvStatus.setText(result);
                } catch (Exception e) {
                    mTvStatus.setText("Error : " + e);
                    Log.d(TAG, e.toString(), e.getCause());
                }
                showNetworkProgress(false);
            }

            @Override
            public void onRefreshTokenExpiredErrorReceived() {
                // Refresh Tokenの有効期限が切れている。再ログインが必要。
                mTvStatus.setText("Error : Refresh Token 有効期限切れ。再認証が必要。");
                Log.d(TAG, "Error : Refresh Token 有効期限切れ。再認証が必要。");
                showNetworkProgress(false);

                // clear session
                SessionClient sessionClient = getSessionClient();
                sessionClient.clear();
                showSignedOutMode();
            }

            @Override
            public void onError(Exception e) {
                // エラー発生
                mTvStatus.setText("Error : " + e);
                Log.d(TAG, e.toString(), e.getCause());
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
