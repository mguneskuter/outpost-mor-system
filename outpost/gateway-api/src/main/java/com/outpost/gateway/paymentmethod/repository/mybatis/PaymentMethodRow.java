package com.outpost.gateway.paymentmethod.repository.mybatis;

/** MyBatis projection of one enabled PSP. */
public record PaymentMethodRow(String pspCode, String name) {}
