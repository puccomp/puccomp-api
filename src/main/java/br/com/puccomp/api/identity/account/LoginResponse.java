package br.com.puccomp.api.identity.account;

public record LoginResponse(String accessToken, String tokenType, long expiresIn) { }
