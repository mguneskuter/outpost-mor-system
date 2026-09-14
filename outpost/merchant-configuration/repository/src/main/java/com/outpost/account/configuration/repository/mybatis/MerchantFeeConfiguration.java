package com.outpost.account.configuration.repository.mybatis;

import org.jspecify.annotations.Nullable;

/** Fee terms as {@code merchant_fee_configuration} stores them, the fee mode as a code. */
record MerchantFeeConfiguration(
    long merchantFeeConfigurationId, String feeMode, int feeRateBps, @Nullable Long feeFixed) {}
