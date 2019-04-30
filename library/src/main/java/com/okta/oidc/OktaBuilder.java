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

package com.okta.oidc;

import android.content.Context;

import androidx.annotation.NonNull;

import com.okta.oidc.clients.AuthClientFactory;
import com.okta.oidc.net.HttpConnectionFactory;
import com.okta.oidc.storage.OktaRepository;
import com.okta.oidc.storage.OktaStorage;

/**
 * The type Builder.
 */
public abstract class OktaBuilder<A, T extends OktaBuilder<A, T>> {
    HttpConnectionFactory mConnectionFactory;
    OIDCConfig mOIDCConfig;
    OktaStorage mStorage;
    Context context;
    AuthClientFactory<A> authClientFactory;

    abstract T toThis();

    abstract A create();

    /**
     * Sets the config used for this client.
     * {@link OIDCConfig}
     *
     * @param config the account
     * @return current builder
     */
    public T withConfig(@NonNull OIDCConfig config) {
        mOIDCConfig = config;
        return toThis();
    }

    /**
     * Sets the connection factory to use, which creates a {@link java.net.HttpURLConnection}
     * instance for communication with Okta OIDC endpoints.
     *
     * @param connectionFactory the connection factory
     * @return the builder
     */
    public T withHttpConnectionFactory(HttpConnectionFactory connectionFactory) {
        mConnectionFactory = connectionFactory;
        return toThis();
    }

    /**
     * Sets the context.
     *
     * @param context the context
     * @return current builder
     */
    public T withContext(Context context) {
        this.context = context;
        return toThis();
    }

    /**
     * Set a storage implementation for the client to use. You can define your own storage
     * or use the default implementation {@link com.okta.oidc.storage.SimpleOktaStorage}
     *
     * @param storage the storage implementation
     * @return current builder
     */
    public T withStorage(OktaStorage storage) {
        this.mStorage = storage;
        return toThis();
    }

    T withAuthenticationClientFactory(AuthClientFactory<A> authClientFactory) {
        this.authClientFactory = authClientFactory;
        return toThis();
    }

    protected A createAuthClient() {
        return this.authClientFactory.createClient(mOIDCConfig, new OktaState(new OktaRepository(mStorage, context)), mConnectionFactory);
    }
}