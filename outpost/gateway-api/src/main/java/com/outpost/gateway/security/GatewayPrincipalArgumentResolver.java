package com.outpost.gateway.security;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Hands each controller the {@link GatewayPrincipal} that {@link MerchantAuthenticationFilter}
 * authenticated, so no controller reads the request attribute itself.
 */
public final class GatewayPrincipalArgumentResolver implements HandlerMethodArgumentResolver {
  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return GatewayPrincipal.class.equals(parameter.getParameterType());
  }

  @Override
  public @Nullable Object resolveArgument(
      MethodParameter parameter,
      @Nullable ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      @Nullable WebDataBinderFactory binderFactory) {
    Object principal =
        webRequest.getAttribute(
            MerchantAuthenticationFilter.PRINCIPAL_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
    if (principal instanceof GatewayPrincipal authenticated) {
      return authenticated;
    }
    throw new IllegalStateException("the request reached a controller without a principal");
  }
}
