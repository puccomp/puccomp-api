package br.com.puccomp.api.identity.token;

import java.time.Instant;
import java.util.UUID;

public record PatCreatedResponse(UUID id, String name, String token, Instant expiresAt) { }
