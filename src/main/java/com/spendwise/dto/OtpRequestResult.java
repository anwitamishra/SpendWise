package com.spendwise.dto;

public record OtpRequestResult(String maskedEmail, int expiresInSeconds, int resendAfterSeconds) {}