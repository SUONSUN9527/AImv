package com.aimv.domain.provider;

public interface ProviderHttpGateway {

    ProviderHttpResponse invoke(ProviderHttpRequest request);
}
